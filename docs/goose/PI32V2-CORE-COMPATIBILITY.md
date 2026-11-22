# pi32v2 compatibility of the portable core

## Object compilation

`src/embedded_multiboot.c` is written as freestanding C99 and avoids host I/O. It uses fixed-width integers, fixed buffers and the repository runtime's `memcpy`/`memset` contract. Host validation uses:

```text
cc -std=c99 -pedantic -ffreestanding -fno-builtin -Wall -Wextra -Werror -Isrc -c src/embedded_multiboot.c
```

A dedicated pi32v2 object target is attempted in CI when `scripts/get-toolchain.sh` is available. The result is reported separately from the approved APP1 build.

## Current status

- Host strict object: **verified**.
- Host functional tests: **verified**.
- pi32v2 object compilation: **CI gate**, separate from APP1.
- Independent freestanding link: **pending**; no startup, linker placement or HAL binding is claimed.
- Execution on AC791N: **not authorized and not tested**.

The core does not link `lcd.c`, `husb.c`, GPIO, SPI, Flash or RAM loader code. It therefore cannot accidentally activate the physical backend. The existing `make all` APP1 target and approved Android asset are not modified by this phase.

Static size observations are produced by the compiler/object inspection in CI. The core has fixed catalog storage and a 512-byte hash buffer, but its exact pi32v2 stack and SRAM placement require a linker map and ABI evidence that are not available yet.
