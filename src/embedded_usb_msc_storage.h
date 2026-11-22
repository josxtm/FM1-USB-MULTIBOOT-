/* SPDX-License-Identifier: MIT */
#ifndef EMBEDDED_USB_MSC_STORAGE_H
#define EMBEDDED_USB_MSC_STORAGE_H
#include "embedded_fat32_storage.h"

/* Explicit operation: callers must invoke this; linking this adapter never
 * initializes USB0.  Failure is retained for inspection by the application. */
struct mb_usb_msc_storage {
    struct usb_device device;
    u32 blocks;
    struct fat32 fs;
    struct mb_fat32_storage fat;
    struct mb_storage storage;
    enum diag_result error;
    u8 ready;
};

enum diag_result mb_usb_msc_storage_open(struct mb_usb_msc_storage *);
/* Host/simulation seam: mount an already emulated read-only block device using
 * exactly the same FAT32 and mb_storage adapter as the physical path. */
enum diag_result mb_usb_msc_storage_mount(struct mb_usb_msc_storage *, struct block_device);
void mb_usb_msc_storage_init(struct mb_usb_msc_storage *);
struct mb_storage mb_usb_msc_storage_hal(struct mb_usb_msc_storage *);
const char *mb_usb_msc_storage_error(const struct mb_usb_msc_storage *);
#endif
