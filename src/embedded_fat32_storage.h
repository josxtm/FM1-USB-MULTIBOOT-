/* SPDX-License-Identifier: MIT */
#ifndef FM1_EMBEDDED_FAT32_STORAGE_H
#define FM1_EMBEDDED_FAT32_STORAGE_H
#include "embedded_multiboot.h"
#include "storage.h"

struct mb_fat32_storage { struct fat32 *fs; };
void mb_fat32_storage_init(struct mb_fat32_storage *, struct fat32 *);
struct mb_storage mb_fat32_storage_hal(struct mb_fat32_storage *);
#endif
