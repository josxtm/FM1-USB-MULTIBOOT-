/* SPDX-License-Identifier: MIT */
#ifndef FM1_USBC_LAB_H
#define FM1_USBC_LAB_H
#include "mbl.h"

/*
 * External qualification is an operator prerequisite, NOT a property derived
 * from the buttons or the controller's VBUSVALID register.
 *
 * Host mode remains disabled in the ordinary APP1. A separately built lab
 * APP1 may arm only after disconnecting the Android host, releasing buttons,
 * and holding OCT+ and PLAY together for two continuous seconds.
 */
enum usbc_lab_mode {
    USBC_LAB_WAIT_RELEASE,
    USBC_LAB_WAIT_CHORD,
    USBC_LAB_HOLDING,
    USBC_LAB_ARMED,
    USBC_LAB_STOPPED
};
struct usbc_lab_gate {
    enum usbc_lab_mode mode;
    u32 since, started;
    u8 released;
};
void usbc_lab_init(struct usbc_lab_gate *gate, u32 now_ms);
/* 1 = operator armed the qualified lab configuration; -1 = abort/timeout. */
int usbc_lab_poll(struct usbc_lab_gate *gate, u32 now_ms, u32 raw);
#endif
