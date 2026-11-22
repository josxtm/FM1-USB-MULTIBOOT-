#include "embedded_multiboot.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
static char index_data[24577]; static unsigned long index_len; static unsigned cards;
static enum mb_io_result read_index(void*c,const char*p,uint32_t o,uint8_t*out,uint32_t cap,uint32_t*n){(void)c;if(strcmp(p,"FM1/INDEX.CSV"))return MB_IO_MISSING;if(o>=index_len)return MB_IO_EOF;*n=(uint32_t)(index_len-o);if(*n>cap)*n=cap;memcpy(out,index_data+o,*n);return MB_IO_OK;}
static void begin(void*c){(void)c;cards=0;} static void card(void*c,const struct mb_entry*e,int side,int focus){(void)c;(void)e;(void)side;(void)focus;cards++;} static void text(void*c,const char*s){(void)c;(void)s;} static void end(void*c){(void)c;}
int main(void){const char*hash="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";for(unsigned count=1;count<=64;count=count==3?64:count+1){index_len=(unsigned long)snprintf(index_data,sizeof index_data,"FM1CAT|1\nID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\n");for(unsigned i=0;i<count;i++)index_len+=(unsigned long)snprintf(index_data+index_len,sizeof index_data-index_len,"F%02u|FIRMWARE %u|1|F%02u.BIN|3|%s|UNVERIFIED\n",i+1,i+1,i+1,hash);struct mb_core c;mb_init(&c,(struct mb_storage){0,read_index},(struct mb_renderer){0,begin,card,text,end});assert(mb_load_catalog(&c)==MB_IO_OK);mb_render(&c);assert(cards==(count==1?1:(count==2?2:3)));}puts("embedded render matrix: 1, 2, 3 and 64 entries: OK");return 0;}
