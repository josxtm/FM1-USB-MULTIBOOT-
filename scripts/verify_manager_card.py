#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Stream an FM1CAT v2 export in host tests; the FM-1 embedded reader remains v1."""
import argparse
from hashlib import sha256
import json
from pathlib import Path
import re

from virtual_slotfile import read_file

SHA = re.compile(r"[a-f0-9]{64}")
GEN = re.compile(r"[A-F0-9]{16}")
EXT = {"fwsc": "FWS", "ufw": "UFW", "bin": "BIN"}


def need(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data):
    return sha256(data).hexdigest()


def page_path(root, generation, i):
    return root / "CATALOG" / generation[:8] / generation[8:] / f"P{i + 1:07d}.JSN"


def index(path):
    tag, expected, body = read_file(path, 24 * 1024).split(b"\n", 2)
    need(tag == b"FM1CAT|2" and digest(body).encode() == expected, "invalid v2 index hash")
    obj = json.loads(body)
    generation, sequence, count, hashes = (obj[key] for key in ("generation", "sequence", "count", "pages"))
    need(GEN.fullmatch(generation) and type(sequence) is int and sequence > 0 and
         type(count) is int and 1 <= count <= 4096 and
         len(hashes) == (count + 31) // 32 and all(SHA.fullmatch(h) for h in hashes) and
         generation == digest("".join(hashes).encode())[:16].upper(), "invalid index metadata")
    return generation, sequence, count, hashes


def validate(root, path, files=False):
    generation, sequence, count, pages = index(path)
    previous = ""
    for i, expected in enumerate(pages):
        data = read_file(page_path(root, generation, i), 128 * 1024)
        need(digest(data) == expected and data.endswith(b"\n"), "page corrupted")
        head, *rows = data.splitlines()
        need(head == f"FM1CAT|2|PAGE|{i+1}".encode() and len(rows) == min(32, count - i * 32), "invalid page")
        for line in rows:
            row = json.loads(line)
            name, size, checksum = row["original"], row["size"], row["sha256"]
            need(SHA.fullmatch(checksum) and checksum > previous and row["compat"] == "UNVERIFIED" and
                 type(size) is int and 0 < size <= 16 * 1024 * 1024 and
                 isinstance(name, str) and 0 < len(name) <= 160 and not re.search(r"[/\\:\x00-\x1f]", name),
                 "invalid record")
            previous = checksum
            h = checksum.upper()
            ext = EXT[name.rsplit(".", 1)[-1].lower()]
            relative = f"FW/{h[:2]}/{h[2:10]}/{h[10:18]}.{ext}"
            need(row["path"] == relative, "invalid payload path")
            if files:
                original = read_file(root / relative, size)
                need(len(original) == size and digest(original) == checksum, "payload changed")
    return sequence, count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path, help="FM1 folder")
    parser.add_argument("--files", action="store_true")
    args = parser.parse_args()
    valid, rejected = [], 0
    for slot in ("INDEXA.V2", "INDEXB.V2"):
        path = args.root / "CATALOG" / slot
        if not path.exists():
            continue
        try:
            valid.append(validate(args.root, path, args.files))
        except (OSError, ValueError, KeyError, TypeError):
            rejected += 1
    need(bool(valid) and len(valid) == len({s for s, _ in valid}), "no unambiguous valid v2 index")
    seq, count = max(valid)
    print(json.dumps({"format": "FM1CAT-v2", "sequence": seq, "entries": count,
                      "rejected_slots": rejected, "files_checked": args.files}))


if __name__ == "__main__":
    main()
