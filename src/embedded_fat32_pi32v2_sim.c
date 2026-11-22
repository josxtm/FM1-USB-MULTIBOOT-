/* SPDX-License-Identifier: MIT */
#include "embedded_fat32_storage.h"

static enum diag_result simulated_block_read(void *context, u32 lba, u8 *out)
{
    (void)context; (void)lba; (void)out; return DIAG_RANGE;
}
static void sim_begin(void *context) { (void)context; }
static void sim_card(void *context, const struct mb_entry *entry, int side, int focus)
{ (void)context; (void)entry; (void)side; (void)focus; }
static void sim_text(void *context, const char *text) { (void)context; (void)text; }
static void sim_end(void *context) { (void)context; }

void embedded_fat32_sim_entry(void)
{
    struct fat32 fs = { 0 };
    struct block_device disk = { 0, 0, simulated_block_read };
    struct mb_fat32_storage fat_storage;
    struct mb_core core;
    const struct mb_renderer renderer = { 0, sim_begin, sim_card, sim_text, sim_end };
    (void)fat32_mount(&fs, disk);
    mb_fat32_storage_init(&fat_storage, &fs);
    mb_init(&core, mb_fat32_storage_hal(&fat_storage), renderer);
    mb_render(&core);
}
