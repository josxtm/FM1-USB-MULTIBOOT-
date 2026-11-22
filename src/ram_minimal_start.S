/* SPDX-License-Identifier: MIT */
.section .text.start,"ax"
.global ram_minimal_start
ram_minimal_start:
    cli
    sp = ram_minimal_stack_top
    ssp = ram_minimal_sstack_top
    call fm1_ram_minimal_entry
    goto .
