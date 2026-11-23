/* SPDX-License-Identifier: MIT */
#include "usbc_lab.h"
#include <assert.h>
#include <stdio.h>
int main(void) {
    struct usbc_lab_gate g;
    usbc_lab_init(&g, 0);
    assert(usbc_lab_poll(&g, 10, 6) == 0);
    assert(usbc_lab_poll(&g, 100, 0) == 0);
    assert(usbc_lab_poll(&g, 299, 0) == 0);
    assert(usbc_lab_poll(&g, 300, 0) == 0);
    assert(g.mode == USBC_LAB_WAIT_CHORD);
    assert(usbc_lab_poll(&g, 350, 6) == 0);
    assert(usbc_lab_poll(&g, 2349, 6) == 0);
    assert(usbc_lab_poll(&g, 2350, 6) == 1);
    assert(usbc_lab_poll(&g, 2351, 0) == 1);
    usbc_lab_init(&g, 0);
    assert(usbc_lab_poll(&g, 1, 0) == 0);
    assert(usbc_lab_poll(&g, 201, 0) == 0);
    assert(usbc_lab_poll(&g, 210, 6) == 0);
    assert(usbc_lab_poll(&g, 1000, 2) == 0);
    assert(usbc_lab_poll(&g, 3010, 6) == 0);
    assert(usbc_lab_poll(&g, 3011, 8) == -1);
    assert(usbc_lab_poll(&g, 6000, 6) == -1);
    usbc_lab_init(&g, 0);
    assert(usbc_lab_poll(&g, 55000, 0) == -1);
    usbc_lab_init(&g, 0);
    assert(usbc_lab_poll(&g, 1, 0) == 0);
    assert(usbc_lab_poll(&g, 201, 0) == 0);
    assert(usbc_lab_poll(&g, 202, 7) == 0);
    assert(g.mode == USBC_LAB_WAIT_CHORD);
    puts("USB-C host lab arm: release/chord/timing/cancel/timeout PASS");
    return 0;
}
