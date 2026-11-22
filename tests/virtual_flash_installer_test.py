#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
import hashlib
import importlib.util
import os
import struct
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPEC = importlib.util.spec_from_file_location(
    'virtual_flash_installer', os.path.join(ROOT, 'scripts', 'virtual_flash_installer.py'))
MOD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MOD)


def app1(payload=b'candidate-pi32v2'):
    if len(payload) % 2:
        payload += b'\0'
    return payload + b'APP1' + struct.pack('<HBB', MOD.crc16(payload), 1, 1)


class VirtualInstallerTest(unittest.TestCase):
    def test_rejects_opaque_bin_and_bad_crc(self):
        installer = MOD.VirtualInstaller()
        self.assertEqual(installer.install(b'opaque').get('result'), 'REJECTED')
        bad = bytearray(app1()); bad[0] ^= 1
        self.assertEqual(installer.install(bytes(bad)).get('result'), 'REJECTED')

    def test_confirmation_commit_reboot_and_already_installed(self):
        image = app1(b'new firmware')
        installer = MOD.VirtualInstaller()
        self.assertEqual(installer.install(image)['result'], 'CONFIRMATION_REQUIRED')
        result = installer.install(image, hashlib.sha256(image).hexdigest(), True)
        self.assertEqual(result['result'], 'COMMITTED')
        self.assertTrue(result['protected_unchanged'])
        self.assertFalse(result['reboot']['selector_available'])
        self.assertEqual(installer.install(image, confirmed=True)['result'], 'ALREADY_INSTALLED')

    def test_power_cuts_recover_and_protect_boot_region(self):
        image = app1(b'power cut')
        for fault in ('cut-before-commit', 'cut-after-erase', 'cut-after-program',
                      'corrupt-after-program'):
            installer = MOD.VirtualInstaller()
            before = bytes(installer.flash[:MOD.PROTECTED_END])
            result = installer.install(image, confirmed=True, fault=fault)
            self.assertEqual(result['result'], 'RECOVERED')
            self.assertTrue(result['protected_unchanged'])
            self.assertEqual(bytes(installer.flash[:MOD.PROTECTED_END]), before)

    def test_recovery_unavailable_is_not_called_rollback(self):
        result = MOD.VirtualInstaller(recovery_available=False).install(
            app1(b'no rollback'), confirmed=True, fault='cut-after-program')
        self.assertEqual(result['result'], 'RECOVERY_REQUIRED')
        self.assertEqual(result['state'], 'RECOVERY_REQUIRED')
        self.assertTrue(result['protected_unchanged'])


if __name__ == '__main__':
    unittest.main()
