/* SPDX-License-Identifier: MIT */
#include "embedded_presets.h"

void mb_presets_init(struct mb_presets_encoder *e, u8 transitions)
{
    if (!e) return;
    *e = (struct mb_presets_encoder){0, transitions ? transitions : 4, 0, 0};
}

int mb_presets_poll(struct mb_presets_encoder *e, u8 ab, enum mb_event *event)
{
    static const s32 table[16] = {0, -1, 1, 0, 1, 0, 0, -1, -1, 0, 0, 1, 0, 1, -1, 0};
    u8 now; s32 step;
    if (!e || !event) return 0;
    now = ab & 3u;
    if (!e->initialized) { e->previous = now; e->initialized = 1; return 0; }
    step = table[(e->previous << 2) | now];
    e->previous = now;
    if (!step) return 0; /* stable or invalid two-bit jump: reject */
    if (step < 0) {
        if (e->accumulator > 0) e->accumulator = 0;
        --e->accumulator;
    } else {
        if (e->accumulator < 0) e->accumulator = 0;
        ++e->accumulator;
    }
    if (e->accumulator <= -(s32)e->detent) { e->accumulator = 0; *event = MB_EVENT_PREVIOUS; return 1; }
    if (e->accumulator >= (s32)e->detent) { e->accumulator = 0; *event = MB_EVENT_NEXT; return 1; }
    return 0;
}
