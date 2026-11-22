/* SPDX-License-Identifier: MIT */
#ifndef DIAG_STORAGE_H
#define DIAG_STORAGE_H
#include "husb.h"

struct usb_device {
    u16 vid, pid;
    u8 device_class, device_subclass;
    u8 mps0, interface, class_code, subclass, protocol;
    u8 ep_in, ep_out;
    u16 bulk_mps;
};

enum diag_result {
    DIAG_OK, DIAG_DISCONNECTED, DIAG_TIMEOUT, DIAG_TRANSFER, DIAG_DESCRIPTOR,
    DIAG_UNSUPPORTED, DIAG_SCSI, DIAG_BLOCK_SIZE, DIAG_RANGE, DIAG_FAT,
    DIAG_END, DIAG_LIMIT
};

enum diag_result usb_enumerate(struct usb_device *device);
enum diag_result disk_open(const struct usb_device *device, u32 *blocks);
enum diag_result disk_read(void *disk, u32 lba, u8 *out);
const char *diag_error(enum diag_result result);

struct block_device {
    void *context;
    u32 count;
    enum diag_result (*read)(void *, u32, u8 *);
};

struct fat32 {
    struct block_device disk;
    u32 fat_lba, data_lba, root, clusters;
    u8 sectors_per_cluster;
    u8 sector[512];
};

struct fat_file { char name[13]; u32 cluster, size; };
struct fat_cursor { u32 cluster, offset, in_cluster; };
struct fat_dir { u32 cluster, transitions; u8 sector, entry, end; };

enum diag_result fat32_mount(struct fat32 *fs, struct block_device disk);
void fat32_root(const struct fat32 *fs, struct fat_dir *dir);
/* Only FM1/INDEX.CSV, read-only; no generic path traversal. */
enum diag_result fat32_open_fm1_index(struct fat32 *fs, struct fat_file *index);
enum diag_result fat32_open_fm1_file(struct fat32 *fs, const char *short_name, struct fat_file *file);
enum diag_result fat32_read_at(struct fat32 *fs, const struct fat_file *file, u32 offset,
                               u8 *output, u32 capacity, u32 *written);
enum diag_result fat32_seek(struct fat32 *, const struct fat_file *, u32 offset, struct fat_cursor *);
enum diag_result fat32_read_next(struct fat32 *, const struct fat_file *, struct fat_cursor *,
                                u8 *, u32 capacity, u32 *written);
enum diag_result fat32_check_eof(struct fat32 *, const struct fat_file *, const struct fat_cursor *);
enum diag_result fat32_next(struct fat32 *fs, struct fat_dir *dir, struct fat_file *file);
enum diag_result fat32_read_prefix(struct fat32 *fs, const struct fat_file *file,
                                   u8 *output, u32 capacity, u32 *written);
#endif
