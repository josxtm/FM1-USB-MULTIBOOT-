/* SPDX-License-Identifier: MIT
 * Shared ST7789 sink; renderers provide policy and geometry. */
#include "embedded_lcd_renderer.h"
#include "lcd.h"
static void window(void*p,u32 x,u32 y,u32 w,u32 h){(void)p;lcd_window(x,y,w,h);}
static void pixels(void*p,const u8*b,u32 n){(void)p;lcd_pixels(b,n);}
static void finish(void*p){(void)p;lcd_end();}
struct mb_lcd_sink mb_lcd_physical_sink(void){return (struct mb_lcd_sink){0,window,pixels,finish};}
int mb_lcd_physical_init(u32 board){return lcd_init(board);}
