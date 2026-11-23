# SPDX-License-Identifier: MIT
import binascii
import contextlib
import io
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import fm1_hardware_preflight as preflight


class ArtifactTests(unittest.TestCase):
    def test_wrong_zip_hash_refused_before_opening_zip(self):
        with tempfile.TemporaryDirectory() as root:
            bundle = Path(root) / "wrong.zip"
            bundle.write_bytes(b"PK wrong")
            with patch.object(preflight.zipfile, "ZipFile", side_effect=AssertionError):
                evidence = preflight.report(bundle)
        self.assertEqual(evidence["checks"]["package"]["status"], "FAIL")
        self.assertIn("ZIP SHA-256", evidence["checks"]["package"]["detail"])
        self.assertEqual(evidence["checks"]["elf_ram_and_entry"]["status"], "NOT VERIFIED")

    def test_wrong_component_hash(self):
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            preflight.check_files(lambda _: b"wrong", {"diagnostic.elf": preflight.sha256(b"expected")})

    def test_wrong_crc(self):
        with self.assertRaisesRegex(ValueError, "CRC16"):
            preflight.check_app1(bytes(8) + b"APP1" + b"\x00\x00\x01\x01")

    def test_wrong_board_rejected_before_crc(self):
        with self.assertRaisesRegex(ValueError, "FM-1"):
            preflight.check_app1(bytes(8) + b"APP1" + b"\x86\xb7\x02\x01")

    def test_oversize(self):
        with self.assertRaisesRegex(ValueError, "size"):
            preflight.check_app1(bytes(8194))

    def test_incompatible_elf(self):
        with self.assertRaisesRegex(ValueError, "ELF32"):
            preflight.check_link(b"invalid", b"", b"")

    def test_wrong_load_address(self):
        elf = bytearray(52)
        elf[:7] = b"\x7fELF\x01\x01\x01"
        struct.pack_into("<HHIII", elf, 16, 2, 241, 1, 0x01C28000, 52)
        with self.assertRaisesRegex(ValueError, "pi32v2 at APP"):
            preflight.check_link(elf, b"", b"")

    def test_cannot_substitute_fresh_crc_for_known_hash(self):
        payload = b"altered!"
        corrupt = payload + struct.pack("<4sHBB", b"APP1", binascii.crc_hqx(payload, 0), 1, 1)
        with self.assertRaisesRegex(ValueError, "CRC16"):
            preflight.check_app1(corrupt)
        self.assertNotEqual(preflight.sha256(corrupt), preflight.IMAGE_DIGEST)


class HostTests(unittest.TestCase):
    def test_source_version_mismatch(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            (root / "Cargo.toml").write_text('[package]\nversion = "0.12.2"\n')
            with self.assertRaisesRegex(ValueError, "unexpected M-Flasher version"):
                preflight.check_mflasher_source(root)

    def test_missing_dependency_does_not_become_pass(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            missing = root / "missing"
            with patch.object(preflight, "host_profile", return_value=("linux", "x86_64")):
                evidence = preflight.report(root, missing, None)
        self.assertEqual(evidence["checks"]["mflasher_source"]["status"], "FAIL")
        self.assertEqual(evidence["checks"]["mflasher_release"]["status"], "NOT VERIFIED")
        self.assertEqual(evidence["checks"]["flash_command_audit"]["status"], "NOT VERIFIED")
        self.assertEqual(evidence["physical_status"], "BLOCKED")

    def test_fake_version_string_or_wrong_arch_cannot_approve_package(self):
        with tempfile.TemporaryDirectory() as root:
            asset = Path(root) / "m-flasher-0.12.1-linux-x86_64.tar.gz"
            asset.write_bytes(b"0.12.1")
            with self.assertRaisesRegex(ValueError, "unexpected release SHA-256"):
                preflight.check_host_release(asset, ("linux", "x86_64"))
            with self.assertRaisesRegex(ValueError, "no audited"):
                preflight.check_host_release(asset, ("android", "arm64"))

    def test_linux_runtime_minimum(self):
        for version in ("glibc 2.34", None):
            with patch.object(preflight.os, "confstr", return_value=version, create=True):
                with self.assertRaisesRegex(ValueError, "requires glibc"):
                    preflight.check_linux_runtime()
        with patch.object(preflight.os, "confstr", return_value="glibc 2.35", create=True):
            self.assertIn("glibc 2.35", preflight.check_linux_runtime())

    def test_report_is_reproducible_offline_json(self):
        with tempfile.TemporaryDirectory() as root:
            argv = ["fm1_hardware_preflight.py", str(Path(root) / "missing.zip")]
            output = []
            for _ in range(2):
                with patch.object(sys, "argv", argv), contextlib.redirect_stdout(io.StringIO()) as stream:
                    self.assertEqual(preflight.main(), 1)
                    output.append(stream.getvalue())
        self.assertEqual(output[0], output[1])
        evidence = json.loads(output[0])
        self.assertEqual(evidence["mode"], "DRY-RUN OFFLINE")
        self.assertFalse(evidence["hardware_io"])
        self.assertFalse(evidence["physical_launch_available"])
        self.assertEqual(evidence["physical_status"], "BLOCKED")

    def test_cli_has_no_hardware_escape_hatch(self):
        with patch.object(sys, "argv", ["preflight", ".", "--execute"]):
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as error:
                preflight.main()
        self.assertEqual(error.exception.code, 2)


class TraceTests(unittest.TestCase):
    def test_faults_stay_distinct(self):
        self.assertEqual(preflight.simulate_policy(), {
            "complete": "SIMULATED_COMPLETE",
            "ambiguous_device": "DEVICE_AMBIGUOUS_OR_ABSENT",
            "unknown_rom": "ROM_UNVERIFIED",
            "connection_error": "CONNECTION_ERROR",
            "short_upload": "INCOMPLETE_TRANSFER",
            "timeout": "TIMEOUT",
            "disconnect": "DISCONNECTED",
            "missing_jump_reply": "EXECUTION_UNCONFIRMED",
            "recovery_unconfirmed": "RECOVERY_UNCONFIRMED",
            "flash_command": "COMMAND_FORBIDDEN_OR_OUT_OF_ORDER",
        })

    def test_flash_and_unreviewed_commands_rejected(self):
        for opcode in ("FB00", "FB01", "FB02", "FB04", "FC09", "FC0A", "FC12", "FD05", "FD61"):
            with self.subTest(opcode=opcode):
                self.assertEqual(preflight.evaluate_trace(1, True, [("1200", "OK", 36), (opcode, "OK", 16)]),
                                 "COMMAND_FORBIDDEN_OR_OUT_OF_ORDER")

    def test_no_retry_and_no_early_jump(self):
        prefix = [("1200", "OK", 36), ("FC40", "OK", 16)]
        self.assertEqual(preflight.evaluate_trace(1, True, prefix + [("FB08", "OK", 16)]),
                         "COMMAND_FORBIDDEN_OR_OUT_OF_ORDER")
        blocks = [("FB06", "OK", n) for n in preflight.BLOCKS]
        twice = prefix + blocks + [("FB08", "OK", 16)] * 2
        self.assertEqual(preflight.evaluate_trace(1, True, twice, True, True), "COMMAND_FORBIDDEN_OR_OUT_OF_ORDER")

    def test_successful_ack_without_screen_is_unconfirmed(self):
        prefix = [("1200", "OK", 36), ("FC40", "OK", 16)]
        data = prefix + [("FB06", "OK", n) for n in preflight.BLOCKS] + [("FB08", "OK", 16)]
        self.assertEqual(preflight.evaluate_trace(1, True, data, False, True), "EXECUTION_UNCONFIRMED")


if __name__ == "__main__":
    unittest.main()
