/* SPDX-License-Identifier: MIT */
#include <stdio.h>
#include "mbl.h"
extern volatile u32 fm1_ram_minimal_marker;
extern volatile u32 fm1_ram_minimal_status;
int main(void)
{
    if (fm1_ram_minimal_marker != 0x314e4545u || fm1_ram_minimal_status != 0u) return 1;
    puts("minimal RAM candidate: marker, no HAL/USB/launch dependency: OK");
    return 0;
}
