/* SPDX-License-Identifier: MIT */
#include <stdio.h>
#include "embedded_clock.h"
int main(void)
{
 struct mb_clock c;
 mb_clock_init(&c,48000u,0xfffffff0u);
 if(!mb_clock_update(&c,0x0000bb70u)||c.now_ms!=1u)return 1;
 if(!mb_clock_update(&c,0x00017700u)||c.now_ms!=2u)return 2;
 mb_clock_init(&c,0,0);if(mb_clock_update(&c,1))return 3;
 mb_clock_init(&c,1000u,10u);if(mb_clock_update(&c,0x80000020u)||!c.failed)return 4;
 mb_clock_init(&c,60000u,100u);if(!mb_clock_update(&c,60100u)||c.now_ms!=1u)return 5;
 puts("clock contract: explicit rate, wraparound, invalid rate and ambiguous jump: OK");return 0;
}
