# Memory budget — FASE 5C.2

Measured by `make memory-report` on the host:

```text
sizeof(mb_entry)=140
sizeof(mb_catalog)=8964
sizeof(mb_core)=9096
catalog_limit=24576
sha_buffer=512
render_framebuffer=0
```

The catalog parser uses one bounded static 24,577-byte input buffer (the extra byte is not used as a second catalog representation) and fixed entry records. Verification uses a 512-byte data buffer plus the SHA-256 working state. The host SVG renderer allocates no RGB565 framebuffer; it emits callbacks to SVG.

The isolated link budget is 64 KiB for text, rodata, data and bss. Stack depth and actual pi32v2 SRAM availability remain unverified. The FAT32 adapter measurement is `sizeof(struct fat32)=560` (one 512-byte sector plus metadata) and `sizeof(struct mb_fat32_storage)=8`; it reuses the parser's sector buffer and performs no dynamic allocation in the embedded path. Integrated host measurements read 26 sectors for the three-entry image and 60 for 64 entries from mount through verification. There is currently no cluster cache; repeated directory/FAT reads are deliberate and measurable rather than hidden. The integrated pi32v2 link report measured `.text=5138`, `.rodata=736`, `.data=0`, `.bss=24577`, total 30,451 bytes, below the virtual 64 KiB assertion. This is linker allocation, not confirmed physical SRAM. The host integration harness uses heap only to load the generated test image. Existing USB transport is not linked into the portable core. FASE 5F adds a freestanding `msc_bot` object (no heap, no framebuffer, no host libc) to the integrated pi32v2 target; CI measured the integrated link after consolidation as `.text=6018`, `.rodata=748`, `.data=0`, `.bss=24577`, static total `31343` bytes; the exact symbol contribution is present in the ELF/map report. The physical `husb` and descriptor storage path remain separate. BOT uses bounded 31-byte CBW, 13-byte CSW and one 512-byte sector buffer; the Cover Flow ST7789 backend adds one 480-byte RGB565 scanline and no framebuffer; the key adapter is fixed-size (`sizeof(mb_key_adapter)=20`); host report measures `sizeof(mb_lcd_renderer)=552` and `lcd_scanline_bytes=480`. Stack usage still requires toolchain/ABI measurement.

These are measured software sizes, not a claim that the FM-1 has sufficient free SRAM. The linker address `0x01C30000` remains a simulation/link hypothesis.
