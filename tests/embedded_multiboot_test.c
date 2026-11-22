#include "embedded_multiboot.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>

static const char hash_abc[] = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
struct fixture { const char *index; size_t index_len; int disconnect; int extra; };
static enum mb_io_result read_file(void *ctx, const char *path, uint32_t off, uint8_t *out, uint32_t cap, uint32_t *got) {
    struct fixture *f=ctx; if(f->disconnect)return MB_IO_DISCONNECTED;
    if(!strcmp(path,"FM1/INDEX.CSV")){if(off>=f->index_len)return MB_IO_EOF;*got=(uint32_t)(f->index_len-off);if(*got>cap)*got=cap;memcpy(out,f->index+off,*got);return MB_IO_OK;}
    if(!strcmp(path,"FM1/FW/ONE.BIN")||!strcmp(path,"FM1/FW/F01.BIN")){const char x[]="abcX";uint32_t len=f->extra?4u:3u;if(off>=len)return MB_IO_EOF;*got=len-off;if(*got>cap)*got=cap;memcpy(out,x+off,*got);return MB_IO_OK;}
    return MB_IO_MISSING;
}
static unsigned cards;
static void render_begin(void *p){(void)p;cards=0;} static void render_card(void*p,const struct mb_entry*e,int side,int focus){(void)p;(void)e;(void)side;(void)focus;cards++;} static void render_text(void*p,const char*s){(void)p;(void)s;} static void render_end(void*p){(void)p;}
static struct mb_core load(struct fixture *f){struct mb_core c;struct mb_renderer r={0,render_begin,render_card,render_text,render_end};mb_init(&c,(struct mb_storage){f,read_file},r);assert(mb_load_catalog(&c)==MB_IO_OK);return c;}
int main(void){
    const char one[]="FM1CAT|1\nID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\nONE|One|1|ONE.BIN|3|ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad|UNVERIFIED\n";
    struct fixture f={one,sizeof one-1,0,0}; struct mb_core c=load(&f); assert(c.state==MB_BROWSE&&c.catalog.count==1); mb_render(&c); assert(cards==1); assert(mb_verify_selected(&c)==MB_IO_OK&&c.state==MB_READY_FOR_SIMULATION); f.extra=1; assert(mb_verify_selected(&c)==MB_IO_ERROR&&c.state==MB_INCOMPATIBLE); f.extra=0; assert(mb_verify_selected(&c)==MB_IO_OK&&c.state==MB_READY_FOR_SIMULATION); assert(mb_prepare_launch(&c)==MB_LAUNCH_BLOCKED); mb_event(&c,MB_EVENT_PLAY);assert(c.state==MB_INSPECT);mb_event(&c,MB_EVENT_REC);assert(c.state==MB_BROWSE);
    const char empty[]="FM1CAT|1\nID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\n"; f=(struct fixture){empty,sizeof empty-1,0,0}; c=load(&f); assert(c.state==MB_EMPTY&&c.catalog.count==0);
    const char two[]="FM1CAT|1\nID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\nONE|One|1|ONE.BIN|3|ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad|UNVERIFIED\nF01|Two|1|F01.BIN|3|ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad|UNVERIFIED\n";
    f=(struct fixture){two,sizeof two-1,0,0};c=load(&f);mb_render(&c);assert(cards==2);mb_event(&c,MB_EVENT_PREVIOUS);assert(!strcmp(mb_selected(&c)->id,"F01"));mb_event(&c,MB_EVENT_NEXT);assert(!strcmp(mb_selected(&c)->id,"ONE"));
    f.disconnect=1;assert(mb_verify_selected(&c)==MB_IO_DISCONNECTED&&c.state==MB_NO_STORAGE);
    char many[24577];size_t n=(size_t)snprintf(many,sizeof many,"FM1CAT|1\nID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\n");for(int i=1;i<=64;i++)n+=(size_t)snprintf(many+n,sizeof many-n,"F%02d|Firmware %d|1|F%02d.BIN|3|%s|UNVERIFIED\n",i,i,i,hash_abc);f=(struct fixture){many,n,0,0};c=load(&f);assert(c.catalog.count==64);for(int i=0;i<65;i++)mb_event(&c,MB_EVENT_NEXT);assert(c.selected==1);
    const char bad[]="FM1CAT|1\nID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\nBAD|x|1|../X.BIN|3|bad|UNVERIFIED\n";f=(struct fixture){bad,sizeof bad-1,0,0};mb_init(&c,(struct mb_storage){&f,read_file},(struct mb_renderer){0});assert(mb_load_catalog(&c)==MB_IO_ERROR&&c.state==MB_ERROR);
    puts("embedded multiboot core: catalog, navigation, SHA, disconnect, limits, blocked launch: OK");return 0;
}
