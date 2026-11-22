/* SPDX-License-Identifier: MIT */
OUTPUT_FORMAT("elf32-pi32v2")
ENTRY(mb_app_start)
MEMORY { APP (rwx) : ORIGIN = 0x01C30000, LENGTH = 0x4FD78
         LOW (rw) : ORIGIN = 0x01C16800, LENGTH = 0x7800 }
PHDRS { program PT_LOAD FLAGS(7); zero PT_LOAD FLAGS(6); }
SECTIONS {
 .image : { KEEP(*(.text.start)) *(.text .text.*) *(.rodata .rodata.* .srodata .srodata.*) *(.data .data.* .sdata .sdata.*) . = ALIGN(4); } > APP :program
 mb_image_end = ADDR(.image)+SIZEOF(.image);
 .bss (NOLOAD) : { mb_bss_start=.; *(.bss .bss.* .sbss .sbss.* COMMON) . = ALIGN(4); mb_bss_end=.; } > LOW :zero
 mb_stack_top=0x01C1F800; mb_sstack_top=0x01C20000;
 /DISCARD/ : { *(.comment) *(.eh_frame) *(.note*) }
}
ASSERT(mb_app_start==ORIGIN(APP),"entry must be first")
ASSERT(mb_bss_end<=0x01C1E000,"BSS overlaps stack")
ASSERT(SIZEOF(.image)<=0x4FD78,"RAM image exceeds APP region")
