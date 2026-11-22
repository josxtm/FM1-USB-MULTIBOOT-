/* SPDX-License-Identifier: MIT
 * RAM-only Cover Flow application composition. The physical entry is
 * experimental; host hooks make the complete flow testable without registers. */
#include "embedded_ram_app.h"
#include "embedded_lcd_renderer.h"
#include "embedded_keys.h"
#include "embedded_presets.h"
#include "embedded_clock.h"
#include "lcd.h"
#include "keys.h"
#include "usbio.h"
#ifdef MB_USB_STORAGE_LINKED
#include "embedded_usb_msc_storage.h"
#include "embedded_lcd_list_renderer.h"
#endif

static const unsigned char dark[]="DARK-1", melodee[]="MELODEE", tessera[]="TESSERA";
static const char catalog[]=
 "FM1CAT|1\nID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\n"
 "DARK_1|DARK-1|DEMO-1|DARK_1.BIN|6|1be90ebfbbef2351dd404b94e9112d8f754b37db2897d00c8fd2ea9f5aa7ef75|UNVERIFIED\n"
 "MELODEE|MELODEE|DEMO-1|MELODEE.BIN|7|8408e8364482341b88ca50e852453a262a40b480ec2a2ae50e5e4b74dc956509|UNVERIFIED\n"
 "TESSERA|TESSERA|DEMO-1|TESSERA.BIN|7|5e8300fa168cba88679336a53d77b60834d50bb9b434afd4de1a0882c0e87611|UNVERIFIED\n";
static unsigned long slen(const char*s){unsigned long n=0;while(s[n])++n;return n;}
static int eq(const char*a,const char*b){while(*a&&*a==*b){++a;++b;}return *a==*b;}
static enum mb_io_result demo_read(void*ctx,const char*path,uint32_t off,uint8_t*out,uint32_t cap,uint32_t*got)
{
 const unsigned char*data=0;unsigned long size=0;(void)ctx;*got=0;
 if(eq(path,"FM1/INDEX.CSV")){data=(const unsigned char*)catalog;size=slen(catalog);}
 else if(eq(path,"FM1/FW/DARK_1.BIN")){data=dark;size=sizeof dark-1;}
 else if(eq(path,"FM1/FW/MELODEE.BIN")){data=melodee;size=sizeof melodee-1;}
 else if(eq(path,"FM1/FW/TESSERA.BIN")){data=tessera;size=sizeof tessera-1;}
 else return MB_IO_MISSING;
 if(off>size)return MB_IO_ERROR;
 if(off==size)return MB_IO_EOF;
 if(cap>size-off)cap=(uint32_t)(size-off);
 for(uint32_t i=0;i<cap;i++) {
  out[i]=data[off+i];
 }
 *got=cap;
 return MB_IO_OK;
}
struct mb_storage mb_demo_storage_hal(struct mb_demo_storage*s){return (struct mb_storage){s,demo_read};}
void mb_demo_catalog_init(struct mb_core*c,struct mb_demo_storage*s,struct mb_renderer r)
{mb_init(c,mb_demo_storage_hal(s),r);(void)mb_load_catalog(c);}
int mb_ram_app_init(struct mb_core*c,enum mb_app_backend backend,struct mb_storage external,
                    struct mb_demo_storage*demo,struct mb_renderer renderer)
{
 if(!c)return 0;
 if(backend==MB_APP_DEMO){if(!demo)return 0;mb_demo_catalog_init(c,demo,renderer);}
 else {mb_init(c,external,renderer);if(mb_load_catalog(c)!=MB_IO_OK)return 0;}
 return 1;
}

#define APP_DATA __attribute__((section(".data.app")))
static struct mb_ram_app_hooks app_hooks APP_DATA={0};
static int hooks_present APP_DATA=0;
static int app_result APP_DATA=0;
static enum mb_state app_state APP_DATA=MB_BOOT;
static enum mb_compatibility app_compat APP_DATA=MB_COMPAT_UNKNOWN;
static unsigned app_frames APP_DATA=0;
static struct mb_core app_core APP_DATA={0};
static struct mb_demo_storage app_demo APP_DATA={0};
#ifndef MB_TEXT_SELECTOR
static struct mb_lcd_renderer app_lcd APP_DATA={0};
#endif
#ifdef MB_TEXT_SELECTOR
static struct mb_lcd_list_renderer app_list APP_DATA={0};
#endif
static struct mb_key_adapter app_keys APP_DATA={0};
static struct mb_presets_encoder app_presets APP_DATA={0};
static struct mb_clock app_clock APP_DATA={0};
#ifdef MB_USB_STORAGE_LINKED
static struct mb_usb_msc_storage app_usb APP_DATA={0};
/* A volatile reference keeps the complete explicit USB path in the linked
 * image without initializing USB0.  The call remains a future physical gate. */
static enum diag_result (*volatile app_usb_open)(struct mb_usb_msc_storage *) = mb_usb_msc_storage_open;
#endif
extern unsigned char mb_bss_start[],mb_bss_end[];
void mb_ram_app_set_host_hooks(const struct mb_ram_app_hooks*h)
{if(h){app_hooks=*h;hooks_present=1;}}
int mb_ram_app_last_result(void){return app_result;}
enum mb_state mb_ram_app_last_state(void){return app_state;}
unsigned mb_ram_app_frame_count(void){return app_frames;}
enum mb_compatibility mb_ram_app_last_compatibility(void){return app_compat;}
const char *mb_ram_app_last_compatibility_reason(void){return mb_compatibility_reason(app_compat);}
void plat_delay_ms(u32 ms){delay_us(1000u*ms);}
static int timer_begin(void*v){return hooks_present?app_hooks.timer_start(v):timer_start_unconfigured();}
static int timer_ok(void*v){return hooks_present?app_hooks.timer_calibrated(v):timer_calibrated();}
static u32 timer_rate(void*v){return hooks_present?app_hooks.timer_ticks_per_ms(v):timer_ticks_per_ms();}
static u32 timer_now(void*v){return hooks_present?app_hooks.timer_now(v):tb_now();}
static int panel_init(void*v){return hooks_present?app_hooks.lcd_init(v,BRD_FM1):lcd_init(BRD_FM1);}
static u32 key_scan(void*v){return hooks_present?app_hooks.keys_scan(v):plat_menu_scan();}
static void wait_ms(void*v,u32 ms){if(hooks_present)app_hooks.delay_ms(v,ms);else plat_delay_ms(ms);}
static void app_status(const char *prefix, const char *reason)
{
 unsigned i=0,j=0;
 while(prefix[i] && i+1<sizeof app_core.error){app_core.error[i]=prefix[i];++i;}
 while(reason[j] && i+1<sizeof app_core.error){app_core.error[i++]=reason[j++];}
 app_core.error[i]=0;
}
static void process_play(void)
{
 struct mb_entry *e;
 struct mb_launch_metadata m={0,0,0,0,0,0,0};
 if(app_core.selected>=app_core.catalog.count)return;
 e=&app_core.catalog.entries[app_core.selected];
 m.format_known=e->metadata_valid;
 if(mb_verify_selected(&app_core)!=MB_IO_OK && app_core.state!=MB_INCOMPATIBLE)
  app_core.state=MB_ERROR;
 m.integrity_verified=e->integrity_verified;
 app_compat=mb_classify_launch(e,m);
 (void)mb_prepare_launch(&app_core);
 app_status("BLOCKED: ",mb_compatibility_reason(app_compat));
}
static void process_event(enum mb_event event)
{
 if(event==MB_EVENT_PLAY)process_play();
 else mb_event(&app_core,event);
}
void embedded_ram_app_entry(u32 rom_arg)
{
 struct mb_renderer renderer;enum mb_event event;u32 steps=0,raw;
 void*ctx=app_hooks.context;
 app_result=0;app_frames=0;app_state=MB_BOOT;app_compat=MB_COMPAT_UNKNOWN;
#ifdef MB_USB_STORAGE_LINKED
 mb_usb_msc_storage_init(&app_usb); (void)app_usb_open;
#endif
 if(rom_arg<0x01000000u){app_result=1;return;}
 if(!hooks_present)for(unsigned char*p=mb_bss_start;p<mb_bss_end;p++)*p=0;
 if(!timer_begin(ctx)||!timer_ok(ctx)||!timer_rate(ctx)){app_result=2;return;}
 mb_clock_init(&app_clock,timer_rate(ctx),timer_now(ctx));
 if(!panel_init(ctx)){app_result=3;return;}
 if(hooks_present)renderer=app_hooks.renderer;
 else {
#ifdef MB_TEXT_SELECTOR
  mb_lcd_list_renderer_init(&app_list,mb_lcd_physical_sink()); renderer=app_list.renderer;
#else
  mb_lcd_renderer_init(&app_lcd,mb_lcd_physical_sink()); renderer=app_lcd.renderer;
#endif
 }
 mb_keys_init(&app_keys); mb_presets_init(&app_presets,4);
 if(!mb_ram_app_init(&app_core,hooks_present?app_hooks.backend:MB_APP_DEMO,
                     hooks_present?app_hooks.external_storage:(struct mb_storage){0,0},&app_demo,renderer)){
  app_state=app_core.state;app_result=4;return;
 }
 mb_render(&app_core);++app_frames;app_state=app_core.state;
 for(;;){
  raw=key_scan(ctx);
  if(!mb_clock_update(&app_clock,timer_now(ctx))){app_result=5;app_state=MB_ERROR;return;}
  if(hooks_present && app_hooks.encoder_scan && mb_presets_poll(&app_presets,app_hooks.encoder_scan(ctx),&event)){
   process_event(event);mb_render(&app_core);++app_frames;app_state=app_core.state;
  }
  if(mb_keys_poll(&app_keys,raw,app_clock.now_ms,&event)){process_event(event);mb_render(&app_core);++app_frames;app_state=app_core.state;}
  ++steps;if(hooks_present&&app_hooks.max_steps&&steps>=app_hooks.max_steps){app_result=0;return;}
  wait_ms(ctx,10);
 }
}
