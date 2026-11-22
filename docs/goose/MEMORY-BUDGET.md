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

The isolated link budget is 64 KiB for text, rodata, data and bss. Stack depth and actual pi32v2 SRAM availability remain unverified. The FAT32 adapter measurement is `sizeof(struct fat32)=560` (one 512-byte sector plus metadata) and `sizeof(struct mb_fat32_storage)=8`; it reuses the parser's sector buffer and performs no dynamic allocation in the embedded path. The host integration harness uses heap only to load the generated test image. Existing USB transport is not linked into the portable core.

These are measured software sizes, not a claim that the FM-1 has sufficient free SRAM. The linker address `0x01C30000` remains a simulation/link hypothesis.
