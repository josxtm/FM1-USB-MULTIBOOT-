/* SPDX-License-Identifier: MIT */
#ifndef FM1_STG2_SD_H
#define FM1_STG2_SD_H
#include "storage.h"

/* SPI session ownership belongs to the caller. The protocol only reads SDHC/SDXC. */
struct stg2_sd_bus {
    void *context;
    int (*exchange)(void *, u8, u8 *);
    void (*select)(void *, int);
    void (*wait_ms)(void *);
};

struct stg2_sd {
    struct stg2_sd_bus bus;
    u32 sectors;
    int transfer_failed;
};

enum diag_result stg2_sd_start(struct stg2_sd *);
enum diag_result stg2_sd_read(void *, u32, u8 *);
#endif
