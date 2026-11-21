/* SPDX-License-Identifier: MIT */
/* Entry: the ROM calls the first byte of the image like a C function
 * (r0 -> its API table, unused). Interrupts off and separate stacks below
 * the image, then C. The ROM entry is never resumed. */
	.section .text.start, "ax"
	.global diag_start
diag_start:
	cli
	sp = diag_stack_top
	ssp = diag_sstack_top
	call diag_main
	goto .
