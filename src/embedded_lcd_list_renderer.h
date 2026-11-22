/* SPDX-License-Identifier: MIT */
#ifndef EMBEDDED_LCD_LIST_RENDERER_H
#define EMBEDDED_LCD_LIST_RENDERER_H
#include "embedded_lcd_renderer.h"
struct mb_lcd_list_renderer { struct mb_renderer renderer; struct mb_lcd_sink sink; u8 line[MB_LCD_LINE_BYTES]; };
void mb_lcd_list_renderer_init(struct mb_lcd_list_renderer *, struct mb_lcd_sink);
/* Reuse the text glyphs and scanline for a 15-row selector view. */
void mb_lcd_list_row(struct mb_lcd_list_renderer *, u32 row, const char *, u16 color);
#endif
