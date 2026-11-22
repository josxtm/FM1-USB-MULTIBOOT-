#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
from binascii import crc_hqx
import json
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
import zlib

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'scripts'))
import virtual_slotfile as model


class SlotfileTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.card = self.root / 'card'
        self.fw = self.card / 'FM1' / 'FW'
        self.fw.mkdir(parents=True)
        self.disk = model.VirtualSlotfile(self.root / 'runtime')
        self.disk.create()
        self.initial = self.disk.flash.read_bytes()
        self.set_card(model.fixture_slot('A'), model.fixture_slot('B'))

    def set_card(self, *slots):
        lines = list(model.CATALOG_HEADER)
        for ident, data in zip(('A', 'B'), slots):
            (self.fw / f'{ident}.BIN').write_bytes(data)
            lines.append(f'{ident}|Demo {ident}|1.0|{ident}.BIN|{len(data)}|{model.sha(data)}|UNVERIFIED')
        self.index.write_text('\n'.join(lines) + '\n', encoding='ascii')

    @property
    def index(self):
        return self.card / 'FM1' / 'INDEX.CSV'

    def assert_protected(self, disk):
        data = disk.flash.read_bytes()
        for lo, hi in model.POLICY.protected:
            self.assertEqual(data[lo:hi], self.initial[lo:hi])

    def test_catalog_conformance_matches_shared_fixtures(self):
        for path in sorted((ROOT / 'tests' / 'fixtures' / 'fm1cat').glob('*.csv')):
            with self.subTest(path=path.name):
                self.index.write_bytes(path.read_bytes())
                if path.name.startswith('valid-'):
                    self.assertIsInstance(model.catalog(self.card), dict)
                else:
                    with self.assertRaises(ValueError):
                        model.catalog(self.card)

    def test_select_confirm_replace_and_keep_protected_bytes(self):
        self.assertEqual(list(model.catalog(self.card)), ['A', 'B'])
        self.assertEqual(self.disk.install(self.card, 'A')['status'], 'CONFIRMATION_REQUIRED')
        self.assertEqual(self.disk.flash.read_bytes(), self.initial)
        self.assertEqual(list(self.disk.backups.iterdir()), [])
        a = self.disk.install(self.card, 'A', confirmed=True)
        self.assertEqual(a['status'], 'COMMITTED')
        self.assertEqual(a['boot']['boot'], 'INSTALLED_FIRMWARE')
        self.assertFalse(a['boot']['selector_available'])
        self.assertEqual(self.disk.install(self.card, 'A')['status'], 'ALREADY_INSTALLED')
        b = model.VirtualSlotfile(self.disk.root).install(self.card, 'B', confirmed=True)
        self.assertEqual(b['active']['id'], 'B')
        self.assertEqual(self.disk.flash.read_bytes()[model.START + model.SECTOR:model.START + 2 * model.SECTOR],
                         b'\xff' * model.SECTOR)
        self.assert_protected(self.disk)

    def test_corrupt_candidate_and_ineligible_format_never_open_transaction(self):
        data = model.fixture_slot('A')
        (self.fw / 'A.BIN').write_bytes(data[:-1] + b'\0')
        with self.assertRaisesRegex(ValueError, 'SHA-256'):
            self.disk.install(self.card, 'A', confirmed=True)
        self.set_card(b'synthetic APP1 payload APP1')
        with self.assertRaisesRegex(ValueError, 'expected SLOT'):
            self.disk.install(self.card, 'A', confirmed=True)
        self.assertEqual(self.disk.flash.read_bytes(), self.initial)
        self.assert_no_transaction()

    def test_slot_rejects_invalid_header_payload_bitmap_and_jedec(self):
        valid = model.fixture_slot('A')
        def changed(offset, value, fresh_crc=True):
            out = bytearray(valid)
            out[offset] = value
            if fresh_crc:
                struct.pack_into('<I', out, 508, zlib.crc32(out[:508]))
            return out
        self.assertEqual(len(model.decode_slot(valid)), 2)
        outside = changed(16, 0x10)
        outside[18] = 1  # Sector 16 (0x10000), just past the virtual write window.
        struct.pack_into('<I', outside, 508, zlib.crc32(outside[:508]))
        for data, reason in ((changed(7, 0), 'size'), (changed(12, 0x17), 'JEDEC'),
                             (changed(48, 0, False), 'header CRC32'),
                             (changed(model.HEADER + 100, 1), 'data CRC32'),
                             (changed(16, 0x11), 'bitmap'),
                             (outside, 'synthetic fixture profile')):
            with self.subTest(reason=reason):
                self.set_card(data)
                with self.assertRaisesRegex(ValueError, reason):
                    self.disk.install(self.card, 'A', confirmed=True)
        self.assertEqual(self.disk.flash.read_bytes(), self.initial)
        self.assert_no_transaction()

    def assert_no_transaction(self):
        self.assertIsNone(json.loads(self.disk.journal.read_bytes())['pending'])
        self.assertEqual(list(self.disk.backups.iterdir()), [])

    def test_all_persistence_boundaries_are_reopened_from_disk(self):
        for cut in model.CUTS:
            with self.subTest(cut=cut):
                disk = model.VirtualSlotfile(self.root / cut)
                disk.create()
                self.assertEqual(disk.install(self.card, 'B', confirmed=True, cut=cut)['stage'], cut)
                disk = model.VirtualSlotfile(disk.root)
                self.assert_protected(disk)
                if cut in ('first-erase', 'body-erase', 'body'):
                    self.assertEqual(disk.flash.read_bytes()[model.START:model.START + model.SECTOR],
                                     b'\xff' * model.SECTOR)
                if cut in ('backup', 'committed'):
                    self.assertEqual(disk.status()['active']['id'], 'B' if cut == 'committed' else 'CURRENT')
                    self.assertEqual(disk.recover()['status'], 'NO_PENDING')
                else:
                    self.assertEqual(disk.status()['status'], 'RECOVERY_REQUIRED')
                    self.assertEqual(disk.install(self.card, 'A', confirmed=True)['status'], 'RECOVERY_REQUIRED')
                    self.assertEqual(disk.recover()['status'], 'RECOVERED')
                    self.assertEqual(disk.flash.read_bytes(), self.initial)
                    self.assertEqual(disk.status()['active']['id'], 'CURRENT')

    def test_restore_is_idempotent_and_independent_of_catalog(self):
        self.disk.install(self.card, 'A', confirmed=True, cut='first-program')
        shutil.rmtree(self.card)
        self.assertEqual(self.disk.recover(cut_after_restore=True)['stage'], 'restored')
        self.assertEqual(self.disk.flash.read_bytes(), self.initial)
        restarted = model.VirtualSlotfile(self.disk.root)
        self.assertEqual(restarted.status()['status'], 'RECOVERY_REQUIRED')
        self.assertEqual(restarted.recover()['status'], 'RECOVERED')
        self.assertEqual(restarted.recover()['status'], 'NO_PENDING')

    def test_missing_or_corrupt_backup_remains_recovery_required(self):
        self.disk.install(self.card, 'A', confirmed=True, cut='body-erase')
        pending = self.disk.journal.read_bytes()
        interrupted = self.disk.flash.read_bytes()
        backup, = self.disk.backups.iterdir()
        backup.write_bytes(b'corrupt backup')
        self.assertEqual(self.disk.recover()['status'], 'RECOVERY_REQUIRED')
        backup.unlink()
        self.assertEqual(self.disk.recover()['status'], 'RECOVERY_REQUIRED')
        self.assertEqual(self.disk.flash.read_bytes(), interrupted)
        self.assertEqual(self.disk.journal.read_bytes(), pending)

    def test_altered_protected_virtual_data_is_not_restored_silently(self):
        self.disk.install(self.card, 'A', confirmed=True, cut='journal')
        tampered = bytearray(self.disk.flash.read_bytes())
        tampered[model.KEY_MAC_START] ^= 1
        self.disk.flash.write_bytes(tampered)
        self.assertEqual(self.disk.recover()['status'], 'RECOVERY_REQUIRED')
        self.assertEqual(self.disk.flash.read_bytes(), tampered)

    def test_rejects_symlink_payload(self):
        slot = self.fw / 'A.BIN'
        alternate = slot.with_suffix('.dat')
        slot.rename(alternate)
        slot.symlink_to(alternate.name)
        with self.assertRaises(OSError):
            self.disk.install(self.card, 'A', confirmed=True)
        self.assert_no_transaction()

    def test_cli_demo_records_commit_and_recovery(self):
        result = subprocess.check_output([sys.executable, str(ROOT / 'scripts' / 'virtual_slotfile.py'),
                                          'demo', str(self.root / 'demo')])
        trace = json.loads(result)
        self.assertEqual([step['status'] for step in trace], [
            'CREATED', 'CONFIRMATION_REQUIRED', 'COMMITTED', 'INTERRUPTED', 'RECOVERY_REQUIRED',
            'RECOVERED', 'BOOTABLE_IN_MODEL', 'COMMITTED', 'BOOTABLE_IN_MODEL'])

    def test_recovery_selector_respects_transport_menu_and_stage2_crc(self):
        classify = model.RecoveryModel().recovery_selector_v24
        body = b'synthetic envelope only'
        stage2 = body + b'STG2' + crc_hqx(body, 0).to_bytes(2, 'little') + b'\0\0'
        self.assertEqual(classify(source='usb-c', menu_open=True, spi_card=True, stage2=stage2)['next'],
                         'NO_PREBOOT_USB_MSC')
        self.assertEqual(classify(source='m-card-spi', menu_open=False, spi_card=True, stage2=stage2)['next'],
                         'BOOT_APP_PATH')
        self.assertEqual(classify(source='m-card-spi', menu_open=True)['next'], 'RECOVERY_MENU_WITHOUT_SD')
        for invalid in (b'', b'X' + stage2[1:], stage2[:-1] + b'X', b'\0' * 0x8001):
            outcome = classify(source='m-card-spi', menu_open=True, spi_card=True, stage2=invalid)
            self.assertEqual(outcome['next'], 'RECOVERY_MENU_STAGE2_INVALID')
        outcome = classify(source='m-card-spi', menu_open=True, spi_card=True, stage2=stage2)
        self.assertEqual(outcome['next'], 'STAGE2_ENVELOPE_ACCEPTED_IN_MODEL')
        self.assertFalse(outcome['physical_verified'])
        self.assertFalse(outcome['selector_invoked'])


if __name__ == '__main__':
    unittest.main()
