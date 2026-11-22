#include "embedded_multiboot.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
struct fixture { FILE *file; long length; };
static enum mb_io_result read_fixture(void *context, const char *path, uint32_t offset, uint8_t *out, uint32_t cap, uint32_t *got)
{
    struct fixture *f = context; if (strcmp(path, "FM1/INDEX.CSV")) return MB_IO_MISSING;
    if ((long)offset >= f->length) { *got = 0; return MB_IO_EOF; }
    if (fseek(f->file, (long)offset, SEEK_SET)) return MB_IO_ERROR;
    size_t n = fread(out, 1, cap, f->file); *got = (uint32_t)n;
    return n ? MB_IO_OK : MB_IO_ERROR;
}
static int check(const char *name, int valid)
{
    char path[128]; snprintf(path, sizeof path, "tests/fixtures/fm1cat/%s", name);
    struct fixture f = { fopen(path, "rb"), 0 }; assert(f.file); assert(!fseek(f.file, 0, SEEK_END)); f.length = ftell(f.file); rewind(f.file);
    struct mb_core c; mb_init(&c, (struct mb_storage){&f, read_fixture}, (struct mb_renderer){0});
    enum mb_io_result r = mb_load_catalog(&c); fclose(f.file);
    assert((r == MB_IO_OK) == valid); return 0;
}
int main(void)
{
    check("valid-empty.csv", 1); check("valid-one.csv", 1); check("valid-crlf.csv", 1);
    check("invalid-path.csv", 0); check("invalid-duplicate.csv", 0); check("invalid-label.csv", 0);
    check("invalid-version.csv", 0); check("invalid-no-final-lf.csv", 0);
    puts("FM1CAT shared C fixtures: accepted/rejected matrix: OK"); return 0;
}
