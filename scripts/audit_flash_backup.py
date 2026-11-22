#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Offline, privacy-preserving audit of a raw 1 MiB FM-1 Flash backup.

Only a host file is opened. Sensitive configuration bytes, chip keys, UID,
MAC and calibration values are never printed. The parser is conservative:
unknown or ambiguous records become evidence='UNKNOWN' rather than being
assigned a writable or executable meaning.
"""
import argparse
import hashlib
import json
import struct
import zlib

SIZE = 0x100000
BOOT_END = 0x4000
KEY_START = 0xFF000
SECTOR = 0x1000
MAX_TABLE_RECORDS = 128


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


def u16(data, at):
    return struct.unpack_from('<H', data, at)[0]


def u32(data, at):
    return struct.unpack_from('<I', data, at)[0]


def text_name(data):
    return data[16:32].split(b'\0', 1)[0].decode('ascii', 'replace')


def entry(raw, offset, absolute=False):
    return {'offset': offset, 'valid': crc16(raw[2:]) == u16(raw, 0),
            'data_crc': u16(raw, 2), 'address': u32(raw, 4),
            'length': u32(raw, 8), 'attribute': raw[12],
            'name': text_name(raw), 'address_kind': 'absolute' if absolute else 'relative'}


def decode_unit_key(config):
    """Decode only an internal key for transient use; never return/log it."""
    if len(config) < 34:
        return None
    adjusted = sum(config[:16]) & 0xff
    if adjusted >= 0xe0:
        adjusted = 0xaa
    elif adjusted <= 0x10:
        adjusted = 0x55
    key = 0
    for bit in range(16):
        if (config[16 + bit] ^ config[15 - bit]) < adjusted:
            key |= 1 << bit
    return key


def app_read(data, base, offset, length, key):
    result = bytearray()
    aligned = offset - offset % 32
    while aligned < offset + length:
        start = base + aligned
        if start + 32 > len(data):
            return None
        result.extend(enc(data[start:start + 32], key ^ ((aligned >> 2) & 0xffff)))
        aligned += 32
    skip = offset % 32
    return bytes(result[skip:skip + length])


def public_entry(e, evidence, physical=None):
    out = {k: e[k] for k in ('offset', 'valid', 'address', 'length', 'attribute', 'name', 'address_kind')}
    out['data_crc_valid'] = evidence
    out['evidence'] = 'OBSERVED_STRUCTURE' if evidence else 'UNKNOWN'
    if physical is not None:
        out['physical_start'], out['physical_end'] = physical
    return out


def ranges_overlap(items):
    ordered = sorted((x['physical_start'], x['physical_end'], x['name']) for x in items
                     if 'physical_start' in x and x['physical_end'] > x['physical_start'])
    overlaps = []
    for previous, current in zip(ordered, ordered[1:]):
        if current[0] < previous[1]:
            overlaps.append({'first': previous[2], 'second': current[2],
                             'at': current[0]})
    return overlaps


def audit(data, expected_sha=None):
    result = {'size': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
              'crc32': f'{zlib.crc32(data) & 0xffffffff:08x}',
              'format': 'RAW-FLASH-BACKUP', 'read_only': True,
              'regions': {'boot': [0, BOOT_END], 'key_mac': [KEY_START, SIZE]},
              'checks': {}, 'warnings': [], 'privacy': {
                  'sensitive_values_emitted': False,
                  'sensitive_values': ['chip_key', 'UID', 'MAC', 'calibration', 'config_bytes']}}
    result['checks']['capacity_1MiB'] = len(data) == SIZE
    if expected_sha:
        result['checks']['expected_sha256'] = result['sha256'] == expected_sha.lower()
    if len(data) != SIZE:
        result['warnings'].append('full 1 MiB backup required for complete audit')
        result['checks']['boot_header'] = False
        result['checks']['app_table'] = 'UNKNOWN'
        return result

    header = enc(data[:32])
    result['checks']['boot_header'] = crc16(header[2:]) == u16(header, 0)
    result['checks']['key_mac_present'] = len(data[KEY_START:]) == SIZE - KEY_START
    result['header'] = {'filesystem_size': u32(header, 8),
                        'version': header[12], 'alignment_256': header[13]}
    boot_entries = []
    for offset in range(0x20, 0xA0, 0x20):
        raw_stored = data[offset:offset + 0x20]
        if raw_stored == b'\xff' * 0x20:
            break
        raw = enc(raw_stored)
        parsed = entry(raw, offset, True)
        if not parsed['valid']:
            boot_entries.append(public_entry(parsed, False))
            result['warnings'].append('invalid boot directory record')
            break
        physical = (parsed['address'], parsed['address'] + parsed['length'])
        special = parsed['name'] == 'app_dir_head' and parsed['address'] == BOOT_END
        special = special or (parsed['name'] == 'key_mac' and physical == (KEY_START, SIZE))
        if not special and (physical[1] > BOOT_END or physical[0] < 0):
            parsed['valid'] = False
            boot_entries.append(public_entry(parsed, False))
            result['warnings'].append('boot record outside boot area')
            break
        if special:
            public = public_entry(parsed, True)
            public['category'] = 'APP_DIRECTORY' if parsed['name'] == 'app_dir_head' else 'KEY_MAC'
            boot_entries.append(public)
            continue
        parsed['data_crc_valid'] = False
        if parsed['length'] and physical[1] <= len(data):
            parsed['data_crc_valid'] = crc16(data[physical[0]:physical[1]]) == parsed['data_crc']
        boot_entries.append(public_entry(parsed, parsed['data_crc_valid'], physical))
    result['boot_table'] = boot_entries
    result['checks']['boot_table_entries'] = bool(boot_entries) and all(e['valid'] for e in boot_entries)

    config_entry = next((e for e in boot_entries if e['name'] == 'isd_config.ini' and e['valid']), None)
    app_dir = next((e for e in boot_entries if e['name'] == 'app_dir_head' and e['valid']), None)
    if config_entry and config_entry.get('physical_end', 0) <= len(data):
        config = data[config_entry['physical_start']:config_entry['physical_end']]
        result['configuration'] = {'length': len(config),
                                   'length_96': len(config) == 96,
                                   'data_crc_valid': config_entry['data_crc_valid'],
                                   'chip_key_record_crc_valid': len(config) >= 34 and crc16(config[:32]) == u16(config, 32)}
        unit_key = decode_unit_key(config)
    else:
        result['configuration'] = {'length': None, 'length_96': False,
                                   'data_crc_valid': False,
                                   'chip_key_record_crc_valid': False}
        unit_key = None
    result['checks']['configuration'] = bool(result['configuration']['data_crc_valid'])

    app_base = app_dir['address'] if app_dir else None
    if unit_key is None or app_base is None or app_base >= len(data):
        result['checks']['app_table'] = 'UNKNOWN: valid config/app_dir/key required'
        result['warnings'].append('application table not decoded; no private key was emitted')
        result['regions']['application'] = 'UNKNOWN'
        return result

    # app_dir_head points to the app-area table; records are 32-byte entries.
    app_entries = []
    max_records = min(MAX_TABLE_RECORDS, (len(data) - app_base) // 32)
    for index in range(max_records):
        relative = index * 32
        raw = app_read(data, app_base, relative, 32, unit_key)
        if raw is None:
            result['warnings'].append('application table truncated')
            break
        parsed = entry(raw, app_base + relative, False)
        if not parsed['valid']:
            if index == 0:
                result['warnings'].append('application table first record invalid')
            break
        is_descriptor = parsed['attribute'] in (0x12, 0x92)
        if parsed['name'] == 'app_area_head':
            public = public_entry(parsed, True)
            public['category'] = 'APP_AREA'
            public['address_kind'] = 'cpu_entry'
            app_entries.append(public)
            continue
        if is_descriptor:
            start, end = parsed['address'], parsed['address'] + parsed['length']
            parsed['address_kind'] = 'absolute'
        else:
            start, end = app_base + parsed['address'], app_base + parsed['address'] + parsed['length']
        safe = start <= end <= len(data) and end >= start
        if not safe:
            parsed['valid'] = False
            result['warnings'].append('application record outside Flash')
            app_entries.append(public_entry(parsed, False))
            break
        crc_ok = True if is_descriptor else crc16(app_read(data, app_base, parsed['address'], parsed['length'], unit_key) or b'') == parsed['data_crc']
        category = parsed['name'] if parsed['name'] in ('PRCT', 'VM', 'BTIF', 'USR') else ('FILE' if parsed['name'] in ('app.bin', 'cfg_tool.bin') else 'UNKNOWN')
        public = public_entry(parsed, crc_ok, (start, end))
        public['category'] = category
        app_entries.append(public)
    result['app_table'] = app_entries
    result['checks']['app_table'] = bool(app_entries) and all(e['valid'] for e in app_entries)
    result['checks']['app_files_crc'] = all(e['data_crc_valid'] for e in app_entries if e['name'] in ('app.bin', 'cfg_tool.bin'))
    result['map_overlaps'] = ranges_overlap(app_entries)
    if result['map_overlaps']:
        result['warnings'].append('application regions overlap')
    result['regions']['application'] = [BOOT_END, KEY_START]
    result['regions']['unknown_gap_policy'] = 'protected unless explicitly described by a valid table'
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
