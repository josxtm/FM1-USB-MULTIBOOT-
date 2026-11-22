/* SPDX-License-Identifier: MIT */
/* The return sequence follows m-apps kit/start.S (Julius Vitkauskas). r0 is LSB MHz. */
.section .text.start,"ax",@progbits
.global fm1_stg2_entry
.type fm1_stg2_entry,@function
fm1_stg2_entry:
    cli
    [--sp] = {rets, r15-r4}
    r2 = stg2_caller_stacks
    r3 = sp
    [r2+0] = r3
    r3 = ssp
    [r2+4] = r3
    r2 = stg2_exception_top
    ssp = r2
    r2 = stg2_stack_top
    sp = r2
    call fm1_stg2_main
    cli
    r2 = stg2_caller_stacks
    r3 = [r2+0]
    sp = r3
    r3 = [r2+4]
    ssp = r3
    {pc, r15-r4} = [sp++]
.size fm1_stg2_entry,.-fm1_stg2_entry

.section .data.stg2_caller_stacks,"aw",@progbits
.balign 4
stg2_caller_stacks:
    .long 0, 0
