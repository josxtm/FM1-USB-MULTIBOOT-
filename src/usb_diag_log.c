/* SPDX-License-Identifier: MIT
 * Fixed-size USB diagnostic event log; no I/O and no dynamic allocation. */
#include "usb_diag_log.h"
void usb_diag_log_init(struct usb_diag_log *log)
{
    if (!log) return;
    log->count = 0;
    log->dropped = 0;
}
void usb_diag_log_event(struct usb_diag_log *log, u8 code, u8 detail)
{
    if (!log || !code) return;
    if (log->count >= USB_DIAG_LOG_CAPACITY) { log->dropped = 1; return; }
    log->events[log->count].code = code;
    log->events[log->count].detail = detail;
    ++log->count;
}
const char *usb_diag_code_name(u8 code)
{
    static const char *const names[] = {
        "INVALID", "BOOT", "ATTACH", "RESET", "ENUM", "ENDPOINTS",
        "BOT", "CAPACITY", "READ", "FAT32", "DISCONNECT", "TIMEOUT", "ERROR"
    };
    return code < ARRAY_LEN(names) ? names[code] : "UNKNOWN";
}
