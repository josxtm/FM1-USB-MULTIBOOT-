/* SPDX-License-Identifier: MIT
 * Cover Flow renderer using the existing FM-1 LCD window/pixel HAL. */
#ifndef EMBEDDED_LCD_RENDERER_H
#define EMBEDDED_LCD_RENDERER_H
#include "embedded_multiboot.h"
#include "mbl.h"
#define MB_LCD_WIDTH 240u
#define MB_LCD_HEIGHT 240u
#define MB_LCD_LINE_BYTES (MB_LCD_WIDTH * 2u)
struct mb_lcd_sink {
    void *context;
    void (*window)(void *, u32 x, u32 y, u32 w, u32 h);
    void (*pixels)(void *, const u8 *, u32 bytes);
    void (*end)(void *);
};
struct mb_lcd_renderer { struct mb_renderer renderer; struct mb_lcd_sink sink; u8 line[MB_LCD_LINE_BYTES]; };
void mb_lcd_renderer_init(struct mb_lcd_renderer *, struct mb_lcd_sink);
struct mb_lcd_sink mb_lcd_physical_sink(void);
int mb_lcd_physical_init(u32 board);
#endif
