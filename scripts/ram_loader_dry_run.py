#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Deterministic RAM-loader transport dry-run; never opens USB or hardware.

It models 512-byte APP1 fragments and an acknowledgement for each fragment,
then a separately recorded jump request.  The receiver is an in-memory byte
array and rejects Flash/SCSI operations by construction.
"""
import argparse, hashlib, json, struct, sys

BLOCK = 512
APP = 0x01C30000
END = 0x01C7FD80

def crc16(data):
    crc = 0
    for byte in data:
        crc ^= byte << 8
        for _ in range(8):
            crc = ((crc << 1) ^ (0x1021 if crc & 0x8000 else 0)) & 0xffff
    return crc

def fail(msg):
    raise ValueError(msg)

def run(path, cancel_after=None, timeout_at=None):
    image = open(path, 'rb').read()
    if len(image) < 16 or len(image) % 2 or len(image) > END - APP: fail('APP1 size/range')
    if image[-8:-4] != b'APP1' or image[-1] != 1 or image[-2] != 1: fail('APP1 trailer')
    if struct.unpack_from('<H', image, len(image) - 4)[0] != crc16(image[:-8]): fail('APP1 CRC')
    digest = hashlib.sha256(image).hexdigest()
    receiver = bytearray(len(image)); events = []
    for index, offset in enumerate(range(0, len(image), BLOCK)):
        if cancel_after is not None and index >= cancel_after: return {'result':'CANCELLED','blocks':index,'sha256':digest}
        if timeout_at is not None and index == timeout_at: return {'result':'TIMEOUT','blocks':index,'sha256':digest}
        chunk = image[offset:offset + BLOCK]
        address = APP + offset
        if address < APP or address + len(chunk) > END: fail('destination range')
        receiver[offset:offset + len(chunk)] = chunk
        events.append({'block':index, 'address':hex(address), 'bytes':len(chunk), 'ack':True})
    if bytes(receiver) != image: fail('receiver mismatch')
    return {'result':'READY_FOR_SIMULATION', 'blocks':len(events), 'sha256':digest,
            'crc16':format(crc16(image[:-8]), '04x'), 'jump_recorded':True,
            'hardware_io':False, 'events':events}

def main():
    p = argparse.ArgumentParser(); p.add_argument('image'); p.add_argument('--cancel-after', type=int)
    p.add_argument('--timeout-at', type=int); a = p.parse_args()
    print(json.dumps(run(a.image, a.cancel_after, a.timeout_at), sort_keys=True))

if __name__ == '__main__':
    try: main()
    except (OSError, ValueError, struct.error) as exc:
        print('DRY-RUN INVALID:', exc, file=sys.stderr); sys.exit(1)
