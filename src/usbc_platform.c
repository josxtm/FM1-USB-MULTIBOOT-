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

#ifdef FM1_USBC_LAB
#include "usbc_lab.h"
/*
 * This image is a SEPARATE laboratory artifact; regular readonly APP1 keeps
 * USB_POWER_UNKNOWN and never initializes a host. Physical CC, VBUS and
 * backfeed protection are an EXTERNAL qualification. No register proves it.
 */
static void lab_screen(struct mb_lcd_list_renderer *display, const char *message)
{
    for (u32 i = 0; i < 15; ++i) mb_lcd_list_row(display, i, "", C_WHITE);
    mb_lcd_list_row(display, 0, "FM1 USB-C LAB", C_GREEN);
    mb_lcd_list_row(display, 2, "RAM APP1 / READ ONLY", C_WHITE);
    mb_lcd_list_row(display, 4, "DESCONEXION ANDROID", C_YELLOW);
    mb_lcd_list_row(display, 5, "ADAPTADOR SD EXTERNO", C_YELLOW);
    mb_lcd_list_row(display, 6, "VBUS/CC VALIDAR ANTES", C_YELLOW);
    mb_lcd_list_row(display, 8, "OCT+ Y PLAY: 2 SEG", C_WHITE);
    mb_lcd_list_row(display, 9, "REC: CANCELAR", C_GREY);
    mb_lcd_list_row(display, 11, message, C_GREEN);
    mb_lcd_list_row(display, 14, "FLASH: NO WRITES", C_YELLOW);
}

static int wait_for_lab_operator(struct rom_diag_state *clock_state,
                                 struct mb_lcd_list_renderer *display)
{
    struct usbc_lab_gate gate;
    usbc_lab_init(&gate, clock_state->clock.now_ms);
    enum usbc_lab_mode previous = gate.mode;
    lab_screen(display, "LIBERA LAS TECLAS");
    for (u32 polls = 0; polls < 9000 && lcd_operational(); ++polls) {
        delay_us(7000);
        u32 raw = plat_menu_scan() | (plat_boot_keys() & K_REC);
        rom_diag_input_poll(clock_state, tb_now(), raw);
        if (clock_state->result != ROM_DIAG_ACTIVE) return 0;
        int arming = usbc_lab_poll(&gate, clock_state->clock.now_ms, raw);
        if (arming == 1) {
            /* Don't activate the PHY while either key is still depressed.
             * User may remove the Android cable and connect their qualified reader. */
            lab_screen(display, "SUELTA OCT+ / PLAY");
            for (u32 j = 0; j < 700 && lcd_operational(); ++j) {
                delay_us(7000);
                raw = plat_menu_scan() | (plat_boot_keys() & K_REC);
                rom_diag_input_poll(clock_state, tb_now(), raw);
                if (clock_state->result != ROM_DIAG_ACTIVE || (raw & K_REC)) return 0;
                if (!raw) return 1;
            }
            return 0;
        }
        if (arming < 0) return 0;
        if (gate.mode != previous) {
            previous = gate.mode;
            lab_screen(display, gate.mode == USBC_LAB_HOLDING ? "MANTEN OCT+ + PLAY" :
                gate.mode == USBC_LAB_WAIT_CHORD ? "PREPARADO PARA ARMAR" : "LIBERA LAS TECLAS");
        }
    }
    return 0;
}

static void explorer_session_lab(struct rom_diag_state *time,
                                  struct mb_lcd_list_renderer *display)
{
    struct usb_device identity = {0};
    struct block_device disk = {0};
    if (!wait_for_lab_operator(time, display)) {
        lab_screen(display, "CANCELADO / SIN USB");
        return;
    }
    lab_screen(display, "BUSCANDO LECTOR USB");
    /*
     * This call intentionally requests HOST only in the separately qualified
     * laboratory build after an explicit physical key chord. The core API
     * still rejects unknown/absent power and never supplies 5V itself.
     */
    enum diag_result result = usbc_connect(&identity, &disk, USB_POWER_QUALIFIED);
    usbc_mount(&explorer, disk, result);
    int diagnostic = 1;
    usbc_draw_diag(&explorer, &identity, display);
    struct mb_key_adapter keys;
    mb_keys_init(&keys);
    u32 chord_ms = 0;
    int chord_started = 0;
    for (u32 polls = 0; polls < 18000 && !explorer.exit && lcd_operational(); ++polls) {
        delay_us(7000);
        const u32 tick = tb_now();
        const u32 raw = plat_menu_scan() | (plat_boot_keys() & K_REC);
        rom_diag_input_poll(time, tick, raw);
        if (time->result != ROM_DIAG_ACTIVE) break;
        /* OCT- + PLAY toggles the diagnostic panel; no accidental file action. */
        if (raw == (K_PREV | K_SEL)) {
            if (!chord_started) {
                chord_started = 1;
                chord_ms = time->clock.now_ms;
            } else if (time->clock.now_ms - chord_ms >= 900u) {
                diagnostic = !diagnostic;
                if (diagnostic) usbc_draw_diag(&explorer, &identity, display);
                else usbc_draw(&explorer, display);
                chord_started = 0;
                chord_ms = time->clock.now_ms + 60000u;
            }
            enum mb_event ignored;
            (void)mb_keys_poll(&keys, 0, time->clock.now_ms, &ignored);
            continue;
        }
        chord_started = 0;
        enum mb_event event;
        if (!mb_keys_poll(&keys, raw, time->clock.now_ms, &event)) continue;
        if (diagnostic) {
            if (event == MB_EVENT_REC) explorer.exit = 1;
            else if (event == MB_EVENT_PLAY) {
                diagnostic = 0;
                usbc_draw(&explorer, display);
            }
        } else {
            usbc_event(&explorer, event);
            usbc_draw(&explorer, display);
        }
    }
}

void fm1_usbc_explorer_main(u32 arg)
{
    for (u8 *p = rom_diag_bss_start; p < rom_diag_bss_end; ++p) *p = 0;
    if (rom_diag_prepare(arg) == ROM_DIAG_ACTIVE && lcd_init(BRD_FM1)) {
        mb_lcd_list_renderer_init(&panel, mb_lcd_physical_sink());
        struct rom_diag_state time;
        rom_diag_input_init(&time, tb_now(), plat_menu_scan() | (plat_boot_keys() & K_REC));
        explorer_session_lab(&time, &panel);
        plat_keys_off();
        lcd_end();
    }
    usb_disable();
    WR(TMR0, 0x4000u);
    csync();
}
#else
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

#endif /* FM1_USBC_LAB */
