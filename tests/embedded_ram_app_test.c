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
int timer_start_unconfigured(void){return 1;}
int timer_calibrated(void){return 1;}
u32 timer_ticks_per_ms(void){return 48000;}
u32 tb_now(void){return 0;}
unsigned char mb_bss_start[1], mb_bss_end[1];

static const char ext_index[]="FM1CAT|1\nID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\nUSB_1|USB Reader|1|USB_1.BIN|3|61f0acff1735aac46ba11f249dd555a22fd09193d54e6383809d3cce12780408|UNVERIFIED\n";
static enum mb_io_result ext_read(void *p,const char *path,uint32_t off,uint8_t *out,uint32_t cap,uint32_t *got)
{
    const char *data;unsigned long size;(void)p;*got=0;
    if(!strcmp(path,"FM1/INDEX.CSV")){data=ext_index;size=sizeof ext_index-1;}
    else if(!strcmp(path,"FM1/FW/USB_1.BIN")){data="USB";size=3;}
    else return MB_IO_MISSING;
    if(off>size)return MB_IO_ERROR;
    if(off==size)return MB_IO_EOF;
    if(cap>size-off)cap=(uint32_t)(size-off);
    memcpy(out,data+off,cap);
    *got=cap;
    return MB_IO_OK;
}
struct host { u32 tick; unsigned step; int lcd; };
static int host_timer(void *p){(void)p;return 1;}
static int host_calibrated(void *p){(void)p;return 1;}
static u32 host_rate(void *p){(void)p;return 48000;}
static u32 host_now(void *p){struct host*h=p;u32 t=h->tick;h->tick+=960000u;return t;}
static int host_lcd(void *p,u32 board){struct host*h=p;(void)board;h->lcd++;return 1;}
static u32 host_keys(void *p){struct host*h=p;u32 r=0;if(h->step<3)r=MB_KEY_NEXT;else if(h->step<6)r=MB_KEY_PLAY;else if(h->step<9)r=MB_KEY_REC;++h->step;return r;}
static void host_delay(void *p,u32 ms){(void)p;(void)ms;}
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
    {
        struct mb_storage external={0,ext_read};
        if(!mb_ram_app_init(&c,MB_APP_USB_MSC,external,&s,r)||c.catalog.count!=1)return 9;
        if(mb_verify_selected(&c)!=MB_IO_OK||c.state!=MB_READY_FOR_SIMULATION)return 10;
        if(mb_ram_app_init(&c,MB_APP_USB_MSC,(struct mb_storage){0,0},&s,r)||c.state!=MB_NO_STORAGE)return 11;
    }
    {
        struct host h={0,0,0};
        struct mb_ram_app_hooks hooks={&h,host_timer,host_calibrated,host_rate,host_now,host_lcd,host_keys,host_delay,r,MB_APP_DEMO,{0,0},10};
        mb_ram_app_set_host_hooks(&hooks);
        embedded_ram_app_entry(0x01c30000u);
        if(mb_ram_app_last_result()!=0||h.lcd!=1||mb_ram_app_frame_count()<2||mb_ram_app_last_state()!=MB_BROWSE)return 12;
        h=(struct host){0,0,0};hooks.context=&h;hooks.backend=MB_APP_USB_MSC;hooks.external_storage=(struct mb_storage){0,ext_read};hooks.max_steps=1;
        mb_ram_app_set_host_hooks(&hooks);embedded_ram_app_entry(0x01c30000u);
        if(mb_ram_app_last_result()!=0||mb_ram_app_last_state()!=MB_BROWSE)return 13;
    }
    puts("RAM-only Cover Flow app: full startup DEMO/USB, buttons, inspection, cancel, verify, blocked launch: OK");
    return 0;
}
