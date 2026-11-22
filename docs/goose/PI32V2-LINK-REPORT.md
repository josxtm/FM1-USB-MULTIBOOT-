# pi32v2 link report — FASE 5C.2

The isolated target uses `src/embedded_multiboot.ld`, `embedded_multiboot.c`, `embedded_multiboot_sim.c` and the existing freestanding `runtime.c`.

## Measurements

CI produces `embedded_multiboot_pi32v2.o`, `embedded_multiboot_pi32v2.elf` and `embedded_multiboot_pi32v2.map`, plus `llvm-readelf` reports. The ELF is an isolated simulation link at virtual linker origin `0x01C30000`; this is a linker hypothesis, not a hardware-authorized SRAM window.

The first link attempt exposed unresolved `memcpy`/`memset`; linking the repository runtime resolved them without libc. No unresolved symbols remain in the final isolated link. The ELF has no FM-1 startup, LCD, USB, Flash or RAM execution backend.

The linker asserts a total text/rodata/data/bss budget of 64 KiB. Stack usage is not proven by this link and no physical SRAM reservation is claimed. The exact ELF class/machine/sections/symbols are preserved in the CI artifact report rather than inferred from source.

Execution on AC791N is not authorized or tested. The ELF is not converted to APP1 or an installable firmware.
