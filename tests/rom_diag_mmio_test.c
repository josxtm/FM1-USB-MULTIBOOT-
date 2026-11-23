/* SPDX-License-Identifier: MIT */
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "rom_diag.h"
#include "hw.h"
#include "lcd.h"

static struct sim {
    u32 con, div, tmr, count, readback, control, writes, reads;
    u8 frame[6];
    u32 bytes;
    u32 gpio[2][9], mux, spi, draws;
    int advancing, accepting_clock, stalled_bridge, stuck_wdt, usb_off, passive_pins;
    int panel_allowed, spi_stalled;
} s;

static void reset(void)
{
    s = (struct sim){.con = 0x98780, .advancing = 1, .accepting_clock = 1};
}

u32 sim_rd(u32 address)
{
    ++s.reads;
    if (s.panel_allowed) {
        if (address == MUX1) return s.mux;
        if (address == SPI1 + SPI_CON) return s.spi | (s.spi_stalled ? 0 : SPI_DONE);
        if (address >= GPIO(PA) && address <= GPIO(PA) + GP_DIEH) return s.gpio[0][(address - GPIO(PA)) / 4];
        if (address >= GPIO(PC) && address <= GPIO(PC) + GP_DIEH) return s.gpio[1][(address - GPIO(PC)) / 4];
    }
    switch (address) {
    case CLK_CON2: return s.con;
    case CLK_DIV: return s.div;
    case P33_CON: return s.control | (s.stalled_bridge && s.bytes ? 2u : 0u);
    case P33_DAT: return s.readback;
    case TMR0 + 4:
        if (s.advancing && s.tmr == 1) s.count += ROM_DIAG_TICKS_MS;
        return s.count;
    default: assert(!"unexpected MMIO read"); return 0;
    }
}

void sim_wr(u32 address, u32 value)
{
    ++s.writes;
    if (s.panel_allowed) {
        if (address == MUX1) { s.mux = value; return; }
        if (address == SPI1 + SPI_CON) { s.spi = value; return; }
        if (address == SPI1 + SPI_BAUD) { assert(value == 4); return; }
        if (address == SPI1 + SPI_BUF) { assert(value <= 255); return; }
        if (address == SPI1 + SPI_ADR) { assert(value == 0x01c17200u); return; }
        if (address == SPI1 + SPI_CNT) { assert(value == 480); ++s.draws; return; }
        if (address >= GPIO(PA) && address <= GPIO(PA) + GP_DIEH) { s.gpio[0][(address - GPIO(PA)) / 4] = value; return; }
        if (address >= GPIO(PC) && address <= GPIO(PC) + GP_DIEH) { s.gpio[1][(address - GPIO(PC)) / 4] = value; return; }
    }
    switch (address) {
    case CLK_DIV:
        assert(value == 0x100 && !s.bytes);
        s.div = value;
        break;
    case CLK_CON2:
        assert(value == 0x98740 && s.div == 0x100 && !s.bytes);
        if (s.accepting_clock) s.con = value;
        break;
    case P33_CON:
        assert(!s.usb_off);
        s.control = value;
        break;
    case P33_DAT: {
        static const u8 allowed[] = {0x40, 0x80, 0xef, 0x80, 0x80, 0x5e};
        assert((s.control & 1u) && s.bytes < sizeof allowed && value == allowed[s.bytes]);
        s.frame[s.bytes++] = (u8)value;
        s.readback = s.bytes == sizeof allowed && !s.stuck_wdt ? 0 : 0x10;
        break;
    }
    case 0x11800u:
        assert(value == 0 && s.bytes == 6 && !s.stuck_wdt);
        s.usb_off = 1;
        break;
    case 0x51000u:
        assert(value == 0x0c && s.usb_off);
        s.passive_pins = 1;
        break;
    case TMR0:
        assert(s.passive_pins && (value == 1 || value == 0x4000));
        s.tmr = value;
        break;
    case TMR0 + 4:
        assert(!value && s.passive_pins);
        s.count = value;
        break;
    case TMR0 + 8:
        assert(value == 0xffffffffu && s.passive_pins);
        break;
    default: assert(!"unexpected MMIO write, no SPI0, eFuse, Flash, VBUS or reset registers allowed");
    }
}

u32 sim_dma(const void *bytes)
{
    assert(s.panel_allowed && bytes);
    return 0x01c17200u;
}

int main(void)
{
    reset();
    assert(rom_diag_prepare(60) == ROM_DIAG_INVALID_ENTRY);
    assert(rom_diag_prepare(0x01c02001) == ROM_DIAG_INVALID_ENTRY);
    assert(s.reads == 0 && s.writes == 0);
    s.con = 0x60;
    assert(rom_diag_prepare(0x01c02000) == ROM_DIAG_UNKNOWN_CLOCK && s.writes == 0);
    reset();
    s.accepting_clock = 0;
    assert(rom_diag_prepare(0x01c02000) == ROM_DIAG_UNKNOWN_CLOCK && !s.bytes && !s.usb_off);
    reset();
    s.stalled_bridge = 1;
    assert(rom_diag_prepare(0x01c02000) == ROM_DIAG_WATCHDOG_FAULT && s.bytes == 1 && !s.usb_off);
    reset();
    s.stuck_wdt = 1;
    assert(rom_diag_prepare(0x01c02000) == ROM_DIAG_WATCHDOG_FAULT && s.bytes == 6 && !s.usb_off);
    reset();
    s.advancing = 0;
    assert(rom_diag_prepare(0x01c02000) == ROM_DIAG_TIMER_FAULT && s.tmr == 0x4000 && s.passive_pins);
    reset();
    assert(rom_diag_prepare(0x01c02000) == ROM_DIAG_ACTIVE && s.tmr == 1 && s.passive_pins);
    reset();
    s.con = 0x98740;
    s.div = 0x100;
    assert(rom_diag_prepare(0x01c02000) == ROM_DIAG_ACTIVE && s.passive_pins);
    s.panel_allowed = 1;
    assert(lcd_init(BRD_FM1) && lcd_operational() && s.draws == 240);
    s.spi_stalled = 1;
    assert(!lcd_init(BRD_FM1) && !lcd_operational() && s.draws == 240);
    puts("ROM MMIO model: invalid entry/clock fail closed, watchdog aborts before USB, passive shutdown and timer gates OK");
    puts("ST7789 MMIO model: initialization, 240 DMA scanlines and SPI timeout OK");
    return 0;
}
