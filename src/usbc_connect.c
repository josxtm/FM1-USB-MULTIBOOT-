/* SPDX-License-Identifier: MIT */
#include "usbc_explorer.h"

enum diag_result usbc_connect(struct usb_device *identity, struct block_device *disk, enum usb_power_evidence power)
{
    *identity = (struct usb_device){0};
    *disk = (struct block_device){0};
    if (power == USB_POWER_ABSENT) return DIAG_NO_VBUS;
    if (power != USB_POWER_QUALIFIED) return DIAG_POWER_UNVERIFIED;
    husb_init();
    if (!husb_wait_attach(1500)) return DIAG_DISCONNECTED;
    enum diag_result r = usb_enumerate(identity);
    if (r != DIAG_OK) return r;
    r = disk_open(identity, &disk->count);
    if (r == DIAG_OK) disk->read = disk_read;
    return r;
}
