/* SPDX-License-Identifier: MIT */
#ifndef DIAG_KEYS_H
#define DIAG_KEYS_H
#include "mbl.h"
#include "disp.h"
#define K_PREV 1u
#define K_NEXT 2u
#define K_SEL 4u
#define K_REC 8u
u32 plat_boot_keys(void);
u32 plat_menu_scan(void);
void plat_keys_off(void);
void plat_delay_ms(u32 ms);
#endif
