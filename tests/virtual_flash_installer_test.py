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
    def policy(self):
        # Deliberately synthetic test profile: unknown regions on both sides
        # remain protected; this is not the physical FM-1 map.
        return MOD.FlashRegionPolicy([(0x8000, 0xF0000)])

    def test_default_policy_rejects_erase_without_documented_region(self):
        self.assertEqual(MOD.VirtualInstaller().install(app1(), confirmed=True)['result'], 'REJECTED')

    def test_rejects_opaque_bin_and_bad_crc(self):
        installer = MOD.VirtualInstaller(policy=self.policy())
        self.assertEqual(installer.install(b'opaque').get('result'), 'REJECTED')
        bad = bytearray(app1()); bad[0] ^= 1
        self.assertEqual(installer.install(bytes(bad)).get('result'), 'REJECTED')

    def test_confirmation_commit_reboot_and_already_installed(self):
        image = app1(b'new firmware')
        installer = MOD.VirtualInstaller(policy=self.policy())
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
            installer = MOD.VirtualInstaller(policy=self.policy())
            before = bytes(installer.flash[:MOD.PROTECTED_END])
            result = installer.install(image, confirmed=True, fault=fault)
            self.assertEqual(result['result'], 'RECOVERED')
            self.assertTrue(result['protected_unchanged'])
            self.assertEqual(bytes(installer.flash[:MOD.PROTECTED_END]), before)

    def test_recovery_unavailable_is_not_called_rollback(self):
        result = MOD.VirtualInstaller(recovery_available=False, policy=self.policy()).install(
            app1(b'no rollback'), confirmed=True, fault='cut-after-program')
        self.assertEqual(result['result'], 'RECOVERY_REQUIRED')
        self.assertEqual(result['state'], 'RECOVERY_REQUIRED')
        self.assertTrue(result['protected_unchanged'])

    def test_factory_and_configuration_regions_survive_commit(self):
        flash = bytearray(b'\xff' * MOD.FLASH_SIZE)
        flash[0x4000:0x8000] = (b'FACTORY' * ((0x4000 + 6) // 7))[:0x4000]
        flash[0xF0000:0x100000] = (b'CONFIG' * ((0x10000 + 5) // 6))[:0x10000]
        installer = MOD.VirtualInstaller(flash=flash, policy=self.policy())
        factory = bytes(flash[0x4000:0x8000])
        config = bytes(flash[0xF0000:0x100000])
        result = installer.install(app1(b'preserve regions'), confirmed=True)
        self.assertEqual(result['result'], 'COMMITTED')
        self.assertEqual(bytes(installer.flash[0x4000:0x8000]), factory)
        self.assertEqual(bytes(installer.flash[0xF0000:0x100000]), config)
        self.assertTrue(result['protected_unchanged'])


if __name__ == '__main__':
    unittest.main()
