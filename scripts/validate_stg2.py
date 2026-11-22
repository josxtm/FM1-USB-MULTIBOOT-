#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Package a pi32v2 STG2 and audit its m-bootloader v2.4 load/return contract."""
import binascii
from pathlib import Path
import re
import struct
import sys

LOAD = 0x01C28000
WORK = 0x01C16800
MAX = 0x8000
RETAINED = 0x01C7FD80


def require(ok, reason):
    if not ok:
        raise ValueError(reason)


def wrap(flat):
    require(16 <= len(flat) + 8 <= MAX, 'STG2 outside 16..32768 bytes with trailer')
    return flat + b'STG2' + struct.pack('<H', binascii.crc_hqx(flat, 0)) + b'\0\0'


def check_trailer(image):
    require(len(image) >= 16 and image == wrap(image[:-8]), 'bad STG2 trailer or CRC16')


def sections_and_symbols(elf):
    shoff, = struct.unpack_from('<I', elf, 32)
    width, count = struct.unpack_from('<HH', elf, 46)
    require(width == 40 and 0 < count < 64 and shoff + count * width <= len(elf), 'bad section table')
    sections = [struct.unpack_from('<10I', elf, shoff + i * width) for i in range(count)]
    require(not any(s[1] in (4, 6, 9, 11) for s in sections), 'relocations or dynamic linkage survived')
    tables = [s for s in sections if s[1] == 2]
    require(len(tables) == 1, 'expected one ELF symbol table')
    tab = tables[0]
    require(tab[9] == 16 and tab[5] % 16 == 0 and tab[4] + tab[5] <= len(elf) and tab[6] < count,
            'bad ELF symbols')
    names = sections[tab[6]]
    names = elf[names[4]:names[4] + names[5]]
    symbols = {}
    for off in range(tab[4] + 16, tab[4] + tab[5], 16):
        name, value, size, info, _, defined = struct.unpack_from('<IIIBBH', elf, off)
        label = names[name:].partition(b'\0')[0].decode('ascii')
        require(defined != 0 or info >> 4 == 0, f'unresolved ELF symbol {label}')
        if label:
            symbols[label] = (value, size)
    return symbols


def check_return(disassembly, symbols):
    entry = re.search(r'<fm1_stg2_entry>:\n(.*?)(?:\n\n|\Z)', disassembly, re.S)
    require(entry, 'STG2 entry missing from disassembly')
    instructions = re.findall(r'^\s*[0-9a-f]+:\s+(?:[0-9a-f]{2}\s+)+(.+?)\s*$', entry[1], re.M)
    caller = symbols['stg2_caller_stacks'][0]
    main_sp = symbols['stg2_stack_top'][0]
    exception_sp = symbols['stg2_exception_top'][0]
    require(len(instructions) == 19 and instructions[11].endswith('<fm1_stg2_main>') and
            instructions[11].startswith('call '), 'entry must call the native selector once')
    expected = ['cli', '[--sp] = {rets, r15-r4}', f'r2 = {caller}', 'r3 = sp', '[r2+0] = r3',
                'r3 = ssp', '[r2+4] = r3', f'r2 = {exception_sp}', 'ssp = r2', f'r2 = {main_sp}',
                'sp = r2', instructions[11], 'cli', f'r2 = {caller}', 'r3 = [r2+0]', 'sp = r3',
                'r3 = [r2+4]', 'ssp = r3', '{pc, r15-r4} = [sp++]']
    require(instructions == expected, 'entry does not preserve r0, caller stacks and callee-saved registers')


def validate(elf, flat, image, disassembly):
    check_trailer(image)
    require(image[:-8] == flat, 'image payload differs from flat binary')
    require(len(elf) >= 52 and elf[:7] == b'\x7fELF\x01\x01\x01', 'expected little-endian ELF32')
    kind, arch, version, entry, phoff = struct.unpack_from('<HHIII', elf, 16)
    require((kind, arch, version, entry) == (2, 241, 1, LOAD), 'expected pi32v2 executable at 0x01C28000')
    entsize, n = struct.unpack_from('<HH', elf, 42)
    require(entsize == 32 and n == 2 and phoff <= len(elf) - 64, 'expected two program headers')
    payload, bss = [struct.unpack_from('<8I', elf, phoff + i * entsize) for i in range(n)]
    pt, off, va, pa, size, memory, flags, _ = payload
    require((pt, va, pa, size, memory, flags) == (1, LOAD, LOAD, len(flat), len(flat), 7) and
            elf[off:off + size] == flat, 'bad loadable image segment')
    pt, _, va, pa, size, memory, flags, _ = bss
    require((pt, va, pa, size, flags) == (1, WORK, WORK, 0, 6) and 0 < memory <= LOAD - WORK,
            'work RAM overlaps bootloader or image')
    require(LOAD + ((len(image) + 511) & ~511) <= LOAD + MAX <= RETAINED, 'sector-rounded load overlaps retained RAM')
    syms = sections_and_symbols(elf)
    names = ('fm1_stg2_entry', 'fm1_stg2_main', 'stg2_caller_stacks', 'stg2_bss_start', 'stg2_bss_end',
             'stg2_stack_bottom', 'stg2_stack_top', 'stg2_exception_top', 'stg2_sd_read',
             'mb_verify_selected', 'mb_prepare_launch')
    require(all(name in syms for name in names), 'required native selector symbols are missing')
    require(syms['fm1_stg2_entry'][0] == LOAD, 'symbol entry mismatch')
    require(LOAD <= syms['stg2_caller_stacks'][0] <= LOAD + len(flat) - 8, 'caller context must be in data')
    start, end, bottom, top, exception = (syms[name][0] for name in names[3:8])
    require(start == WORK and end == bottom and top == bottom + 8192 and exception == top + 1024 and
            exception == WORK + memory and end % 8 == 0, 'bad BSS and private stack layout')
    forbidden = ('usb_disable', 'husb_', 'usb_enumerate', 'disk_open', 'disk_read', 'p33_',
                 'flash_', 'rom_download', 'boot_reset', 'watchdog')
    require(not any(name.startswith(forbidden) for name in syms), 'USB, reset or Flash implementation linked')
    check_return(disassembly, syms)
    return (f'ELF32 pi32v2 (machine 241), entry 0x{LOAD:08X}, LSB r0 = 60 MHz\n'
            f'STG2 {len(image)} B / {MAX} B, CRC16-XMODEM 0x{int.from_bytes(image[-4:-2], "little"):04X}\n'
            f'Payload {len(flat)} B, rounded card DMA span {(len(image) + 511) & ~511} B\n'
            f'BSS 0x{start:08X}–0x{end:08X} ({end-start} B), total reserved work {memory} B\n'
            f'Main stack 0x{bottom:08X}–0x{top:08X} (8192 B), exception stack ends 0x{exception:08X} (1024 B)\n'
            'ABI saves and restores SP, SSP and r4–r15 with interrupts disabled; r0 reaches the C entry unchanged.\n'
            'No USB Host, reset or Flash write implementation linked. Experimental, hardware unverified.\n')


def main():
    elf, flat, output, report, disassembly = map(Path, sys.argv[1:])
    try:
        payload = flat.read_bytes()
        image = wrap(payload)
        summary = validate(elf.read_bytes(), payload, image, disassembly.read_text())
        output.write_bytes(image)
        report.write_text(summary)
        print(summary, end='')
    except (ValueError, struct.error) as error:
        sys.exit(str(error))


if __name__ == '__main__':
    main()
