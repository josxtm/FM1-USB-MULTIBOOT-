/* SPDX-License-Identifier: MIT */
#include "usbc_lab.h"
#define LAB_PREV 1u
#define LAB_NEXT 2u
#define LAB_PLAY 4u
#define LAB_REC 8u
#define LAB_MASK 15u
#define LAB_ARM (LAB_NEXT | LAB_PLAY)
#define LAB_RELEASE_MS 200u
#define LAB_HOLD_MS 2000u
#define LAB_DEADLINE_MS 55000u

void usbc_lab_init(struct usbc_lab_gate *gate, u32 now_ms)
{
    *gate = (struct usbc_lab_gate){0};
    gate->mode = USBC_LAB_WAIT_RELEASE;
    gate->started = now_ms;
}

int usbc_lab_poll(struct usbc_lab_gate *gate, u32 now_ms, u32 raw)
{
    if (gate->mode == USBC_LAB_ARMED) return 1;
    if (gate->mode == USBC_LAB_STOPPED) return -1;
    if (now_ms - gate->started >= LAB_DEADLINE_MS ||
        (raw & LAB_MASK) == LAB_REC) {
        gate->mode = USBC_LAB_STOPPED;
        return -1;
    }
    raw &= LAB_MASK;
    if (gate->mode == USBC_LAB_WAIT_RELEASE) {
        if (raw) gate->released = 0;
        else if (!gate->released) { gate->released = 1; gate->since = now_ms; }
        else if (now_ms - gate->since >= LAB_RELEASE_MS)
            gate->mode = USBC_LAB_WAIT_CHORD;
        return 0;
    }
    if (gate->mode == USBC_LAB_WAIT_CHORD) {
        if (raw == LAB_ARM) {
            gate->mode = USBC_LAB_HOLDING;
            gate->since = now_ms;
        }
        return 0;
    }
    if (raw != LAB_ARM) {
        gate->mode = USBC_LAB_WAIT_CHORD;
        return 0;
    }
    if (now_ms - gate->since >= LAB_HOLD_MS) {
        gate->mode = USBC_LAB_ARMED;
        return 1;
    }
    return 0;
}
