#include "embedded_fat32_storage.h"
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

struct image { unsigned char *bytes; uint32_t sectors, reads; const char *fault; };
static enum diag_result block_read(void *context, u32 lba, u8 *out)
{
    struct image *image = context;
    ++image->reads;
    if (image->fault && !strcmp(image->fault, "disconnect") && image->reads > 0) return DIAG_DISCONNECTED;
    if (image->fault && !strcmp(image->fault, "timeout") && image->reads > 0) return DIAG_TIMEOUT;
    if (lba >= image->sectors) return DIAG_RANGE;
    memcpy(out, image->bytes + (size_t)lba * 512u, 512u);
    return DIAG_OK;
}

struct render_context { FILE *file; unsigned cards; };
static void xml(FILE *file, const char *text) { for (; *text; ++text) { if (*text == '&') fputs("&amp;", file); else if (*text == '<') fputs("&lt;", file); else if (*text == '>') fputs("&gt;", file); else if (*text == '"') fputs("&quot;", file); else if (*text == '\'') fputs("&apos;", file); else fputc(*text, file); } }
static void render_begin(void *p) { struct render_context *r = p; r->cards = 0; fputs("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"240\" height=\"240\" viewBox=\"0 0 240 240\"><rect width=\"240\" height=\"240\" fill=\"#08131a\"/><style>text{font-family:monospace;fill:#65e8cd}</style>", r->file); }
static void render_card(void *p, const struct mb_entry *e, int side, int focus) { struct render_context *r = p; int x = focus ? 70 : (side < 0 ? 12 : 158), w = focus ? 100 : 70; ++r->cards; fprintf(r->file, "<rect x=\"%d\" y=\"45\" width=\"%d\" height=\"105\" rx=\"8\" fill=\"%s\" stroke=\"#65e8cd\"/><text x=\"%d\" y=\"100\" text-anchor=\"middle\">", x, w, focus ? "#183841" : "#10242a", x + w / 2); xml(r->file, e->label); fputs("</text><text x=\"120\" y=\"120\" text-anchor=\"middle\">", r->file); xml(r->file, e->version); fputs("</text>", r->file); }
static void render_text(void *p, const char *s) { struct render_context *r = p; fputs("<text x=\"120\" y=\"205\" text-anchor=\"middle\">", r->file); xml(r->file, s); fputs(" · PLAY INSPECT ONLY</text>", r->file); }
static void render_end(void *p) { struct render_context *r = p; fputs("</svg>\n", r->file); }
static void no_begin(void *p) { (void)p; }
static void no_card(void *p, const struct mb_entry *e, int side, int focus) { (void)p; (void)e; (void)side; (void)focus; }
static void no_text(void *p, const char *s) { (void)p; (void)s; }
static void no_end(void *p) { (void)p; }

int main(int argc, char **argv)
{
    const char *path = argc > 1 ? argv[1] : "build/fat32-test.img";
    const char *fault = argc > 2 ? argv[2] : "";
    unsigned expected = argc > 3 ? (unsigned)atoi(argv[3]) : 3u;
    const char *svg_path = argc > 4 ? argv[4] : 0;
    FILE *file = fopen(path, "rb"); assert(file);
    assert(!fseek(file, 0, SEEK_END)); long length = ftell(file); assert(length > 0 && length % 512 == 0);
    assert(!fseek(file, 0, SEEK_SET)); unsigned char *bytes = malloc((size_t)length); assert(bytes);
    assert(fread(bytes, 1, (size_t)length, file) == (size_t)length); fclose(file);
    struct image image = { bytes, (uint32_t)(length / 512), 0, "" };
    if (!strcmp(fault, "bad-bpb")) { bytes[11] = 0; bytes[12] = 0; }
    if (!strcmp(fault, "bad-volume")) { bytes[32] = 0xff; bytes[33] = 0xff; bytes[34] = 0xff; bytes[35] = 0xff; }
    struct block_device disk = { &image, image.sectors, block_read };
    struct fat32 fs; enum diag_result mounted = fat32_mount(&fs, disk);
    if (!strcmp(fault, "bad-bpb") || !strcmp(fault, "bad-volume")) { printf("CORRUPT %s result=%d\n", fault, mounted); assert(mounted != DIAG_OK); free(bytes); return 0; }
    assert(mounted == DIAG_OK);
    image.fault = fault; image.reads = 0;
    struct mb_fat32_storage fat_storage; mb_fat32_storage_init(&fat_storage, &fs);
    struct mb_storage storage = mb_fat32_storage_hal(&fat_storage);
    struct mb_core core;
    struct render_context render = { 0, 0 };
    FILE *svg = svg_path ? fopen(svg_path, "w") : 0;
    if (svg_path) assert(svg);
    render.file = svg;
    struct mb_renderer renderer = svg ? (struct mb_renderer){ &render, render_begin, render_card, render_text, render_end } : (struct mb_renderer){ 0, no_begin, no_card, no_text, no_end };
    mb_init(&core, storage, renderer);
    enum mb_io_result loaded = mb_load_catalog(&core);
    printf("MOUNT FAT32 sectors=%u reads=%u\n", image.sectors, image.reads);
    int verify_fault = !strcmp(fault, "cycle") || !strcmp(fault, "truncated") || !strcmp(fault, "hash");
    if (strcmp(fault, "") && !verify_fault) {
        assert(loaded != MB_IO_OK);
        printf("FAULT %s result=%d state=%s\n", fault, loaded, mb_state_name(core.state));
        if (svg) fclose(svg);
        free(bytes);
        return 0;
    }
    assert(loaded == MB_IO_OK && core.catalog.count == expected);
    if (!strcmp(fault, "hash")) core.catalog.entries[1].sha256[0] = core.catalog.entries[1].sha256[0] == '0' ? '1' : '0';
    if (!strcmp(fault, "cycle") || !strcmp(fault, "truncated")) {
        struct fat_file damaged; assert(fat32_open_fm1_file(&fs, core.catalog.entries[1].file, &damaged) == DIAG_OK);
        uint32_t value = !strcmp(fault, "cycle") ? damaged.cluster : 0x0fffffffu;
        memcpy(bytes + (size_t)32 * 512u + damaged.cluster * 4u, &value, sizeof value);
    }
    if (verify_fault) {
        if (core.catalog.count > 1) mb_event(&core, MB_EVENT_NEXT);
        enum mb_io_result result = mb_verify_selected(&core);
        printf("VERIFY FAULT %s result=%d state=%s hash=%s\n", fault, result, mb_state_name(core.state), core.catalog.entries[1].sha256);
        assert(core.state == MB_INCOMPATIBLE && !core.catalog.entries[1].integrity_verified);
        printf("FAULT %s result=%d state=%s\n", fault, result, mb_state_name(core.state));
        if (svg) fclose(svg);
        free(bytes);
        return 0;
    }
    uint8_t probe[4]; uint32_t probe_got = 0;
    assert(storage.read(storage.context, "FM1/FW/MISSING.BIN", 0, probe, sizeof probe, &probe_got) == MB_IO_MISSING);
    struct fat_file index_file; assert(fat32_open_fm1_index(&fs, &index_file) == DIAG_OK);
    assert(storage.read(storage.context, "FM1/INDEX.CSV", index_file.size, probe, 1, &probe_got) == MB_IO_EOF);
    assert(storage.read(storage.context, "FM1/INDEX.CSV", index_file.size + 1, probe, 1, &probe_got) == MB_IO_ERROR);
    if (!expected) { assert(core.state == MB_EMPTY); mb_render(&core); if (svg) fclose(svg); free(bytes); return 0; }
    if (expected > 1) mb_event(&core, MB_EVENT_NEXT);
    mb_event(&core, MB_EVENT_PLAY); assert(core.state == MB_INSPECT); mb_event(&core, MB_EVENT_REC);
    enum mb_io_result verified = mb_verify_selected(&core);
    assert(verified == MB_IO_OK && core.state == MB_READY_FOR_SIMULATION);
    assert(mb_prepare_launch(&core) == MB_LAUNCH_BLOCKED);
    mb_render(&core); if (svg) fclose(svg);
    printf("CATALOG count=%u selected=%s path=FM1/FW/%s size=%u integrity=%u state=%s launch=BLOCKED cards=%u sector_reads=%u\n",
           core.catalog.count, mb_selected(&core)->id, mb_selected(&core)->file,
           mb_selected(&core)->size, mb_selected(&core)->integrity_verified, mb_state_name(core.state), render.cards, image.reads);
    free(bytes); return 0;
}
