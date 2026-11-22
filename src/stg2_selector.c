/* SPDX-License-Identifier: MIT */
#include "stg2_selector.h"
#include "disp.h"

void stg2_selector_mount(struct stg2_selector *s, struct block_device disk, enum diag_result card)
{
    s->detail = 0;
    s->notice = 0;
    mb_fat32_storage_init(&s->storage, &s->fs);
    mb_init(&s->core, mb_fat32_storage_hal(&s->storage), (struct mb_renderer){0});
    s->media = card == DIAG_OK ? fat32_mount(&s->fs, disk) : card;
    s->index = s->media == DIAG_OK ? mb_load_catalog(&s->core) : MB_IO_MISSING;
    /* The core can hold partially parsed entries after a malformed index. */
    if (s->index != MB_IO_OK) s->core.catalog.count = 0;
}

enum stg2_action stg2_selector_event(struct stg2_selector *s, enum mb_event event)
{
    if (event == MB_EVENT_REC || event == MB_EVENT_CANCEL) {
        if (!s->detail) return STG2_EXIT;
        s->detail = 0;
        s->notice = 0;
        s->core.state = MB_BROWSE;
        return STG2_REDRAW;
    }
    if (event == MB_EVENT_PLAY) {
        if (!mb_selected(&s->core)) return STG2_RELOAD;
        if (s->detail) return STG2_VERIFY;
        mb_event(&s->core, event);
        s->detail = 1;
        s->notice = 0;
        return STG2_REDRAW;
    }
    if (s->detail || !s->core.catalog.count) return STG2_NO_CHANGE;
    mb_event(&s->core, event);
    return STG2_REDRAW;
}

void stg2_selector_verify(struct stg2_selector *s)
{
    if (!s->detail || !mb_selected(&s->core)) return;
    enum mb_io_result result = mb_verify_selected(&s->core);
    (void)mb_prepare_launch(&s->core);
    if (result == MB_IO_OK && s->core.state == MB_READY_FOR_SIMULATION) s->notice = "SHA-256 CORRECTO";
    else if (result == MB_IO_MISSING) s->notice = "ARCHIVO AUSENTE";
    else if (result == MB_IO_DISCONNECTED) s->notice = "SD DESCONECTADA";
    else if (result == MB_IO_TIMEOUT) s->notice = "TIEMPO AGOTADO";
    else s->notice = "ARCHIVO ILEGIBLE";
    if (result == MB_IO_OK && s->core.state == MB_INCOMPATIBLE) s->notice = "SHA-256 INCORRECTO";
}

static const char *media_label(const struct stg2_selector *s)
{
    switch (s->media) {
    case DIAG_OK:
        if (s->index == MB_IO_OK) return "SD SPI1 / FAT32";
        if (s->index == MB_IO_MISSING) return "FALTA INDEX.CSV";
        if (s->index == MB_IO_DISCONNECTED) return "SD DESCONECTADA";
        return "INDICE INVALIDO";
    case DIAG_DISCONNECTED: return "SIN TARJETA SPI";
    case DIAG_FAT: return "FAT32 INVALIDA";
    case DIAG_UNSUPPORTED: return "SDHC/SDXC REQUERIDA";
    case DIAG_TIMEOUT: return "TIEMPO AGOTADO";
    default: return "ERROR AL LEER SD";
    }
}

static void listing(const struct stg2_selector *s, struct mb_lcd_list_renderer *lcd)
{
    const u32 selected = s->core.selected;
    const u32 count = s->core.catalog.count;
    const u32 top = selected / 7u * 7u;
    for (u32 i = 0; i < 7; ++i) {
        char label[21] = {0};
        if (top + i < count) {
            const char *name = s->core.catalog.entries[top + i].label;
            label[0] = top + i == selected ? '>' : ' ';
            label[1] = ' ';
            for (u32 n = 0; n < 18 && name[n]; ++n) label[n + 2] = name[n];
        }
        mb_lcd_list_row(lcd, i + 2, label, top + i == selected ? C_GREEN : C_WHITE);
    }
    mb_lcd_list_row(lcd, 9, !count && s->index == MB_IO_OK ? "BIBLIOTECA VACIA" : "", C_GREY);
    mb_lcd_list_row(lcd, 10, count ? "OCT +/- NAVEGAR" : "", C_GREY);
    mb_lcd_list_row(lcd, 11, count ? "PLAY: INFO" : "PLAY: REINTENTAR", C_WHITE);
    mb_lcd_list_row(lcd, 12, "REC: SALIR", C_WHITE);
}

static void information(const struct stg2_selector *s, struct mb_lcd_list_renderer *lcd,
                        const struct mb_entry *entry)
{
    char label[21] = {0};
    u32 n = 0;
    while (n < 20 && entry->label[n]) { label[n] = entry->label[n]; ++n; }
    mb_lcd_list_row(lcd, 2, label, C_GREEN);
    mb_lcd_list_row(lcd, 3, n == 20 ? entry->label + n : "", C_GREEN);
    mb_lcd_list_row(lcd, 4, entry->version, C_WHITE);
    mb_lcd_list_row(lcd, 5, entry->file, C_WHITE);
    char byte_label[21] = "BYTES: ";
    char digits[10];
    u32 count = 0, size = entry->size;
    do { digits[count++] = (char)('0' + size % 10u); size /= 10u; } while (size);
    for (u32 i = 0; i < count; ++i) byte_label[7 + i] = digits[count - 1 - i];
    mb_lcd_list_row(lcd, 6, byte_label, C_WHITE);
    mb_lcd_list_row(lcd, 7, "COMPAT NO VALIDADA", C_YELLOW);
    mb_lcd_list_row(lcd, 8, s->notice ? s->notice : "SHA-256 SIN VERIF.",
                    entry->integrity_verified ? C_GREEN : C_GREY);
    mb_lcd_list_row(lcd, 9, "", C_WHITE);
    mb_lcd_list_row(lcd, 10, "", C_WHITE);
    mb_lcd_list_row(lcd, 11, "PLAY: VERIFICAR", C_WHITE);
    mb_lcd_list_row(lcd, 12, "REC: VOLVER", C_WHITE);
}

void stg2_selector_draw(const struct stg2_selector *s, struct mb_lcd_list_renderer *lcd)
{
    mb_lcd_list_row(lcd, 0, "FM1 MULTIBOOT", C_GREEN);
    mb_lcd_list_row(lcd, 1, media_label(s), C_GREY);
    const struct mb_entry *entry = mb_selected(&s->core);
    if (s->detail && entry) information(s, lcd, entry);
    else listing(s, lcd);
    mb_lcd_list_row(lcd, 13, "", C_WHITE);
    mb_lcd_list_row(lcd, 14, "FLASH BLOQUEADA", C_YELLOW);
}
