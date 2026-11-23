#!/usr/bin/env python3
"""Generate a deterministic, read-only FAT32 test image for the host harness."""
import argparse
import hashlib, stat, struct
from pathlib import Path

SECTOR = 512
CLUSTERS = 65525
RESERVED = 32
FATS = 2
FAT_SECTORS = (CLUSTERS + 2 + 127) // 128
TOTAL = RESERVED + FATS * FAT_SECTORS + CLUSTERS

def put32(b, off, value): struct.pack_into('<I', b, off, value)
def name11(name):
    if name in ('.', '..'):
        return name.ljust(11).encode('ascii')
    stem, ext = (name.split('.', 1) + [''])[:2]
    return (stem.upper().ljust(8)[:8] + ext.upper().ljust(3)[:3]).encode('ascii')
def entry(name, cluster, size, directory=False):
    b = bytearray(32); b[:11] = name11(name); b[11] = 0x10 if directory else 0x20
    struct.pack_into('<H', b, 20, cluster >> 16); struct.pack_into('<H', b, 26, cluster & 0xffff)
    put32(b, 28, size); return b

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', nargs='?', type=Path, default=Path('build/fat32-test.img'))
    parser.add_argument('count', type=int, nargs='?', default=3)
    parser.add_argument('--slotfile-card', type=Path, help='use the same synthetic catalog and files as a 5Y demo')
    parser.add_argument('--spi-package', type=Path, help='verified EMPTY or SYNTHETIC 6D ZIP; output is a test file')
    parser.add_argument('--mbr', action='store_true', help='place a 6D test volume in the first MBR partition')
    args = parser.parse_args()
    out, count = args.output, args.count
    if (args.mbr and not args.spi_package) or (args.spi_package and args.slotfile_card):
        parser.error('--mbr requires --spi-package, which is exclusive of --slotfile-card')
    if not 0 <= count <= 64: raise SystemExit('count must be 0..64')
    menu = None
    if args.spi_package:
        from fm1_sd_package import read_package
        _, menu, index, firmware, _ = read_package(args.spi_package)
        sources = [(ident, f'{ident}.BIN', data) for ident, data in firmware.items()]
        count = len(sources)
    elif args.slotfile_card:
        from virtual_slotfile import candidate, parse_catalog, read_file
        folder = args.slotfile_card / 'FM1'
        index = read_file(folder / 'INDEX.CSV', 24576)
        sources = []
        for item in parse_catalog(index).values():
            data, _ = candidate(args.slotfile_card, item)
            sources.append((item.id, item.filename, data))
        count = len(sources)
    else:
        index = None
        sources = []
        for i in range(count):
            ident = f'F{i+1:02d}'
            filename = ident + ('.FWS' if i == 1 else '.BIN')
            data = (f'FM1-5D-{ident}-' + ('0123456789abcdef' * 40)).encode()[:700 + i]
            sources.append((ident, filename, data))
    base = 2048 if args.mbr else 0
    data_start = base + RESERVED + FATS * FAT_SECTORS
    image = bytearray((base + TOTAL) * SECTOR)
    if base:
        image[446:462] = struct.pack('<4s4sII', b'\x00\xfe\xff\xff', b'\x0c\xfe\xff\xff', base, TOTAL)
        image[510:512] = b'\x55\xaa'
    # The parser intentionally requires at least 65525 clusters.
    b = memoryview(image)[base*SECTOR:(base+1)*SECTOR]
    b[0:3] = b'\xeb\x58\x90'; b[3:11] = b'GOOSE5D '
    struct.pack_into('<H', b, 11, SECTOR); b[13] = 1; struct.pack_into('<H', b, 14, RESERVED)
    b[16] = FATS; struct.pack_into('<H', b, 17, 0); struct.pack_into('<H', b, 19, 0)
    struct.pack_into('<I', b, 32, TOTAL); struct.pack_into('<I', b, 36, FAT_SECTORS)
    struct.pack_into('<H', b, 40, 0); struct.pack_into('<H', b, 42, 0); struct.pack_into('<I', b, 44, 2)
    struct.pack_into('<H', b, 48, 1); b[64] = 0x80; b[66] = 0x29; b[82:90] = b'FAT32   '
    b[510:512] = b'\x55\xaa'
    if menu is not None:
        b[21] = 0xf8
        struct.pack_into('<HHI', b, 24, 63, 255, base)
        struct.pack_into('<H', b, 50, 6)
        b[71:82] = b'NO NAME    '
        image[(base+6)*SECTOR:(base+7)*SECTOR] = b
    fat = [0] * (CLUSTERS + 2); fat[0] = 0x0ffffff8; fat[1] = 0x0fffffff
    next_cluster = 2
    def alloc_chain(data):
        nonlocal next_cluster
        chunks = [data[i:i+SECTOR] for i in range(0, len(data), SECTOR)] or [b'']
        # Deliberately leave gaps between allocations to exercise fragmented chains.
        chain = list(range(next_cluster, next_cluster + len(chunks) * 2, 2)); next_cluster += len(chunks) * 2
        for a, z in zip(chain, chain[1:] + [0x0fffffff]): fat[a] = z
        for c, chunk in zip(chain, chunks):
            start = (data_start + c - 2) * SECTOR
            image[start:start + len(chunk)] = chunk
        return chain[0], len(data)
    # Directory chains are allocated from fixed clusters and may span sectors.
    def dir_chain(entries):
        raw = b''.join(bytes(e) for e in entries) + b'\0' * 32
        return alloc_chain(raw)
    root_cluster, _ = dir_chain([])
    fm1_cluster, _ = dir_chain([])
    fw_entries = []
    records = []
    for ident, filename, data in sources:
        cluster, size = alloc_chain(data)
        fw_entries.append(entry(filename, cluster, size))
        records.append((ident, filename, size, hashlib.sha256(data).hexdigest()))
    if menu is not None:
        fw_entries = [entry('.', next_cluster, 0, True), entry('..', fm1_cluster, 0, True)] + fw_entries
    fw_cluster, _ = dir_chain(fw_entries)
    # Build INDEX after hashes are known, then allocate it.
    if index is None:
        lines = ['FM1CAT|1', 'ID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT']
        lines += [f'{i}|Firmware {i}|1.0|{fn}|{size}|{sha}|UNVERIFIED' for i, fn, size, sha in records]
        index = ('\n'.join(lines) + '\n').encode()
    index_cluster, index_size = alloc_chain(index)
    # Rebuild the two directories with the actual child clusters.
    def write_cluster(cluster, data):
        start = (data_start + cluster - 2) * SECTOR
        image[start:start + len(data)] = data
    root_entries = [entry('FM1', fm1_cluster, 0, True)]
    fm1_entries = [entry('FW', fw_cluster, 0, True), entry('INDEX.CSV', index_cluster, index_size)]
    if menu is not None:
        menu_cluster, menu_size = alloc_chain(menu)
        mboot_cluster, _ = dir_chain([entry('.', next_cluster, 0, True), entry('..', 0, 0, True),
                                      entry('MENU.BIN', menu_cluster, menu_size)])
        root_entries.append(entry('MBOOT', mboot_cluster, 0, True))
        fm1_entries = [entry('.', fm1_cluster, 0, True), entry('..', 0, 0, True)] + fm1_entries
    write_cluster(root_cluster, b''.join(root_entries) + bytes(32))
    write_cluster(fm1_cluster, b''.join(fm1_entries) + bytes(32))
    if menu is not None:
        fsinfo = bytearray(SECTOR)
        free = fat.count(0)
        first_free = fat.index(0, 2) if free else 0xffffffff
        for offset, value in ((0, 0x41615252), (484, 0x61417272), (488, free),
                              (492, first_free), (508, 0xaa550000)):
            put32(fsinfo, offset, value)
        for relative in (1, 7):
            image[(base+relative)*SECTOR:(base+relative+1)*SECTOR] = fsinfo
    # FAT copies.
    fat_bytes = b''.join(struct.pack('<I', x) for x in fat)
    fat_bytes += b'\0' * (FAT_SECTORS * SECTOR - len(fat_bytes))
    for copy in range(FATS):
        start = (base + RESERVED + copy * FAT_SECTORS) * SECTOR
        image[start:start + len(fat_bytes)] = fat_bytes
    out.parent.mkdir(parents=True, exist_ok=True)
    # Sparse fixture. 6D creates only a previously nonexistent regular output file.
    if (out.exists() or out.is_symlink()) and not stat.S_ISREG(out.lstat().st_mode):
        parser.error('output must be a regular file, never a device or symbolic link')
    with out.open('xb' if args.spi_package else 'wb') as stream:
        for start in range(0, len(image), 65536):
            chunk = image[start:start + 65536]
            if chunk.strip(b'\0'):
                stream.seek(start)
                stream.write(chunk)
        stream.truncate(len(image))
    print(f'image={out} bytes={len(image)} clusters={CLUSTERS} entries={count} index_bytes={len(index)}')
    for ident, fn, size, sha in records: print(f'{ident} {fn} {size} {sha}')

if __name__ == '__main__': main()
