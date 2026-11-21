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
