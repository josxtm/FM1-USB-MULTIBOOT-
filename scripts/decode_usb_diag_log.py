#!/usr/bin/env python3
"""Decode compact USB diagnostic records: code,detail byte pairs."""
import sys
NAMES = {1:'BOOT',2:'ATTACH',3:'RESET',4:'ENUM',5:'ENDPOINTS',6:'BOT',
         7:'CAPACITY',8:'READ',9:'FAT32',10:'DISCONNECT',11:'TIMEOUT',12:'ERROR'}
def main():
    if len(sys.argv) > 1:
        raw = bytes.fromhex(''.join(sys.argv[1:]))
    else:
        source = sys.stdin.buffer.read()
        try: raw = bytes.fromhex(source.decode())
        except (UnicodeDecodeError, ValueError): raw = source
    if len(raw) % 2: raise SystemExit('odd diagnostic byte count')
    for i in range(0, len(raw), 2): print(f'{i//2:02d} {NAMES.get(raw[i], "UNKNOWN")} detail=0x{raw[i+1]:02x}')
if __name__ == '__main__': main()
