#!/usr/bin/env python3
"""Generate synthetic records using the FM1CAT v2 wire contract from alpha03."""
import argparse
from hashlib import sha256
import json
from pathlib import Path


def digest(data):
    return sha256(data).hexdigest()


def body(obj):
    return json.dumps(obj, separators=(",", ":"), ensure_ascii=False).encode()


def catalogue(root, count, slots, corrupt=None):
    items = []
    for i in range(count):
        payload = (f"synthetic-usbc-{i} ".encode() * (30000 if i == 0 else 5))[:400123]
        checksum = digest(payload)
        original, fmt, extension = (("Instrument.BIN", "SLOT v1", "BIN"),
                                    ("Instrument.FWSC", "FWSC/UFW", "FWS"),
                                    ("Instrument.UFW", "UFW", "UFW"))[i % 3]
        key = checksum.upper()
        path = f"FW/{key[:8]}.{extension}" if i != 1 else f"FW/{key[:2]}/{key[2:10]}/{key[10:18]}.{extension}"
        file = root / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_bytes(payload)
        items.append(dict(name='Sintetizador "α" 🎵' + f" {i}", project="test/project", author="Autora",
                          version="1.2", original=original, size=len(payload), sha256=checksum, format=fmt,
                          path=path, compat="UNVERIFIED"))
    items.sort(key=lambda row: row["sha256"])
    cat = root / "CATALOG"
    cat.mkdir(parents=True, exist_ok=True)
    hashes, pages = [], []
    for p, start in enumerate(range(0, count, 32), 1):
        page = f"FM1CAT|2|PAGE|{p}\n".encode() + b"".join(body(item) + b"\n" for item in items[start:start+32])
        hashes.append(digest(page))
        pages.append(page)
    generation = digest("".join(hashes).encode())[:16].upper()
    folder = cat / generation[:8] / generation[8:]
    folder.mkdir(parents=True, exist_ok=True)
    for i, page in enumerate(pages, 1):
        (folder / f"P{i:07d}.JSN").write_bytes(page)
    for slot, sequence in slots:
        index = body(dict(pages=hashes, sequence=sequence, count=count, generation=generation))
        (cat / slot).write_bytes(b"FM1CAT|2\n" + digest(index).encode() + b"\n" + index)
    if corrupt == "page":
        (folder / "P0000002.JSN").write_bytes(pages[1].replace(b"Autora", b"AutorX", 1))
    elif corrupt == "index":
        (cat / "INDEXB.V2").write_bytes(b"broken")
    elif corrupt == "file":
        (root / items[0]["path"]).write_bytes(bytes(items[0]["size"]))
    elif corrupt == "missing":
        (root / items[0]["path"]).unlink()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path)
    parser.add_argument("count", type=int)
    parser.add_argument("--fault", choices=["page", "index", "file", "missing", "tie"])
    args = parser.parse_args()
    if not 1 <= args.count <= 4096 or args.root.exists() or args.root.is_symlink():
        parser.error("provide a new destination and a count of 1..4096")
    catalogue(args.root, args.count,
              [("INDEXA.V2", 4294967297), ("INDEXB.V2", 4294967297 if args.fault == "tie" else 4294967298)],
              args.fault)
