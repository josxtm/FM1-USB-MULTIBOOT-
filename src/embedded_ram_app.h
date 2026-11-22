/* SPDX-License-Identifier: MIT */
#ifndef EMBEDDED_RAM_APP_H
#define EMBEDDED_RAM_APP_H
#include "embedded_multiboot.h"
#include "mbl.h"

/* Demonstration-only catalog; entries are display data, not launchable images. */
struct mb_demo_storage { unsigned selected_reads; };
enum mb_app_backend { MB_APP_DEMO, MB_APP_USB_MSC };
struct mb_storage mb_demo_storage_hal(struct mb_demo_storage *);
void mb_demo_catalog_init(struct mb_core *, struct mb_demo_storage *, struct mb_renderer);
int mb_ram_app_init(struct mb_core *, enum mb_app_backend, struct mb_storage, struct mb_demo_storage *, struct mb_renderer);
void embedded_ram_app_entry(u32 rom_arg);
#endif
