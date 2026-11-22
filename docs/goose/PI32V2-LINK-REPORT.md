# pi32v2 link report — FASE 5C.2

The isolated target uses `src/embedded_multiboot.ld`, `embedded_multiboot.c`, `embedded_multiboot_sim.c` and the existing freestanding `runtime.c`.

## Measurements

CI produces `embedded_multiboot_pi32v2.o`, `embedded_multiboot_pi32v2.elf` and `embedded_multiboot_pi32v2.map`, plus `llvm-readelf` reports. The ELF is an isolated simulation link at virtual linker origin `0x01C30000`; this is a linker hypothesis, not a hardware-authorized SRAM window.

The first link attempt exposed unresolved `memcpy`/`memset`; linking the repository runtime resolved them without libc. No unresolved symbols remain in the final isolated link. The ELF has no FM-1 startup, LCD, USB, Flash or RAM execution backend.

The final CI report records ELF32 little-endian, type EXEC, machine JieLi pi32v2 (ID 241), entry `0x01c30998`, with `.text=0x9f2` (2546), `.rodata=0x2b0` (688), no `.data`, and `.bss=0x8370` (33648); static allocatable total is 36,882 bytes. The linked map has no relocation section and the symbol table has no unresolved external symbols (only the null UND entry); `memcpy`/`memset` resolve to the included `runtime.c`. The linker asserts a total text/rodata/data/bss budget of 64 KiB. Stack usage is not proven by this link and no physical SRAM reservation is claimed.

Execution on AC791N is not authorized or tested. The ELF is not converted to APP1 or an installable firmware.
