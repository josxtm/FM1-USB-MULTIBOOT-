#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Verify the FM-1 ROM diagnostic envelope, work RAM, entry and linked surface."""
from pathlib import Path
import re
import struct
import sys

from validate import APP, BSS, validate as validate_app1
from validate_stg2 import require, sections_and_symbols


def check_entry(disassembly, stack, exception):
    entry = re.search(r'<fm1_rom_diag_entry>:\n(.*?)(?:\n\n|\Z)', disassembly, re.S)
    require(entry, 'ROM entry missing from disassembly')
    instructions = re.findall(r'^\s*[0-9a-f]+:\s+(?:[0-9a-f]{2}\s+)+(.+?)\s*$', entry[1], re.M)
    expected = ['cli', f'r2 = {exception}', 'ssp = r2', f'r2 = {stack}', 'sp = r2']
    require(len(instructions) == 7 and instructions[:5] == expected and
            re.fullmatch(r'call \d+ <fm1_rom_diag_main>', instructions[5]) and
            instructions[6] == 'goto -2 <fm1_rom_diag_entry+0x1a>',
            'ROM entry must preserve r0, set private stacks and park without returning')


def check_frames(paths):
    frames = {}
    for path in paths:
        for line in path.read_text().splitlines():
            name, amount, mode = line.split('\t')
            require(mode == 'static', f'nonstatic stack: {name}')
            frames[name] = int(amount)
    require(frames and sum(frames.values()) <= 4096, 'stack frame budget exceeded')
    largest = max(frames, key=frames.get)
    print(f'Compiler static frames: sum {sum(frames.values())} B / 4096 B; largest {largest} ({frames[largest]} B)')


def validate(elf, flat, image, disassembly):
    validate_app1(elf, flat, image)
    require(len(image) <= 8192, 'ROM diagnostic exceeds its local 8 KiB budget')
    symbols = sections_and_symbols(elf)
    names = ('fm1_rom_diag_entry', 'rom_diag_bss_start', 'rom_diag_bss_end', 'rom_diag_stack_bottom',
             'rom_diag_stack_top', 'rom_diag_exception_top', 'fm1_rom_diag_status',
             'rom_diag_prepare', 'lcd_init', 'plat_keys_off', 'usb_disable')
    require(all(name in symbols for name in names), 'required ROM diagnostic symbols missing')
    entry, start, end, bottom, stack, exception, status = (symbols[name][0] for name in names[:7])
    phoff, = struct.unpack_from('<I', elf, 28)
    work_length, = struct.unpack_from('<I', elf, phoff + 32 + 20)
    require(entry == APP and start == BSS and end == bottom and start <= status <= end - 4 and
            end % 8 == 0 and stack == bottom + 4096 and exception == stack + 1024 and
            exception == BSS + work_length, 'ROM working-memory or stack contract violated')
    forbidden = ('husb', 'usb_enumerate', 'usb_host', 'disk_', 'fat32', 'msc_', 'flash_', 'efuse',
                 'p33_', 'hal_reset', 'rom_download', 'sd_', 'boot_reset', 'watchdog_enable')
    require(not any(name.startswith(forbidden) for name in symbols), 'forbidden driver or command symbol linked')
    require(all(not name.startswith('usb_') or name == 'usb_disable' for name in symbols),
            'only the passive USB shutdown may link')
    check_entry(disassembly, stack, exception)
    print(f'ROM entry 0x{entry:08X}; APP1 FM-1 {len(image)} B, CRC16 0x{int.from_bytes(image[-4:-2], "little"):04X}')
    print(f'BSS 0x{start:08X}–0x{end:08X} ({end-start} B); work + two stacks {work_length} B')
    print(f'Main SP 0x{stack:08X}; SSP 0x{exception:08X}; private status word 0x{status:08X}')
    print('Entry: CLI, private stacks, unchanged r0, one C call, terminal wait. No return to ROM.')
    print('Linked audit: no Flash, eFuse, mass storage, Host or reset driver. Only passive USB shutdown.')
    print('Hardware not tested; launch requires separate authorization.')


def main():
    elf, flat, image, disassembly, *frames = map(Path, sys.argv[1:])
    try:
        validate(elf.read_bytes(), flat.read_bytes(), image.read_bytes(), disassembly.read_text())
        check_frames(frames)
    except (ValueError, OSError, struct.error) as error:
        sys.exit(str(error))


if __name__ == '__main__':
    main()
