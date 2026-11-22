#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
import importlib.util
import os
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPEC = importlib.util.spec_from_file_location(
    'boot_recovery_model', os.path.join(ROOT, 'scripts', 'boot_recovery_model.py'))
MOD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MOD)


class BootRecoveryModelTest(unittest.TestCase):
    def setUp(self):
        self.model = MOD.RecoveryModel()

    def test_normal_reboot_does_not_claim_selector_return(self):
        result = self.model.simulate()
        self.assertEqual(result['boot'], 'INSTALLED_FIRMWARE')
        self.assertFalse(result['selector_available'])
        self.assertFalse(result['recovery_success'])
        self.assertEqual(result['decision'], 'NO-GO')

    def test_corruption_and_power_cut_are_unproven_recovery(self):
        for kwargs in ({'corrupt': True}, {'power_cut': True, 'sd_available': False}):
            result = self.model.simulate(**kwargs)
            self.assertEqual(result['boot'], 'BOOT_FAILURE_OR_BOOTLOADER_RECOVERY')
            self.assertFalse(result['recovery_success'])
            self.assertEqual(result['decision'], 'NO-GO')

    def test_spi_stage2_is_temporary_and_usb_c_is_not_substituted(self):
        spi = self.model.temporary_selector('m-card-spi')
        usb = self.model.temporary_selector('usb-c')
        self.assertTrue(spi['selector_available'])
        self.assertFalse(spi['persistent'])
        self.assertFalse(usb['selector_available'])
        self.assertFalse(usb['usb_c_supported'])

    def test_explicit_hypothetical_recovery_is_conditional(self):
        model = MOD.RecoveryModel(bootloader_recovery_entry=True)
        result = model.simulate(corrupt=True)
        self.assertTrue(result['recovery_success'])
        self.assertEqual(result['decision'], 'CONDITIONAL_ONLY')


if __name__ == '__main__':
    unittest.main()
