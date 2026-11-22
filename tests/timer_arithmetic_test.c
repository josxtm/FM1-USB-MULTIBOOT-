/* SPDX-License-Identifier: MIT
 * Host-side proxy for timer arithmetic: the MMIO timer is target-only, while
 * these cases exercise the same zero-rate, wrap and ambiguous-jump contract. */
#include <stdio.h>
#include "embedded_clock.h"
int main(void)
{
    struct mb_clock c;
    mb_clock_init(&c, 0, 0);
    if (mb_clock_update(&c, 1)) return 1;
    mb_clock_init(&c, 48000, 0xfffffff0u);
    if (!mb_clock_update(&c, 0x0000bb70u) || c.now_ms != 1) return 2;
    mb_clock_init(&c, 1, 0);
    if (mb_clock_update(&c, 0x80000001u) || !c.failed) return 3;
    puts("timer contract: zero-rate rejection, wrap and ambiguous jump: OK");
    return 0;
}
