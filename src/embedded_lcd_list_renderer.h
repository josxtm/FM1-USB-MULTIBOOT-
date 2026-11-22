/* SPDX-License-Identifier: MIT */
#ifndef EMBEDDED_LCD_LIST_RENDERER_H
#define EMBEDDED_LCD_LIST_RENDERER_H
#include "embedded_lcd_renderer.h"
struct mb_lcd_list_renderer { struct mb_renderer renderer; struct mb_lcd_sink sink; u8 line[MB_LCD_LINE_BYTES]; };
void mb_lcd_list_renderer_init(struct mb_lcd_list_renderer *, struct mb_lcd_sink);
#endif
