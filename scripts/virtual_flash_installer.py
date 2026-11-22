#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Safety-only virtual FM-1 installer.

This module operates exclusively on a bytearray.  It accepts only the
repository's structurally validated APP1-v1 image; BIN/FWS names alone never
authorize installation.  It models transaction states and power-loss recovery
without claiming that the FM-1 has a backup slot or a resident selector.
"""
import argparse
import hashlib
import json
import os
import struct

FLASH_SIZE = 0x100000
PROTECTED_END = 0x4000
APP_START = PROTECTED_END
APP_END = FLASH_SIZE
APP_MAX = 0x4FD80


def crc16(data):
    state = 0
    for byte in data:
        state ^= byte << 8
        for _ in range(8):
            state = ((state << 1) ^ (0x1021 if state & 0x8000 else 0)) & 0xffff
    return state


def sha(data):
    return hashlib.sha256(data).hexdigest()


def validate_app1(data, filename='image.bin'):
    """Return a structural report; no image bytes are written anywhere."""
    report = {'format': 'UNKNOWN', 'valid': False, 'reasons': []}
    if len(data) < 16 or len(data) > APP_MAX or len(data) % 2:
        report['reasons'].append('size must be even and between 16 and APP_MAX')
        return report
    if data[-8:-4] != b'APP1':
        report['reasons'].append('APP1-v1 trailer is missing')
        return report
    report['format'] = 'APP1-v1'
    expected, board, version = struct.unpack_from('<HBB', data, len(data) - 4)
    actual = crc16(data[:-8])
    report.update(board=board, version=version, crc_expected=expected,
                  crc_actual=actual, sha256=sha(data))
    if board != 1:
        report['reasons'].append('board is not FM-1')
    if version != 1:
        report['reasons'].append('unsupported APP1 version')
    if expected != actual:
        report['reasons'].append('CRC16-XMODEM mismatch')
    if APP_START + len(data) > APP_END:
        report['reasons'].append('image exceeds application area')
    report['valid'] = not report['reasons']
    return report


class VirtualInstaller:
    """Transaction simulator with explicit confirmation and recovery gates."""
    def __init__(self, flash=None, recovery_available=True):
        self.flash = bytearray(b'\xff' * FLASH_SIZE) if flash is None else bytearray(flash)
        if len(self.flash) != FLASH_SIZE:
            raise ValueError('virtual Flash must be exactly 1 MiB')
        self.recovery_available = recovery_available

    def _protected_ok(self, before):
        return self.flash[:PROTECTED_END] == before[:PROTECTED_END]

    def reboot(self):
        """Model a reset after installation; no resident selector is invented."""
        return {
            'installed_sha256': sha(bytes(self.flash[APP_START:]).rstrip(b'\xff')),
            'selector_available': False,
            'recovery_entry': 'm-bootloader/reinstall or external recovery',
        }

    def install(self, image, expected_sha256=None, confirmed=False, fault=None):
        before = bytes(self.flash)
        report = validate_app1(image)
        result = {'state': 'REJECTED', 'result': 'REJECTED',
                  'protected_unchanged': True, 'operations': []}
        result['validation'] = report
        if not report['valid']:
            return result
        image_sha = sha(image)
        if expected_sha256 and image_sha != expected_sha256.lower():
            result['validation']['reasons'].append('catalog SHA-256 mismatch')
            return result
        current = bytes(self.flash[APP_START:APP_START + len(image)])
        if current == image:
            result.update(state='ALREADY_INSTALLED', result='ALREADY_INSTALLED',
                          sha256=image_sha)
            return result
        if not confirmed:
            result.update(state='CONFIRMATION_REQUIRED', result='CONFIRMATION_REQUIRED',
                          sha256=image_sha)
            return result

        result['operations'].append('PREPARED')
        backup = bytes(self.flash[APP_START:APP_END])
        if fault == 'cut-before-commit':
            result.update(state='ROLLED_BACK', result='RECOVERED')
            return result
        self.flash[APP_START:APP_END] = b'\xff' * (APP_END - APP_START)
        result['operations'].append('ERASED')
        if fault == 'cut-after-erase':
            return self._recover(result, backup, before)
        self.flash[APP_START:APP_START + len(image)] = image
        result['operations'].append('PROGRAMMED')
        if fault == 'cut-after-program':
            return self._recover(result, backup, before)
        if fault == 'corrupt-after-program':
            self.flash[APP_START] ^= 1
        verified = bytes(self.flash[APP_START:APP_START + len(image)]) == image
        result['operations'].append('VERIFIED' if verified else 'VERIFY_FAILED')
        if not verified:
            return self._recover(result, backup, before)
        result.update(state='COMMITTED', result='COMMITTED', sha256=image_sha,
                      reboot=self.reboot())
        result['protected_unchanged'] = self._protected_ok(before)
        return result

    def _recover(self, result, backup, before):
        result['operations'].append('RECOVERY_REQUIRED')
        if not self.recovery_available:
            result.update(state='RECOVERY_REQUIRED', result='RECOVERY_REQUIRED')
        else:
            self.flash[APP_START:APP_END] = backup
            result.update(state='ROLLED_BACK', result='RECOVERED')
        result['protected_unchanged'] = self._protected_ok(before)
        return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('image')
    parser.add_argument('--sha256')
    parser.add_argument('--confirm', action='store_true')
    parser.add_argument('--fault', choices=['cut-before-commit', 'cut-after-erase',
                                            'cut-after-program', 'corrupt-after-program'])
    args = parser.parse_args()
    with open(args.image, 'rb') as stream:
        image = stream.read()
    print(json.dumps(VirtualInstaller().install(image, args.sha256, args.confirm,
                                                args.fault), sort_keys=True))


if __name__ == '__main__':
    main()
