/* SPDX-License-Identifier: MIT */
#include "embedded_fat32_storage.h"

static int same_path(const char *, const char *);

static enum mb_io_result translate(enum diag_result r)
{
    if (r == DIAG_OK) return MB_IO_OK;
    if (r == DIAG_END) return MB_IO_EOF;
    if (r == DIAG_DISCONNECTED) return MB_IO_DISCONNECTED;
    if (r == DIAG_TIMEOUT) return MB_IO_TIMEOUT;
    if (r == DIAG_RANGE) return MB_IO_ERROR;
    return MB_IO_ERROR;
}

static enum mb_io_result read_file(void *context, const char *path, uint32_t offset,
                                   uint8_t *out, uint32_t capacity, uint32_t *got)
{
    struct mb_fat32_storage *storage = context;
    struct fat_file file;
    enum diag_result r;
    *got = 0;
    if (!storage || !storage->fs || !path || !out || !capacity) return MB_IO_ERROR;
    if (!same_path(path, "FM1/INDEX.CSV")) {
        const char *prefix = "FM1/FW/";
        unsigned i = 0;
        while (prefix[i] && path[i] == prefix[i]) ++i;
        if (prefix[i] || !path[i] || path[i] == '.' || path[i] == '/' || path[i] == '\\') return MB_IO_MISSING;
        r = fat32_open_fm1_file(storage->fs, path + i, &file);
    } else {
        r = fat32_open_fm1_index(storage->fs, &file);
    }
    if (r != DIAG_OK) return r == DIAG_END ? MB_IO_EOF : translate(r);
    if (offset > file.size) return MB_IO_ERROR;
    if (offset == file.size) return MB_IO_EOF;
    uint32_t available = file.size - offset;
    if (capacity > available) capacity = available;
    r = fat32_read_at(storage->fs, &file, offset, out, capacity, got);
    if (r != DIAG_OK) return translate(r);
    return *got ? MB_IO_OK : MB_IO_EOF;
}

static int same_path(const char *a, const char *b)
{
    while (*a && *a == *b) { ++a; ++b; }
    return *a == *b;
}

void mb_fat32_storage_init(struct mb_fat32_storage *storage, struct fat32 *fs)
{
    if (storage) storage->fs = fs;
}

struct mb_storage mb_fat32_storage_hal(struct mb_fat32_storage *storage)
{
    struct mb_storage result = { storage, read_file };
    return result;
}
