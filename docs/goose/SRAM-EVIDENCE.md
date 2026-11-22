# SRAM evidence and limits

## Verified

- APP1 software artifact is ELF32 little-endian, machine pi32v2 (241), with entry `0x01C30000` and trailer/CRC16 validated by the existing scripts.
- The linker describes the current diagnostic APP1 RAM window as `[0x01C30000,0x01C7FD80)` and asserts its low BSS region remains below `0x01C1E000`.
- The physical identity response reports `RAM_ANNOUNCED=true`.

## Not verified in hardware

No independent AC791N memory map, bootloader reservation map, ABI, clock state, peripheral initialization contract, stack ownership, return protocol or collision test has been obtained. `RAM_ANNOUNCED=true` does not prove that the current APP1 is executable.

## Software budget

The portable multiboot core uses fixed storage: catalog maximum 24,576 bytes, 64 entries, fixed entry records, and a 512-byte verification buffer. The host test exercises the 64-entry limit. The core does not allocate a framebuffer and does not link the experimental LCD/USB backend.

Measured host sizes are `sizeof(mb_entry)=140`, `sizeof(mb_catalog)=8964`, and `sizeof(mb_core)=9096`. The catalog buffer is intentionally bounded at 24,576 bytes (plus one sentinel byte), verification uses a 512-byte chunk and SHA state, and the renderer has no framebuffer. The isolated linker asserts text+rodata+data+bss <= 64 KiB, but stack depth, runtime reservations, FAT32 sector buffers and actual pi32v2 SRAM availability are unknown. These are measurements of software layouts, not physical allocations.

The catalog buffer is intentionally bounded and is a host/simulation implementation. A future pi32v2 port may replace it with a streaming line reader preserving the parser contract; that would reduce peak catalog storage but requires a line-oriented HAL and has not been claimed here. The FAT32 parser uses one 512-byte sector buffer (`sizeof(struct fat32)=560` including metadata); this is a software measurement, not confirmed free SRAM.

## Gate

No physical RAM load or execution is authorized. Commands `0x25` and `0x30`, Flash writes/erase and arbitrary jumps remain absent from the core.
