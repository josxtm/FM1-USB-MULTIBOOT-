# SPDX-License-Identifier: MIT
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import zlib

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import fm1_sd_package as sd
from validate_stg2 import wrap
from virtual_slotfile import HEADER, fixture_slot


class PackageTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.menu = self.root / "selector.bin"
        self.archive = self.root / "package.zip"
        raw = wrap(bytes(range(128)) * 9)
        self.menu.write_bytes(raw)
        digest = patch.object(sd, "STG2_SHA", sd.sha(raw))
        digest.start()
        self.addCleanup(digest.stop)

    def rewrite(self, change):
        with zipfile.ZipFile(self.archive) as original:
            content = [(name, original.read(name)) for name in original.namelist()]
        with zipfile.ZipFile(self.archive, "w") as altered:
            for name, data in change(content):
                sd.put_zip(altered, name, data)

    def test_empty_preserves_required_structure(self):
        sd.build(self.archive, self.menu)
        summary, menu, index, firmware, _ = sd.read_package(self.archive)
        self.assertEqual(summary["mode"], "EMPTY")
        self.assertEqual(summary["launch_policy"], "BLOCKED")
        self.assertEqual(menu, self.menu.read_bytes())
        self.assertEqual(index.decode().splitlines(), sd.CATALOG_HEADER)
        self.assertFalse(firmware)
        with zipfile.ZipFile(self.archive) as archive:
            self.assertEqual(set(archive.namelist()), {*sd.DIRS, sd.SELECTOR, sd.INDEX, sd.REPORT})

    def test_synthetic_zip_is_reproducible(self):
        repeat = self.root / "repeat.zip"
        sd.build(self.archive, self.menu, demo=True)
        sd.build(repeat, self.menu, demo=True)
        self.assertEqual(self.archive.read_bytes(), repeat.read_bytes())
        summary, _, _, files, _ = sd.read_package(self.archive)
        self.assertEqual(summary["mode"], "SYNTHETIC")
        self.assertEqual(files, {name: fixture_slot(name) for name in sd.DEMO_IDS})
        with self.assertRaises(FileExistsError):
            sd.build(self.archive, self.menu, demo=True)

    def test_unapproved_selector_with_valid_stg2_trailer(self):
        self.menu.write_bytes(wrap(b"modified"))
        with self.assertRaisesRegex(ValueError, "selector SHA-256"):
            sd.build(self.archive, self.menu)
        self.assertFalse(self.archive.exists())

    def test_missing_firmware_and_path_escape(self):
        sd.build(self.archive, self.menu, demo=True)
        original = self.archive.read_bytes()
        for invalid in (
            lambda members: [(p, b) for p, b in members if not p.endswith("DEMOA.BIN")],
            lambda members: members + [("../escape", b"x")],
        ):
            with self.subTest(extra=invalid):
                self.archive.write_bytes(original)
                self.rewrite(invalid)
                with self.assertRaisesRegex(ValueError, "ZIP path"):
                    sd.read_package(self.archive)

    def test_corruption_with_fresh_zip_crc_fails(self):
        sd.build(self.archive, self.menu, demo=True)
        self.rewrite(lambda members: [(p, bytes(len(b)) if p.endswith("DEMOA.BIN") else b) for p, b in members])
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            sd.read_package(self.archive)

    def test_catalog_compatibility_cannot_be_upgraded_in_zip(self):
        sd.build(self.archive, self.menu, demo=True)
        self.rewrite(lambda members: [(p, b.replace(b"UNVERIFIED", b"VERIFIED") if p == sd.INDEX else b)
                                      for p, b in members])
        with self.assertRaisesRegex(ValueError, "FM1CAT"):
            sd.read_package(self.archive)

    def private_inputs(self, slot, **changes):
        source = self.root / "firmware"
        source.mkdir(exist_ok=True)
        (source / "LOCAL.BIN").write_bytes(slot)
        review = self.root / "review.json"
        row = {"id": "LOCAL", "label": "Local Sample", "version": "1", "size": len(slot), "sha256": sd.sha(slot),
               "format": "SLOT-v1", "source": "Independent lab archive test double"}
        row.update(changes)
        review.write_bytes(sd.json_bytes({"schema": 1, "target": sd.TARGET, "entries": [row]}))
        return review, source

    def test_private_requires_same_external_review(self):
        # This private profile test double is fabricated and never packaged for release.
        slot = bytearray(fixture_slot("LOCAL"))
        slot[HEADER] = ord("N")
        struct.pack_into("<I", slot, 112, zlib.crc32(slot[HEADER:]))
        struct.pack_into("<I", slot, 508, zlib.crc32(slot[:508]))
        review, source = self.private_inputs(bytes(slot))
        sd.build(self.archive, self.menu, review=review, source=source)
        with self.assertRaisesRegex(ValueError, "independent local review"):
            sd.read_package(self.archive)
        summary, _, _, _, _ = sd.read_package(self.archive, review)
        self.assertEqual((summary["mode"], summary["entries"][0]["compat"]), ("PRIVATE_REVIEWED", "UNVERIFIED"))

    def test_private_rejects_fixture_opaque_and_wrong_expected_hash(self):
        for slot, changes, message in (
            (fixture_slot("LOCAL"), {}, "synthetic content"),
            (bytes(640), {}, "SLOT v1"),
            (fixture_slot("LOCAL"), {"sha256": "e" * 64}, "SHA-256"),
            (fixture_slot("LOCAL"), {"format": "FWSC"}, "unsupported format"),
            (fixture_slot("LOCAL"), {"id": "CON"}, "reserved"),
        ):
            with self.subTest(message=message):
                review, source = self.private_inputs(slot, **changes)
                with self.assertRaisesRegex(ValueError, message):
                    sd.build(self.archive, self.menu, review=review, source=source)

    def test_private_still_checks_header_and_payload_crc(self):
        slot = bytearray(fixture_slot("LOCAL"))
        slot[-1] ^= 1
        review, source = self.private_inputs(bytes(slot))
        with self.assertRaisesRegex(ValueError, "bad data CRC32"):
            sd.build(self.archive, self.menu, review=review, source=source)

    def test_private_rejects_wrong_board_and_jedec(self):
        slot = bytearray(fixture_slot("LOCAL"))
        slot[5] = 2
        review, source = self.private_inputs(bytes(slot))
        with self.assertRaisesRegex(ValueError, "FM-1"):
            sd.build(self.archive, self.menu, review=review, source=source)
        original = sd.parse_json(review.read_bytes())
        original["target"]["jedec"] = "FFFFFF"
        review.write_bytes(sd.json_bytes(original))
        with self.assertRaisesRegex(ValueError, "profile"):
            sd.build(self.archive, self.menu, review=review, source=source)


if __name__ == "__main__":
    unittest.main()
