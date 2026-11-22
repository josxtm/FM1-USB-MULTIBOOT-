/* SPDX-License-Identifier: MIT
 * Read-only USB Mass Storage Bulk-Only Transport policy and framing.
 * This layer has no USB-controller or FAT32 dependency. */
#ifndef MB_MSC_BOT_H
#define MB_MSC_BOT_H
#include "mbl.h"

#define MB_MSC_CBW_SIZE 31u
#define MB_MSC_CSW_SIZE 13u
#define MB_MSC_SIGNATURE_CBW 0x43425355u
#define MB_MSC_SIGNATURE_CSW 0x53425355u

enum mb_msc_result {
    MB_MSC_OK, MB_MSC_MALFORMED, MB_MSC_UNSUPPORTED, MB_MSC_STALL,
    MB_MSC_TIMEOUT, MB_MSC_DISCONNECTED, MB_MSC_TRANSPORT, MB_MSC_DEVICE
};

struct mb_msc_io {
    void *context;
    enum mb_msc_result (*bulk_out)(void *, const u8 *, u32);
    enum mb_msc_result (*bulk_in)(void *, u8 *, u32, u32 *);
    enum mb_msc_result (*clear_halt)(void *, int in);
};

struct mb_msc_bot {
    struct mb_msc_io io;
    u32 tag;
    u32 blocks;
};

/* Validate the fixed framing before interpreting any command bytes. */
enum mb_msc_result mb_msc_validate_cbw(const u8 *cbw, u32 length, u32 *tag,
                                       u32 *transfer, int *data_in, u8 *cdb_len);
enum mb_msc_result mb_msc_validate_csw(const u8 *csw, u32 length, u32 tag,
                                       u32 transfer, u32 received, u8 *status);
/* Only the read-only SCSI subset is admitted by this policy. */
int mb_msc_read_only_cdb(const u8 *cdb, u8 length, u32 *lba, u16 *blocks,
                         u32 *bytes);
void mb_msc_bot_init(struct mb_msc_bot *bot, struct mb_msc_io io);
enum mb_msc_result mb_msc_bot_read10(struct mb_msc_bot *bot, u32 lba, u8 *sector);
#endif
