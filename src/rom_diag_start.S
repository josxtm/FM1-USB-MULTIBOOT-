/* SPDX-License-Identifier: MIT */
.section .text.start,"ax",@progbits
.global fm1_rom_diag_entry
.type fm1_rom_diag_entry,@function
fm1_rom_diag_entry:
    cli
    r2 = rom_diag_exception_top
    ssp = r2
    r2 = rom_diag_stack_top
    sp = r2
    call fm1_rom_diag_main
1:
    goto 1b
.size fm1_rom_diag_entry,.-fm1_rom_diag_entry
