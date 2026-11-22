/* SPDX-License-Identifier: MIT
 * Host HAL test for the real mb_core and ST7789 strip renderer. */
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "embedded_lcd_renderer.h"
#include "embedded_keys.h"
static unsigned char fb[240u*240u*2u]; static unsigned cx,cy,cw,ch;
int lcd_init(u32 b){(void)b;return 1;} void lcd_window(u32 x,u32 y,u32 w,u32 h){cx=x;cy=y;cw=w;ch=h;} void lcd_pixels(const u8*p,u32 n){assert(n==2*cw*ch);for(u32 y=0;y<ch;y++)memcpy(fb+2*((cy+y)*240+cx),p+2*y*cw,2*cw);} void lcd_end(void){}
struct fixture{char data[24577];u32 len;};
static enum mb_io_result read_index(void*p,const char*path,u32 off,u8*out,u32 cap,u32*got){struct fixture*f=p;if(strcmp(path,"FM1/INDEX.CSV"))return MB_IO_MISSING;if(off>=f->len)return MB_IO_EOF;if(cap>f->len-off)cap=f->len-off;memcpy(out,f->data+off,cap);*got=cap;return MB_IO_OK;}
static void make(struct fixture*f,unsigned n){u32 at=(u32)snprintf(f->data,sizeof f->data,"FM1CAT|1\nID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\n");const char*h="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";for(unsigned i=0;i<n;i++)at+=(u32)snprintf(f->data+at,sizeof f->data-at,"F%02u|Firmware %u|1.0|F%02u.BIN|3|%s|UNVERIFIED\n",i+1,i+1,i+1,h);f->len=at;}
static void ppm(const char*name){FILE*f=fopen(name,"wb");assert(f);fprintf(f,"P6\n240 240\n255\n");for(unsigned i=0;i<240*240;i++){u16 c=(u16)(fb[2*i]<<8|fb[2*i+1]);fputc((c>>8)&0xf8,f);fputc((c>>3)&0xfc,f);fputc((c<<3)&0xf8,f);}fclose(f);}
int main(void){struct mb_lcd_renderer lr;struct mb_lcd_sink sink=mb_lcd_physical_sink();struct fixture f;struct mb_core c;for(unsigned n=0;n<=64;n=n==3?64:n+1){make(&f,n);memset(fb,0,sizeof fb);mb_lcd_renderer_init(&lr,sink);mb_init(&c,(struct mb_storage){&f,read_index},lr.renderer);{enum mb_io_result rr=mb_load_catalog(&c);if(rr!=MB_IO_OK||c.catalog.count!=n){fprintf(stderr,"n=%u rr=%d count=%u len=%u data=%s\n",n,rr,c.catalog.count,f.len,f.data);return 1;}}mb_render(&c);char name[64];snprintf(name,sizeof name,"build/lcd-coverflow-%u.ppm",n);ppm(name);}struct mb_key_adapter k;enum mb_event e;mb_keys_init(&k);assert(!mb_keys_poll(&k,MB_KEY_NEXT,0,&e));assert(!mb_keys_poll(&k,MB_KEY_NEXT,19,&e));assert(mb_keys_poll(&k,MB_KEY_NEXT,20,&e)&&e==MB_EVENT_NEXT);assert(!mb_keys_poll(&k,MB_KEY_NEXT,100,&e));assert(mb_keys_poll(&k,0,120,&e)==0);assert(mb_keys_poll(&k,0,121,&e)==0);puts("embedded ST7789 strip renderer and debounced key adapter: OK");return 0;}
