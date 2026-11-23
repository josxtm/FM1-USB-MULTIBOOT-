/* SPDX-License-Identifier: MIT */
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "rom_diag.h"
#include "disp.h"

static u16 panel[240 * 240];
static u32 y, lines;

static void window(void *ctx, u32 x, u32 row, u32 w, u32 h)
{
    (void)ctx;
    assert(x == 0 && row < 240 && w == 240 && h == 1);
    y = row;
}

static void pixels(void *ctx, const u8 *data, u32 size)
{
    (void)ctx;
    assert(size == 480);
    for (u32 x = 0; x < 240; ++x) panel[y * 240 + x] = bd16(data + 2 * x);
    ++lines;
}

static void end(void *ctx) { (void)ctx; }

static int has_color(u32 row, u16 color)
{
    for (u32 i = row * 16 * 240; i < (row + 1) * 16 * 240; ++i)
        if (panel[i] == color) return 1;
    return 0;
}

static void capture(void)
{
    FILE *file = fopen("build/rom-diag-display.ppm", "wb");
    assert(file);
    assert(fprintf(file, "P6\n240 240\n255\n") > 0);
    for (u32 i = 0; i < 240 * 240; ++i) {
        u16 c = panel[i];
        fputc(c >> 8 & 248, file);
        fputc(c >> 3 & 252, file);
        fputc(c << 3 & 248, file);
    }
    assert(!fclose(file));
}

static void sample(struct rom_diag_state *s, u32 ms, u32 keys)
{
    (void)rom_diag_input_poll(s, s->clock.last_tick + ms * ROM_DIAG_TICKS_MS, keys);
}

static void settle(struct rom_diag_state *s, u32 keys)
{
    sample(s, 10, keys);
    sample(s, 21, keys);
}

int main(void)
{
    struct rom_diag_state s;
    struct mb_lcd_list_renderer lcd;
    mb_lcd_list_renderer_init(&lcd, (struct mb_lcd_sink){0, window, pixels, end});
    rom_diag_input_init(&s, 0xfffffff0u, MB_KEY_PLAY | MB_KEY_REC);
    rom_diag_draw(&s, &lcd);
    assert(lines == 208 && has_color(0, C_GREEN) && has_color(5, C_YELLOW));
    settle(&s, MB_KEY_PLAY | MB_KEY_REC);
    assert(!s.seen && s.clock.now_ms == 31u);
    settle(&s, 0);
    for (u32 key = MB_KEY_PREVIOUS; key <= MB_KEY_REC; key *= 2) {
        settle(&s, key);
        assert(s.keys.stable == key && (s.seen & key));
        rom_diag_draw_keys(&s, &lcd);
        u32 row = key == 1 ? 9 : key == 2 ? 10 : key == 4 ? 11 : 12;
        assert(has_color(row, C_GREEN));
        settle(&s, 0);
        rom_diag_draw_keys(&s, &lcd);
        assert(!has_color(row, C_GREEN) && has_color(row, C_WHITE));
    }
    assert(s.seen == 15u && s.result == ROM_DIAG_ACTIVE);
    u16 old_timer[16 * 240];
    memcpy(old_timer, panel + 8 * 16 * 240, sizeof old_timer);
    sample(&s, 1000, 0);
    rom_diag_draw_tick(&s, &lcd);
    assert(memcmp(old_timer, panel + 8 * 16 * 240, sizeof old_timer));
    capture();
    settle(&s, MB_KEY_REC);
    sample(&s, 1999, MB_KEY_REC);
    assert(s.result == ROM_DIAG_ACTIVE);
    sample(&s, 1, 0); /* bounce or accidental release must restart the hold */
    sample(&s, 1, MB_KEY_REC);
    assert(s.result == ROM_DIAG_ACTIVE);
    sample(&s, 2000, MB_KEY_REC);
    assert(s.result == ROM_DIAG_FINISHED);
    rom_diag_draw_end(s.result, &lcd);
    assert(has_color(13, C_GREEN) && has_color(14, C_WHITE));

    rom_diag_input_init(&s, 42, 0);
    for (u32 i = 0; i < ROM_DIAG_STALL_POLLS; ++i) (void)rom_diag_input_poll(&s, 42, 0);
    assert(s.result == ROM_DIAG_TIMER_FAULT);
    rom_diag_input_init(&s, 0, 0);
    (void)rom_diag_input_poll(&s, 0x80000000u, 0);
    assert(s.result == ROM_DIAG_TIMER_FAULT);
    rom_diag_input_init(&s, 0, 0);
    for (u32 sec = 0; sec < ROM_DIAG_SESSION_MS / 1000u; ++sec) sample(&s, 1000, 0);
    assert(s.result == ROM_DIAG_TIME_LIMIT);
    puts("ROM diagnostics: 240x240 scanlines, four debounced keys, startup latch, hold, wrap, timer fault and timeout OK");
    return 0;
}
