/* SPDX-License-Identifier: MIT */
#ifndef FM1_STG2_SELECTOR_H
#define FM1_STG2_SELECTOR_H
#include "embedded_fat32_storage.h"
#include "embedded_lcd_list_renderer.h"

#define STG2_LSB_MHZ 60u

enum stg2_action { STG2_NO_CHANGE, STG2_REDRAW, STG2_RELOAD, STG2_VERIFY, STG2_EXIT };

struct stg2_selector {
    struct fat32 fs;
    struct mb_fat32_storage storage;
    struct mb_core core;
    enum diag_result media;
    enum mb_io_result index;
    const char *notice;
    int detail;
};

void stg2_selector_mount(struct stg2_selector *, struct block_device, enum diag_result);
void stg2_selector_draw(const struct stg2_selector *, struct mb_lcd_list_renderer *);
enum stg2_action stg2_selector_event(struct stg2_selector *, enum mb_event);
void stg2_selector_verify(struct stg2_selector *);
void fm1_stg2_main(u32 lsb_mhz);
#endif
