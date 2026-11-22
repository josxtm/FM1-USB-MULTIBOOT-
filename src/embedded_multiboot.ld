ENTRY(embedded_multiboot_sim_entry)
SECTIONS
{
  . = 0x01C30000;
  .text : { *(.text*) }
  .rodata : { *(.rodata*) }
  .data : { *(.data*) }
  .bss (NOLOAD) : { *(.bss*) *(COMMON) }
  /DISCARD/ : { *(.comment) *(.note*) }
}
ASSERT(SIZEOF(.text) + SIZEOF(.rodata) + SIZEOF(.data) + SIZEOF(.bss) <= 0x10000,
       "embedded multiboot simulation exceeds isolated 64 KiB link budget")
