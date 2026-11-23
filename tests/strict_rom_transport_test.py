# SPDX-License-Identifier: MIT
import binascii
import concurrent.futures
from hashlib import sha256
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import strict_rom_transport as rom


class TransportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # A fabricated payload exercises the model. CI separately runs the frozen
        # binary through preflight and through the actual command-line entry.
        body = bytes((i % 256 for i in range(rom.IMAGE_SIZE - 10)))
        before = binascii.crc_hqx(body, 0)
        suffix = next(i.to_bytes(2, "little") for i in range(65536)
                      if binascii.crc_hqx(i.to_bytes(2, "little"), before) == 0xB786)
        cls.image = body + suffix + b"APP1\x86\xb7\x01\x01"

    def setUp(self):
        self.patch = patch.object(rom, "IMAGE_DIGEST", sha256(self.image).hexdigest())
        self.patch.start()
        self.addCleanup(self.patch.stop)

    def test_cdb_golden_and_block_schedule(self):
        commands = list(rom.plan(self.image))
        self.assertEqual(commands[0].frame(1).hex(),
                         "55534243010000002400000080000612000000240000000000000000000000")
        self.assertEqual(commands[1].cdb.hex(), "fc40ffffffffffffffffffffffffffff")
        self.assertEqual(commands[-1].cdb.hex(), "fb0801c300000001ffffffffffffffff")
        self.assertEqual([c.size for c in commands[2:-1]], [512] * 8 + [4])
        self.assertEqual([struct.unpack_from(">I", c.cdb, 2)[0] for c in commands[2:-1]],
                         [rom.APP + i * 512 for i in range(9)])
        self.assertEqual(rom.transform(bytes.fromhex("0001020304050607")).hex(), GOLDEN)

    def test_exact_sequential_transfer(self):
        result = rom.Session().run(self.image)
        self.assertEqual(result["result"], "JUMP_ACKNOWLEDGED_IN_MODEL")
        self.assertEqual(result["attempted"], ["1200", "FC40"] + ["FB06"] * 9 + ["FB08"])
        self.assertEqual((result["blocks"], result["jumps"], result["hardware_io"]), (9, 1, False))

    def test_no_packets_for_wrong_candidate_or_evidence(self):
        for value in (b"", bytearray(self.image), bytes(len(self.image))):
            with self.subTest(value=type(value)):
                self.assertEqual(rom.Session().run(value)["attempted"], [])
        for fault, reason in (("device-absent", "DEVICE_COUNT"), ("device-ambiguous", "DEVICE_COUNT"),
                              ("wrong-interface", "INTERFACE_IDENTITY"), ("unqualified-rom", "ROM_UNQUALIFIED")):
            result = rom.Session(fault).run(self.image)
            self.assertEqual((result["result"], result["attempted"]), (reason, []))

    def test_probe_must_be_explicitly_refused(self):
        for fault, reason in (("resident", "RESIDENT_DETECTED"), ("probe-timeout", "TIMEOUT"),
                              ("probe-ambiguous", "RESIDENT_AMBIGUOUS")):
            result = rom.Session(fault).run(self.image)
            self.assertEqual((result["result"], result["attempted"]), (reason, ["1200", "FC40"]))

    def test_every_fault_is_terminal_with_no_retry(self):
        reasons = {
            "bad-inquiry": "INQUIRY_IDENTITY", "bad-signature": "CSW_IDENTITY", "bad-tag": "CSW_IDENTITY",
            "short-csw": "CSW_LENGTH", "phase-error": "CSW_PHASE_ERROR", "failed-status": "CSW_FAILURE",
            "short-cbw": "SHORT_WRITE", "short-out": "SHORT_WRITE", "residue": "SHORT_TRANSFER",
            "extra-data": "SHORT_TRANSFER", "timeout": "TIMEOUT", "disconnect": "DISCONNECTED",
            "connect-error": "CONNECTION_ERROR", "jump-no-ack": "JUMP_UNCONFIRMED",
            "jump-bad-echo": "JUMP_UNCONFIRMED", "forbidden-request": "REQUEST_DENIED",
        }
        for fault, reason in reasons.items():
            with self.subTest(fault=fault):
                session = rom.Session(fault)
                result = session.run(self.image)
                self.assertEqual(result["result"], reason)
                expected = 2 if fault == "forbidden-request" else rom.STAGED_FAULTS[fault]
                self.assertEqual(len(result["attempted"]), expected)
                self.assertEqual(result["jumps"], int(fault.startswith("jump-")))
                again = session.run(self.image)
                self.assertEqual((again["result"], again["attempted"]), ("SESSION_TERMINAL", []))

    def test_concurrent_calls_share_one_attempt(self):
        session = rom.Session()
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            outputs = list(pool.map(session.run, [self.image, self.image]))
        self.assertEqual({r["result"] for r in outputs}, {"JUMP_ACKNOWLEDGED_IN_MODEL", "SESSION_TERMINAL"})
        self.assertEqual(sum(r["jumps"] for r in outputs), 1)

    def test_wrong_request_opcode_and_each_changed_field_rejected(self):
        cmd = list(rom.plan(self.image))[2]
        cbw = cmd.frame(3)
        for opcode in ("fb00", "fb01", "fb02", "fb04", "fc09", "fc0a", "fc12", "fd05", "fd61"):
            with self.assertRaisesRegex(rom.Stop, "REQUEST_DENIED"):
                rom.check_request(cmd, 3, cbw[:15] + bytes.fromhex(opcode) + cbw[17:], cmd.payload)
        # Any altered header, address, count, CRC, padding or payload is rejected.
        for offset in range(len(cbw)):
            changed = bytearray(cbw)
            changed[offset] ^= 1
            with self.assertRaisesRegex(rom.Stop, "REQUEST_DENIED"):
                rom.check_request(cmd, 3, bytes(changed), cmd.payload)
        with self.assertRaisesRegex(rom.Stop, "REQUEST_DENIED"):
            rom.check_request(cmd, 3, cbw, bytes(len(cmd.payload)))

    def test_out_of_range_or_incomplete_final_ram_chunk_rejected(self):
        for addr, data in ((rom.APP - 512, b"x" * 512), (rom.APP + 1, b"x" * 512),
                           (rom.APP + 4096, b"x" * 5), (rom.RETAINED, b"x" * 4)):
            with self.assertRaisesRegex(rom.Stop, "REQUEST_RANGE"):
                rom.upload(addr, data)

    def test_input_must_be_a_regular_file_before_open(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)
            with patch.object(rom.os, "open", side_effect=AssertionError("must not open")):
                with self.assertRaisesRegex(rom.Stop, "ARTIFACT_NOT_REGULAR"):
                    rom.read_image(path)


# Independent CRC16 bit-loop vector from the pinned M-Flasher algorithm.
GOLDEN = "2fa583cf4ac45bed"
