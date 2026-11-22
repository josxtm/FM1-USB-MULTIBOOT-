#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Read-only firmware inspector. It never rewrites or copies its input."""
import argparse, hashlib, json, struct, sys

APP = 0x01C30000
RETAINED = 0x01C7FD80

def crc16(data):
    c = 0
    for x in data:
        c ^= x << 8
        for _ in range(8): c = ((c << 1) ^ (0x1021 if c & 0x8000 else 0)) & 0xffff
    return c

def inspect(path):
    data = open(path, 'rb').read(); sha = hashlib.sha256(data).hexdigest()
    out = {'path': path, 'size': len(data), 'sha256': sha, 'format': 'UNKNOWN',
           'classification': 'UNKNOWN', 'integrity': False, 'requirements': [], 'evidence': []}
    if len(data) >= 8 and data[-8:-4] == b'APP1':
        out['format'] = 'APP1-v1'; out['board'] = data[-2]; out['version'] = data[-1]
        out['crc16_expected'] = struct.unpack_from('<H', data, len(data)-4)[0]
        out['crc16_actual'] = crc16(data[:-8])
        out['integrity'] = (out['version'] == 1 and out['board'] == 1 and
                            out['crc16_expected'] == out['crc16_actual'] and
                            APP + len(data) <= RETAINED and len(data) % 2 == 0)
        out['requirements'] = ['run-ram/stage-2 ABI confirmation', 'SRAM map and stack confirmation']
        out['classification'] = 'RAM_CANDIDATE' if out['integrity'] else 'INCOMPATIBLE'
        out['evidence'].append('APP1 trailer/CRC/range are structurally checked; they do not prove execution')
    elif data[:4] == b'\x7fELF':
        out['format'] = 'ELF'; out['elf_class'] = data[4] if len(data) > 4 else None
        out['machine'] = struct.unpack_from('<H', data, 18)[0] if len(data) >= 20 else None
        out['requirements'] = ['load map, ABI, vectors, SRAM and entry validation']
        out['classification'] = 'INSPECTION_ONLY'
    elif path.upper().endswith('.FWS'):
        out['format'] = 'FWS-OPAQUE'; out['requirements'] = ['public FWS container specification']
        out['classification'] = 'INSPECTION_ONLY'
    elif path.upper().endswith('.BIN'):
        out['format'] = 'BIN-OPAQUE'; out['requirements'] = ['documented header, entry and memory map']
        out['classification'] = 'INSPECTION_ONLY'
    else:
        out['requirements'] = ['recognized container and architecture metadata']
    return out

def main():
    p = argparse.ArgumentParser(); p.add_argument('file'); p.add_argument('--pretty', action='store_true'); a = p.parse_args()
    result = inspect(a.file); print(json.dumps(result, indent=2 if a.pretty else None, sort_keys=True))
    return 0

if __name__ == '__main__':
    try: raise SystemExit(main())
    except (OSError, struct.error) as e: print('INSPECTION ERROR:', e, file=sys.stderr); raise SystemExit(1)
