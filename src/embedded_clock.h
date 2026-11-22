/* SPDX-License-Identifier: MIT */
#ifndef EMBEDDED_CLOCK_H
#define EMBEDDED_CLOCK_H
#include "mbl.h"
struct mb_clock { u32 ticks_per_ms, last_tick, remainder, now_ms; u8 ready, failed; };
void mb_clock_init(struct mb_clock *, u32 ticks_per_ms, u32 first_tick);
int mb_clock_update(struct mb_clock *, u32 tick);
#endif
