/* SPDX-License-Identifier: MIT */
OUTPUT_FORMAT("elf32-pi32v2")
ENTRY(fm1_usbc_explorer_entry)
MEMORY {
    IMAGE (rwx) : ORIGIN = 0x01C30000, LENGTH = 0x7FF8
    WORK (rw) : ORIGIN = 0x01C16800, LENGTH = 0x7800
}
PHDRS {
    program PT_LOAD FLAGS(7);
    work PT_LOAD FLAGS(6);
}
SECTIONS {
    .image : {
        KEEP(*(.text.start))
        *(.text .text.* .rodata .rodata.* .srodata .srodata.*)
        *(.data .data.* .sdata .sdata.*)
        . = ALIGN(4);
    } > IMAGE :program
    .work (NOLOAD) : {
        rom_diag_bss_start = .;
        *(.bss .bss.* .sbss .sbss.* COMMON)
        . = ALIGN(8);
        rom_diag_bss_end = .;
        rom_diag_stack_bottom = .;
        . += 9216;
        rom_diag_stack_top = .;
        . += 1024;
        rom_diag_exception_top = .;
    } > WORK :work
    /DISCARD/ : { *(.comment) *(.note*) *(.eh_frame*) *(.llvm_addrsig) }
}
ASSERT(fm1_usbc_explorer_entry == ORIGIN(IMAGE), "ROM entry must be first")
ASSERT(SIZEOF(.image) + 8 <= 0x8000, "ROM explorer exceeds its local 32 KiB budget")
ASSERT(rom_diag_exception_top <= ORIGIN(WORK) + LENGTH(WORK), "ROM work exceeds low RAM")
