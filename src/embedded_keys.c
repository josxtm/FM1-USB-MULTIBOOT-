/* SPDX-License-Identifier: MIT */
#include "embedded_keys.h"
void mb_keys_init(struct mb_key_adapter*k){if(k){k->candidate=k->stable=k->changed_at=k->repeat_at=0;k->pressed=0;}}
int mb_keys_poll(struct mb_key_adapter*k,u32 raw,u32 now,enum mb_event*e)
{
    u32 bit=0; if(!k||!e)return 0;
    raw&=MB_KEY_PREVIOUS|MB_KEY_NEXT|MB_KEY_PLAY|MB_KEY_REC;
    if(raw!=k->candidate){k->candidate=raw;k->changed_at=now;return 0;}
    if(raw!=k->stable&&now-k->changed_at<20u)return 0;
    if(raw!=k->stable){k->stable=raw;k->pressed=0;k->repeat_at=now+400u;}
    if(!k->stable)return 0;
    if(!k->pressed||now>=k->repeat_at){k->pressed=1;k->repeat_at=now+100u;
        if(k->stable&MB_KEY_REC)bit=MB_KEY_REC;else if(k->stable&MB_KEY_PLAY)bit=MB_KEY_PLAY;else if(k->stable&MB_KEY_PREVIOUS)bit=MB_KEY_PREVIOUS;else bit=MB_KEY_NEXT;
        *e=bit==MB_KEY_PREVIOUS?MB_EVENT_PREVIOUS:bit==MB_KEY_NEXT?MB_EVENT_NEXT:bit==MB_KEY_PLAY?MB_EVENT_PLAY:MB_EVENT_REC;return 1;}
    return 0;
}
