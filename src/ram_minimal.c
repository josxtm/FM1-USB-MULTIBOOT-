/* SPDX-License-Identifier: MIT
 * FASE 5P minimal RAM candidate.  No LCD, USB, storage, timer, Flash or
 * firmware-launch dependency.  The run-ram profile has no documented return,
 * so observation is a bounded state trace in RAM followed by a safe terminal
 * wait; it does not pretend that a return is available. */
#include "mbl.h"

#define MINIMAL_ENTERED 0x314e4545u /* "EEN1" */
#define MINIMAL_DONE    0x314e4f44u /* "DON1" */
#define MINIMAL_WAIT    0x314e544fu /* "OTN1" */

volatile u32 fm1_ram_minimal_marker = MINIMAL_ENTERED;
volatile u32 fm1_ram_minimal_heartbeat;
volatile u32 fm1_ram_minimal_status;

void fm1_ram_minimal_entry(void)
{
    u32 i;
    fm1_ram_minimal_marker = MINIMAL_ENTERED;
    fm1_ram_minimal_status = 0;
    fm1_ram_minimal_heartbeat = 0;
    /* A finite trace distinguishes entry from an immediate fault when RAM is
     * inspected by an authorized harness.  It has no timing assumption. */
    for (i = 0; i < 4096u; ++i) fm1_ram_minimal_heartbeat = i + 1u;
    fm1_ram_minimal_status = MINIMAL_DONE;
    /* run-ram has no documented safe return.  Keep the terminal state stable
     * instead of returning into an unknown caller. */
    for (;;) fm1_ram_minimal_marker = MINIMAL_WAIT;
}
