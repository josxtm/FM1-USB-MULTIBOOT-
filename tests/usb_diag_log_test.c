/* SPDX-License-Identifier: MIT */
#include <assert.h>
#include <string.h>
#include "usb_diag_log.h"
int main(void)
{
    struct usb_diag_log log; usb_diag_log_init(&log);
    assert(log.count == 0 && log.dropped == 0);
    for (u8 i = 0; i < USB_DIAG_LOG_CAPACITY + 2u; ++i) usb_diag_log_event(&log, USB_DIAG_READ, i);
    assert(log.count == USB_DIAG_LOG_CAPACITY && log.dropped == 1);
    assert(log.events[0].detail == 0 && log.events[31].detail == 31);
    assert(strcmp(usb_diag_code_name(USB_DIAG_CAPACITY), "CAPACITY") == 0);
    usb_diag_log_event(0, USB_DIAG_BOOT, 0);
    usb_diag_log_init(0);
    return 0;
}
