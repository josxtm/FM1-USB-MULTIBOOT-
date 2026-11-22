/* SPDX-License-Identifier: MIT
 * Cover Flow drawing backend. It renders one scanline at a time; no framebuffer. */
#include "embedded_lcd_renderer.h"
#include "lcd.h"
#include "glyphs.h"
#include "disp.h"

static void px(struct mb_lcd_renderer *r,u32 i,u16 color){r->line[2u*i]=(u8)(color>>8);r->line[2u*i+1]=(u8)color;}
static void fill(struct mb_lcd_renderer *r,u32 x,u32 y,u32 w,u32 h,u16 color)
{
    u32 row,i;
    if(!w||!h||x>=MB_LCD_WIDTH||y>=MB_LCD_HEIGHT)return;
    if(x+w>MB_LCD_WIDTH) {
        w=MB_LCD_WIDTH-x;
    }
    if(y+h>MB_LCD_HEIGHT) {
        h=MB_LCD_HEIGHT-y;
    }
    for(i=0;i<w;i++) {
        px(r,i,color);
    }
    for(row=0;row<h;row++){r->sink.window(r->sink.context,x,y+row,w,1);r->sink.pixels(r->sink.context,r->line,2u*w);r->sink.end(r->sink.context);}
}
static int glyph_index(char c)
{
    u32 i;
    if(c>='a'&&c<='z') {
        c=(char)(c-'a'+'A');
    }
    for(i=0;characters[i];i++) {
        if(characters[i]==c) {
            return (int)i;
        }
    }
    return 0;
}
static void text(struct mb_lcd_renderer *r,u32 x,u32 y,const char *s,u16 fg)
{
    u32 row,col,i;u32 max=(MB_LCD_WIDTH-x)/12u;
    for(row=0;row<14;row++){
        for(i=0;i<MB_LCD_WIDTH;i++)px(r,i,C_BLACK);
        for(col=0;col<max&&s[col];col++){
            const u8 *g=glyphs[glyph_index(s[col])];
            for(i=0;i<5;i++)if(g[i]&(1u<<row)) {u32 at=col*12u+i*2u;if(at<MB_LCD_WIDTH-x){px(r,at,fg);if(at+1<MB_LCD_WIDTH-x)px(r,at+1,fg);}}
        }
        r->sink.window(r->sink.context,x,y+row,MB_LCD_WIDTH-x,1);r->sink.pixels(r->sink.context,r->line,2u*(MB_LCD_WIDTH-x));r->sink.end(r->sink.context);
    }
}
static void begin(void *p){struct mb_lcd_renderer*r=p;fill(r,0,0,MB_LCD_WIDTH,MB_LCD_HEIGHT,C_BLACK);}
static void card(void *p,const struct mb_entry*e,int side,int focus)
{
    struct mb_lcd_renderer*r=p;u32 x=focus?52u:(side<0?8u:164u),w=focus?136u:68u,y=28u,h=112u;
    fill(r,x,y,w,h,focus?RGB(24,65,75):RGB(12,32,40));
    fill(r,x,y,w,3,focus?C_GREEN:C_GREY);
    text(r,x+4,y+18,e->label,focus?C_WHITE:C_GREY);
    text(r,x+4,y+48,e->version,focus?C_YELLOW:C_GREY);
    text(r,x+4,y+78,e->integrity_verified?"OK":"CHECK",focus?C_GREEN:C_GREY);
}
static void label(void *p,const char*s){struct mb_lcd_renderer*r=p;text(r,4,164,s,C_WHITE);text(r,4,190,"PLAY INSPECT ONLY",C_YELLOW);}
static void end(void *p){(void)p;}
void mb_lcd_renderer_init(struct mb_lcd_renderer*r,struct mb_lcd_sink sink)
{
    if(!r) {
        return;
    }
    r->sink=sink;
    r->renderer=(struct mb_renderer){r,begin,card,label,end};
}
