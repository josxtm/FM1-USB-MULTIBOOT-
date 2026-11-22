#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
import importlib.util
from pathlib import Path
import struct
import unittest

SPEC = importlib.util.spec_from_file_location('validate_stg2', Path(__file__).resolve().parents[1] /
                                            'scripts' / 'validate_stg2.py')
STG2 = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(STG2)


class Stage2ImageTest(unittest.TestCase):
    def test_matches_known_v24_crc_and_trailer(self):
        body = bytes((i * 7 + 3) & 255 for i in range(16))
        self.assertEqual(STG2.wrap(body), body + b'STG2\xdf\xa6\0\0')
        STG2.check_trailer(STG2.wrap(body))

    def test_minimum_and_limit_include_trailer(self):
        for size in (8, 32760):
            self.assertEqual(len(STG2.wrap(bytes(size))), size + 8)
        for size in (7, 32761):
            with self.assertRaises(ValueError):
                STG2.wrap(bytes(size))

    def test_rejects_corruption_app1_and_truncated_image(self):
        image = STG2.wrap(b'12345678')
        for bad in (b'X' + image[1:], image[:-1], image[:-1] + b'X', image[:8] + b'APP1' + image[-4:]):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                STG2.check_trailer(bad)

    def test_rejects_rom_ram_load_address_and_non_pi32(self):
        elf = bytearray(64)
        elf[:7] = b'\x7fELF\x01\x01\x01'
        payload = b'12345678'
        for address, machine in ((0x01C30000, 241), (0x01C28000, 3)):
            struct.pack_into('<HHIII', elf, 16, 2, machine, 1, address, 52)
            with self.assertRaisesRegex(ValueError, 'expected pi32v2 executable'):
                STG2.validate(elf, payload, STG2.wrap(payload), '')


if __name__ == '__main__':
    unittest.main()
