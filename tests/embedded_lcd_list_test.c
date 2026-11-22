/* SPDX-License-Identifier: MIT */
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "embedded_lcd_list_renderer.h"
static unsigned char image[240*240*2]; static u32 x0,y0,w0,h0;
static void window(void*p,u32 x,u32 y,u32 w,u32 h){(void)p;x0=x;y0=y;w0=w;h0=h;}
static void pixels(void*p,const u8*b,u32 n){(void)p;assert(n==2*w0*h0);for(u32 y=0;y<h0;y++)memcpy(image+2*((y0+y)*240+x0),b+2*y*w0,2*w0);}
static void finish(void*p){(void)p;}
static enum mb_io_result read_index(void*p,const char*path,u32 off,u8*out,u32 cap,u32*got){const char*s=p;u32 n=(u32)strlen(s);if(strcmp(path,"FM1/INDEX.CSV"))return MB_IO_MISSING;if(off>=n)return MB_IO_EOF;if(cap>n-off)cap=n-off;memcpy(out,s+off,cap);*got=cap;return MB_IO_OK;}
static void ppm(void){FILE*f=fopen("build/lcd-selector-list.ppm","wb");assert(f);fprintf(f,"P6\n240 240\n255\n");for(u32 i=0;i<240*240;i++){u16 c=(u16)(image[2*i]<<8|image[2*i+1]);fputc((c>>8)&0xf8,f);fputc((c>>3)&0xfc,f);fputc((c<<3)&0xf8,f);}fclose(f);}
int main(void){const char data[]="FM1CAT|1\nID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\nA|DARK-1|1|A.BIN|1|ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb|UNVERIFIED\nB|MELODEE|1|B.BIN|1|ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb|UNVERIFIED\nC|TESSERA|1|C.BIN|1|ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb|UNVERIFIED\n";struct mb_lcd_list_renderer r;struct mb_core c;mb_lcd_list_renderer_init(&r,(struct mb_lcd_sink){0,window,pixels,finish});mb_init(&c,(struct mb_storage){(void*)data,read_index},r.renderer);assert(mb_load_catalog(&c)==MB_IO_OK&&c.catalog.count==3);memset(image,0,sizeof image);mb_render(&c);ppm();puts("text selector 240x240: list, selection marker and no framebuffer backend: OK");return 0;}
