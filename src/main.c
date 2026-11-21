/* SPDX-License-Identifier: MIT */
#include "hw.h"
#include "keys.h"
#include "lcd.h"
#include "storage.h"
#include "usbio.h"
#include "glyphs.h"

#define PAGES 3u
#define PREVIEW 80u
extern u8 diag_bss_start[], diag_bss_end[];
static struct usb_device reader;
static struct fat32 filesystem;
static struct fat_dir directory;
static struct fat_file file;
static u8 preview[PREVIEW];
static u8 pixels[240 * 2] __attribute__((aligned(4)));
static u32 file_index, preview_size, blocks;
static enum diag_result usb_result, disk_result, filesystem_result, file_result;

void plat_delay_ms(u32 ms) { delay_us(1000u * ms); }

static u32 glyph(char c)
{
    if (c >= 'a' && c <= 'z') c -= 'a' - 'A';
    for (u32 i = 0; characters[i]; ++i)
        if (characters[i] == c) return i;
    return 8; /* '?' */
}

static void line(u32 row, u16 color, const char *text)
{
    u8 codes[20] = {0};
    for (u32 col = 0; col < 20 && text[col]; ++col) codes[col] = (u8)glyph(text[col]);
    lcd_window(0, 16u * row, 240, 16);
    for (u32 y = 0; y < 16; ++y) {
        for (u32 x = 0; x < 240; ++x) {
            const u32 col = x % 12u / 2u;
            const u16 c = col < 5 && (glyphs[codes[x / 12u]][col] & (1u << (y / 2u))) ? color : C_BLACK;
            pixels[2u * x] = (u8)(c >> 8);
            pixels[2u * x + 1] = (u8)c;
        }
        lcd_pixels(pixels, sizeof pixels);
    }
    lcd_end();
}

static void number(u32 row, const char *label, u32 value, unsigned hex_digits)
{
    static const char hex[] = "0123456789ABCDEF";
    char text[21], reverse[10];
    u32 end = 0, len = 0;
    while (*label && end < 10) text[end++] = *label++;
    if (hex_digits) {
        while (hex_digits) {
            --hex_digits;
            text[end++] = hex[(value >> (4u * hex_digits)) & 15u];
        }
    } else {
        do {
            reverse[len++] = hex[value % 10u];
            value /= 10;
        } while (value);
        while (len) text[end++] = reverse[--len];
    }
    text[end] = 0;
    line(row, C_WHITE, text);
}

static void header(const char *title)
{
    line(0, C_GREEN, "FM1 USB DIAG / RAM");
    line(1, C_WHITE, title);
    for (u32 y = 2; y < 15; ++y) line(y, C_BLACK, "");
}

static void read_file(void)
{
    preview_size = 0;
    if (file_index >= 40) { file_result = DIAG_LIMIT; return; }
    file_result = fat32_next(&filesystem, &directory, &file);
    if (file_result != DIAG_OK) return;
    ++file_index;
    file_result = fat32_read_prefix(&filesystem, &file, preview, sizeof preview, &preview_size);
}

static void scan(void)
{
    usb_disable();
    husb_init();
    reader = (struct usb_device){0};
    blocks = file_index = preview_size = 0;
    filesystem.sectors_per_cluster = 0;
    disk_result = filesystem_result = file_result = DIAG_UNSUPPORTED;
    header("BUSCANDO LECTOR");
    line(3, C_YELLOW, "ESPERA USB");
    usb_result = husb_wait_attach(2500) ? usb_enumerate(&reader) : DIAG_TIMEOUT;
    if (usb_result != DIAG_OK) return;
    disk_result = disk_open(&reader, &blocks);
    if (disk_result != DIAG_OK) return;
    filesystem_result = fat32_mount(&filesystem, (struct block_device){0, blocks, disk_read});
    if (filesystem_result != DIAG_OK) return;
    fat32_root(&filesystem, &directory);
    read_file();
}

static void draw(u32 page)
{
    if (page == 0) {
        header("USB / INTERFAZ");
        line(3, usb_result == DIAG_OK ? C_GREEN : C_YELLOW, diag_error(usb_result));
        number(5, "VID: ", reader.vid, 4);
        number(6, "PID: ", reader.pid, 4);
        number(7, "DISP CL: ", reader.device_class, 2);
        number(8, "DISP SUB: ", reader.device_subclass, 2);
        number(9, "INTF CL: ", reader.class_code, 2);
        number(10, "INTF SUB: ", reader.subclass, 2);
        number(11, "PROTO: ", reader.protocol, 2);
        if (usb_result != DIAG_OK) number(12, "SIE: ", husb_dbg[3], 8);
        line(13, C_YELLOW, "PLAY: BUSCAR USB");
    } else if (page == 1) {
        header("DISCO / FAT32");
        line(3, C_WHITE, "VBUS CONF EXTERNA");
        line(5, C_WHITE, diag_error(disk_result));
        line(6, C_WHITE, diag_error(filesystem_result));
        number(8, "BLOQUES: ", blocks, 0);
        number(9, "SECT/CL: ", filesystem.sectors_per_cluster, 0);
    } else {
        header("ARCHIVOS RAIZ");
        line(3, file_result == DIAG_OK ? C_GREEN : C_YELLOW, diag_error(file_result));
        if (file_result == DIAG_OK) {
            line(4, C_WHITE, file.name);
            number(5, "BYTES: ", file.size, 0);
            for (u32 row = 0; row < 4; ++row) {
                char text[21];
                u32 len = 0;
                while (len < 20 && 20u * row + len < preview_size) {
                    const u8 c = preview[20u * row + len];
                    text[len++] = c >= 32 && c < 127 ? (char)c : '.';
                }
                text[len] = 0;
                line(7 + row, C_WHITE, text);
            }
        }
        line(13, C_YELLOW, "PLAY: SIG ARCHIVO");
    }
    line(14, C_YELLOW, "OCT-/+ PAG  REC FIN");
}

/* Accept only the ROM download-mode calling convention. All state is RAM.
 * Exit leaves USB passive and waits for the physical power switch. */
void diag_main(u32 rom_arg)
{
    if (rom_arg < 0x01000000u || !timer_start()) return;
    for (u8 *at = diag_bss_start; at < diag_bss_end; ++at) *at = 0;
    p33_and(P33_WDOG, 0xef);
    usb_disable();
    if (!lcd_init(BRD_FM1)) return;
    header("VBUS SIN VERIFICAR");
    line(3, C_YELLOW, "RETIRA LA TABLET");
    line(5, C_WHITE, "MIDE VBUS EN LECTOR");
    line(6, C_WHITE, "USA AISLAMIENTO");
    line(8, C_WHITE, "PLAY 2S: CONFIRMA");
    line(10, C_YELLOW, "REC: SALIR");

    u32 started = tb_now();
    int pressed = 0, released = 0;
    for (;;) {
        const u32 key = plat_boot_keys();
        if (!key) released = 1;
        if (released && (key & K_REC)) goto end;
        if (!released || key != K_SEL) pressed = 0;
        else if (!pressed) { started = tb_now(); pressed = 1; }
        else if (tb_ms_since(started) >= 2000u) break;
        plat_delay_ms(10);
    }

    scan();
    u32 page = 0, previous = K_SEL;
    draw(page);
    for (;;) {
        const u32 keys = plat_menu_scan() | (plat_boot_keys() & K_REC);
        const u32 down = keys & ~previous;
        previous = keys;
        if (down & K_REC) break;
        if (down & K_PREV) page = (page + PAGES - 1u) % PAGES;
        else if (down & K_NEXT) page = (page + 1u) % PAGES;
        else if ((down & K_SEL) && page == 0) scan();
        else if ((down & K_SEL) && page == 2 && filesystem_result == DIAG_OK &&
                 file_result != DIAG_END && file_result != DIAG_LIMIT) read_file();
        else continue;
        draw(page);
    }

end:
    usb_disable();
    plat_keys_off();
    header("DIAGNOSTICO CERRADO");
    line(4, C_YELLOW, "RETIRA EL USB");
    line(5, C_WHITE, "APAGA Y ENCIENDE");
}
