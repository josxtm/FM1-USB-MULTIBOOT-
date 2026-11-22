/* SPDX-License-Identifier: MIT */
#include <stdio.h>
#include <string.h>
#include "embedded_ram_app.h"
#include "embedded_lcd_renderer.h"
#include "embedded_keys.h"

/* The application composition also contains the guarded physical entry. Host
 * tests provide inert HAL symbols so no register access can occur. */
int lcd_init(u32 board){(void)board;return 1;}
void lcd_window(u32 x,u32 y,u32 w,u32 h){(void)x;(void)y;(void)w;(void)h;}
void lcd_pixels(const u8 *p,u32 n){(void)p;(void)n;}
void lcd_end(void){}
u32 plat_menu_scan(void){return 0;}
void delay_us(u32 us){(void)us;}
u32 tb_now(void){return 0;}
unsigned char mb_bss_start[1], mb_bss_end[1];

static unsigned frames, cards, labels;
static void begin(void *p){(void)p;frames++;}
static void card(void *p,const struct mb_entry *e,int side,int focus){(void)p;(void)e;(void)side;(void)focus;cards++;}
static void text(void *p,const char *s){(void)p;if(!strcmp(s,"BROWSE")||!strcmp(s,"INSPECT"))labels++;}
static void end(void *p){(void)p;}
int main(void)
{
    struct mb_core c; struct mb_demo_storage s={0}; struct mb_key_adapter k; enum mb_event e;
    struct mb_renderer r={0,begin,card,text,end};
    mb_demo_catalog_init(&c,&s,r);
    if(c.catalog.count!=3||c.state!=MB_BROWSE)return 1;
    mb_render(&c); if(!frames||cards!=3)return 2;
    mb_event(&c,MB_EVENT_NEXT); if(strcmp(mb_selected(&c)->id,"MELODEE"))return 3;
    mb_event(&c,MB_EVENT_PLAY); if(c.state!=MB_INSPECT)return 4;
    mb_event(&c,MB_EVENT_REC); if(c.state!=MB_BROWSE)return 5;
    if(mb_verify_selected(&c)!=MB_IO_OK||c.state!=MB_READY_FOR_SIMULATION)return 6;
    mb_keys_init(&k);
    if(mb_keys_poll(&k,MB_KEY_NEXT,0,&e)||mb_keys_poll(&k,MB_KEY_NEXT,19,&e)||!mb_keys_poll(&k,MB_KEY_NEXT,20,&e)||e!=MB_EVENT_NEXT)return 7;
    if(mb_prepare_launch(&c)!=MB_LAUNCH_BLOCKED)return 8;
    puts("RAM-only Cover Flow app: catalog, navigation, inspection, cancel, verify, blocked launch: OK");
    return 0;
}
