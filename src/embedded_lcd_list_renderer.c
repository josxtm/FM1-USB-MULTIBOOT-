/* SPDX-License-Identifier: MIT
 * Text-only selector backend. One RGB565 scanline, no framebuffer. */
#include "embedded_lcd_list_renderer.h"
#include "glyphs.h"
#include "disp.h"
static void pixel(struct mb_lcd_list_renderer*r,u32 x,u16 c){r->line[2*x]=(u8)(c>>8);r->line[2*x+1]=(u8)c;}
static int gi(char c){u32 i;if(c>='a'&&c<='z')c=(char)(c-'a'+'A');for(i=0;characters[i];++i)if(characters[i]==c)return (int)i;return 0;}
static void row(struct mb_lcd_list_renderer*r,u32 y,u16 bg){u32 x;for(x=0;x<MB_LCD_WIDTH;++x)pixel(r,x,bg);r->sink.window(r->sink.context,0,y,MB_LCD_WIDTH,1);r->sink.pixels(r->sink.context,r->line,MB_LCD_LINE_BYTES);r->sink.end(r->sink.context);}
static void text(struct mb_lcd_list_renderer*r,u32 x,u32 y,const char*s,u16 fg){u32 col,i,bit;for(u32 rr=0;rr<14;++rr){row(r,y+rr,C_BLACK);for(col=0;col<18&&s[col];++col){const u8*g=glyphs[gi(s[col])];for(i=0;i<5;++i)for(bit=0;bit<2;++bit)if(g[i]&(1u<<(rr/2))){u32 at=x+col*12+i*2+bit;if(at<MB_LCD_WIDTH)pixel(r,at,fg);}}r->sink.window(r->sink.context,0,y+rr,MB_LCD_WIDTH,1);r->sink.pixels(r->sink.context,r->line,MB_LCD_LINE_BYTES);r->sink.end(r->sink.context);}}
static void begin(void*p){struct mb_lcd_list_renderer*r=p;for(u32 y=0;y<MB_LCD_HEIGHT;y++)row(r,y,C_BLACK);text(r,4,4,"FM1 MULTIBOOT OS",C_WHITE);}
static void card(void*p,const struct mb_entry*e,int side,int focus){struct mb_lcd_list_renderer*r=p;u32 y=focus?94u:(side<0?64u:124u);char line[32];u32 i=0;line[i++]=focus?'>' :' ';for(;i<30&&e->label[i-1];++i)line[i]=e->label[i-1];line[i]=0;text(r,4,y,line,focus?C_GREEN:C_WHITE);}
static void label(void*p,const char*s){struct mb_lcd_list_renderer*r=p;text(r,4,174,s,C_GREY);text(r,4,202,"PRESETS: ELEGIR",C_WHITE);text(r,4,226,"PLAY: INICIAR",C_GREEN);}
static void end(void*p){(void)p;}
void mb_lcd_list_renderer_init(struct mb_lcd_list_renderer*r,struct mb_lcd_sink sink){if(!r)return;r->sink=sink;r->renderer=(struct mb_renderer){r,begin,card,label,end};}
