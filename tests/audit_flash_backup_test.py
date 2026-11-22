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

    def test_synthetic_config_and_application_directory(self):
        flash = bytearray(self.valid_backup())
        config = bytearray(96)
        config[0:32] = bytes(range(32))
        config[32:34] = struct.pack('<H', MOD.crc16(config[:32]))
        flash[0x3FA0:0x4000] = config

        def make_entry(name, address, length, attr, data_crc=0):
            raw = bytearray(32)
            raw[2:4] = struct.pack('<H', data_crc)
            raw[4:8] = struct.pack('<I', address)
            raw[8:12] = struct.pack('<I', length)
            raw[12] = attr
            raw[16:16 + len(name)] = name.encode('ascii')
            raw[0:2] = struct.pack('<H', MOD.crc16(raw[2:]))
            return bytes(raw)

        boot_payload = b'BOOT-CONTAINER!!'
        flash[0xA0:0xB0] = MOD.enc(boot_payload)
        records = [
            ('uboot.boot', 0xA0, 16, 0x02, MOD.crc16(boot_payload)),
            ('isd_config.ini', 0x3FA0, 96, 0x02, MOD.crc16(config)),
            ('app_dir_head', 0x4000, 0xFFFFFFFF, 0x81, 0),
            ('key_mac', 0xFF000, 0x1000, 0x02, 0),
        ]
        for i, record in enumerate(records):
            flash[0x20 + i * 32:0x40 + i * 32] = MOD.enc(make_entry(*record))
        # A zero-key synthetic app table is sufficient to test table decoding.
        key = MOD.decode_unit_key(config)
        app_records = [
            make_entry('app_area_head', 0x02000120, 0x8F000, 0x83),
            make_entry('app.bin', 0x120, 0x1000, 0x82, MOD.crc16(b'A' * 0x1000)),
            make_entry('cfg_tool.bin', 0x1120, 0x1000, 0x82, MOD.crc16(b'B' * 0x1000)),
            make_entry('PRCT', 0x4000, 0x3000, 0x12),
            make_entry('VM', 0x93000, 0x1000, 0x12),
            make_entry('BTIF', 0xE9000, 0x1000, 0x12),
            make_entry('USR', 0xEA000, 0x1000, 0x12),
        ]
        for i, raw in enumerate(app_records):
            offset = 0x4000 + i * 32
            flash[offset:offset + 32] = MOD.enc(raw, key ^ ((i * 32 // 4) & 0xffff))
        for rel, payload in ((0x120, b'A' * 0x1000), (0x1120, b'B' * 0x1000)):
            for off in range(0, len(payload), 32):
                physical = 0x4000 + rel + off
                flash[physical:physical + 32] = MOD.enc(payload[off:off + 32], key ^ (((rel + off) >> 2) & 0xffff))
        result = MOD.audit(bytes(flash))
        self.assertTrue(result['checks']['configuration'])
        self.assertTrue(result['checks']['app_table'])
        self.assertTrue(result['checks']['app_files_crc'])
        uboot = next(entry for entry in result['boot_table'] if entry['name'] == 'uboot.boot')
        self.assertTrue(uboot['data_crc_valid'])
        self.assertEqual(uboot['data_crc_kind'], 'plain_container_crc16')
        self.assertTrue(any(item['first'] == 'PRCT' or item['second'] == 'PRCT'
                            for item in result['map_nesting']))
        names = [entry['name'] for entry in result['app_table']]
        self.assertEqual(names[:3], ['app_area_head', 'app.bin', 'cfg_tool.bin'])
        self.assertFalse(result['privacy']['sensitive_values_emitted'])

    def test_nested_and_invalid_overlap_are_distinct(self):
        report = MOD.ranges_overlap([
            {'name': 'PRCT', 'category': 'PRCT', 'physical_start': 0x4000, 'physical_end': 0x9000},
            {'name': 'app.bin', 'category': 'FILE', 'physical_start': 0x4120, 'physical_end': 0x8000},
            {'name': 'VM', 'category': 'VM', 'physical_start': 0x9000, 'physical_end': 0xA000},
            {'name': 'bad', 'category': 'FILE', 'physical_start': 0x9800, 'physical_end': 0xB000},
        ])
        self.assertEqual(len(report['expected_nesting']), 1)
        self.assertEqual(len(report['invalid']), 1)

    def test_short_or_wrong_backup_never_looks_complete(self):
        result = MOD.audit(b'not-a-device-backup')
        self.assertFalse(result['checks']['capacity_1MiB'])
        self.assertFalse(result['checks']['boot_header'])
        self.assertIn('full 1 MiB backup required for complete audit', result['warnings'])


if __name__ == '__main__':
    unittest.main()
