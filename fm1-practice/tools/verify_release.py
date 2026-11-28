#!/usr/bin/env python3
"""Verify a locally retained FM1 PRACTICE release against its version manifest.
No network, no firmware writes, no extraction of untrusted paths."""
import argparse
import hashlib
import json
import pathlib
import zipfile

def sha256(data):
    return hashlib.sha256(data).hexdigest()

def verify(archive, manifest):
    expected = manifest["archive"]
    if archive.stat().st_size != expected["bytes"]:
        raise ValueError("Unexpected archive size")
    if sha256(archive.read_bytes()) != expected["sha256"]:
        raise ValueError("Archive SHA-256 mismatch")
    with zipfile.ZipFile(archive) as z:
        names = [n for n in z.namelist() if not n.endswith("/")]
        if len(names) != expected["files"] or len(names) != len(set(names)):
            raise ValueError("Unexpected or duplicate ZIP entries")
        for n in names:
            p = pathlib.PurePosixPath(n)
            if p.is_absolute() or ".." in p.parts or "\\" in n:
                raise ValueError("Unsafe archive path: " + n)
        bad = z.testzip()
        if bad is not None:
            raise ValueError("CRC failure: " + bad)
        for key in ("standalone", "build", "validation"):
            item = manifest[key]
            if sha256(z.read(item["name"])) != item["sha256"]:
                raise ValueError("Mismatch: " + item["name"])
    return len(names)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=pathlib.Path)
    parser.add_argument("manifest", type=pathlib.Path)
    a = parser.parse_args()
    m = json.loads(a.manifest.read_text(encoding="utf-8"))
    count = verify(a.archive, m)
    print("OK:", m["version"], count, "files; archive and embedded build/validation verified")
