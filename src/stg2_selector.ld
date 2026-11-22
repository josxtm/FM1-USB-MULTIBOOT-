/* SPDX-License-Identifier: MIT */
OUTPUT_FORMAT("elf32-pi32v2")
ENTRY(fm1_stg2_entry)
MEMORY {
    IMAGE (rwx) : ORIGIN = 0x01C28000, LENGTH = 0x7FF8
    WORK (rw) : ORIGIN = 0x01C16800, LENGTH = 0x11800
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
        stg2_bss_start = .;
        *(.bss .bss.* .sbss .sbss.* COMMON)
        . = ALIGN(8);
        stg2_bss_end = .;
        stg2_stack_bottom = .;
        . += 8192;
        stg2_stack_top = .;
        . += 1024;
        stg2_exception_top = .;
    } > WORK :work
    /DISCARD/ : { *(.comment) *(.note*) *(.eh_frame*) *(.llvm_addrsig) }
}
ASSERT(fm1_stg2_entry == ORIGIN(IMAGE), "STG2 entry must be first")
ASSERT(SIZEOF(.image) + 8 <= 0x8000, "STG2 exceeds 32 KiB with trailer")
ASSERT(stg2_exception_top <= ORIGIN(IMAGE), "STG2 work overlaps loaded image")
