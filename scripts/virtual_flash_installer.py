#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Safety-only virtual FM-1 installer with an explicit region policy.

This module operates exclusively on bytearrays.  No physical Flash, USB or
bootloader API is opened.  The default policy authorizes *no* erase region:
an application range may be supplied only by a future, documented map.
"""
import argparse
import hashlib
import json
import struct

FLASH_SIZE = 0x100000
BOOTLOADER_END = 0x4000
KEY_MAC_START = 0xFF000
# Compatibility name used by the previous virtual recovery tests.
PROTECTED_END = BOOTLOADER_END
APP_MAX = 0x4FD80
SECTOR = 0x1000


def crc16(data):
    state = 0
    for byte in data:
        state ^= byte << 8
        for _ in range(8):
            state = ((state << 1) ^ (0x1021 if state & 0x8000 else 0)) & 0xffff
    return state


def sha(data):
    return hashlib.sha256(data).hexdigest()


def validate_app1(data):
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
    report['valid'] = not report['reasons']
    return report


def validate_flash_bin(data):
    """Validate the documented raw FM-1 flash.bin envelope only.

    The boot bytes present in a raw package are deliberately not written by
    the simulator.  Only the app-area prefix is a candidate destination.
    """
    report = {'format': 'FLASH.BIN', 'valid': False, 'reasons': []}
    if len(data) <= BOOTLOADER_END or len(data) > KEY_MAC_START or len(data) % SECTOR:
        report['reasons'].append('flash.bin must be sector-aligned, >0x4000 and <=0xFF000')
    report['target'] = [BOOTLOADER_END, len(data)]
    report['sha256'] = sha(data)
    report['valid'] = not report['reasons']
    return report


class FlashRegionPolicy:
    """Explicit erase authorization; omitted regions remain protected.

    The default models the documented app span [0x4000,0xFF000); callers
    may narrow it further.  It is not permission to write a real device: the
    simulator has no physical I/O and does not parse proprietary region data.
    """
    def __init__(self, authorized_ranges=((BOOTLOADER_END, KEY_MAC_START),)):
        self.authorized = self._normalise(authorized_ranges)
        for start, end in self.authorized:
            if start < BOOTLOADER_END:
                raise ValueError('authorized range overlaps protected bootloader')
        self.protected = self._complement(self.authorized)

    @staticmethod
    def _normalise(ranges):
        result = []
        for start, end in sorted(ranges):
            if start < 0 or end > FLASH_SIZE or start >= end:
                raise ValueError('invalid Flash range')
            if result and start < result[-1][1]:
                raise ValueError('overlapping Flash ranges')
            result.append((start, end))
        return tuple(result)

    @staticmethod
    def _complement(authorized):
        out = [(0, BOOTLOADER_END)]
        cursor = BOOTLOADER_END
        for start, end in authorized:
            if start > cursor:
                out.append((cursor, start))
            cursor = max(cursor, end)
        if cursor < FLASH_SIZE:
            out.append((cursor, FLASH_SIZE))
        return tuple(out)

    def target(self, size):
        for start, end in self.authorized:
            if size <= end - start:
                return start, start + size
        return None

    def description(self):
        return {'authorized': list(self.authorized), 'protected': list(self.protected),
                'bootloader': [0, BOOTLOADER_END], 'key_mac': [KEY_MAC_START, FLASH_SIZE],
                'evidence': 'm-flasher/m-bootloader public format model; unit validation pending'}


class VirtualInstaller:
    """Transaction simulator with explicit confirmation and recovery gates."""
    def __init__(self, flash=None, recovery_available=True, policy=None):
        self.flash = bytearray(b'\xff' * FLASH_SIZE) if flash is None else bytearray(flash)
        if len(self.flash) != FLASH_SIZE:
            raise ValueError('virtual Flash must be exactly 1 MiB')
        self.recovery_available = recovery_available
        self.policy = policy or FlashRegionPolicy()

    def _protected_ok(self, before):
        return all(self.flash[start:end] == before[start:end]
                   for start, end in self.policy.protected)

    def reboot(self, target=None):
        """Model reset; no resident selector or bootloader hook is invented."""
        installed = b'' if target is None else bytes(self.flash[target[0]:target[1]])
        return {'installed_sha256': sha(installed) if installed else None,
                'selector_available': False,
                'recovery_entry': 'm-bootloader/reinstall or external recovery'}

    def install(self, image, expected_sha256=None, confirmed=False, fault=None,
                filename='image.bin'):
        before = bytes(self.flash)
        if filename.lower().endswith('flash.bin'):
            report = validate_flash_bin(image)
            payload = image[BOOTLOADER_END:]
            target = self.policy.target(len(payload)) if report['valid'] else None
        else:
            report = validate_app1(image)
            if report['valid']:
                report['valid'] = False
                report['reasons'].append('APP1 is experimental RAM format, not a Flash image')
            payload = b''
            target = None
        result = {'state': 'REJECTED', 'result': 'REJECTED',
                  'protected_unchanged': True, 'operations': [],
                  'regions': self.policy.description()}
        result['validation'] = report
        if target is None and report['valid']:
            report['reasons'].append('no documented authorized erase region')
        if not report['valid'] or target is None:
            return result
        image_sha = sha(image)
        if expected_sha256 and image_sha != expected_sha256.lower():
            result['validation']['reasons'].append('catalog SHA-256 mismatch')
            return result
        result['target'] = list(target)
        current = bytes(self.flash[target[0]:target[1]])
        if current == payload:
            result.update(state='ALREADY_INSTALLED', result='ALREADY_INSTALLED', sha256=image_sha)
            return result
        if not confirmed:
            result.update(state='CONFIRMATION_REQUIRED', result='CONFIRMATION_REQUIRED', sha256=image_sha)
            return result

        result['operations'].append('PREPARED')
        backup = bytes(self.flash[target[0]:target[1]])
        if fault == 'cut-before-commit':
            result.update(state='ROLLED_BACK', result='RECOVERED')
            return result
        self.flash[target[0]:target[1]] = b'\xff' * len(backup)
        result['operations'].append('ERASED_AUTHORIZED_RANGE_ONLY')
        if fault == 'cut-after-erase':
            return self._recover(result, backup, target, before)
        self.flash[target[0]:target[1]] = payload
        result['operations'].append('PROGRAMMED')
        if fault == 'cut-after-program':
            return self._recover(result, backup, target, before)
        if fault == 'corrupt-after-program':
            self.flash[target[0]] ^= 1
        verified = bytes(self.flash[target[0]:target[1]]) == payload
        result['operations'].append('VERIFIED' if verified else 'VERIFY_FAILED')
        if not verified:
            return self._recover(result, backup, target, before)
        result.update(state='COMMITTED', result='COMMITTED', sha256=image_sha,
                      reboot=self.reboot(target))
        result['protected_unchanged'] = self._protected_ok(before)
        return result

    def _recover(self, result, backup, target, before):
        result['operations'].append('RECOVERY_REQUIRED')
        if not self.recovery_available:
            result.update(state='RECOVERY_REQUIRED', result='RECOVERY_REQUIRED')
        else:
            self.flash[target[0]:target[1]] = backup
            result.update(state='ROLLED_BACK', result='RECOVERED')
        result['protected_unchanged'] = self._protected_ok(before)
        return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('image')
    parser.add_argument('--sha256')
    parser.add_argument('--confirm', action='store_true')
    parser.add_argument('--authorize-range', nargs=2, type=lambda x: int(x, 0), metavar=('START', 'END'))
    parser.add_argument('--fault', choices=['cut-before-commit', 'cut-after-erase',
                                            'cut-after-program', 'corrupt-after-program'])
    args = parser.parse_args()
    with open(args.image, 'rb') as stream:
        image = stream.read()
    policy = FlashRegionPolicy([tuple(args.authorize_range)] if args.authorize_range else ())
    print(json.dumps(VirtualInstaller(policy=policy).install(
        image, args.sha256, args.confirm, args.fault, args.image), sort_keys=True))


if __name__ == '__main__':
    main()
