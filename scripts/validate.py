#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Check the exact pi32v2 RAM app layout and APP1 trailer."""
import binascii
import struct
import sys
from pathlib import Path

APP = 0x01C30000
RETAINED = 0x01C7FD80
BSS = 0x01C16800
STACK_REGION = 0x01C1E000


def check(ok, message):
    if not ok:
        raise ValueError(message)


def validate(elf, flat, image):
    check(len(elf) >= 52 and elf[:7] == b"\x7fELF\x01\x01\x01", "expected little-endian ELF32")
    kind, machine, version, entry, phoff = struct.unpack_from("<HHIII", elf, 16)
    entsize, n = struct.unpack_from("<HH", elf, 42)
    check((kind, machine, version, entry) == (2, 241, 1, APP), "expected executable JieLi pi32v2 at APP")
    check(entsize == 32 and n == 2 and phoff <= len(elf) - 64, "expected two program headers")
    code, zero = [struct.unpack_from("<8I", elf, phoff + entsize * i) for i in range(n)]
    kind, offset, virt, phys, filesz, memsz, flags, _ = code
    check(kind == 1 and virt == phys == APP and flags == 7 and filesz == memsz == len(flat),
          "bad image segment")
    check(flat == elf[offset:offset + filesz], "flat image differs from ELF segment")
    kind, _, virt, phys, filesz, memsz, flags, _ = zero
    check(kind == 1 and virt == phys == BSS and filesz == 0 and flags == 6 and
          0 < memsz <= STACK_REGION - BSS, "BSS overlaps protected memory or stacks")
    check(16 <= len(image) <= RETAINED - APP and len(image) % 2 == 0, "invalid app size")
    magic, crc, board, fmt = struct.unpack("<4sHBB", image[-8:])
    check((magic, board, fmt) == (b"APP1", 1, 1) and image[:-8] == flat, "invalid APP1 format or board")
    check(binascii.crc_hqx(flat, 0) == crc, "invalid APP1 CRC")
    print(f"pi32v2, entry 0x{entry:08X}, RAM image {len(image)} bytes, BSS {memsz} bytes, APP1 CRC {crc:04X}")


if __name__ == "__main__":
    try:
        validate(*(Path(path).read_bytes() for path in sys.argv[1:]))
    except (ValueError, struct.error) as error:
        sys.exit(str(error))
