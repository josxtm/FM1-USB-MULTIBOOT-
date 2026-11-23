/* SPDX-License-Identifier: MIT */
#include "fm1cat_v2.h"

struct reader {
    struct fat32 *fs;
    struct fat_file file;
    struct fat_cursor cursor;
    u8 block[512];
    u32 pos, end;
    enum diag_result result;
    struct sha *digest;
};
struct parser {
    struct reader *stream;
    const char *memory;
    int ch, record, ended;
};

static int eq(const char *a, const char *b)
{
    while (*a && *a == *b) { ++a; ++b; }
    return *a == *b;
}

static int hex(int c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    return -1;
}

static char upper(char c) { return c >= 'a' && c <= 'z' ? (char)(c - 'a' + 'A') : c; }
static char lower(char c) { return c >= 'A' && c <= 'Z' ? (char)(c - 'A' + 'a') : c; }

static int hex_string(const char *s, u32 n)
{
    for (u32 i = 0; i < n; ++i) if (hex(s[i]) < 0) return 0;
    return !s[n];
}

static enum diag_result start_read(struct reader *r, struct fat32 *fs, const char *path, u32 limit)
{
    *r = (struct reader){ .fs = fs };
    enum diag_result result = fat32_open_fm1_path(fs, path, &r->file);
    if (result != DIAG_OK) return result;
    if (!r->file.size || r->file.size > limit) return DIAG_LIMIT;
    return fat32_seek(fs, &r->file, 0, &r->cursor);
}

static int get(struct reader *r)
{
    if (r->result != DIAG_OK) return -1;
    if (r->pos == r->end) {
        u32 remaining = r->file.size - r->cursor.offset;
        if (!remaining) {
            r->result = fat32_check_eof(r->fs, &r->file, &r->cursor);
            return -1;
        }
        if (remaining > sizeof r->block) remaining = sizeof r->block;
        r->result = fat32_read_next(r->fs, &r->file, &r->cursor, r->block, remaining, &r->end);
        if (r->result != DIAG_OK) return -1;
        r->pos = 0;
    }
    if (r->digest) shadd(r->digest, &r->block[r->pos], 1);
    return r->block[r->pos++];
}

static enum diag_result parse_result(const struct reader *r)
{
    return r->result == DIAG_OK ? DIAG_FAT : r->result;
}

static int line(struct reader *r, char *out, u32 cap)
{
    for (u32 i = 0; i < cap; ++i) {
        int ch = get(r);
        if (ch < 0 || ch == 0) return 0;
        if (ch == '\n') { out[i] = 0; return 1; }
        if (i + 1 == cap) return 0;
        out[i] = (char)ch;
    }
    return 0;
}

static void advance(struct parser *p)
{
    if (p->ended) { p->ch = -1; return; }
    p->ch = p->stream ? get(p->stream) : (*p->memory ? (u8)*p->memory++ : -1);
    if (p->record && p->ch == '\n') { p->ended = 1; p->ch = -1; }
}

static void ws(struct parser *p)
{
    while (p->ch == ' ' || p->ch == '\t' || p->ch == '\r' || p->ch == '\n') advance(p);
}

static int consume(struct parser *p, int c)
{
    ws(p);
    if (p->ch != c) return 0;
    advance(p);
    return 1;
}

static int emit(char *out, u32 cap, u32 *n, int byte)
{
    if (!byte || *n + 1 >= cap) return 0;
    if (out) out[*n] = (char)byte;
    ++*n;
    return 1;
}

static int utf8(char *out, u32 cap, u32 *n, u32 value)
{
    if (value < 0x80) return emit(out, cap, n, value);
    if (value < 0x800) return emit(out, cap, n, 0xc0 | (value >> 6)) && emit(out, cap, n, 0x80 | (value & 63));
    if (value >= 0xd800 && value <= 0xdfff) return 0;
    if (value >= 0x10000 && !emit(out, cap, n, 0xf0 | (value >> 18))) return 0;
    return emit(out, cap, n, (value < 0x10000 ? 0xe0 : 0x80) | ((value >> 12) & 63)) &&
        emit(out, cap, n, 0x80 | ((value >> 6) & 63)) && emit(out, cap, n, 0x80 | (value & 63));
}

static int unicode_escape(struct parser *p)
{
    int value = 0;
    for (unsigned i = 0; i < 4; ++i) {
        int digit = hex(p->ch);
        if (digit < 0 && p->ch >= 'A' && p->ch <= 'F') digit = p->ch - 'A' + 10;
        if (digit < 0) return -1;
        value = value * 16 + digit;
        advance(p);
    }
    return value;
}

static int string(struct parser *p, char *out, u32 cap)
{
    if (!consume(p, '"')) return 0;
    u32 n = 0;
    while (p->ch != '"') {
        int ch = p->ch;
        if (ch < 32) return 0;
        advance(p);
        if (ch == '\\') {
            ch = p->ch;
            advance(p);
            if (ch == 'u') {
                int value = unicode_escape(p);
                if (value >= 0xd800 && value <= 0xdbff) {
                    if (p->ch != '\\') return 0;
                    advance(p);
                    if (p->ch != 'u') return 0;
                    advance(p);
                    int low = unicode_escape(p);
                    if (low < 0xdc00 || low > 0xdfff) return 0;
                    value = 0x10000 + (value - 0xd800) * 1024 + low - 0xdc00;
                }
                if (value < 0 || !utf8(out, cap, &n, (u32)value)) return 0;
                continue;
            }
            if (ch == 'n') ch = '\n';
            else if (ch == 'r') ch = '\r';
            else if (ch == 't') ch = '\t';
            else if (ch == 'b') ch = '\b';
            else if (ch == 'f') ch = '\f';
            else if (ch != '"' && ch != '/' && ch != '\\') return 0;
        }
        if (!emit(out, cap, &n, ch)) return 0;
    }
    if (out) out[n] = 0;
    advance(p);
    return 1;
}

static int number(struct parser *p, mb_u64 *value)
{
    ws(p);
    mb_u64 n = 0;
    if (p->ch < '0' || p->ch > '9') return 0;
    if (p->ch == '0') { advance(p); *value = 0; return 1; }
    while (p->ch >= '0' && p->ch <= '9') {
        unsigned digit = (unsigned)(p->ch - '0');
        if (n > 922337203685477580ull || (n == 922337203685477580ull && digit > 7)) return 0;
        n = n * 10 + digit;
        advance(p);
    }
    *value = n;
    return 1;
}

static int page_hashes(struct parser *p, struct cat2_index *idx, u32 *pages)
{
    if (!consume(p, '[')) return 0;
    do {
        char h[65];
        if (*pages >= CAT2_MAX / CAT2_PAGE || !string(p, h, sizeof h) || !hex_string(h, 64)) return 0;
        for (u32 j = 0; j < 32; ++j) idx->hashes[*pages][j] = (u8)(hex(h[j * 2]) * 16 + hex(h[j * 2 + 1]));
        ++*pages;
    } while (consume(p, ','));
    return consume(p, ']');
}

static int generation_valid(const struct cat2_index *idx, u32 pages)
{
    static const char digits[] = "0123456789abcdef";
    struct sha sha;
    shinit(&sha);
    for (u32 i = 0; i < pages; ++i) {
        u8 wire[64];
        for (u32 j = 0; j < 32; ++j) {
            wire[j * 2] = digits[idx->hashes[i][j] >> 4];
            wire[j * 2 + 1] = digits[idx->hashes[i][j] & 15];
        }
        shadd(&sha, wire, sizeof wire);
    }
    char hash[65];
    shfinal(&sha, hash);
    for (u32 i = 0; i < 16; ++i) if (upper(hash[i]) != idx->generation[i]) return 0;
    return 1;
}

static enum diag_result index_read(struct cat2 *cat, u32 slot)
{
    struct reader r;
    enum diag_result result = start_read(&r, cat->fs, slot ? "FM1/CATALOG/INDEXB.V2" : "FM1/CATALOG/INDEXA.V2", 24576);
    if (result != DIAG_OK) return result;
    char expected[65], got[65];
    if (!line(&r, got, sizeof got) || !eq(got, "FM1CAT|2") ||
        !line(&r, expected, sizeof expected) || !hex_string(expected, 64)) return parse_result(&r);
    struct sha digest;
    shinit(&digest);
    r.digest = &digest;
    struct parser p = { .stream = &r };
    advance(&p);
    char key[16];
    mb_u64 count = 0;
    u32 pages = 0, seen = 0;
    if (!consume(&p, '{')) return parse_result(&r);
    do {
        if (!string(&p, key, sizeof key) || !consume(&p, ':')) return parse_result(&r);
        u32 bit = eq(key, "generation") ? 1 : eq(key, "sequence") ? 2 : eq(key, "count") ? 4 : eq(key, "pages") ? 8 : 0;
        if (!bit || (seen & bit)) return DIAG_FAT;
        seen |= bit;
        int valid = bit == 1 ? string(&p, cat->index.generation, sizeof cat->index.generation)
            : bit == 2 ? number(&p, &cat->index.sequence)
            : bit == 4 ? number(&p, &count) : page_hashes(&p, &cat->index, &pages);
        if (!valid) return parse_result(&r);
    } while (consume(&p, ','));
    if (!consume(&p, '}') || seen != 15 || !cat->index.sequence || !count || count > CAT2_MAX ||
        pages != (count + CAT2_PAGE - 1) / CAT2_PAGE || !generation_valid(&cat->index, pages)) return parse_result(&r);
    ws(&p);
    if (p.ch != -1 || r.result != DIAG_OK) return parse_result(&r);
    shfinal(&digest, got);
    if (!eq(expected, got)) return DIAG_FAT;
    cat->index.count = (u32)count;
    cat->slot = slot;
    return DIAG_OK;
}

static int suffix(struct cat2_record *item, const char *original)
{
    const char *ext = 0;
    for (const char *p = original; *p; ++p) {
        if ((u8)*p < 32 || *p == '/' || *p == '\\' || *p == ':') return 0;
        if (*p == '.') ext = p + 1;
    }
    if (!ext || ext == original + 1) return 0;
    char suffix[5] = {0};
    for (unsigned i = 0; ext[i]; ++i) {
        if (i == 4) return 0;
        suffix[i] = lower(ext[i]);
    }
    const char *dos = eq(suffix, "fwsc") ? "FWS" : eq(suffix, "ufw") ? "UFW" : eq(suffix, "bin") ? "BIN" : 0;
    if (!dos) return 0;
    char flat[16] = "FW/12345678.EXT";
    char legacy[28] = "FW/XX/12345678/12345678.EXT";
    for (u32 i = 0; i < 8; ++i) {
        flat[3 + i] = upper(item->sha[i]);
        legacy[6 + i] = upper(item->sha[2 + i]);
        legacy[15 + i] = upper(item->sha[10 + i]);
    }
    legacy[3] = upper(item->sha[0]); legacy[4] = upper(item->sha[1]);
    for (unsigned i = 0; i < 3; ++i) { flat[12 + i] = dos[i]; legacy[24 + i] = dos[i]; }
    return eq(item->path, flat) || eq(item->path, legacy);
}

static int record_parse(struct reader *r, struct cat2_record *item)
{
    char original[481], key[20];
    __builtin_memset(item, 0, sizeof *item);
    struct parser p = { .stream = r, .record = 1 };
    advance(&p);
    if (!consume(&p, '{')) return 0;
    struct field { const char *key; char *data; u32 cap; };
    const struct field fields[] = {
        {"name", item->name, sizeof item->name}, {"version", item->version, sizeof item->version},
        {"original", original, sizeof original}, {"sha256", item->sha, sizeof item->sha},
        {"path", item->path, sizeof item->path}, {"format", item->format, sizeof item->format},
        {"compat", item->compatibility, sizeof item->compatibility}, {"project", item->project, sizeof item->project},
        {"author", item->author, sizeof item->author}, {"identity", 0, 301}, {"crc", 0, 361},
        {"layout", 0, 2701}, {"source", 0, 1537}, {"size", 0, 0}
    };
    u32 seen = 0;
    do {
        if (!string(&p, key, sizeof key) || !consume(&p, ':')) return 0;
        u32 i = 0;
        while (i < ARRAY_LEN(fields) && !eq(key, fields[i].key)) ++i;
        if (i == ARRAY_LEN(fields) || (seen & (1u << i))) return 0;
        seen |= 1u << i;
        if (fields[i].cap) {
            if (!string(&p, fields[i].data, fields[i].cap)) return 0;
        } else {
            mb_u64 size;
            if (!number(&p, &size) || !size || size > 16u * 1024u * 1024u) return 0;
            item->size = (u32)size;
        }
    } while (consume(&p, ','));
    if (!consume(&p, '}')) return 0;
    ws(&p);
    return p.ended && p.ch == -1 && (seen & 0x207fu) == 0x207fu &&
        ((eq(item->format, "FWSC") || eq(item->format, "FWSC/UFW")) || eq(item->format, "UFW") || eq(item->format, "SLOT v1") || eq(item->format, "BIN")) &&
        eq(item->compatibility, "UNVERIFIED") && hex_string(item->sha, 64) && suffix(item, original);
}

static void page_path(struct cat2 *cat, u32 page, char out[43])
{
    static const char pattern[] = "FM1/CATALOG/AAAAAAAA/BBBBBBBB/P0000000.JSN";
    for (u32 i = 0; i < sizeof pattern; ++i) out[i] = pattern[i];
    for (u32 i = 0; i < 8; ++i) {
        out[12 + i] = cat->index.generation[i];
        out[21 + i] = cat->index.generation[8 + i];
    }
    for (u32 pos = 37, n = page + 1; n; --pos, n /= 10) out[pos] = (char)('0' + n % 10);
}

static enum diag_result read_page(struct cat2 *cat, u32 page, cat2_visit visit, void *context)
{
    struct reader r;
    char path[43];
    page_path(cat, page, path);
    enum diag_result result = start_read(&r, cat->fs, path, 128u * 1024u);
    if (result != DIAG_OK) return result == DIAG_END ? DIAG_FAT : result;
    struct sha digest;
    shinit(&digest);
    r.digest = &digest;
    char header[32];
    if (!line(&r, header, sizeof header)) return parse_result(&r);
    static const char tag[] = "FM1CAT|2|PAGE|";
    for (u32 i = 0; tag[i]; ++i) if (header[i] != tag[i]) return DIAG_FAT;
    struct parser p = { .memory = header + sizeof tag - 1 };
    advance(&p);
    mb_u64 ordinal;
    if (!number(&p, &ordinal) || p.ch != -1 || ordinal != page + 1) return DIAG_FAT;
    const u32 first = page * CAT2_PAGE;
    const u32 rows = cat->index.count - first < CAT2_PAGE ? cat->index.count - first : CAT2_PAGE;
    char previous[65] = {0};
    for (u32 i = 0; i < rows; ++i) {
        struct cat2_record *item = &cat->row;
        if (!record_parse(&r, item)) return parse_result(&r);
        unsigned j = 0;
        while (j < 64 && previous[j] == item->sha[j]) ++j;
        if (j == 64 || previous[j] > item->sha[j]) return DIAG_FAT;
        for (j = 0; j < 65; ++j) previous[j] = item->sha[j];
        /* The caller stages visited rows and publishes them only on DIAG_OK. */
        if (visit) visit(context, first + i, item);
    }
    if (get(&r) != -1 || r.result != DIAG_OK) return parse_result(&r);
    char hash[65];
    shfinal(&digest, hash);
    for (u32 i = 0; i < 32; ++i)
        if (cat->index.hashes[page][i] != (u8)(hex(hash[i * 2]) * 16 + hex(hash[i * 2 + 1]))) return DIAG_FAT;
    return DIAG_OK;
}

enum diag_result cat2_open(struct cat2 *cat, struct fat32 *fs)
{
    cat->fs = fs;
    cat->recovered = 0;
    enum diag_result a = index_read(cat, 0);
    mb_u64 seq_a = a == DIAG_OK ? cat->index.sequence : 0;
    enum diag_result b = index_read(cat, 1);
    mb_u64 seq_b = b == DIAG_OK ? cat->index.sequence : 0;
    cat->index.count = 0;
    if (a == DIAG_DISCONNECTED || b == DIAG_DISCONNECTED) return DIAG_DISCONNECTED;
    if (a == DIAG_TIMEOUT || b == DIAG_TIMEOUT) return DIAG_TIMEOUT;
    if (!seq_a && !seq_b) return a == DIAG_END ? b : a;
    if (seq_a == seq_b) return DIAG_FAT;
    u32 slot = seq_b > seq_a ? 1 : 0;
    enum diag_result result = index_read(cat, slot);
    if (result != DIAG_OK) return result == DIAG_END ? DIAG_FAT : result;
    if (cat->index.sequence != (slot ? seq_b : seq_a)) return DIAG_FAT;
    cat->recovered = (slot ? a : b) != DIAG_OK && (slot ? a : b) != DIAG_END;
    /* Validate the first visible page before accepting a new slot. Other pages
     * are validated on demand, avoiding a full-card scan on entry. */
    result = read_page(cat, 0, 0, 0);
    if (result == DIAG_OK) return result;
    if (!seq_a || !seq_b || result == DIAG_DISCONNECTED || result == DIAG_TIMEOUT || result == DIAG_TRANSFER) return result;
    result = index_read(cat, slot ^ 1u);
    if (result != DIAG_OK) return result == DIAG_END ? DIAG_FAT : result;
    if (cat->index.sequence != (slot ? seq_a : seq_b)) return DIAG_FAT;
    cat->recovered = 1;
    return read_page(cat, 0, 0, 0);
}

enum diag_result cat2_page(struct cat2 *cat, u32 page, cat2_visit visit, void *context)
{
    if (page >= (cat->index.count + CAT2_PAGE - 1) / CAT2_PAGE) return DIAG_RANGE;
    return read_page(cat, page, visit, context);
}

enum diag_result cat2_verify(struct cat2 *cat, const struct cat2_record *item)
{
    char path[39] = "FM1/";
    u32 i = 0;
    for (; i < sizeof item->path && item->path[i]; ++i) path[4 + i] = item->path[i];
    if (i == sizeof item->path) return DIAG_FAT;
    path[4 + i] = 0;
    struct fat_file file;
    struct fat_cursor cursor;
    enum diag_result r = fat32_open_fm1_path(cat->fs, path, &file);
    if (r != DIAG_OK) return r;
    if (file.size != item->size) return DIAG_FAT;
    r = fat32_seek(cat->fs, &file, 0, &cursor);
    if (r != DIAG_OK) return r;
    struct sha digest;
    shinit(&digest);
    u8 bytes[512];
    while (cursor.offset < file.size) {
        const u32 left = file.size - cursor.offset;
        u32 got;
        r = fat32_read_next(cat->fs, &file, &cursor, bytes, left < sizeof bytes ? left : sizeof bytes, &got);
        if (r != DIAG_OK) return r;
        shadd(&digest, bytes, got);
    }
    r = fat32_check_eof(cat->fs, &file, &cursor);
    if (r != DIAG_OK) return r;
    char got[65];
    shfinal(&digest, got);
    return eq(got, item->sha) ? DIAG_OK : DIAG_FAT;
}
