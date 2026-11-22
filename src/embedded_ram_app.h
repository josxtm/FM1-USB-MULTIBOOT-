/* SPDX-License-Identifier: MIT */
#ifndef EMBEDDED_RAM_APP_H
#define EMBEDDED_RAM_APP_H
#include "embedded_multiboot.h"
#include "mbl.h"

enum mb_app_backend { MB_APP_DEMO, MB_APP_USB_MSC };
struct mb_demo_storage { unsigned selected_reads; };
struct mb_ram_app_hooks {
    void *context;
    int (*timer_start)(void *);
    int (*timer_calibrated)(void *);
    u32 (*timer_ticks_per_ms)(void *);
    u32 (*timer_now)(void *);
    int (*lcd_init)(void *, u32);
    u32 (*keys_scan)(void *);
    void (*delay_ms)(void *, u32);
    struct mb_renderer renderer;
    enum mb_app_backend backend;
    struct mb_storage external_storage;
    u32 max_steps;
    u8 (*encoder_scan)(void *); /* optional PRESETS A/B sample, board HAL-owned */
};
struct mb_storage mb_demo_storage_hal(struct mb_demo_storage *);
void mb_demo_catalog_init(struct mb_core *, struct mb_demo_storage *, struct mb_renderer);
int mb_ram_app_init(struct mb_core *, enum mb_app_backend, struct mb_storage, struct mb_demo_storage *, struct mb_renderer);
void mb_ram_app_set_host_hooks(const struct mb_ram_app_hooks *);
int mb_ram_app_last_result(void);
enum mb_state mb_ram_app_last_state(void);
unsigned mb_ram_app_frame_count(void);
void embedded_ram_app_entry(u32 rom_arg);
#endif
