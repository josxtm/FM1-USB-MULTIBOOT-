/* SPDX-License-Identifier: MIT */
/* TMR0 setup follows M-Unbricker by Julius Vitkauskas. */
#include "hw.h"
#include "usbio.h"
static u32 ticks_ms;

int timer_start_unconfigured(void)
{
    ticks_ms = 0;
    WR(TMR0, 0x4000);
    WR(TMR0 + 4, 0);
    WR(TMR0 + 8, 0xffffffffu);
    WR(TMR0, 1);
    csync();
    u32 start = tb_now();
    spin(20000);
    return tb_now() != start;
}

int timer_start(void)
{
    if(!timer_start_unconfigured())return 0;
    ticks_ms=48000; /* legacy diagnostic default; not used by RAM preflight */
    return 1;
}
int timer_set_ticks_per_ms(u32 ticks) { if(!ticks)return 0;ticks_ms=ticks;return 1; }
int timer_calibrated(void) { return ticks_ms!=0; }
u32 timer_ticks_per_ms(void) { return ticks_ms; }

u32 tb_now(void) { return RD(TMR0 + 4); }
u32 tb_ms_since(u32 start) { return (tb_now() - start) / ticks_ms; }

void delay_us(u32 us)
{
    const u32 start = tb_now();
    const u32 ticks = us * (ticks_ms / 1000u);
    for (u32 guard = ticks * 4u + 100u; tb_now() - start < ticks && guard; --guard)
        ;
}

void usb_disable(void)
{
    WR(0x11800u, 0);
    WR(0x51000u, 0x0cu); /* PHY off and data pins as inputs */
    csync();
}
