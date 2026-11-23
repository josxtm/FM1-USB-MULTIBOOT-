/* SPDX-License-Identifier: MIT */
#include "usbc_explorer.h"
#include "rom_diag.h"
#include "keys.h"
#include "lcd.h"
#include "hw.h"
#include "usbio.h"

extern u8 rom_diag_bss_start[], rom_diag_bss_end[];
static struct usbc_explorer explorer;
static struct mb_lcd_list_renderer panel;

void fm1_usbc_explorer_main(u32 arg)
{
    for (u8 *p = rom_diag_bss_start; p < rom_diag_bss_end; ++p) *p = 0;
    if (rom_diag_prepare(arg) == ROM_DIAG_ACTIVE) {
        if (lcd_init(BRD_FM1)) {
            mb_lcd_list_renderer_init(&panel, mb_lcd_physical_sink());
            struct usb_device identity;
            struct block_device disk;
            /* There is no board-qualified source of CC/VBUS authorization.
             * In production this call can only take the no-drive branch. */
            enum diag_result result = usbc_connect(&identity, &disk, USB_POWER_UNKNOWN);
            usbc_mount(&explorer, disk, result);
            usbc_draw(&explorer, &panel);
            struct mb_key_adapter keys;
            struct rom_diag_state time;
            mb_keys_init(&keys);
            rom_diag_input_init(&time, tb_now(), 0);
            for (u32 polls = 0; polls < 18000 && !explorer.exit && lcd_operational(); ++polls) {
                delay_us(7000);
                const u32 tick = tb_now();
                rom_diag_input_poll(&time, tick, 0);
                if (time.result != ROM_DIAG_ACTIVE) break;
                const u32 raw = plat_menu_scan() | (plat_boot_keys() & K_REC);
                enum mb_event event;
                if (mb_keys_poll(&keys, raw, time.clock.now_ms, &event)) {
                    usbc_event(&explorer, event);
                    usbc_draw(&explorer, &panel);
                }
            }
            plat_keys_off();
            lcd_end();
        }
        usb_disable();
        WR(TMR0, 0x4000u);
        csync();
    }
}
