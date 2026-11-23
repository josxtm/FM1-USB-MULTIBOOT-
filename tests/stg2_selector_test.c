/* SPDX-License-Identifier: MIT */
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "stg2_sd.h"
#include "stg2_selector.h"

struct mock {
    FILE *image;
    u8 frame[6], answer[516];
    u32 frame_at, answer_at, answer_len, reads, corrupt_lba;
    unsigned wait;
    int selected, card, bus_lost, data_lost, bad_crc, oversized_index;
    int package, missing_file;
};

static void reply(struct mock *m, u8 b)
{
    assert(m->answer_len < sizeof m->answer);
    m->answer[m->answer_len++] = b;
}

static void data_reply(struct mock *m, const u8 *data, u32 size)
{
    u16 crc = 0;
    reply(m, 0xfe);
    for (u32 i = 0; i < size; ++i) {
        reply(m, data[i]);
        crc ^= (u16)data[i] << 8;
        for (u32 bit = 0; bit < 8; ++bit)
            crc = (u16)((crc & 0x8000u) ? (crc << 1) ^ 0x1021 : crc << 1);
    }
    reply(m, (u8)(crc >> 8));
    reply(m, (u8)(crc ^ (m->bad_crc ? 1u : 0u)));
}

static void command_reply(struct mock *m)
{
    u8 cmd = m->frame[0] & 63;
    u32 argument = bd32(m->frame + 1);
    unsigned char bytes[512];
    m->answer_at = m->answer_len = 0;
    switch (cmd) {
    case 0:
        assert(!argument && m->frame[5] == 0x95);
        reply(m, 1);
        break;
    case 8:
        assert(argument == 0x1aa && m->frame[5] == 0x87);
        reply(m, 1); reply(m, 0); reply(m, 0); reply(m, 1); reply(m, 0xaa);
        break;
    case 55:
        assert(!argument);
        reply(m, 1);
        break;
    case 41:
        assert(argument == 1u << 30);
        reply(m, m->wait == 2 ? 0 : 1);
        break;
    case 58:
        assert(!argument);
        reply(m, 0); reply(m, 0xc0); reply(m, 0xff); reply(m, 0x80); reply(m, 0);
        break;
    case 9:
        assert(!argument);
        memset(bytes, 0, 16);
        bytes[0] = 0x40;
        bytes[9] = m->package ? 69 : 65;
        reply(m, 0);
        data_reply(m, bytes, 16);
        break;
    case 17:
        ++m->reads;
        assert(fseek(m->image, (long)argument * 512L, SEEK_SET) == 0);
        assert(fread(bytes, 1, 512, m->image) == 512);
        if (m->oversized_index)
            for (u32 i = 0; i < 512; i += 32)
                if (!memcmp(bytes + i, "INDEX   CSV", 11)) st32(bytes + i + 28, MB_MAX_CATALOG + 1u);
        if (m->missing_file)
            for (u32 i = 0; i < 512; i += 32)
                if (!memcmp(bytes + i, "DEMOB   BIN", 11)) bytes[i] = 0xe5;
        if (argument == m->corrupt_lba) bytes[argument == 0 ? 510 : 0] ^= 1;
        reply(m, 0);
        if (m->data_lost) {
            reply(m, 0xfe);
            reply(m, bytes[0]);
        } else data_reply(m, bytes, 512);
        break;
    default:
        assert(!"unexpected or writing SD command");
    }
}

static int mock_exchange(void *context, u8 out, u8 *in)
{
    struct mock *m = context;
    *in = 0xff;
    if (m->bus_lost || (m->data_lost && m->answer_at == 3 && m->answer_len == 3)) return 0;
    if (!m->card || !m->selected) return 1;
    if (m->answer_at < m->answer_len) {
        *in = m->answer[m->answer_at++];
        return 1;
    }
    if (!m->frame_at && (out & 0xc0) != 0x40) return 1;
    if (m->frame_at < sizeof m->frame) {
        m->frame[m->frame_at++] = out;
        if (m->frame_at == sizeof m->frame) command_reply(m);
    }
    return 1;
}

static void mock_select(void *context, int selected)
{
    struct mock *m = context;
    m->selected = selected;
    m->frame_at = m->answer_at = m->answer_len = 0;
}

static void mock_wait(void *context) { ++((struct mock *)context)->wait; }

struct screen { u32 y, rows; u8 pixels[240u * 240u * 2u]; };

static void window(void *context, u32 x, u32 y, u32 w, u32 h)
{
    struct screen *s = context;
    assert(!x && y < 240 && w == 240 && h == 1);
    s->y = y;
}

static void pixels(void *context, const u8 *pixels, u32 length)
{
    struct screen *s = context;
    assert(length == 480);
    memcpy(s->pixels + s->y * length, pixels, length);
    ++s->rows;
}

static void finish(void *context) { (void)context; }

static void capture(struct screen *s, struct stg2_selector *selector, int save)
{
    struct mb_lcd_list_renderer lcd;
    mb_lcd_list_renderer_init(&lcd, (struct mb_lcd_sink){s, window, pixels, finish});
    s->rows = 0;
    stg2_selector_draw(selector, &lcd);
    assert(s->rows == 240);
    if (!save) return;
    FILE *file = fopen("build/stg2-selector.ppm", "wb");
    assert(file);
    assert(fprintf(file, "P6\n240 240\n255\n") > 0);
    for (u32 i = 0; i < sizeof s->pixels; i += 2) {
        u16 c = bd16(s->pixels + i);
        fputc(c >> 8 & 248, file); fputc(c >> 3 & 252, file); fputc(c << 3 & 248, file);
    }
    assert(!fclose(file));
}

int main(int argc, char **argv)
{
    assert(argc == 2 || (argc == 3 && (!strcmp(argv[2], "--package") || !strcmp(argv[2], "--empty"))));
    int empty = argc == 3 && !strcmp(argv[2], "--empty");
    struct mock mock = {.image = fopen(argv[1], "rb"), .corrupt_lba = 0xffffffffu, .package = argc == 3};
    assert(mock.image);
    struct stg2_sd sd = {{&mock, mock_exchange, mock_select, mock_wait}, 0, 0};
    struct block_device disk = {&sd, 0, stg2_sd_read};
    static struct stg2_selector selector;
    static struct screen screen;
    enum diag_result status = stg2_sd_start(&sd);
    assert(status == DIAG_DISCONNECTED && !sd.sectors && !mock.selected);
    stg2_selector_mount(&selector, disk, status);
    assert(stg2_selector_event(&selector, MB_EVENT_PLAY) == STG2_RELOAD);
    assert(stg2_selector_event(&selector, MB_EVENT_REC) == STG2_EXIT);
    capture(&screen, &selector, 0);

    mock.card = 1;
    mock.bus_lost = 1;
    assert(stg2_sd_start(&sd) == DIAG_TIMEOUT && !mock.selected);
    mock.bus_lost = 0;
    assert(stg2_sd_start(&sd) == DIAG_OK && !mock.selected && sd.sectors == (mock.package ? 71680u : 67584u));
    disk.count = sd.sectors;
    assert(stg2_sd_read(&sd, sd.sectors, selector.fs.sector) == DIAG_RANGE);
    stg2_selector_mount(&selector, disk, DIAG_OK);
    assert(selector.index == MB_IO_OK && selector.core.catalog.count == (empty ? 0u : mock.package ? 2u : 12u));
    if (empty) {
        assert(stg2_selector_event(&selector, MB_EVENT_PLAY) == STG2_RELOAD);
        assert(stg2_selector_event(&selector, MB_EVENT_REC) == STG2_EXIT);
        assert(!fclose(mock.image));
        puts("STG2 accepted the empty 6D catalog and returned safely");
        return 0;
    }
    for (u32 i = 0; i < (mock.package ? 1u : 8u); ++i)
        assert(stg2_selector_event(&selector, MB_EVENT_NEXT) == STG2_REDRAW);
    assert(!strcmp(mb_selected(&selector.core)->id, mock.package ? "DEMOB" : "F09"));
    capture(&screen, &selector, !mock.package);
    assert(stg2_selector_event(&selector, MB_EVENT_PLAY) == STG2_REDRAW && selector.detail);
    assert(stg2_selector_event(&selector, MB_EVENT_PLAY) == STG2_VERIFY);
    stg2_selector_verify(&selector);
    assert(!strcmp(selector.notice, "SHA-256 CORRECTO"));
    assert(mb_prepare_launch(&selector.core) == MB_LAUNCH_BLOCKED);

    struct fat_file file;
    assert(fat32_open_fm1_file(&selector.fs, mock.package ? "DEMOB.BIN" : "F09.BIN", &file) == DIAG_OK);
    if (mock.package) {
        mock.missing_file = 1;
        stg2_selector_verify(&selector);
        assert(!strcmp(selector.notice, "ARCHIVO AUSENTE"));
        mock.missing_file = 0;
    }
    mock.corrupt_lba = selector.fs.data_lba + (file.cluster - 2) * selector.fs.sectors_per_cluster;
    stg2_selector_verify(&selector);
    assert(!strcmp(selector.notice, "SHA-256 INCORRECTO"));
    mock.corrupt_lba = 0xffffffffu;
    mock.bad_crc = 1;
    stg2_selector_verify(&selector);
    assert(!strcmp(selector.notice, "ARCHIVO ILEGIBLE") && !mock.selected);
    mock.bad_crc = 0;
    mock.data_lost = 1;
    stg2_selector_verify(&selector);
    assert(!strcmp(selector.notice, "TIEMPO AGOTADO") && !mock.selected);
    mock.data_lost = 0;
    mock.card = 0;
    stg2_selector_verify(&selector);
    assert(!strcmp(selector.notice, "SD DESCONECTADA") && !mock.selected);
    assert(stg2_selector_event(&selector, MB_EVENT_REC) == STG2_REDRAW);
    assert(stg2_selector_event(&selector, MB_EVENT_REC) == STG2_EXIT);
    mock.card = 1;
    mock.corrupt_lba = 0;
    stg2_selector_mount(&selector, disk, DIAG_OK);
    assert(selector.media == DIAG_FAT && !selector.core.catalog.count);
    mock.corrupt_lba = 0xffffffffu;
    mock.oversized_index = 1;
    stg2_selector_mount(&selector, disk, DIAG_OK);
    assert(selector.media == DIAG_OK && selector.index == MB_IO_ERROR && !selector.core.catalog.count);
    assert(!fclose(mock.image));
    printf("STG2 SPI simulated: %u catalog entries, scrolling, SHA-256, safe actions, %u reads, card faults OK\n",
           mock.package ? 2u : 12u, mock.reads);
    return 0;
}
