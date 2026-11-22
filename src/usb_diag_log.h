/* SPDX-License-Identifier: MIT
 * Compact, transport-independent USB diagnostic event log. */
#ifndef USB_DIAG_LOG_H
#define USB_DIAG_LOG_H
#include "mbl.h"
#define USB_DIAG_LOG_CAPACITY 32u
struct usb_diag_event { u8 code; u8 detail; };
struct usb_diag_log { struct usb_diag_event events[USB_DIAG_LOG_CAPACITY]; u8 count; u8 dropped; };
enum usb_diag_code {
    USB_DIAG_BOOT=1, USB_DIAG_ATTACH, USB_DIAG_RESET, USB_DIAG_ENUM,
    USB_DIAG_ENDPOINTS, USB_DIAG_BOT, USB_DIAG_CAPACITY, USB_DIAG_READ,
    USB_DIAG_FAT32, USB_DIAG_DISCONNECT, USB_DIAG_TIMEOUT, USB_DIAG_ERROR
};
void usb_diag_log_init(struct usb_diag_log *log);
void usb_diag_log_event(struct usb_diag_log *log, u8 code, u8 detail);
const char *usb_diag_code_name(u8 code);
#endif
