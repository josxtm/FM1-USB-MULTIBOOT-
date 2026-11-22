#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Conservative model of post-install boot and selector recovery.

The default profile encodes only evidence already documented in this
repository: stage-2 may be temporary and SPI/m-card-specific, while no
USB-C pre-application recovery or resident selector is proven.  It never
opens hardware and deliberately refuses to report an unproven recovery as
successful.
"""
import argparse
import json


class RecoveryModel:
    def __init__(self, resident_selector=False, bootloader_recovery_entry=False,
                 stage2_spi=True, usb_c_preboot=False):
        self.resident_selector = resident_selector
        self.bootloader_recovery_entry = bootloader_recovery_entry
        self.stage2_spi = stage2_spi
        self.usb_c_preboot = usb_c_preboot

    def simulate(self, installed=True, corrupt=False, power_cut=False,
                 sd_available=True, source='usb-c'):
        result = {'source': source, 'installed': installed,
                  'selector_available': False, 'recovery_success': False,
                  'claims': [], 'decision': 'NO-GO'}
        if not installed:
            result.update(boot='SELECTOR_BEFORE_INSTALL', selector_available=True,
                          recovery_success=True, decision='SIMULATED_ONLY')
            return result
        if self.resident_selector:
            result.update(boot='RESIDENT_SELECTOR', selector_available=True,
                          claims=['resident selector requires documented protected region'])
            return result
        if corrupt or power_cut:
            result['boot'] = 'BOOT_FAILURE_OR_BOOTLOADER_RECOVERY'
            if self.bootloader_recovery_entry:
                result['claims'].append('recovery entry supplied by caller profile, not default evidence')
                result['recovery_success'] = True
                result['decision'] = 'CONDITIONAL_ONLY'
            else:
                result['claims'].append('no documented selector recovery entry after application replacement')
            if not sd_available:
                result['claims'].append('microSD unavailable before application/USB initialization')
            return result
        result['boot'] = 'INSTALLED_FIRMWARE'
        result['claims'].append('normal boot replaces the previous application selector')
        if source == 'usb-c' and not self.usb_c_preboot:
            result['claims'].append('USB-C MSC preboot access is unverified')
        return result

    def temporary_selector(self, source='m-card-spi'):
        available = source == 'm-card-spi' and self.stage2_spi
        return {'source': source, 'selector_available': available,
                'persistent': False, 'usb_c_supported': self.usb_c_preboot,
                'decision': 'CONDITIONAL_ONLY' if available else 'NO-GO',
                'reason': ('temporary stage-2 is not a persistent selector and is not '
                           'evidence for USB-C host access')}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--scenario', choices=['normal', 'corrupt', 'power-cut', 'no-install'], default='normal')
    parser.add_argument('--source', choices=['usb-c', 'm-card-spi'], default='usb-c')
    args = parser.parse_args()
    model = RecoveryModel()
    result = model.simulate(installed=args.scenario != 'no-install',
                            corrupt=args.scenario == 'corrupt',
                            power_cut=args.scenario == 'power-cut', source=args.source)
    print(json.dumps(result, sort_keys=True))


if __name__ == '__main__':
    main()
