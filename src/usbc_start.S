/* SPDX-License-Identifier: MIT */
.section .text.start,"ax",@progbits
.global fm1_usbc_explorer_entry
.type fm1_usbc_explorer_entry,@function
fm1_usbc_explorer_entry:
    cli
    r2 = rom_diag_exception_top
    ssp = r2
    r2 = rom_diag_stack_top
    sp = r2
    call fm1_usbc_explorer_main
1:
    goto 1b
.size fm1_usbc_explorer_entry,.-fm1_usbc_explorer_entry
