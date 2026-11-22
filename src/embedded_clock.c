/* SPDX-License-Identifier: MIT */
#include "embedded_clock.h"
void mb_clock_init(struct mb_clock*c,u32 ticks_per_ms,u32 first)
{ if(!c)return; c->ticks_per_ms=ticks_per_ms;c->last_tick=first;c->remainder=0;c->now_ms=0;c->ready=(ticks_per_ms!=0);c->failed=0; }
int mb_clock_update(struct mb_clock*c,u32 tick)
{
    u32 delta,total;
    if(!c||!c->ready||c->failed)return 0;
    delta=tick-c->last_tick;
    /* A jump over half the 32-bit domain is ambiguous, not a valid timeout. */
    if(delta>0x7fffffffu){c->failed=1;return 0;}
    c->last_tick=tick; total=c->remainder+delta;
    c->now_ms+=total/c->ticks_per_ms;c->remainder=total%c->ticks_per_ms;return 1;
}
