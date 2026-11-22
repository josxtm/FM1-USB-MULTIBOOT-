#include "embedded_multiboot.h"
#include <stdio.h>
#include <string.h>

static const char catalog[] = "FM1CAT|1\nID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\nONE|ONE DEMO|1.0|ONE.BIN|3|ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad|UNVERIFIED\nTWO|TWO DEMO|2.0|TWO.FWS|3|ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad|UNVERIFIED\nTHREE|THREE DEMO|3.0|THREE.BIN|3|ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad|UNVERIFIED\n";
static FILE *svg;
static enum mb_io_result read_memory(void *context,const char *path,uint32_t offset,uint8_t *out,uint32_t cap,uint32_t *got)
{ (void)context; if(strcmp(path,"FM1/INDEX.CSV")) return MB_IO_MISSING; if(offset>=sizeof catalog-1)return MB_IO_EOF; *got=(uint32_t)(sizeof catalog-1-offset);if(*got>cap)*got=cap;memcpy(out,catalog+offset,*got);return MB_IO_OK; }
static void begin(void *context){(void)context;fprintf(svg,"<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"240\" height=\"240\" viewBox=\"0 0 240 240\"><rect width=\"240\" height=\"240\" fill=\"#08131a\"/><style>text{font-family:monospace;fill:#65e8cd}</style>");}
static void card(void *context,const struct mb_entry *e,int side,int focus){(void)context;int x=focus?70:(side<0?12:158),w=focus?100:70;fprintf(svg,"<rect x=\"%d\" y=\"45\" width=\"%d\" height=\"105\" rx=\"8\" fill=\"%s\" stroke=\"#65e8cd\"/><text x=\"%d\" y=\"100\" text-anchor=\"middle\">%s</text><text x=\"%d\" y=\"120\" text-anchor=\"middle\">%s</text>",x,w,focus?"#183841":"#10242a",x+w/2,e->label,x+w/2,e->version);}
static void text(void *context,const char *s){(void)context;fprintf(svg,"<text x=\"120\" y=\"205\" text-anchor=\"middle\">%s · PLAY INSPECT ONLY</text>",s);}
static void end(void *context){(void)context;fputs("</svg>\n",svg);}
int main(void){struct mb_core c;svg=fopen("build/embedded-coverflow-demo.svg","w");if(!svg)return 2;mb_init(&c,(struct mb_storage){0,read_memory},(struct mb_renderer){0,begin,card,text,end});if(mb_load_catalog(&c)!=MB_IO_OK)return 3;mb_event(&c,MB_EVENT_NEXT);mb_event(&c,MB_EVENT_PLAY);mb_render(&c);fclose(svg);puts("embedded Cover Flow demo: catalog, selection, inspect, SVG render: OK");return 0;}
