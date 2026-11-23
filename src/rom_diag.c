/* SPDX-License-Identifier: MIT */
#include "rom_diag.h"
#include "disp.h"

#define BUTTONS (MB_KEY_PREVIOUS | MB_KEY_NEXT | MB_KEY_PLAY | MB_KEY_REC)

void rom_diag_input_init(struct rom_diag_state *s, u32 tick, u32 raw)
{
    *s = (struct rom_diag_state){.latch = raw & BUTTONS, .result = ROM_DIAG_ACTIVE};
    mb_clock_init(&s->clock, ROM_DIAG_TICKS_MS, tick);
    mb_keys_init(&s->keys);
}

int rom_diag_input_poll(struct rom_diag_state *s, u32 tick, u32 raw)
{
    if (s->result != ROM_DIAG_ACTIVE) return 0;
    const u32 previous = s->keys.stable, seen = s->seen;
    s->stagnant = tick == s->clock.last_tick ? s->stagnant + 1u : 0u;
    if (s->stagnant >= ROM_DIAG_STALL_POLLS || !mb_clock_update(&s->clock, tick)) {
        s->result = ROM_DIAG_TIMER_FAULT;
        return 1;
    }
    s->latch &= raw;
    enum mb_event unused;
    (void)mb_keys_poll(&s->keys, raw & BUTTONS & ~s->latch, s->clock.now_ms, &unused);
    s->seen |= s->keys.stable;
    if (s->keys.stable == MB_KEY_REC && (raw & BUTTONS) == MB_KEY_REC) {
        if (!s->rec_armed) s->rec_started = s->clock.now_ms;
        s->rec_armed = 1;
        if (s->clock.now_ms - s->rec_started >= ROM_DIAG_HOLD_MS)
            s->result = ROM_DIAG_FINISHED;
    } else s->rec_armed = 0;
    if (s->result == ROM_DIAG_ACTIVE && s->clock.now_ms >= ROM_DIAG_SESSION_MS)
        s->result = ROM_DIAG_TIME_LIMIT;
    return previous != s->keys.stable || seen != s->seen || s->result != ROM_DIAG_ACTIVE;
}

void rom_diag_draw_keys(const struct rom_diag_state *s, struct mb_lcd_list_renderer *lcd)
{
    static const char *const rows[4][2] = {
        {"OCT-: WAIT", "OCT-: SEEN"}, {"OCT+: WAIT", "OCT+: SEEN"},
        {"PLAY: WAIT", "PLAY: SEEN"}, {"REC: WAIT", "REC: SEEN"}
    };
    for (u32 i = 0; i < 4; ++i) {
        u32 bit = 1u << i;
        u16 color = s->keys.stable & bit ? C_GREEN : s->seen & bit ? C_WHITE : C_GREY;
        mb_lcd_list_row(lcd, i + 9, rows[i][!!(s->seen & bit)], color);
    }
}

void rom_diag_draw_tick(const struct rom_diag_state *s, struct mb_lcd_list_renderer *lcd)
{
    u32 seconds = s->clock.now_ms / 1000u;
    char line[] = "TMR0 SEC: 000";
    for (u32 i = 13; i > 10; --i) {
        line[i - 1] = (char)('0' + seconds % 10u);
        seconds /= 10u;
    }
    mb_lcd_list_row(lcd, 8, line, C_GREY);
}

void rom_diag_draw(const struct rom_diag_state *s, struct mb_lcd_list_renderer *lcd)
{
    mb_lcd_list_row(lcd, 0, "FM1 MULTIBOOT", C_GREEN);
    mb_lcd_list_row(lcd, 1, "HARDWARE TEST", C_WHITE);
    mb_lcd_list_row(lcd, 3, "DISPLAY: TEST", C_WHITE);
    mb_lcd_list_row(lcd, 4, "KEYS: TEST", C_WHITE);
    mb_lcd_list_row(lcd, 5, "FLASH: NOT WRITTEN", C_YELLOW);
    mb_lcd_list_row(lcd, 7, "LSB PROFILE: 48 MHZ", C_GREY);
    rom_diag_draw_tick(s, lcd);
    rom_diag_draw_keys(s, lcd);
    mb_lcd_list_row(lcd, 13, "HOLD REC 2S: FINISH", C_WHITE);
    mb_lcd_list_row(lcd, 14, "IRQ OFF / RAM ONLY", C_GREY);
}

void rom_diag_draw_end(enum rom_diag_result result, struct mb_lcd_list_renderer *lcd)
{
    const char *message = result == ROM_DIAG_FINISHED ? "TEST FINISHED" :
        result == ROM_DIAG_TIME_LIMIT ? "TIME LIMIT REACHED" : "TMR0 ERROR";
    mb_lcd_list_row(lcd, 13, message, result == ROM_DIAG_FINISHED ? C_GREEN : C_YELLOW);
    mb_lcd_list_row(lcd, 14, "POWER OFF THEN ON", C_WHITE);
}
