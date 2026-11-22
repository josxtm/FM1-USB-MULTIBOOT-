/* SPDX-License-Identifier: MIT
 * Explicit USB0 -> MSC/BOT -> block device -> FAT32 adapter. */
#include "embedded_usb_msc_storage.h"

void mb_usb_msc_storage_init(struct mb_usb_msc_storage *s)
{
    if (!s) return;
    *s = (struct mb_usb_msc_storage){0};
    s->error = DIAG_DISCONNECTED;
}

struct mb_storage mb_usb_msc_storage_hal(struct mb_usb_msc_storage *s)
{
    return s ? s->storage : (struct mb_storage){0, 0};
}

enum diag_result mb_usb_msc_storage_mount(struct mb_usb_msc_storage *s, struct block_device disk)
{
    enum diag_result r;
    if (!s || !disk.read || !disk.count) return DIAG_TRANSFER;
    r = fat32_mount(&s->fs, disk);
    if (r != DIAG_OK) { s->error = r; return r; }
    mb_fat32_storage_init(&s->fat, &s->fs);
    s->storage = mb_fat32_storage_hal(&s->fat);
    s->blocks = disk.count;
    s->error = DIAG_OK;
    s->ready = 1;
    return DIAG_OK;
}

enum diag_result mb_usb_msc_storage_open(struct mb_usb_msc_storage *s)
{
    enum diag_result r;
    struct block_device disk;
    if (!s) return DIAG_TRANSFER;
    mb_usb_msc_storage_init(s);
    r = usb_enumerate(&s->device);
    if (r != DIAG_OK) { s->error = r; return r; }
    r = disk_open(&s->device, &s->blocks);
    if (r != DIAG_OK) { s->error = r; return r; }
    disk.context = s;
    disk.count = s->blocks;
    disk.read = disk_read;
    return mb_usb_msc_storage_mount(s, disk);
}

const char *mb_usb_msc_storage_error(const struct mb_usb_msc_storage *s)
{
    return s ? diag_error(s->error) : "ERROR";
}
