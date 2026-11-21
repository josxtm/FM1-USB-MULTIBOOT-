/* SPDX-License-Identifier: MIT */
#ifndef DIAG_TIMER_H
#define DIAG_TIMER_H
#include "mbl.h"
int timer_start(void);
void delay_us(u32 us);
u32 tb_now(void);
u32 tb_ms_since(u32 start);
void usb_disable(void);
#endif
