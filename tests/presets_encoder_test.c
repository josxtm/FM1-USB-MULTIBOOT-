/* SPDX-License-Identifier: MIT */
#include <assert.h>
#include <stdio.h>
#include "embedded_presets.h"
static void seq(struct mb_presets_encoder *e, const u8 *v, unsigned n, enum mb_event wanted)
{ enum mb_event event=MB_EVENT_REC; unsigned hits=0; for(unsigned i=0;i<n;i++) if(mb_presets_poll(e,v[i],&event)){++hits;assert(event==wanted);} assert(hits==1); }
int main(void)
{
    struct mb_presets_encoder e; const u8 cw[]={0,1,3,2,0}; const u8 ccw[]={0,2,3,1,0};
    mb_presets_init(&e,4); seq(&e,cw,sizeof cw,MB_EVENT_PREVIOUS);
    mb_presets_init(&e,4); seq(&e,ccw,sizeof ccw,MB_EVENT_NEXT);
    mb_presets_init(&e,4); { enum mb_event x; assert(!mb_presets_poll(&e,0,&x)); assert(!mb_presets_poll(&e,3,&x)); }
    puts("PRESETS quadrature: direction, detent and invalid-transition rejection: OK"); return 0;
}
