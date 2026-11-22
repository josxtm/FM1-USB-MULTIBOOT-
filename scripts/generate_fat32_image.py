#!/usr/bin/env python3
"""Generate a deterministic, read-only FAT32 test image for the host harness."""
import hashlib, struct, sys
from pathlib import Path

SECTOR = 512
CLUSTERS = 65525
RESERVED = 32
FATS = 2
FAT_SECTORS = (CLUSTERS + 2 + 127) // 128
TOTAL = RESERVED + FATS * FAT_SECTORS + CLUSTERS

def put32(b, off, value): struct.pack_into('<I', b, off, value)
def name11(name):
    stem, ext = (name.split('.', 1) + [''])[:2]
    return (stem.upper().ljust(8)[:8] + ext.upper().ljust(3)[:3]).encode('ascii')
def entry(name, cluster, size, directory=False):
    b = bytearray(32); b[:11] = name11(name); b[11] = 0x10 if directory else 0x20
    struct.pack_into('<H', b, 20, cluster >> 16); struct.pack_into('<H', b, 26, cluster & 0xffff)
    put32(b, 28, size); return b

def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else 'build/fat32-test.img')
    count = int(sys.argv[2] if len(sys.argv) > 2 else 3)
    if not 0 <= count <= 64: raise SystemExit('count must be 0..64')
    image = bytearray(TOTAL * SECTOR)
    # FAT32 superfloppy BPB. The parser intentionally requires >=65525 clusters.
    b = memoryview(image)[0:SECTOR]; b[0:3] = b'\xeb\x58\x90'; b[3:11] = b'GOOSE5D '
    struct.pack_into('<H', b, 11, SECTOR); b[13] = 1; struct.pack_into('<H', b, 14, RESERVED)
    b[16] = FATS; struct.pack_into('<H', b, 17, 0); struct.pack_into('<H', b, 19, 0)
    struct.pack_into('<I', b, 32, TOTAL); struct.pack_into('<I', b, 36, FAT_SECTORS)
    struct.pack_into('<H', b, 40, 0); struct.pack_into('<H', b, 42, 0); struct.pack_into('<I', b, 44, 2)
    struct.pack_into('<H', b, 48, 1); b[64] = 0x80; b[66] = 0x29; b[82:90] = b'FAT32   '
    b[510:512] = b'\x55\xaa'
    fat = [0] * (CLUSTERS + 2); fat[0] = 0x0ffffff8; fat[1] = 0x0fffffff
    next_cluster = 2
    def alloc_chain(data):
        nonlocal next_cluster
        chunks = [data[i:i+SECTOR] for i in range(0, len(data), SECTOR)] or [b'']
        # Deliberately leave gaps between allocations to exercise fragmented chains.
        chain = list(range(next_cluster, next_cluster + len(chunks) * 2, 2)); next_cluster += len(chunks) * 2
        for a, z in zip(chain, chain[1:] + [0x0fffffff]): fat[a] = z
        for c, chunk in zip(chain, chunks):
            start = (RESERVED + FATS * FAT_SECTORS + c - 2) * SECTOR
            image[start:start + len(chunk)] = chunk
        return chain[0], len(data)
    # Directory chains are allocated from fixed clusters and may span sectors.
    def dir_chain(entries):
        raw = b''.join(bytes(e) for e in entries) + b'\0' * 32
        return alloc_chain(raw)
    root_cluster, _ = dir_chain([entry('FM1', 3, 0, True)])
    fm1_cluster, _ = dir_chain([entry('FW', 4, 0, True)])
    # Keep the named directory clusters deterministic for easier report review.
    # alloc_chain starts at 2, so root/FW are the first two allocations; patch names below.
    fw_entries = []
    records = []
    for i in range(count):
        ident = f'F{i+1:02d}'
        filename = ident + ('.FWS' if i == 1 else '.BIN')
        data = (f'FM1-5D-{ident}-' + ('0123456789abcdef' * 40)).encode()[:700 + i]
        cluster, size = alloc_chain(data)
        fw_entries.append(entry(filename, cluster, size))
        records.append((ident, filename, size, hashlib.sha256(data).hexdigest()))
    fw_cluster, _ = dir_chain(fw_entries)
    # Build INDEX after hashes are known, then allocate it.
    lines = ['FM1CAT|1', 'ID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT']
    lines += [f'{i}|Firmware {i}|1.0|{fn}|{size}|{sha}|UNVERIFIED' for i, fn, size, sha in records]
    index = ('\n'.join(lines) + '\n').encode(); index_cluster, index_size = alloc_chain(index)
    # Rebuild the two directories with the actual child clusters.
    def write_cluster(cluster, data):
        start = (RESERVED + FATS * FAT_SECTORS + cluster - 2) * SECTOR
        image[start:start + len(data)] = data
    write_cluster(root_cluster, bytes(entry('FM1', fm1_cluster, 0, True)) + b'\0' * 32)
    write_cluster(fm1_cluster, bytes(entry('FW', fw_cluster, 0, True)) + bytes(entry('INDEX.CSV', index_cluster, index_size)) + b'\0' * 32)
    # FAT copies.
    fat_bytes = b''.join(struct.pack('<I', x) for x in fat)
    fat_bytes += b'\0' * (FAT_SECTORS * SECTOR - len(fat_bytes))
    for copy in range(FATS): image[(RESERVED + copy * FAT_SECTORS) * SECTOR:(RESERVED + (copy + 1) * FAT_SECTORS) * SECTOR] = fat_bytes
    out.parent.mkdir(parents=True, exist_ok=True); out.write_bytes(image)
    print(f'image={out} bytes={len(image)} clusters={CLUSTERS} entries={count} index_bytes={len(index)}')
    for ident, fn, size, sha in records: print(f'{ident} {fn} {size} {sha}')

if __name__ == '__main__': main()
