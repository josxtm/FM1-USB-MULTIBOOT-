#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Prepare or verify FM-1 SPI1 card contents in an offline ZIP."""
import argparse
from dataclasses import asdict
from io import BytesIO
import json
from pathlib import Path
import re
import stat
import zipfile

from validate_stg2 import check_trailer
from virtual_slotfile import (CATALOG_HEADER, FIXTURE_MAGIC, ID, MAX_SLOT, START,
                              decode_slot, fixture_slot, parse_catalog, read_file, sha)

STG2_SHA = "0e9ef963d5a0c614d09a7bd3dc3ba808b25c611d572e9544f37bd9586c283002"
TARGET = {"board": "FM-1", "jedec": "856014", "bootloader": "2.4-395E6CC"}
SELECTOR = "MBOOT/MENU.BIN"
INDEX = "FM1/INDEX.CSV"
FW = "FM1/FW/"
REPORT = "PACKAGE.JSON"
DIRS = ("MBOOT/", "FM1/", FW)
DEMO_IDS = ("DEMOA", "DEMOB")
ARCHIVE_LIMIT = MAX_SLOT * 64 + 256 * 1024
RESERVED_DOS = re.compile(r"(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])")


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def json_bytes(data):
    return (json.dumps(data, ensure_ascii=True, sort_keys=True, indent=2) + "\n").encode("ascii")


def no_duplicate_pairs(pairs):
    result = dict(pairs)
    require(len(result) == len(pairs), "duplicate JSON member")
    return result


def parse_json(data):
    return json.loads(data, object_pairs_hook=no_duplicate_pairs)


def reviewed_entries(review_path):
    review = parse_json(read_file(review_path, 64 * 1024))
    require(isinstance(review, dict) and set(review) == {"schema", "target", "entries"} and
            type(review["schema"]) is int and review["schema"] == 1 and review["target"] == TARGET,
            "review must match the FM-1/Puya/m-bootloader profile")
    require(isinstance(review["entries"], list) and 0 < len(review["entries"]) <= 64,
            "review needs 1–64 entries")
    metadata = {}
    for record in review["entries"]:
        require(isinstance(record, dict) and set(record) == {"id", "label", "version", "size", "sha256", "format", "source"},
                "invalid review record fields")
        require(all(isinstance(record[k], str) for k in record if k != "size") and
                type(record["size"]) is int and 0 < record["size"] <= MAX_SLOT, "invalid review field types or size")
        ident = record["id"]
        require(ID.fullmatch(ident) and not RESERVED_DOS.fullmatch(ident) and ident not in metadata,
                "invalid, reserved or duplicate firmware ID")
        require(record["format"] == "SLOT-v1" and 1 <= len(record["source"]) <= 200 and
                record["source"].isascii() and record["source"].isprintable(), "unsupported format or missing provenance")
        metadata[ident] = record
    return metadata


def catalog_from(records):
    lines = list(CATALOG_HEADER)
    for ident, row in sorted(records.items()):
        lines.append(f'{ident}|{row["label"]}|{row["version"]}|{ident}.BIN|{row["size"]}|{row["sha256"]}|UNVERIFIED')
    catalog = ("\n".join(lines) + "\n").encode("ascii")
    parsed = parse_catalog(catalog)
    require(set(parsed) == set(records), "review contains a malformed catalog field")
    return catalog


def demo_records():
    records = {}
    for ident in DEMO_IDS:
        data = fixture_slot(ident)
        records[ident] = {"label": f"Synthetic {ident[-1]}", "version": "1",
                          "size": len(data), "sha256": sha(data)}
    return records


def validate_content(selector, catalog, firmware, mode, records):
    check_trailer(selector)
    require(sha(selector) == STG2_SHA, "selector SHA-256 differs from the compiled 5Z reference")
    require(catalog == catalog_from(records), "FM1CAT does not match independent inputs")
    entries = parse_catalog(catalog)
    require(firmware.keys() == entries.keys(), "missing or unexpected firmware")
    for ident, entry in entries.items():
        data = firmware[ident]
        require((len(data), sha(data)) == (entry.size, entry.sha256), f"{ident}: size or SHA-256 mismatch")
        sectors = decode_slot(data)
        if mode == "SYNTHETIC":
            require(data == fixture_slot(ident), f"{ident}: synthetic fixture mismatch")
        else:
            require(not sectors[START].startswith(FIXTURE_MAGIC), f"{ident}: synthetic content in private review")
    return {
        "schema": 1, "target": TARGET, "mode": mode, "catalog_format": "FM1CAT-v1",
        "launch_policy": "BLOCKED", "hardware_io": False,
        "selector_sha256": STG2_SHA, "catalog_sha256": sha(catalog),
        "entries": [{**asdict(entries[ident]), "format": "SLOT-v1", "compat": "UNVERIFIED",
                     "provenance": records[ident].get("source", "5Y synthetic fixture")}
                    for ident in entries],
    }


def content_for(mode, review):
    require((mode == "PRIVATE_REVIEWED") == (review is not None), "private mode requires the independent local review")
    if mode == "PRIVATE_REVIEWED":
        return reviewed_entries(review)
    if mode == "SYNTHETIC":
        return demo_records()
    require(mode == "EMPTY", "unknown package mode")
    return {}


def put_zip(archive, name, data):
    meta = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
    meta.create_system = 3
    meta.external_attr = (0o40755 << 16 | 0x10) if name.endswith("/") else (0o100644 << 16)
    archive.writestr(meta, data)


def build(output, selector, demo=False, review=None, source=None):
    require(not (demo and review) and ((review is None) == (source is None)), "choose empty, demo or private reviewed inputs")
    mode = "SYNTHETIC" if demo else "PRIVATE_REVIEWED" if review else "EMPTY"
    records = content_for(mode, review)
    catalog = catalog_from(records)
    menu = read_file(selector, 0x8000)
    firmware = {ident: fixture_slot(ident) if demo else read_file(source / f"{ident}.BIN", MAX_SLOT)
                for ident in records}
    summary = validate_content(menu, catalog, firmware, mode, records)
    mem = BytesIO()
    with zipfile.ZipFile(mem, "w", zipfile.ZIP_STORED) as archive:
        for name in DIRS:
            put_zip(archive, name, b"")
        put_zip(archive, SELECTOR, menu)
        put_zip(archive, INDEX, catalog)
        for ident, data in sorted(firmware.items()):
            put_zip(archive, f"{FW}{ident}.BIN", data)
        put_zip(archive, REPORT, json_bytes(summary))
    with output.open("xb") as stream:
        stream.write(mem.getbuffer())
    return summary


def read_member(archive, name, limit, directory=False):
    info = archive.getinfo(name)
    mode = (info.external_attr >> 16) & 0xFFFF
    require(info.is_dir() == directory and stat.S_IFMT(mode) == (stat.S_IFDIR if directory else stat.S_IFREG) and
            info.file_size <= limit and info.compress_type == zipfile.ZIP_STORED and not (info.flag_bits & 1),
            f"unexpected type, size or encoding for {name}")
    return archive.read(info)


def read_package(package, review=None):
    """Verify without extraction. Return the exact inputs for the image fixture."""
    raw = read_file(package, ARCHIVE_LIMIT)
    with zipfile.ZipFile(BytesIO(raw)) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), "duplicate ZIP member")
        report = read_member(archive, REPORT, 128 * 1024)
        stored = parse_json(report)
        require(isinstance(stored, dict) and isinstance(stored.get("mode"), str), "invalid report mode")
        mode = stored["mode"]
        records = content_for(mode, review)
        expected = {*DIRS, SELECTOR, INDEX, REPORT, *(f"{FW}{ident}.BIN" for ident in records)}
        require(set(names) == expected, "unexpected or missing ZIP path")
        for name in DIRS:
            read_member(archive, name, 0, directory=True)
        menu = read_member(archive, SELECTOR, 0x8000)
        catalog = read_member(archive, INDEX, 24 * 1024)
        firmware = {ident: read_member(archive, f"{FW}{ident}.BIN", MAX_SLOT) for ident in records}
        summary = validate_content(menu, catalog, firmware, mode, records)
        require(report == json_bytes(summary), "metadata differs from verified content")
        return summary, menu, catalog, firmware, sha(raw)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    actions = parser.add_subparsers(dest="action", required=True)
    create = actions.add_parser("build")
    create.add_argument("--selector", type=Path, required=True)
    create.add_argument("--output", type=Path, required=True)
    create.add_argument("--demo", action="store_true")
    create.add_argument("--review", type=Path)
    create.add_argument("--source", type=Path)
    verify = actions.add_parser("verify")
    verify.add_argument("package", type=Path)
    verify.add_argument("--review", type=Path)
    args = parser.parse_args()
    try:
        if args.action == "build":
            build(args.output, args.selector, args.demo, args.review, args.source)
            summary, _, _, _, digest = read_package(args.output, args.review)
        else:
            summary, _, _, _, digest = read_package(args.package, args.review)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f"SD PACKAGE REJECTED: {error}\n")
    print(json_bytes({**summary, "package_sha256": digest}).decode("ascii"), end="")


if __name__ == "__main__":
    main()
