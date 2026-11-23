/* SPDX-License-Identifier: MIT */
#include "usbc_explorer.h"
#include "disp.h"

static u32 ustrlen(const char *s)
{
    u32 n = 0;
    while (s[n]) ++n;
    return n;
}

static void append(char *buf, u32 *n, const char *s, u32 cap)
{
    while (*s && *n + 1 < cap) buf[(*n)++] = *s++;
    buf[*n] = 0;
}
static void decimal(char *buf, u32 *n, u32 value, u32 cap)
{
    char reversed[10], number[11];
    u32 len = 0;
    do { reversed[len++] = (char)('0' + value % 10); value /= 10; } while (value);
    for (u32 i = 0; i < len; ++i) number[i] = reversed[len - i - 1];
    number[len] = 0;
    append(buf, n, number, cap);
}

static void display_slice(char *out, u32 size, const char *text, u32 skip)
{
    u32 n = 0;
    while (*text && n + 1 < size) {
        u8 c = (u8)*text++;
        if ((c & 0xc0) == 0x80) continue;
        if (skip) { --skip; continue; }
        out[n++] = c >= 32 && c < 127 ? (char)c : (c >= 128 ? '?' : ' ');
    }
    out[n] = 0;
}

static void display_row(void *context, u32 rank, const struct cat2_record *item)
{
    struct usbc_explorer *e = context;
    if (rank >= e->window && rank - e->window < USB_EXPLORER_ROWS) {
        char *out = e->labels[rank - e->window];
        u32 n = 0;
        char version[10], name[19];
        display_slice(version, sizeof version, item->version, 0);
        display_slice(name, 19u - (u32)ustrlen(version), item->name, 0);
        append(out, &n, name, 20);
        append(out, &n, " ", 20);
        append(out, &n, version, 20);
    }
    if (rank == e->current) e->item = *item;
}

static enum diag_result load_window(struct usbc_explorer *e)
{
    if (!e->count) return DIAG_OK;
    const u32 page = e->current / CAT2_PAGE;
    e->window = page * CAT2_PAGE + ((e->current % CAT2_PAGE) / USB_EXPLORER_ROWS) * USB_EXPLORER_ROWS;
    for (u32 i = 0; i < USB_EXPLORER_ROWS; ++i) e->labels[i][0] = 0;
    if (e->version == 2) return cat2_page(&e->catalog, page, display_row, e);
    for (u32 i = e->window; i < e->count && i - e->window < USB_EXPLORER_ROWS; ++i) {
        const struct mb_entry *old = &e->legacy.catalog.entries[i];
        struct cat2_record *record = &e->catalog.row;
        __builtin_memset(record, 0, sizeof *record);
        u32 n = 0;
        append(record->name, &n, old->label, sizeof record->name); n = 0;
        append(record->version, &n, old->version, sizeof record->version); n = 0;
        append(record->sha, &n, old->sha256, sizeof record->sha); n = 0;
        append(record->path, &n, "FW/", sizeof record->path);
        append(record->path, &n, old->file, sizeof record->path); n = 0;
        const u32 len = (u32)ustrlen(old->file);
        append(record->format, &n, old->file + len - 3, sizeof record->format);
        record->size = old->size;
        display_row(e, i, record);
    }
    return DIAG_OK;
}

void usbc_mount(struct usbc_explorer *e, struct block_device disk, enum diag_result result)
{
    e->media = result;
    e->count = e->current = e->window = 0;
    e->version = e->detail = e->checked = e->exit = e->name_page = 0;
    if (result != DIAG_OK) return;
    e->media = fat32_mount_explorer(&e->fs, disk);
    if (e->media != DIAG_OK) return;
    e->media = cat2_open(&e->catalog, &e->fs);
    if (e->media == DIAG_OK) {
        e->version = 2;
        e->count = e->catalog.index.count;
    } else if (e->media == DIAG_END) {
        mb_fat32_storage_init(&e->legacy_storage, &e->fs);
        mb_init(&e->legacy, mb_fat32_storage_hal(&e->legacy_storage), (struct mb_renderer){0});
        enum mb_io_result r = mb_load_catalog(&e->legacy);
        if (r == MB_IO_OK) {
            e->version = 1;
            e->count = e->legacy.catalog.count;
            e->media = DIAG_OK;
        } else if (r == MB_IO_DISCONNECTED) e->media = DIAG_DISCONNECTED;
        else if (r == MB_IO_TIMEOUT) e->media = DIAG_TIMEOUT;
        else if (r != MB_IO_MISSING) e->media = DIAG_FAT;
    }
    if (e->media == DIAG_OK) {
        e->catalog.fs = &e->fs;
        e->media = load_window(e);
    }
}

void usbc_event(struct usbc_explorer *e, enum mb_event event)
{
    if (event == MB_EVENT_REC) {
        if (e->detail) { e->detail = 0; e->checked = 0; }
        else e->exit = 1;
    } else if (e->media == DIAG_OK && e->count) {
        if (event == MB_EVENT_PLAY) {
            if (!e->detail) {
                e->media = load_window(e);
                e->detail = e->media == DIAG_OK;
                e->name_page = 0;
            } else {
                e->integrity = cat2_verify(&e->catalog, &e->item);
                e->checked = 1;
                if (e->integrity == DIAG_DISCONNECTED) e->media = DIAG_DISCONNECTED;
            }
        } else if (event == MB_EVENT_NEXT || event == MB_EVENT_PREVIOUS) {
            if (e->detail) {
                if (event == MB_EVENT_PREVIOUS && e->name_page) --e->name_page;
                if (event == MB_EVENT_NEXT) {
                    char more[2];
                    display_slice(more, sizeof more, e->item.name, ((u32)e->name_page + 1) * 60);
                    if (!*more) display_slice(more, sizeof more, e->item.version, ((u32)e->name_page + 1) * 20);
                    if (!*more) display_slice(more, sizeof more, e->item.author, ((u32)e->name_page + 1) * 20);
                    if (*more) ++e->name_page;
                }
                return;
            }
            e->current = (e->current + e->count + (event == MB_EVENT_NEXT ? 1u : e->count - 1u)) % e->count;
            if (e->current < e->window || e->current >= e->window + USB_EXPLORER_ROWS ||
                e->current / CAT2_PAGE != e->window / CAT2_PAGE)
                e->media = load_window(e);
        }
    }
}

static const char *media_text(enum diag_result r)
{
    if (r == DIAG_END) return "SIN CATALOGO FM1";
    if (r == DIAG_FAT) return "FAT32/CATALOGO ERROR";
    return diag_error(r);
}

void usbc_draw(const struct usbc_explorer *e, struct mb_lcd_list_renderer *lcd)
{
    char text[21];
    mb_lcd_list_row(lcd, 0, "FM1 MULTIBOOT", C_WHITE);
    mb_lcd_list_row(lcd, 1, e->version == 2
        ? (e->catalog.recovered ? "V2 INDICE RESPALDO" : "MICROSD FAT32 V2")
        : e->version == 1 ? "MICROSD FAT32 V1" : "USB-C SOLO LECTURA", C_GREY);
    for (u32 i = 2; i < 15; ++i) mb_lcd_list_row(lcd, i, "", C_WHITE);
    if (e->media != DIAG_OK) {
        mb_lcd_list_row(lcd, 4, media_text(e->media), C_RED);
    } else if (!e->count) {
        mb_lcd_list_row(lcd, 4, "CATALOGO VACIO", C_WHITE);
    } else if (e->detail) {
        for (u32 i = 0; i < 3; ++i) {
            display_slice(text, sizeof text, e->item.name, e->name_page * 60u + 20u * i);
            mb_lcd_list_row(lcd, 3 + i, text, C_WHITE);
        }
        display_slice(text, sizeof text, e->item.version, e->name_page * 20u);
        mb_lcd_list_row(lcd, 6, text, C_GREY);
        display_slice(text, sizeof text, e->item.author, e->name_page * 20u);
        mb_lcd_list_row(lcd, 7, text, C_GREY);
        u32 n = 0;
        append(text, &n, e->item.format, sizeof text);
        append(text, &n, " ", sizeof text);
        decimal(text, &n, e->item.size, sizeof text);
        append(text, &n, " B", sizeof text);
        mb_lcd_list_row(lcd, 8, text, C_WHITE);
        mb_lcd_list_row(lcd, 9, "COMPAT: SIN VALIDAR", C_YELLOW);
        mb_lcd_list_row(lcd, 11, !e->checked ? "SHA-256 PENDIENTE" : e->integrity == DIAG_OK ? "SHA-256 CORRECTO" :
            e->integrity == DIAG_END ? "ARCHIVO AUSENTE" : e->integrity == DIAG_FAT ? "ARCHIVO CORRUPTO" :
            diag_error(e->integrity), e->checked && e->integrity != DIAG_OK ? C_RED : C_GREEN);
        mb_lcd_list_row(lcd, 13, "PLAY: VERIFICAR", C_WHITE);
    } else {
        u32 n = 0;
        decimal(text, &n, e->current + 1, sizeof text);
        append(text, &n, "/", sizeof text);
        decimal(text, &n, e->count, sizeof text);
        mb_lcd_list_row(lcd, 2, text, C_GREY);
        for (u32 i = 0; i < USB_EXPLORER_ROWS; ++i) {
            n = 0;
            const int current = e->window + i == e->current;
            append(text, &n, current ? ">" : " ", sizeof text);
            append(text, &n, e->labels[i], sizeof text);
            mb_lcd_list_row(lcd, 3 + i, text, current ? C_GREEN : C_WHITE);
        }
        mb_lcd_list_row(lcd, 13, "OCT+- PLAY: FICHA", C_WHITE);
    }
    mb_lcd_list_row(lcd, 14, e->detail ? "REC: VOLVER" : "REC: SALIR", C_GREY);
}
