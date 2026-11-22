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

static enum diag_result open_file(struct mb_fat32_storage *storage, const char *path, u32 offset)
{
    enum diag_result r;
    storage->path[0] = 0;
    if (!same_path(path, "FM1/INDEX.CSV")) {
        const char *prefix = "FM1/FW/";
        unsigned i = 0;
        while (prefix[i] && path[i] == prefix[i]) ++i;
        if (prefix[i] || !path[i] || path[i] == '.' || path[i] == '/' || path[i] == '\\') return DIAG_END;
        r = fat32_open_fm1_file(storage->fs, path + i, &storage->file);
    } else {
        r = fat32_open_fm1_index(storage->fs, &storage->file);
    }
    if (r != DIAG_OK) return r;
    r = fat32_seek(storage->fs, &storage->file, offset, &storage->cursor);
    if (r == DIAG_OK) {
        /* Both accepted paths were validated by the FAT 8.3 helpers. */
        u32 i = 0;
        while (path[i] && i + 1 < sizeof storage->path) {
            storage->path[i] = path[i];
            ++i;
        }
        storage->path[i] = 0;
    }
    return r;
}

static enum mb_io_result read_file(void *context, const char *path, uint32_t offset,
                                   uint8_t *out, uint32_t capacity, uint32_t *got)
{
    struct mb_fat32_storage *storage = context;
    if (!got) return MB_IO_ERROR;
    *got = 0;
    if (!storage || !storage->fs || !path || !capacity || !out) return MB_IO_ERROR;
    if (!offset || offset != storage->cursor.offset || !same_path(path, storage->path)) {
        enum diag_result r = open_file(storage, path, offset);
        if (r != DIAG_OK) return r == DIAG_END ? MB_IO_MISSING : translate(r);
    }
    if (offset == storage->file.size) {
        enum diag_result r = fat32_check_eof(storage->fs, &storage->file, &storage->cursor);
        storage->path[0] = 0;
        return r == DIAG_OK ? MB_IO_EOF : translate(r);
    }
    uint32_t available = storage->file.size - offset;
    if (capacity > available) capacity = available;
    enum diag_result r = fat32_read_next(storage->fs, &storage->file, &storage->cursor, out, capacity, got);
    if (r != DIAG_OK) {
        storage->path[0] = 0;
        return translate(r);
    }
    return *got ? MB_IO_OK : MB_IO_EOF;
}

static int same_path(const char *a, const char *b)
{
    while (*a && *a == *b) { ++a; ++b; }
    return *a == *b;
}

void mb_fat32_storage_init(struct mb_fat32_storage *storage, struct fat32 *fs)
{
    if (storage) *storage = (struct mb_fat32_storage){.fs = fs};
}

struct mb_storage mb_fat32_storage_hal(struct mb_fat32_storage *storage)
{
    struct mb_storage result = { storage, read_file };
    return result;
}
