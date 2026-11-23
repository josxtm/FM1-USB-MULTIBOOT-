#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Phase 6B offline evidence checks. No USB, external commands, or physical launch."""
import argparse
import binascii
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import re
import stat
import struct
import sys
import zipfile

from validate import APP
from validate_rom_diag import validate as validate_rom

PR12_ZIP = "0a3f63a247e13bada70da3f8d2f5e4dca6549a29efe78cc4d652d4a8eb0a7e5f"
IMAGE_NAME = "fm1-rom-hardware-test"
IMAGE_DIGEST = "a0012d9f32b414ee38aa8b76e0f412b64a77c4163001037a44b77ac8eda91cbb"
IMAGE_SIZE = 4100
ROM_DIR = "build/romdiag/"
ARTIFACTS = {
    IMAGE_NAME + ".bin": IMAGE_DIGEST,
    IMAGE_NAME + ".elf": "b0ec0674cad7a266762d39361dc4b0c35702ad4bb9379edc8e6e3a23356daad2",
    IMAGE_NAME + ".map": "45e941da30f748aacd92e98baa7b19d63f954c3d94f14ef890bc7c297cca5f07",
    "disassembly.txt": "e176c44930754ba9985beaa96f176a196e3ff94bb901f9012a71069b427051fe",
    "elf-report.txt": "002d309a9f212cc9d932517e469c0cec7fbbec2812a96729e2652b05dc55ffc5",
    "ABI-SAFETY.txt": "512bec0f2a34b0403d3db32470a009fb1e4e67248029449075b613fec238c23f",
    "dry-run.json": "2b3aea14009d7cce622c6e1835bf99afa0cf38e75258c66acbf9361345eb8bce",
    "dry-run-timeout.json": "4f1fabf6c469dad3fc2363172e875e82ec50292b80fb3f9d15df0eb41b10da5b",
}
MFLASHER_COMMIT = "8fbece7348d758ac284325eed455781675ac95ff"
MFLASHER_VERSION = "0.12.1"
MFLASHER_SOURCES = {
    "Cargo.toml": "b4beb443f80c31099d70ac70c25001233bb11ddb05a4030acdde11c8560ca384",
    "Cargo.lock": "3b171a2e144933388935860530c2bb7dd8fed378b91b0bb94b645be448cd482e",
    "src/main.rs": "a85efc666101f849c9451190e34e09742bfd88e34553eecf04778d9eacdbac5d",
    "src/cli.rs": "fc0a71512863737db507f1bbdf90d5f319ac45082a84b9b42e5064db368a1614",
    "src/jl.rs": "874f589c2c49f30dae7ec25213d2c45d6ce40d349e5e66e174654a451b4de3ca",
    "src/scsi.rs": "aaa28596096bb3aea5c0b00cd3af0eb794b0fb6c5964113ac78c34c824a50e9a",
    "src/usb.rs": "efc429fc1c957ba3d99fc3b1d0bd5cca76284c2c6300162389572f995bb0403f",
    "src/midi.rs": "9e1dd61fdf59ea9c65d5d7d2f98319fef703bc7aa435c01e082e6d6ad4a05980",
    "src/windows_helper.rs": "ca5f6af288517aaf4b05b43813b5e7d349d2ed39d238e73f49e79524f6f0feef",
    "src/macos.rs": "e488da6e4c98b80907db63696ffc918b1c0a84313094049cd01ba1fc2c43048f",
    "linux/70-m-flasher.rules": "c0e2687a89c32971f07a09a2f5b218743b08ac7437b0d6655a88098f68bfd4c4",
}
HOST_RELEASES = {
    ("linux", "x86_64"): ("Linux x86_64 tar.gz", "c0cf009de744244c072af439f298518dfed54e19cdb29db33a6d00f828253136"),
    ("linux", "arm64"): ("Linux aarch64 tar.gz", "dd9fa1577913b8ff63c440473b16cc657fe33f169fdb0a4ae2debd89c7ee01bc"),
    ("darwin", "x86_64"): ("macOS universal DMG", "ccb469a1ceead508e50a42b6c02946ea0d0caee18a9d06ec830c268ee8717192"),
    ("win32", "x86_64"): ("Windows x64 ZIP", "a4d6ad803014a08e15350f680e6e01c7e23947f905a82a8f958f09642b63804b"),
}
HOST_RELEASES[("darwin", "arm64")] = HOST_RELEASES[("darwin", "x86_64")]
BLOCKS = (512,) * 8 + (4,)
STOPS = {
    "authorization": "Owner authorization has not been recorded by this offline tool",
    "host_usb": "The actual device, OS background I/O, permissions and exclusive transport are unverified",
    "resident": "Stock run-ram treats an ambiguous FC40 probe as absence and may continue",
    "transport": "Stock run-ram retries failed uploads and discards the transferred byte count",
    "recovery": "A cold ROM entry and normal boot/recovery have not been demonstrated on this unit",
}
MAX_MEMBER = 1024 * 1024


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def need(condition, detail):
    if not condition:
        raise ValueError(detail)


def read_regular(path, limit=MAX_MEMBER):
    info = path.lstat()
    need(stat.S_ISREG(info.st_mode) and info.st_size <= limit, "expected a bounded regular file")
    with path.open("rb") as stream:
        data = stream.read(limit + 1)
    need(len(data) <= limit, "file exceeds size limit")
    return data


@contextlib.contextmanager
def open_package(path):
    if path.is_dir():
        yield lambda name: read_regular(path / name)
        return
    package = read_regular(path)
    need(sha256(package) == PR12_ZIP, "ZIP SHA-256 differs from PR #12 artifact")
    with zipfile.ZipFile(io.BytesIO(package)) as archive:
        names = archive.namelist()
        need(len(names) == len(set(names)), "duplicate ZIP members")

        def read_member(name):
            info = archive.getinfo(name)
            need(info.file_size <= MAX_MEMBER, f"oversized ZIP member {name}")
            return archive.read(info)

        yield read_member


def check_app1(image):
    need(16 <= len(image) <= 8192 and len(image) % 2 == 0, "outside 8 KiB APP1 size budget")
    magic, crc, board, fmt = struct.unpack("<4sHBB", image[-8:])
    need((magic, board, fmt) == (b"APP1", 1, 1), "expected APP1 v1 for FM-1")
    need(binascii.crc_hqx(image[:-8], 0) == crc == 0xB786, "APP1 CRC16 differs from 0xB786")
    return "APP1 v1, FM-1, CRC16 0xB786"


def check_link(elf, image, disassembly):
    with contextlib.redirect_stdout(io.StringIO()):
        validate_rom(elf, image[:-8], image, disassembly.decode("utf-8"))
    return "pi32v2 0x01C30000; work 0x01C16800–0x01C18038; 4096 B SP and 1024 B SSP; terminal wait"


def check_files(read, expected):
    for name, digest in expected.items():
        need(sha256(read(name)) == digest, f"SHA-256 mismatch for {name}")
    return f"{len(expected)} files match pinned SHA-256"


def check_regression_logs(read):
    c = read("rom-diagnostic-host-results.txt").decode("utf-8")
    py = read("python-regression-results.txt").decode("utf-8")
    count = re.search(r"^Ran (\d+) tests? in ", py, re.M)
    need(count and int(count[1]) >= 34 and py.rstrip().endswith("\nOK"), "missing successful Python regression log")
    for line in ("ROM diagnostics:", "ROM MMIO model:", "ST7789 MMIO model:"):
        need(re.search(rf"^{re.escape(line)}.* OK$", c, re.M), f"missing successful C log: {line}")
    return f"{count[1]} Python tests and three C results in the supplied logs"


def check_mflasher_source(root):
    def read(name):
        # Git on Windows may convert a tracked LF text file to CRLF.
        return read_regular(root / name).replace(b"\r\n", b"\n")

    cargo = read("Cargo.toml").decode("utf-8")
    need(re.search(rf'^version = "{re.escape(MFLASHER_VERSION)}"$', cargo, re.M), "unexpected M-Flasher version")
    return f"{MFLASHER_COMMIT}; " + check_files(read, MFLASHER_SOURCES)


def host_profile():
    target = sys.platform
    machine = (os.uname().machine if hasattr(os, "uname") else
               os.environ.get("PROCESSOR_ARCHITEW6432", os.environ.get("PROCESSOR_ARCHITECTURE", ""))).lower()
    machine = {"amd64": "x86_64", "aarch64": "arm64"}.get(machine, machine)
    if hasattr(sys, "getandroidapilevel") or "com.termux" in os.environ.get("PREFIX", ""):
        target = "android"
    return target, machine


def check_host_release(asset, profile):
    need(profile in HOST_RELEASES, "no audited M-Flasher release for this OS/architecture")
    label, digest = HOST_RELEASES[profile]
    need(sha256(read_regular(asset, 16 * MAX_MEMBER)) == digest, "unexpected release SHA-256/version or architecture")
    return f"official v{MFLASHER_VERSION} {label} bytes verified; executable not started"


def check_linux_runtime():
    libc = os.confstr("CS_GNU_LIBC_VERSION") or ""
    version = re.fullmatch(r"glibc (\d+)\.(\d+)(?:\.\d+)?", libc)
    need(version and tuple(map(int, version.groups())) >= (2, 35),
         "official Linux release requires glibc 2.35 or newer")
    return f"{libc} reported by host; SG and udev remain unverified"


def evaluate_trace(device_count, rom_gate, events, screen_seen=False, recovery_seen=False):
    """Evaluate synthetic callback receipts against the proposed one-shot policy.

    rom_gate represents independent qualification in a test case. The physical
    gate cannot be satisfied by this model or by an FC40 response alone.
    """
    plan = [("1200", 36), ("FC40", 16), *(("FB06", size) for size in BLOCKS), ("FB08", 16)]
    if device_count != 1:
        return "DEVICE_AMBIGUOUS_OR_ABSENT"
    if not rom_gate:
        return "ROM_UNVERIFIED"
    for index, (opcode, result, size) in enumerate(events):
        if index >= len(plan) or opcode != plan[index][0]:
            return "COMMAND_FORBIDDEN_OR_OUT_OF_ORDER"
        if result == "NO_ACK" and opcode == "FB08" and index + 1 == len(events):
            if not screen_seen:
                return "EXECUTION_UNCONFIRMED"
            break
        if result != "OK":
            return result if result in {"TIMEOUT", "DISCONNECTED", "CONNECTION_ERROR"} else "TRANSPORT_ERROR"
        if size != plan[index][1]:
            return "INCOMPLETE_TRANSFER"
    if len(events) != len(plan):
        return "INCOMPLETE_TRANSFER"
    if not screen_seen:
        return "EXECUTION_UNCONFIRMED"
    return "SIMULATED_COMPLETE" if recovery_seen else "RECOVERY_UNCONFIRMED"


def simulate_policy():
    good = [("1200", "OK", 36), ("FC40", "OK", 16),
            *(("FB06", "OK", size) for size in BLOCKS), ("FB08", "OK", 16)]
    return {
        "complete": evaluate_trace(1, True, good, True, True),
        "ambiguous_device": evaluate_trace(2, True, ()),
        "unknown_rom": evaluate_trace(1, False, ()),
        "connection_error": evaluate_trace(1, True, [("1200", "CONNECTION_ERROR", 0)]),
        "short_upload": evaluate_trace(1, True, good[:2] + [("FB06", "OK", 511)]),
        "timeout": evaluate_trace(1, True, good[:3] + [("FB06", "TIMEOUT", 0)]),
        "disconnect": evaluate_trace(1, True, good[:3] + [("FB06", "DISCONNECTED", 0)]),
        "missing_jump_reply": evaluate_trace(1, True, good[:-1] + [("FB08", "NO_ACK", 0)]),
        "recovery_unconfirmed": evaluate_trace(1, True, good, True),
        "flash_command": evaluate_trace(1, True, good[:2] + [("FB04", "OK", 512)]),
    }


def report(package, source=None, asset=None):
    checks = {}

    def verify(name, operation):
        try:
            checks[name] = {"status": "PASS", "detail": operation()}
        except (OSError, ValueError, KeyError, struct.error, zipfile.BadZipFile) as exc:
            checks[name] = {"status": "FAIL", "detail": str(exc)}

    def unavailable(name, detail):
        checks[name] = {"status": "NOT VERIFIED", "detail": detail}

    try:
        with open_package(package) as read:
            checks["package"] = {"status": "PASS", "detail": "PR #12 ZIP digest" if not package.is_dir()
                                 else "reproduced directory; components checked individually"}
            verify("reference_files", lambda: check_files(read, {ROM_DIR + f: h for f, h in ARTIFACTS.items()}))

            def check_image():
                image = read(ROM_DIR + IMAGE_NAME + ".bin")
                need(len(image) == IMAGE_SIZE, "expected exactly 4100 bytes")
                return check_app1(image)

            verify("app1", check_image)
            verify("elf_ram_and_entry", lambda: check_link(read(ROM_DIR + IMAGE_NAME + ".elf"),
                   read(ROM_DIR + IMAGE_NAME + ".bin"), read(ROM_DIR + "disassembly.txt")))
            verify("regression_evidence", lambda: check_regression_logs(read))
    except (OSError, ValueError, zipfile.BadZipFile) as exc:
        checks["package"] = {"status": "FAIL", "detail": str(exc)}
    for name in ("reference_files", "app1", "elf_ram_and_entry", "regression_evidence"):
        if name not in checks:
            unavailable(name, "package could not be inspected")

    profile = host_profile()
    checks["python_dependencies"] = {"status": "PASS", "detail": "Python standard library only"}
    if profile in HOST_RELEASES:
        checks["host_platform"] = {"status": "PASS", "detail": f"{profile[0]} {profile[1]}; release matrix only"}
        if profile[0] == "linux":
            verify("linux_runtime", check_linux_runtime)
    else:
        unavailable("host_platform", f"{profile[0]} {profile[1]} outside the audited release matrix")
    if source is None:
        unavailable("mflasher_source", "supply the pinned source checkout to compare the audited files")
    else:
        verify("mflasher_source", lambda: check_mflasher_source(source))
    if asset is None:
        unavailable("mflasher_release", "supply the official release archive for this host")
    else:
        verify("mflasher_release", lambda: check_host_release(asset, profile))
    verify("recovery_plan", lambda: f"FASE-6B-RECOVERY.md ({len(read_regular(Path(__file__).resolve().parents[1] / 'docs/FASE-6B-RECOVERY.md'))} bytes)")
    checks["flash_command_audit"] = {
        "status": "PASS" if checks["mflasher_source"]["status"] == "PASS" else "NOT VERIFIED",
        "detail": "Pinned plain run-ram branch contains no Flash CDB; separate from physical transport approval",
    }

    statuses = {check["status"] for check in checks.values()}
    software = "FAIL" if "FAIL" in statuses else "NOT VERIFIED" if "NOT VERIFIED" in statuses else "PASS"
    return {
        "mode": "DRY-RUN OFFLINE", "hardware_io": False, "physical_launch_available": False,
        "software_status": software, "physical_status": "BLOCKED", "checks": checks,
        "reference": {"pr": 12, "size": IMAGE_SIZE, "load_address": f"0x{APP:08X}", "sha256": IMAGE_DIGEST,
                      "crc16": "0xB786", "mflasher_commit": MFLASHER_COMMIT, "mflasher_version": MFLASHER_VERSION},
        "simulation": simulate_policy(), "physical_blockers": STOPS,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package", type=Path, help="original PR #12 ZIP or repository with reproduced build/romdiag and logs")
    parser.add_argument("--mflasher-source", type=Path, help="local source tree at the audited revision")
    parser.add_argument("--mflasher-release", type=Path, help="official archive for the current host, verified without execution")
    args = parser.parse_args()
    evidence = report(args.package, args.mflasher_source, args.mflasher_release)
    print(json.dumps(evidence, indent=2, sort_keys=True))
    return {"PASS": 0, "FAIL": 1, "NOT VERIFIED": 2}[evidence["software_status"]]


if __name__ == "__main__":
    sys.exit(main())
