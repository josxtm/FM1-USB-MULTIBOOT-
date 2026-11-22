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

static void no_begin(void *p) { (void)p; }
static void no_card(void *p, const struct mb_entry *e, int side, int focus) { (void)p; (void)e; (void)side; (void)focus; }
static void no_text(void *p, const char *s) { (void)p; (void)s; }
static void no_end(void *p) { (void)p; }

int main(int argc, char **argv)
{
    const char *path = argc > 1 ? argv[1] : "build/fat32-test.img";
    const char *fault = argc > 2 ? argv[2] : "";
    unsigned expected = argc > 3 ? (unsigned)atoi(argv[3]) : 3u;
    FILE *file = fopen(path, "rb"); assert(file);
    assert(!fseek(file, 0, SEEK_END)); long length = ftell(file); assert(length > 0 && length % 512 == 0);
    assert(!fseek(file, 0, SEEK_SET)); unsigned char *bytes = malloc((size_t)length); assert(bytes);
    assert(fread(bytes, 1, (size_t)length, file) == (size_t)length); fclose(file);
    struct image image = { bytes, (uint32_t)(length / 512), 0, "" };
    struct block_device disk = { &image, image.sectors, block_read };
    struct fat32 fs; assert(fat32_mount(&fs, disk) == DIAG_OK);
    image.fault = fault; image.reads = 0;
    struct mb_fat32_storage fat_storage; mb_fat32_storage_init(&fat_storage, &fs);
    struct mb_storage storage = mb_fat32_storage_hal(&fat_storage);
    struct mb_core core;
    struct mb_renderer renderer = { 0, no_begin, no_card, no_text, no_end };
    mb_init(&core, storage, renderer);
    enum mb_io_result loaded = mb_load_catalog(&core);
    printf("MOUNT FAT32 sectors=%u reads=%u\n", image.sectors, image.reads);
    if (strcmp(fault, "")) {
        assert(loaded != MB_IO_OK);
        printf("FAULT %s result=%d state=%s\n", fault, loaded, mb_state_name(core.state));
        free(bytes); return 0;
    }
    assert(loaded == MB_IO_OK && core.catalog.count == expected);
    if (!expected) { assert(core.state == MB_EMPTY); free(bytes); return 0; }
    if (expected > 1) mb_event(&core, MB_EVENT_NEXT);
    mb_event(&core, MB_EVENT_PLAY); assert(core.state == MB_INSPECT); mb_event(&core, MB_EVENT_REC);
    enum mb_io_result verified = mb_verify_selected(&core);
    assert(verified == MB_IO_OK && core.state == MB_READY_FOR_SIMULATION);
    assert(mb_prepare_launch(&core) == MB_LAUNCH_BLOCKED);
    printf("CATALOG count=%u selected=%s path=FM1/FW/%s size=%u integrity=%u state=%s launch=BLOCKED\n",
           core.catalog.count, mb_selected(&core)->id, mb_selected(&core)->file,
           mb_selected(&core)->size, mb_selected(&core)->integrity_verified, mb_state_name(core.state));
    free(bytes); return 0;
}
