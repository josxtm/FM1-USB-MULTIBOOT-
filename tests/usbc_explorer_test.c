/* SPDX-License-Identifier: MIT */
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "usbc_explorer.h"
#include "embedded_keys.h"

static struct usbc_explorer explorer;
static unsigned reads, decoded;
static int io_failure;
static FILE *image;
static u8 rgb[240][240][3];
static unsigned scanline;
static const char *fault;

static enum diag_result read_block(void *unused, u32 lba, u8 *out)
{
    (void)unused;
    ++reads;
    if (io_failure) return DIAG_DISCONNECTED;
    if (fseek(image, (long)lba * 512, SEEK_SET) || fread(out, 1, 512, image) != 512) return DIAG_TRANSFER;
    if (!strcmp(fault, "bad-fat") && lba == 0) out[13] = 3;
    if (!strcmp(fault, "exfat")) {
        if (lba == 0) memcpy(out + 3, "EXFAT   ", 8);
        /* Remove BPB shape so this models an exFAT sector. */
        if (lba == 0) out[11] = out[12] = 0;
    }
    return DIAG_OK;
}

void usbc_mock_reader(struct block_device source);

static void visit(void *expected, u32 index, const struct cat2_record *row)
{
    assert(index == decoded);
    ++decoded;
    if (*(u32 *)expected > 2) {
        assert(strstr(row->name, "\"α\" 🎵"));
        assert(!strcmp(row->author, "Autora") && !strcmp(row->project, "test/project"));
    }
    if (row->size == 400123 || (strchr(row->path + 3, '/') && index != 0))
        assert(cat2_verify(&explorer.catalog, row) == DIAG_OK);
}

static void row_window(void *p, u32 x, u32 y, u32 w, u32 h)
{
    (void)p;
    assert(!x && y < 240 && w == 240 && h == 1);
    scanline = y;
}

static void row_pixels(void *p, const u8 *bytes, u32 n)
{
    (void)p;
    assert(n == 480);
    for (unsigned i = 0; i < n; i += 2) {
        const u32 color = (bytes[i] << 8) | bytes[i+1];
        rgb[scanline][i/2][0] = (u8)((color >> 11) * 255 / 31);
        rgb[scanline][i/2][1] = (u8)(((color >> 5) & 63) * 255 / 63);
        rgb[scanline][i/2][2] = (u8)((color & 31) * 255 / 31);
    }
}

static void end(void *p) { (void)p; }
static void press(struct mb_key_adapter *keys, u32 key, u32 *time)
{
    enum mb_event event;
    (void)mb_keys_poll(keys, key, *time += 50, &event);
    assert(mb_keys_poll(keys, key, *time += 50, &event));
    usbc_event(&explorer, event);
    (void)mb_keys_poll(keys, 0, *time += 50, &event);
    (void)mb_keys_poll(keys, 0, *time += 50, &event);
}

int main(int argc, char **argv)
{
    assert(argc == 4 || argc == 5);
    image = fopen(argv[1], "rb");
    assert(image);
    assert(!fseek(image, 0, SEEK_END));
    long length = ftell(image);
    assert(length > 0 && !(length % 512));
    struct block_device block = {0, (u32)(length / 512), read_block};
    u32 expected = (u32)strtoul(argv[2], 0, 10);
    fault = argv[3];
    struct usb_device identity;
    struct block_device disk;
    usbc_mock_reader(block);
    assert(usbc_connect(&identity, &disk, USB_POWER_QUALIFIED) == DIAG_OK);
    assert(identity.vid == 0x1234 && identity.pid == 0x5678 && disk.count == block.count);
    usbc_mount(&explorer, disk, DIAG_OK);
    assert(explorer.transport == DIAG_OK && explorer.sectors_detected == block.count);
    if (!strcmp(fault, "tie") || !strcmp(fault, "missing-index")) {
        assert(explorer.media == (!strcmp(fault, "tie") ? DIAG_FAT : DIAG_END));
    } else if (!strcmp(fault, "bad-fat")) {
        assert(explorer.media == DIAG_FAT);
    } else if (!strcmp(fault, "exfat")) {
        assert(explorer.media == DIAG_EXFAT);
    } else {
        assert(explorer.media == DIAG_OK && explorer.count == expected);
        assert(explorer.fat_status == DIAG_OK);
        assert(explorer.version == (!strcmp(fault, "v1") ? 1 : 2));
        if (!strcmp(fault, "index")) assert(explorer.catalog.recovered && explorer.catalog.index.sequence == 4294967297ull);
        if (!strcmp(fault, "page")) {
            assert(cat2_page(&explorer.catalog, 1, 0, 0) == DIAG_FAT);
        } else if (explorer.version == 2) {
            for (u32 i = 0; i * 32 < expected; ++i)
                assert(cat2_page(&explorer.catalog, i, visit, &expected) == DIAG_OK);
            assert(decoded == expected);
        }
        struct mb_key_adapter keys;
        u32 time = 0;
        mb_keys_init(&keys);
        if (expected > 32 && strcmp(fault, "page")) {
            const unsigned initial = reads;
            press(&keys, MB_KEY_NEXT, &time);
            assert(reads == initial && explorer.current == 1);
            press(&keys, MB_KEY_PREVIOUS, &time);
            press(&keys, MB_KEY_PREVIOUS, &time);
            assert(explorer.current == expected - 1 && explorer.media == DIAG_OK);
            press(&keys, MB_KEY_NEXT, &time);
            assert(explorer.current == 0);
        }
        if (expected && strcmp(fault, "page")) {
            press(&keys, MB_KEY_PLAY, &time);
            assert(explorer.detail && !explorer.checked);
            press(&keys, MB_KEY_PLAY, &time);
            assert(explorer.checked);
            assert(explorer.integrity == (!strcmp(fault, "file") ? DIAG_FAT :
                !strcmp(fault, "missing") ? DIAG_END : DIAG_OK));
            struct mb_lcd_list_renderer lcd;
            mb_lcd_list_renderer_init(&lcd, (struct mb_lcd_sink){0, row_window, row_pixels, end});
            usbc_draw(&explorer, &lcd);
            if (argc == 5) {
                FILE *ppm = fopen(argv[4], "wb");
                assert(ppm);
                fprintf(ppm, "P6\n240 240\n255\n");
                assert(fwrite(rgb, sizeof rgb, 1, ppm) == 1);
                fclose(ppm);
            }
            io_failure = 1;
            press(&keys, MB_KEY_PLAY, &time);
            assert(explorer.integrity == DIAG_DISCONNECTED);
            assert(explorer.media == DIAG_DISCONNECTED);
            assert(explorer.transport == DIAG_DISCONNECTED);
            assert(explorer.fat_status == DIAG_DISCONNECTED);
            assert(explorer.count == 0 && explorer.version == 0);
            assert(explorer.sectors_detected == 0);
            for (unsigned k = 0; k < USB_EXPLORER_ROWS; ++k)
                assert(explorer.labels[k][0] == 0);
            usbc_media_lost(&explorer); /* idempotent on repeat disconnect */
            assert(explorer.media == DIAG_DISCONNECTED);
            struct mb_lcd_list_renderer diagnostic;
            mb_lcd_list_renderer_init(&diagnostic,
                (struct mb_lcd_sink){0, row_window, row_pixels, end});
            usbc_draw_diag(&explorer, &identity, &diagnostic);
            press(&keys, MB_KEY_REC, &time);
            assert(!explorer.detail);
            press(&keys, MB_KEY_REC, &time);
            assert(explorer.exit);
        }
    }
    printf("fixture=%s expected=%u decoded=%u sector_reads=%u model_ram=%zu\n",
           fault, expected, decoded, reads, sizeof explorer);
    fclose(image);
}
