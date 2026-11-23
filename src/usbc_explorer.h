/* SPDX-License-Identifier: MIT */
#ifndef FM1_USBC_EXPLORER_H
#define FM1_USBC_EXPLORER_H
#include "fm1cat_v2.h"
#include "embedded_fat32_storage.h"
#include "embedded_lcd_list_renderer.h"

#define USB_EXPLORER_ROWS 9u

enum usb_power_evidence { USB_POWER_UNKNOWN, USB_POWER_ABSENT, USB_POWER_QUALIFIED };
/* The target has no board-qualified implementation. The host seam models independently
 * established electrical conditions; it is not inferred from the controller register. */
enum diag_result usbc_connect(struct usb_device *, struct block_device *, enum usb_power_evidence);

struct usbc_explorer {
    struct fat32 fs;
    struct cat2 catalog;
    /* Legacy catalog has a fixed 64-entry limit; the v2 path is streamed. */
    struct mb_core legacy;
    struct mb_fat32_storage legacy_storage;
    struct cat2_record item;
    char labels[USB_EXPLORER_ROWS][21];
    u32 window, current, count;
    enum diag_result media, integrity;
    u8 version, detail, checked, exit, name_page;
};
void usbc_mount(struct usbc_explorer *, struct block_device, enum diag_result);
void usbc_draw(const struct usbc_explorer *, struct mb_lcd_list_renderer *);
void usbc_event(struct usbc_explorer *, enum mb_event);
#endif
