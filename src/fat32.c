/* SPDX-License-Identifier: MIT */
#include "storage.h"

#define SECTOR_BYTES 512u
#define ENTRIES_PER_SECTOR 16u
#define FAT32_FIRST_EOC 0x0ffffff8u
#define DIRECTORY_TRANSITIONS 8u
#define DIRECTORY_SECTORS 128u
#define PREVIEW_BYTES 1024u

static int valid_cluster(const struct fat32 *fs, u32 cluster)
{
    return cluster >= 2 && cluster - 2 < fs->clusters;
}

static int fat_boot(const u8 *b)
{
    return (b[0] == 0xeb || b[0] == 0xe9) && ld16(b + 510) == 0xaa55 &&
           ld16(b + 11) == SECTOR_BYTES && ld16(b + 17) == 0 && ld16(b + 22) == 0;
}

enum diag_result fat32_mount(struct fat32 *fs, struct block_device disk)
{
    if (!fs) return DIAG_RANGE;
    fs->disk = disk;
    fs->sectors_per_cluster = 0;
    if (disk.count == 0 || !disk.read) return DIAG_RANGE;
    u32 start = 0, available = disk.count;
    u8 *b = fs->sector;
    enum diag_result res = disk.read(disk.context, 0, b);
    if (res != DIAG_OK) return res;
    if (!fat_boot(b)) {
        if (ld16(b + 510) != 0xaa55) return DIAG_FAT;
        for (u32 i = 0; i < 4; ++i) {
            const u8 *p = b + 446 + i * 16;
            if (p[4] != 0x0b && p[4] != 0x0c) continue;
            start = ld32(p + 8);
            available = ld32(p + 12);
            break;
        }
        if (!start || start >= disk.count || !available || available > disk.count - start)
            return DIAG_FAT;
        res = disk.read(disk.context, start, b);
        if (res != DIAG_OK) return res;
        if (!fat_boot(b)) return DIAG_FAT;
    }
    const u32 reserved = ld16(b + 14), copies = b[16], spc = b[13];
    const u32 total = ld32(b + 32), fat_size = ld32(b + 36);
    const u16 flags = ld16(b + 40);
    const u32 active_fat = (flags & 0x80u) ? flags & 15u : 0;
    if (!spc || spc > 128 || (spc & (spc - 1)) || !total || total > available ||
        !reserved || reserved >= total || copies < 1 || copies > 2 || active_fat >= copies ||
        ld16(b + 19) != 0 || ld16(b + 42) != 0 || !fat_size || fat_size > (total - reserved) / copies)
        return DIAG_FAT;
    const u32 data = reserved + fat_size * copies;
    fs->clusters = (total - data) / spc;
    if (fs->clusters < 65525u || fs->clusters > 0x0fffffeeu ||
        (fs->clusters + 129u) / 128u > fat_size) return DIAG_FAT;
    fs->root = ld32(b + 44);
    if (!valid_cluster(fs, fs->root)) return DIAG_FAT;
    fs->fat_lba = start + reserved + active_fat * fat_size;
    fs->data_lba = start + data;
    fs->sectors_per_cluster = (u8)spc;
    return DIAG_OK;
}

static enum diag_result next_cluster(struct fat32 *fs, u32 *cluster)
{
    enum diag_result res = fs->disk.read(fs->disk.context, fs->fat_lba + *cluster / 128u, fs->sector);
    if (res != DIAG_OK) return res;
    const u32 next = ld32(fs->sector + (*cluster % 128u) * 4u) & 0x0fffffffu;
    if (next >= FAT32_FIRST_EOC) return DIAG_END;
    if (!valid_cluster(fs, next) || next == *cluster) return DIAG_FAT;
    *cluster = next;
    return DIAG_OK;
}

static enum diag_result data_sector(struct fat32 *fs, u32 cluster, u32 sector)
{
    if (!fs->sectors_per_cluster || !valid_cluster(fs, cluster) || sector >= fs->sectors_per_cluster)
        return DIAG_FAT;
    return fs->disk.read(fs->disk.context, fs->data_lba + (cluster - 2) * fs->sectors_per_cluster + sector,
                         fs->sector);
}

void fat32_root(const struct fat32 *fs, struct fat_dir *dir)
{
    *dir = (struct fat_dir){ .cluster = fs->root };
}

static void short_name(char *out, const u8 *entry)
{
    u32 n = 0;
    for (u32 i = 0; i < 8 && entry[i] != ' '; ++i)
        out[n++] = (entry[i] >= 33 && entry[i] <= 126) ? (char)entry[i] : '?';
    if (entry[8] != ' ') {
        out[n++] = '.';
        for (u32 i = 8; i < 11 && entry[i] != ' '; ++i)
            out[n++] = (entry[i] >= 33 && entry[i] <= 126) ? (char)entry[i] : '?';
    }
    out[n] = 0;
}

enum diag_result fat32_next(struct fat32 *fs, struct fat_dir *dir, struct fat_file *file)
{
    if (dir->end) return DIAG_END;
    /* Bounded even if a damaged directory chain has a cycle. */
    for (u32 blocks = 0; blocks < DIRECTORY_SECTORS; ++blocks) {
        if (dir->sector == fs->sectors_per_cluster) {
            if (dir->transitions >= DIRECTORY_TRANSITIONS) return DIAG_LIMIT;
            enum diag_result res = next_cluster(fs, &dir->cluster);
            if (res == DIAG_END) dir->end = 1;
            if (res != DIAG_OK) return res;
            ++dir->transitions;
            dir->sector = 0;
        }
        enum diag_result res = data_sector(fs, dir->cluster, dir->sector);
        if (res != DIAG_OK) return res;
        for (; dir->entry < ENTRIES_PER_SECTOR; ) {
            const u8 *p = fs->sector + 32u * dir->entry++;
            if (!p[0]) { dir->end = 1; return DIAG_END; }
            if (p[0] == 0xe5 || (p[11] & 0x18u)) continue; /* also excludes LFN slots */
            file->cluster = ((u32)(ld16(p + 20) & 0x0fffu) << 16) | ld16(p + 26);
            file->size = ld32(p + 28);
            short_name(file->name, p);
            if (file->size && !valid_cluster(fs, file->cluster)) return DIAG_FAT;
            return DIAG_OK;
        }
        dir->entry = 0;
        ++dir->sector;
    }
    return DIAG_LIMIT;
}

enum diag_result fat32_read_prefix(struct fat32 *fs, const struct fat_file *file,
                                   u8 *output, u32 capacity, u32 *written)
{
    *written = 0;
    if (capacity > PREVIEW_BYTES) return DIAG_LIMIT;
    const u32 count = capacity < file->size ? capacity : file->size;
    u32 cluster = file->cluster, offset = 0;
    for (u32 block = 0; offset < count; ++block) {
        if (block == fs->sectors_per_cluster) {
            enum diag_result res = next_cluster(fs, &cluster);
            if (res == DIAG_END) return DIAG_FAT;
            if (res != DIAG_OK) return res;
            block = 0;
        }
        const enum diag_result res = data_sector(fs, cluster, block);
        if (res != DIAG_OK) return res;
        const u32 copied = count - offset < SECTOR_BYTES ? count - offset : SECTOR_BYTES;
        for (u32 i = 0; i < copied; ++i) output[offset + i] = fs->sector[i];
        offset += copied;
    }
    *written = count;
    return DIAG_OK;
}


/*
 * Locate Android FM1 Manager's INDEX.CSV in the FM1 directory.
 * Read-only and deliberately restricted to two fixed FAT 8.3 components.
 * No dynamic allocation, LFN, arbitrary paths or executable images.
 */
static enum diag_result find_short_entry(struct fat32 *fs, u32 parent,
                                         const char short_name_11[11], int directory,
                                         struct fat_file *out)
{
    u32 cluster = parent, transitions = 0;
    u8 sector = 0;
    if (!valid_cluster(fs, parent)) return DIAG_FAT;
    for (u32 visited = 0; visited < DIRECTORY_SECTORS; ++visited) {
        if (sector == fs->sectors_per_cluster) {
            if (transitions >= DIRECTORY_TRANSITIONS) return DIAG_LIMIT;
            enum diag_result next = next_cluster(fs, &cluster);
            if (next != DIAG_OK) return next == DIAG_END ? DIAG_END : next;
            ++transitions;
            sector = 0;
        }
        enum diag_result result = data_sector(fs, cluster, sector++);
        if (result != DIAG_OK) return result;
        for (u32 entry = 0; entry < ENTRIES_PER_SECTOR; ++entry) {
            const u8 *p = fs->sector + 32u * entry;
            if (!p[0]) return DIAG_END;
            if (p[0] == 0xe5 || p[11] == 0x0fu || (p[11] & (0x08u | 0x06u)))
                continue; /* deleted, long-name, volume, hidden or system */
            if (!!(p[11] & 0x10u) != !!directory) continue;
            u32 i;
            for (i = 0; i < 11 && p[i] == (u8)short_name_11[i]; ++i) {}
            if (i != 11) continue;
            const u32 found = ((u32)(ld16(p + 20) & 0x0fffu) << 16) | ld16(p + 26);
            const u32 size = ld32(p + 28);
            if (!valid_cluster(fs, found) || (!directory && !size)) return DIAG_FAT;
            out->cluster = found;
            out->size = size;
            short_name(out->name, p);
            return DIAG_OK;
        }
    }
    return DIAG_LIMIT;
}

static enum diag_result open_fm1_child(struct fat32 *fs, const char *short_name,
                                         int directory, struct fat_file *file)
{
    char name[11];
    u32 n = 0, i;
    for (i = 0; short_name[i] && short_name[i] != '.' && n < 8; ++i) {
        if (!((short_name[i] >= 'A' && short_name[i] <= 'Z') ||
              (short_name[i] >= '0' && short_name[i] <= '9') || short_name[i] == '_')) return DIAG_FAT;
        name[n++] = short_name[i];
    }
    if (!n || short_name[i] != '.') return DIAG_FAT;
    ++i;
    u32 ext = 0;
    while (short_name[i] && ext < 3) {
        if (!((short_name[i] >= 'A' && short_name[i] <= 'Z') ||
              (short_name[i] >= '0' && short_name[i] <= '9'))) return DIAG_FAT;
        name[8 + ext++] = short_name[i++];
    }
    if (short_name[i] || (directory ? ext != 0 : ext != 3)) return DIAG_FAT;
    while (n < 8) name[n++] = ' ';
    while (ext < 3) name[8 + ext++] = ' ';
    struct fat_file folder, fw;
    enum diag_result r = find_short_entry(fs, fs->root, "FM1        ", 1, &folder);
    if (r != DIAG_OK) return r;
    r = find_short_entry(fs, folder.cluster, "FW         ", 1, &fw);
    if (r != DIAG_OK) return r;
    return find_short_entry(fs, fw.cluster, name, directory, file);
}

enum diag_result fat32_open_fm1_index(struct fat32 *fs, struct fat_file *index)
{
    if (!fs || !index) return DIAG_RANGE;
    struct fat_file folder;
    enum diag_result r = find_short_entry(fs, fs->root, "FM1        ", 1, &folder);
    if (r != DIAG_OK) return r;
    r = find_short_entry(fs, folder.cluster, "INDEX   CSV", 0, index);
    if (r != DIAG_OK) return r;
    return index->size <= 24576u ? DIAG_OK : DIAG_LIMIT;
}

enum diag_result fat32_open_fm1_file(struct fat32 *fs, const char *short_name, struct fat_file *file)
{
    if (!fs || !short_name || !file) return DIAG_RANGE;
    return open_fm1_child(fs, short_name, 0, file);
}

enum diag_result fat32_seek(struct fat32 *fs, const struct fat_file *file, u32 offset,
                            struct fat_cursor *cursor)
{
    if (!fs || !fs->sectors_per_cluster || !file || !cursor || offset > file->size) return DIAG_RANGE;
    const u32 bytes_per_cluster = (u32)fs->sectors_per_cluster * 512u;
    const u32 skip = ((offset && offset == file->size) ? offset - 1u : offset) / bytes_per_cluster;
    if (skip >= fs->clusters || !valid_cluster(fs, file->cluster)) return DIAG_FAT;
    u32 cluster = file->cluster;
    for (u32 step = 0; step < skip; ++step) {
        enum diag_result r = next_cluster(fs, &cluster);
        if (r != DIAG_OK) return r == DIAG_END ? DIAG_FAT : r;
    }
    *cursor = (struct fat_cursor){cluster, offset, offset - skip * bytes_per_cluster};
    return DIAG_OK;
}

enum diag_result fat32_read_next(struct fat32 *fs, const struct fat_file *file,
                                struct fat_cursor *cursor, u8 *output, u32 capacity, u32 *written)
{
    if (!written) return DIAG_RANGE;
    *written = 0;
    if (!fs || !fs->sectors_per_cluster || !file || !cursor || (capacity && !output) ||
        cursor->offset > file->size || capacity > file->size - cursor->offset) return DIAG_RANGE;
    const u32 bytes_per_cluster = (u32)fs->sectors_per_cluster * 512u;
    for (u32 steps = 0; *written < capacity && steps <= fs->clusters; ++steps) {
        if (cursor->in_cluster == bytes_per_cluster) {
            enum diag_result r = next_cluster(fs, &cursor->cluster);
            if (r != DIAG_OK) return r == DIAG_END ? DIAG_FAT : r;
            cursor->in_cluster = 0;
        }
        u32 sector = cursor->in_cluster / 512u, in_sector = cursor->in_cluster % 512u;
        enum diag_result r = data_sector(fs, cursor->cluster, sector);
        if (r != DIAG_OK) return r;
        u32 take = 512u - in_sector;
        if (take > capacity - *written) take = capacity - *written;
        for (u32 i = 0; i < take; ++i) output[*written + i] = fs->sector[in_sector + i];
        *written += take;
        cursor->offset += take;
        cursor->in_cluster += take;
    }
    return *written == capacity ? DIAG_OK : DIAG_FAT;
}

enum diag_result fat32_check_eof(struct fat32 *fs, const struct fat_file *file,
                                const struct fat_cursor *cursor)
{
    if (!fs || !file || !cursor || cursor->offset != file->size || !valid_cluster(fs, cursor->cluster))
        return DIAG_RANGE;
    u32 cluster = cursor->cluster;
    enum diag_result r = next_cluster(fs, &cluster);
    return r == DIAG_END ? DIAG_OK : r == DIAG_OK ? DIAG_FAT : r;
}

enum diag_result fat32_read_at(struct fat32 *fs, const struct fat_file *file, u32 offset,
                              u8 *output, u32 capacity, u32 *written)
{
    if (!written) return DIAG_RANGE;
    *written = 0;
    if (!fs || !file || offset > file->size || capacity > file->size - offset) return DIAG_RANGE;
    if (!capacity) return DIAG_OK;
    struct fat_cursor cursor;
    enum diag_result r = fat32_seek(fs, file, offset, &cursor);
    return r == DIAG_OK ? fat32_read_next(fs, file, &cursor, output, capacity, written) : r;
}

/* Read-only, limited to DOS 8.3 descendants of FM1. The expanded lookup
 * budget applies only to the explorer's v2 paths. */
#ifdef FM1_USBC_EXPLORER
static int short_component(const char **path, char name[11])
{
    for (u32 i = 0; i < 11; ++i) name[i] = ' ';
    const char *p = *path;
    u32 pos = 0;
    while (*p && *p != '/' && *p != '.') {
        char c = *p++;
        if (pos == 8 || !((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')) return 0;
        name[pos++] = c;
    }
    if (!pos) return 0;
    if (*p == '.') {
        ++p;
        pos = 8;
        while (*p && *p != '/') {
            char c = *p++;
            if (pos == 11 || !((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) return 0;
            name[pos++] = c;
        }
        if (pos == 8) return 0;
    }
    *path = p;
    return 1;
}

static enum diag_result find_path_entry(struct fat32 *fs, u32 cluster, const char name[11],
                                         int directory, struct fat_file *out)
{
    /* Brent's cycle detector, using no extra FAT reads. */
    u32 anchor = cluster, span = 1, distance = 0;
    for (u32 visited = 0; visited < 4096u; ++visited) {
        const u32 sector = visited % fs->sectors_per_cluster;
        if (!sector && visited) {
            enum diag_result r = next_cluster(fs, &cluster);
            if (r != DIAG_OK) return r;
            if (cluster == anchor) return DIAG_FAT;
            if (++distance == span) {
                anchor = cluster;
                span *= 2;
                distance = 0;
            }
        }
        enum diag_result r = data_sector(fs, cluster, sector);
        if (r != DIAG_OK) return r;
        for (u32 i = 0; i < ENTRIES_PER_SECTOR; ++i) {
            const u8 *p = fs->sector + 32u * i;
            if (p[0] == 0) return DIAG_END;
            if (p[0] == 0xe5 || (p[11] & 0x0eu) || !!(p[11] & 0x10u) != !!directory) continue;
            u32 n = 0;
            while (n < 11 && p[n] == (u8)name[n]) ++n;
            if (n != 11) continue;
            out->cluster = ((u32)(ld16(p + 20) & 0x0fffu) << 16) | ld16(p + 26);
            out->size = ld32(p + 28);
            short_name(out->name, p);
            return valid_cluster(fs, out->cluster) || (!directory && !out->size && !out->cluster)
                ? DIAG_OK : DIAG_FAT;
        }
    }
    return DIAG_LIMIT;
}

enum diag_result fat32_open_fm1_path(struct fat32 *fs, const char *path, struct fat_file *file)
{
    if (!fs || !fs->sectors_per_cluster || !file || !path || path[0] != 'F' ||
        path[1] != 'M' || path[2] != '1' || path[3] != '/') return DIAG_RANGE;
    u32 parent = fs->root;
    for (unsigned level = 0; level < 5; ++level) {
        char name[11];
        if (!short_component(&path, name)) return DIAG_FAT;
        const int directory = *path == '/';
        enum diag_result r = find_path_entry(fs, parent, name, directory, file);
        if (r != DIAG_OK || !directory) return r;
        parent = file->cluster;
        ++path;
    }
    return DIAG_LIMIT;
}

enum diag_result fat32_mount_explorer(struct fat32 *fs, struct block_device disk)
{
    enum diag_result r = fat32_mount(fs, disk);
    const u8 *b = fs->sector + 3;
    if (r == DIAG_FAT && b[0] == 'E' && b[1] == 'X' && b[2] == 'F' && b[3] == 'A' &&
        b[4] == 'T' && b[5] == ' ' && b[6] == ' ' && b[7] == ' ') return DIAG_EXFAT;
    if (r == DIAG_FAT && ld16(fs->sector + 510) == 0xaa55) {
        /* Type 07 may contain exFAT or NTFS. Inspect its signature only. */
        for (unsigned i = 0; i < 4; ++i) {
            const u8 *entry = fs->sector + 446 + 16u * i;
            const u32 lba = ld32(entry + 8), count = ld32(entry + 12);
            if (entry[4] != 7 || !lba || lba >= disk.count || !count || count > disk.count - lba) continue;
            r = disk.read(disk.context, lba, fs->sector);
            if (r != DIAG_OK) return r;
            return b[0] == 'E' && b[1] == 'X' && b[2] == 'F' && b[3] == 'A' && b[4] == 'T'
                ? DIAG_EXFAT : DIAG_FAT;
        }
    }
    return r;
}
#endif
