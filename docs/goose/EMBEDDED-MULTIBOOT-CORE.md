# Portable embedded multiboot core

## Implementation

- `src/embedded_multiboot.h/.c`: C99 core and HAL contracts.
- `tests/embedded_multiboot_test.c`: deterministic in-memory storage and renderer tests.
- `Makefile`: `make core-test` builds and runs the isolated host target.
- `.github/workflows/embedded-multiboot-core.yml`: strict host build, core tests, legacy USB/FAT32 tests and physical-path guard.

## FM1CAT v1

The parser accepts `FM1CAT|1`, the exact column header, up to 64 entries, safe IDs, `ID.BIN`/`ID.FWS`, bounded labels/versions, positive sizes up to 4 MiB, lowercase 64-character SHA-256 and only `UNVERIFIED` compatibility. It rejects duplicates, malformed paths, invalid fields, truncation and oversized catalogs.

The storage callback reads `FM1/INDEX.CSV` and constructs only `FM1/FW/<short filename>` for firmware. Verification hashes real file bytes incrementally with a fixed 512-byte buffer, requires explicit EOF after the declared size, and rejects truncation, over-read and extra bytes. Metadata and integrity remain separate; integrity never authorizes execution.

## Cover Flow and states

Selection is circular and bounded. PREVIOUS/NEXT update the selected entry. PLAY enters INSPECT only; REC/CANCEL returns to BROWSE. The renderer receives one central card, one adjacent card for two entries, and three cards for three or more entries. The core has no framebuffer requirement and can use a banded LCD adapter later.

## Safety

The launch backend is intentionally blocked. There is no physical storage, LCD, USB, Flash, arbitrary jump or `0x25`/`0x30` path in this module. Results are suitable for software simulation only.
