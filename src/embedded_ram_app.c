/* SPDX-License-Identifier: MIT
 * RAM-only Cover Flow application composition. The entry is experimental and
 * is not connected to a physical execution path by any test. */
#include "embedded_ram_app.h"
#include "embedded_lcd_renderer.h"
#include "embedded_keys.h"
#include "lcd.h"
#include "keys.h"
#include "usbio.h"

static const unsigned char dark[] = "DARK-1";
static const unsigned char melodee[] = "MELODEE";
static const unsigned char tessera[] = "TESSERA";
static const char catalog[] =
 "FM1CAT|1\nID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\n"
 "DARK_1|DARK-1|DEMO-1|DARK_1.BIN|6|1be90ebfbbef2351dd404b94e9112d8f754b37db2897d00c8fd2ea9f5aa7ef75|UNVERIFIED\n"
 "MELODEE|MELODEE|DEMO-1|MELODEE.BIN|7|8408e8364482341b88ca50e852453a262a40b480ec2a2ae50e5e4b74dc956509|UNVERIFIED\n"
 "TESSERA|TESSERA|DEMO-1|TESSERA.BIN|7|5e8300fa168cba88679336a53d77b60834d50bb9b434afd4de1a0882c0e87611|UNVERIFIED\n";
static unsigned long slen(const char *s) { unsigned long n=0; while(s[n])++n; return n; }
static int eq(const char *a,const char *b) { while(*a&&*a==*b){++a;++b;} return *a==*b; }
static enum mb_io_result demo_read(void *ctx,const char *path,uint32_t off,uint8_t *out,uint32_t cap,uint32_t *got)
{
    const unsigned char *data=0; unsigned long size=0; (void)ctx;
    *got=0;
    if(eq(path,"FM1/INDEX.CSV")){ data=(const unsigned char *)catalog; size=slen(catalog); }
    else if(eq(path,"FM1/FW/DARK_1.BIN")){data=dark;size=sizeof dark-1;}
    else if(eq(path,"FM1/FW/MELODEE.BIN")){data=melodee;size=sizeof melodee-1;}
    else if(eq(path,"FM1/FW/TESSERA.BIN")){data=tessera;size=sizeof tessera-1;}
    else return MB_IO_MISSING;
    if(off>size)return MB_IO_ERROR;
    if(off==size)return MB_IO_EOF;
    if(cap>size-off)cap=(uint32_t)(size-off);
    for(uint32_t i=0;i<cap;i++)out[i]=data[off+i];
    *got=cap; return MB_IO_OK;
}
struct mb_storage mb_demo_storage_hal(struct mb_demo_storage *s)
{ return (struct mb_storage){s,demo_read}; }
void mb_demo_catalog_init(struct mb_core *c,struct mb_demo_storage *s,struct mb_renderer r)
{ mb_init(c,mb_demo_storage_hal(s),r); (void)mb_load_catalog(c); }

void plat_delay_ms(u32 ms) { delay_us(1000u*ms); }
static void demo_renderer(struct mb_lcd_renderer *r)
{ mb_lcd_renderer_init(r,mb_lcd_physical_sink()); }
extern unsigned char mb_bss_start[], mb_bss_end[];
void embedded_ram_app_entry(u32 rom_arg)
{
    struct mb_core core; struct mb_demo_storage storage={0}; struct mb_lcd_renderer lcd;
    struct mb_key_adapter keys; enum mb_event event;
    if(rom_arg<0x01000000u)return;
    for(unsigned char *p=mb_bss_start;p<mb_bss_end;p++)*p=0;
    if(!lcd_init(BRD_FM1))return;
    demo_renderer(&lcd); mb_keys_init(&keys); mb_demo_catalog_init(&core,&storage,lcd.renderer);
    mb_render(&core);
    for(;;){
        u32 raw=plat_menu_scan();
        if(mb_keys_poll(&keys,raw,tb_now()/48000u,&event)){mb_event(&core,event);mb_render(&core);}
        plat_delay_ms(10);
    }
}