#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Require the checked-in Android APP1 to equal the CI-generated artifact."""
import hashlib
import sys
from pathlib import Path

if len(sys.argv) != 3:
    raise SystemExit("usage: check_app1_consistency.py GENERATED CHECKED_IN")
generated, checked_in = (Path(p).read_bytes() for p in sys.argv[1:])
gh = hashlib.sha256(generated).hexdigest()
ch = hashlib.sha256(checked_in).hexdigest()
print(f"APP1 generated: {len(generated)} bytes SHA-256 {gh}")
print(f"APP1 Android:   {len(checked_in)} bytes SHA-256 {ch}")
if generated != checked_in:
    raise SystemExit("APP1 mismatch: regenerate and review the Android asset before publishing")
print("APP1 consistency: OK")
