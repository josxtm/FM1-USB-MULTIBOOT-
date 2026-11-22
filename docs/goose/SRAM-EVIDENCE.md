# SRAM evidence and limits

## Verified

- APP1 software artifact is ELF32 little-endian, machine pi32v2 (241), with entry `0x01C30000` and trailer/CRC16 validated by the existing scripts.
- The linker describes the current diagnostic APP1 RAM window as `[0x01C30000,0x01C7FD80)` and asserts its low BSS region remains below `0x01C1E000`.
- The physical identity response reports `RAM_ANNOUNCED=true`.

## Not verified in hardware

No independent AC791N memory map, bootloader reservation map, ABI, clock state, peripheral initialization contract, stack ownership, return protocol or collision test has been obtained. `RAM_ANNOUNCED=true` does not prove that the current APP1 is executable.

## Software budget

The portable multiboot core uses fixed storage: catalog maximum 24,576 bytes, 64 entries, fixed entry records, and a 512-byte verification buffer. The host test exercises the 64-entry limit. The core does not allocate a framebuffer and does not link the experimental LCD/USB backend.

The catalog buffer is intentionally bounded and is a host/simulation implementation. A future pi32v2 port may replace it with a streaming line reader while preserving the same parser contract.

## Gate

No physical RAM load or execution is authorized. Commands `0x25` and `0x30`, Flash writes/erase and arbitrary jumps remain absent from the core.
