/* SPDX-License-Identifier: MIT */
#include "rom_diag.h"
#include "hw.h"
#include "keys.h"
#include "lcd.h"
#include "usbio.h"

extern u8 rom_diag_bss_start[], rom_diag_bss_end[];
volatile u32 fm1_rom_diag_status;
static struct mb_lcd_list_renderer panel;

/* Only the two clock states documented for the download ROM's 48 MHz LSB. */
static int rom_clock(void)
{
    const u32 div = RD(CLK_DIV), con = RD(CLK_CON2);
    if ((con & 0xffu) == 0x80u && !(div & 0x30700u)) {
        WR(CLK_DIV, div | (1u << 8));
        csync();
        WR(CLK_CON2, (con & ~0xffu) | 0x40u);
        csync();
    }
    return (RD(CLK_CON2) & 0xffu) == 0x40u && (RD(CLK_DIV) & 0x30700u) == 0x100u;
}

/* SPEC 2.3 P33 sequence. The only callers below target the volatile watchdog register. */
static int watchdog_frame(const u8 frame[3], u8 *last)
{
    if (RD(P33_CON) & 2u) return 0;
    SETB(P33_CON, 1);
    for (u32 i = 0; i < 3; ++i) {
        SETB(P33_CON, 0x0cu);
        WR(P33_DAT, frame[i]);
        SETB(P33_CON, 0x10u);
        u32 wait = 100000u;
        while ((RD(P33_CON) & 2u) && --wait) {}
        if (!wait) {
            CLRB(P33_CON, 1);
            return 0;
        }
    }
    *last = (u8)RD(P33_DAT);
    CLRB(P33_CON, 1);
    return 1;
}

static int watchdog_off(void)
{
    static const u8 mask[] = {0x40, P33_WDOG, 0xef};
    static const u8 read[] = {0x80, P33_WDOG, 0x5e};
    u8 status;
    return watchdog_frame(mask, &status) && watchdog_frame(read, &status) && !(status & 0x10u);
}

enum rom_diag_result rom_diag_prepare(u32 arg)
{
    /* Opaque ROM callback table shape. Do not dereference, register handlers or read identity. */
    if (arg < 0x01000000u || (arg & 3u)) return ROM_DIAG_INVALID_ENTRY;
    if (!rom_clock()) return ROM_DIAG_UNKNOWN_CLOCK;
    if (!watchdog_off()) return ROM_DIAG_WATCHDOG_FAULT;
    /* Passive USB shutdown only, without pin drive, VBUS or Host activation. */
    usb_disable();
    if (!timer_start_unconfigured() || !timer_set_ticks_per_ms(ROM_DIAG_TICKS_MS)) {
        WR(TMR0, 0x4000u);
        return ROM_DIAG_TIMER_FAULT;
    }
    return ROM_DIAG_ACTIVE;
}

static u32 scan_buttons(void)
{
    return plat_menu_scan() | (plat_boot_keys() & K_REC);
}

static enum rom_diag_result run(void)
{
    if (!lcd_init(BRD_FM1)) return ROM_DIAG_LCD_FAULT;
    mb_lcd_list_renderer_init(&panel, mb_lcd_physical_sink());
    struct rom_diag_state state;
    u32 initial = scan_buttons();
    rom_diag_input_init(&state, tb_now(), initial);
    rom_diag_draw(&state, &panel);
    while (state.result == ROM_DIAG_ACTIVE && lcd_operational()) {
        delay_us(7000);
        u32 buttons = scan_buttons();
        u32 second = state.clock.now_ms / 1000u;
        if (rom_diag_input_poll(&state, tb_now(), buttons)) rom_diag_draw_keys(&state, &panel);
        if (state.clock.now_ms / 1000u != second) rom_diag_draw_tick(&state, &panel);
    }
    if (!lcd_operational()) return ROM_DIAG_LCD_FAULT;
    rom_diag_draw_end(state.result, &panel);
    return lcd_operational() ? state.result : ROM_DIAG_LCD_FAULT;
}

void fm1_rom_diag_main(u32 arg)
{
    for (u8 *p = rom_diag_bss_start; p < rom_diag_bss_end; ++p) *p = 0;
    enum rom_diag_result result = rom_diag_prepare(arg);
    fm1_rom_diag_status = (u32)result;
    if (result != ROM_DIAG_ACTIVE) return;
    result = run();
    plat_keys_off();
    lcd_end();
    WR(TMR0, 0x4000u);
    fm1_rom_diag_status = (u32)result;
    csync();
    /* Only returns to the private assembly wait, not to the ROM. */
}
