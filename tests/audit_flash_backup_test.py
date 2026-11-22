#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
import hashlib
import importlib.util
import os
import struct
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPEC = importlib.util.spec_from_file_location(
    'audit_flash_backup', os.path.join(ROOT, 'scripts', 'audit_flash_backup.py'))
MOD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MOD)


class AuditFlashBackupTest(unittest.TestCase):
    def valid_backup(self):
        flash = bytearray(b'\xff' * MOD.SIZE)
        header = bytearray(32)
        header[8:12] = struct.pack('<I', 0xFF000)
        header[12] = 0x10
        header[13] = 16
        header[0:2] = struct.pack('<H', MOD.crc16(header[2:]))
        flash[:32] = MOD.enc(header)
        return bytes(flash)

    def test_full_backup_header_and_reserved_regions(self):
        backup = self.valid_backup()
        result = MOD.audit(backup, hashlib.sha256(backup).hexdigest())
        self.assertTrue(result['read_only'])
        self.assertTrue(result['checks']['capacity_1MiB'])
        self.assertTrue(result['checks']['boot_header'])
        self.assertTrue(result['checks']['key_mac_present'])
        self.assertTrue(result['checks']['expected_sha256'])
        self.assertEqual(result['regions']['boot'], [0, 0x4000])

    def test_short_or_wrong_backup_never_looks_complete(self):
        result = MOD.audit(b'not-a-device-backup')
        self.assertFalse(result['checks']['capacity_1MiB'])
        self.assertFalse(result['checks']['boot_header'])
        self.assertIn('full 1 MiB backup required for complete audit', result['warnings'])


if __name__ == '__main__':
    unittest.main()
