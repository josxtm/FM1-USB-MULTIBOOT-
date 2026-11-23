#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Static release checks for the USB-C explorer APP1."""
from pathlib import Path
import re
import struct
import sys

from validate import APP, BSS, validate
from validate_stg2 import require, sections_and_symbols


def instructions(disassembly, name):
    match = re.search(rf"<{name}>:\n(.*?)(?:\n\n|\Z)", disassembly, re.S)
    require(match, f"missing {name} in disassembly")
    return re.findall(r"^\s*[0-9a-f]+:\s+(?:[0-9a-f]{2}\s+)+(.+?)\s*$", match[1], re.M)


def audit(candidate, disassembly, frames):
    base = candidate.parent / "FM1_USB_C_SD_READER_READONLY"
    elf, flat = [base.with_suffix(ext).read_bytes() for ext in (".elf", ".flat")]
    app = candidate.read_bytes()
    validate(elf, flat, app)
    require(len(app) <= 32768, "oversized APP1")
    symbols = sections_and_symbols(elf)
    for name in ("fm1_usbc_explorer_entry", "fm1_usbc_explorer_main", "usb_disable", "husb_init",
                 "usbc_connect", "usb_enumerate", "mb_msc_read_only_cdb", "cat2_verify", "cat2_page"):
        require(name in symbols, f"missing {name}")
    require(not any(re.match(r"(flash_|sfc_|spi0_|efuse|rom_download|boot_reset|sd_|watchdog_enable)", name)
                    for name in symbols), "unexpected persistent-storage driver or reset path")
    for address in range(0x11C00, 0x11C20, 4):
        require(not re.search(rf"\b{address}\b|\b0x0*{address:x}\b", disassembly),
                "SPI0 immediate address in disassembly")
    for address in range(0x40200, 0x40220, 4):
        require(not re.search(rf"\b{address}\b|\b0x0*{address:x}\b", disassembly),
                "SFC immediate address in disassembly")
    start, end, bottom, top, exception = (symbols["rom_diag_" + label][0] for label in
                                         ("bss_start", "bss_end", "stack_bottom", "stack_top", "exception_top"))
    phoff, = struct.unpack_from("<I", elf, 28)
    memsz, = struct.unpack_from("<I", elf, phoff + 52)
    require(start == BSS and end == bottom and top - bottom == 9216 and exception - top == 1024 and
            exception == start + memsz <= 0x01C1E000 and end % 8 == 0, "unexpected RAM layout")
    entry = instructions(disassembly, "fm1_usbc_explorer_entry")
    require(len(entry) == 7 and entry[:5] == ["cli", f"r2 = {exception}", "ssp = r2", f"r2 = {top}", "sp = r2"]
            and entry[5].endswith("<fm1_usbc_explorer_main>") and
            entry[6] == "goto -2 <fm1_usbc_explorer_entry+0x1a>", "unexpected ROM entry")
    main = instructions(disassembly, "fm1_usbc_explorer_main")
    connects = [i for i, op in enumerate(main) if op.endswith("<usbc_connect>")]
    require(len(connects) == 1 and connects[0] > 0 and main[connects[0] - 1] == "r2 = 0",
            "the ROM entry must supply only USB_POWER_UNKNOWN")
    all_calls = re.findall(r"\bcall [-\d]+ <usbc_connect>", disassembly)
    require(len(all_calls) == 1, "unexpected explorer USB entry")
    frames_by_name = {}
    for file in frames:
        if file.suffix != ".su":
            continue
        for line in file.read_text().splitlines():
            location, amount, mode = line.split("\t")
            name = location.rsplit(":", 1)[-1]
            if name in symbols:
                require(mode == "static", f"nonstatic stack: {name}")
                frames_by_name[name] = max(int(amount), frames_by_name.get(name, 0))
    linked = sum(frames_by_name.values())
    # Conservatively count the entire linked set of compiler-reported C frames.
    # Assembly and any runtime helper stack have the remaining margin.
    require(linked > 0 and linked <= 8704, f"linked static stack budget exceeded: {linked}")
    print(f"ROM entry 0x{APP:08X}, image {len(app)} B, BSS {end-start} B, work incl. stacks {memsz} B")
    print(f"Stack: linked C frames sum {linked} B / 9216 B, SSP 1024 B, top 0x{exception:08X}")
    print("Entry disables interrupts, preserves r0 and parks after peripheral shutdown; no ROM return.")
    print("Power decision supplied by the ROM entry: USB_POWER_UNKNOWN. USB0 Host is blocked by default.")
    print("No linked Flash/eFuse driver or known SPI0/SFC direct literals. SCSI OUT only carries read-only CBWs.")
    print("Hardware not tested. No physical deployment is authorized.")


if __name__ == "__main__":
    try:
        directory, disassembly, *frames = map(Path, sys.argv[1:])
        audit(directory, disassembly.read_text(), frames)
    except (OSError, ValueError, struct.error) as error:
        sys.exit(str(error))
