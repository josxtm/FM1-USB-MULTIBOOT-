/* SPDX-License-Identifier: MIT */
.section .text.start,"ax"
.global mb_app_start
mb_app_start:
    cli
    sp = mb_stack_top
    ssp = mb_sstack_top
    call embedded_ram_app_entry
    goto .
