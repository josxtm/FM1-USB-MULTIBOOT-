/* SPDX-License-Identifier: MIT
 * FASE 5O minimal RAM candidate.  It has no LCD, USB, storage, timer or
 * firmware-launch dependency.  The marker is deliberately unmistakable in
 * a future authorized observation; this object is never executed here. */
#include "mbl.h"

volatile u32 fm1_ram_minimal_marker = 0x354f4d46u; /* ASCII-ish: FM5O */
volatile u32 fm1_ram_minimal_status;

void fm1_ram_minimal_entry(void)
{
    fm1_ram_minimal_status = fm1_ram_minimal_marker;
    for (;;) { }
}
