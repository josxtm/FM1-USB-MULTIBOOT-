#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Offline, read-only audit of a raw 1 MiB FM-1 Flash backup.

The tool opens only a host file. It never sends an opcode, opens USB/MIDI, or
writes a device. Unknown chip-key-dependent application data is reported as
unknown rather than guessed.
"""
import argparse
import hashlib
import json
import struct
import zlib

SIZE = 0x100000
BOOT_END = 0x4000
KEY_START = 0xFF000


def crc16(data):
    c = 0
    for x in data:
        c ^= x << 8
        for _ in range(8):
            c = ((c << 1) ^ (0x1021 if c & 0x8000 else 0)) & 0xffff
    return c


def enc(data, key=0xffff):
    out = bytearray()
    for x in data:
        out.append(x ^ (key & 0xff))
        key = ((key << 1) ^ (0x1021 if key & 0x8000 else 0)) & 0xffff
    return bytes(out)


def audit(data, expected_sha=None):
    result = {'size': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
              'crc32': f'{zlib.crc32(data) & 0xffffffff:08x}',
              'format': 'RAW-FLASH-BACKUP', 'read_only': True,
              'regions': {'boot': [0, BOOT_END], 'key_mac': [KEY_START, SIZE]},
              'checks': {}, 'warnings': []}
    result['checks']['capacity_1MiB'] = len(data) == SIZE
    if expected_sha:
        result['checks']['expected_sha256'] = result['sha256'] == expected_sha.lower()
    if len(data) != SIZE:
        result['warnings'].append('full 1 MiB backup required for complete audit')
        result['checks']['boot_header'] = False
        result['checks']['app_table'] = 'UNKNOWN'
        return result
    header = enc(data[:32])
    result['checks']['boot_header'] = crc16(header[2:]) == struct.unpack_from('<H', header, 0)[0]
    result['header'] = {'filesystem_size': struct.unpack_from('<I', header, 8)[0],
                        'version': header[12], 'alignment_256': header[13]}
    entries = []
    for offset in range(0x20, 0xA0, 0x20):
        raw = enc(data[offset:offset + 0x20])
        if raw == b'\xff' * 0x20:
            break
        valid = crc16(raw[2:]) == struct.unpack_from('<H', raw, 0)[0]
        entries.append({'offset': offset, 'valid': valid,
                        'address': struct.unpack_from('<I', raw, 4)[0],
                        'length': struct.unpack_from('<I', raw, 8)[0],
                        'attribute': raw[12],
                        'name': raw[16:32].split(b'\0', 1)[0].decode('ascii', 'replace')})
    result['boot_table'] = entries
    result['checks']['boot_table_entries'] = all(e['valid'] for e in entries)
    result['checks']['app_table'] = 'UNKNOWN: chip key required for app-area ENC'
    result['checks']['key_mac_present'] = len(data[KEY_START:]) == SIZE - KEY_START
    result['warnings'] += ['application table requires the unit chip key',
                           'backup integrity does not prove restorability or boot compatibility']
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('backup')
    parser.add_argument('--sha256')
    args = parser.parse_args()
    with open(args.backup, 'rb') as stream:
        data = stream.read()
    print(json.dumps(audit(data, args.sha256), indent=2, sort_keys=True))


if __name__ == '__main__':
    main()
