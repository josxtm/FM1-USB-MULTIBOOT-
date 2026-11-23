/* SPDX-License-Identifier: MIT */
#ifndef FM1_ROM_DIAG_H
#define FM1_ROM_DIAG_H
#include "embedded_clock.h"
#include "embedded_keys.h"
#include "embedded_lcd_list_renderer.h"

#define ROM_DIAG_TICKS_MS 48000u
#define ROM_DIAG_HOLD_MS 2000u
#define ROM_DIAG_SESSION_MS 120000u
#define ROM_DIAG_STALL_POLLS 16u

enum rom_diag_result {
    ROM_DIAG_ACTIVE, ROM_DIAG_FINISHED, ROM_DIAG_TIME_LIMIT, ROM_DIAG_TIMER_FAULT,
    ROM_DIAG_INVALID_ENTRY, ROM_DIAG_UNKNOWN_CLOCK, ROM_DIAG_WATCHDOG_FAULT, ROM_DIAG_LCD_FAULT
};

struct rom_diag_state {
    struct mb_clock clock;
    struct mb_key_adapter keys;
    u32 latch, seen, rec_started, stagnant;
    int rec_armed;
    enum rom_diag_result result;
};

void rom_diag_input_init(struct rom_diag_state *, u32 tick, u32 raw);
int rom_diag_input_poll(struct rom_diag_state *, u32 tick, u32 raw);
void rom_diag_draw(const struct rom_diag_state *, struct mb_lcd_list_renderer *);
void rom_diag_draw_keys(const struct rom_diag_state *, struct mb_lcd_list_renderer *);
void rom_diag_draw_tick(const struct rom_diag_state *, struct mb_lcd_list_renderer *);
void rom_diag_draw_end(enum rom_diag_result, struct mb_lcd_list_renderer *);
enum rom_diag_result rom_diag_prepare(u32 arg);
void fm1_rom_diag_main(u32 arg);
#endif
