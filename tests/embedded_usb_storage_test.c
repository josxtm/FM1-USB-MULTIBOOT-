/* SPDX-License-Identifier: MIT */
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "embedded_usb_msc_storage.h"
#include "embedded_multiboot.h"
struct disk { FILE *f; u32 count; };
/* Physical entry points are deliberately inert in this host-only mount test. */
enum diag_result usb_enumerate(struct usb_device *d) { (void)d; return DIAG_DISCONNECTED; }
enum diag_result disk_open(const struct usb_device *d, u32 *n) { (void)d; (void)n; return DIAG_DISCONNECTED; }
enum diag_result disk_read(void *p, u32 lba, u8 *out) { (void)p; (void)lba; (void)out; return DIAG_DISCONNECTED; }
const char *diag_error(enum diag_result r) { (void)r; return "stub"; }
static enum diag_result read_sector(void *p, u32 lba, u8 *out)
{
    struct disk *d = p;
    if (lba >= d->count || fseek(d->f, (long)lba * 512L, SEEK_SET) != 0) return DIAG_RANGE;
    return fread(out, 1, 512, d->f) == 512 ? DIAG_OK : DIAG_TRANSFER;
}
static void noop(void *p) { (void)p; }
static void card(void *p, const struct mb_entry *e, int side, int focus)
{ (void)p; (void)e; (void)side; (void)focus; }
static void text(void *p, const char *s) { (void)p; (void)s; }
int main(int argc, char **argv)
{
    FILE *f; long size; struct disk d; struct mb_usb_msc_storage usb; struct mb_core core;
    struct mb_renderer r = {0, noop, card, text, noop};
    assert(argc == 2); f = fopen(argv[1], "rb"); assert(f); assert(!fseek(f, 0, SEEK_END));
    size = ftell(f); assert(size > 0 && size % 512 == 0); rewind(f);
    d = (struct disk){f, (u32)(size / 512)}; mb_usb_msc_storage_init(&usb);
    assert(mb_usb_msc_storage_mount(&usb, (struct block_device){&d, d.count, read_sector}) == DIAG_OK);
    mb_init(&core, mb_usb_msc_storage_hal(&usb), r);
    assert(mb_load_catalog(&core) == MB_IO_OK && core.catalog.count == 3);
    assert(mb_verify_selected(&core) == MB_IO_OK);
    assert(mb_prepare_launch(&core) == MB_LAUNCH_BLOCKED);
    fclose(f); puts("USB adapter -> FAT32 -> FM1CAT -> core: OK (physical USB not initialized)"); return 0;
}
