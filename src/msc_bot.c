/* SPDX-License-Identifier: MIT
 * Read-only USB MSC BOT framing. No device writes are represented here. */
#include "msc_bot.h"

static int valid_ptr(const void *p) { return p != 0; }

void mb_msc_build_cbw(u8 *cbw, u32 tag, const u8 *cdb, u8 cdb_len,
                      u32 transfer, int data_in)
{
    u8 i;
    if (!cbw || !cdb || cdb_len < 1 || cdb_len > 16) return;
    for (i = 0; i < MB_MSC_CBW_SIZE; ++i) cbw[i] = 0;
    st32(cbw, MB_MSC_SIGNATURE_CBW);
    st32(cbw + 4, tag);
    st32(cbw + 8, transfer);
    cbw[12] = data_in ? 0x80u : 0;
    cbw[14] = cdb_len;
    for (i = 0; i < cdb_len; ++i) cbw[15 + i] = cdb[i];
}

enum mb_msc_result mb_msc_validate_cbw(const u8 *cbw, u32 length, u32 *tag,
                                       u32 *transfer, int *data_in, u8 *cdb_len)
{
    if (!valid_ptr(cbw) || length != MB_MSC_CBW_SIZE || !tag || !transfer ||
        !data_in || !cdb_len || ld32(cbw) != MB_MSC_SIGNATURE_CBW ||
        (cbw[12] & 0x7fu) != 0 || cbw[13] != 0 || cbw[14] < 1 || cbw[14] > 16)
        return MB_MSC_MALFORMED;
    *tag = ld32(cbw + 4);
    *transfer = ld32(cbw + 8);
    *data_in = (cbw[12] & 0x80u) != 0;
    *cdb_len = cbw[14];
    return MB_MSC_OK;
}

enum mb_msc_result mb_msc_validate_csw(const u8 *csw, u32 length, u32 tag,
                                       u32 transfer, u32 received, u8 *status)
{
    u32 residue;
    if (!valid_ptr(csw) || length != MB_MSC_CSW_SIZE || !status ||
        ld32(csw) != MB_MSC_SIGNATURE_CSW || ld32(csw + 4) != tag ||
        csw[12] > 2) return MB_MSC_MALFORMED;
    residue = ld32(csw + 8);
    if (received > transfer || residue > transfer - received)
        return MB_MSC_MALFORMED;
    if (received + residue != transfer)
        return MB_MSC_MALFORMED;
    *status = csw[12];
    return *status == 0 ? MB_MSC_OK : MB_MSC_DEVICE;
}

int mb_msc_read_only_cdb(const u8 *cdb, u8 length, u32 *lba, u16 *blocks,
                         u32 *bytes)
{
    u32 count;
    if (!cdb || !lba || !blocks || !bytes || length < 1) return 0;
    if (cdb[0] == 0x00 && length >= 6) { *lba = 0; *blocks = 0; *bytes = 0; return 1; }
    if (cdb[0] == 0x03 && length >= 6) { *lba = 0; *blocks = 0; *bytes = cdb[4]; return 1; }
    if (cdb[0] == 0x12 && length >= 6) { *lba = 0; *blocks = 0; *bytes = cdb[4]; return 1; }
    if (cdb[0] == 0x25 && length >= 10) { *lba = 0; *blocks = 0; *bytes = 8; return 1; }
    if (cdb[0] != 0x28 || length < 10) return 0;
    count = ((u32)cdb[7] << 8) | cdb[8];
    if (count == 0 || count > 128) return 0;
    *lba = bd32(cdb + 2);
    *blocks = (u16)count;
    *bytes = count * 512u;
    return 1;
}

void mb_msc_bot_init(struct mb_msc_bot *bot, struct mb_msc_io io)
{
    if (!bot) return;
    bot->io = io;
    bot->tag = 0;
    bot->blocks = 0;
}

enum mb_msc_result mb_msc_bot_read10(struct mb_msc_bot *bot, u32 lba, u8 *sector)
{
    u8 cbw[MB_MSC_CBW_SIZE] = {0}, data[512], csw[MB_MSC_CSW_SIZE];
    u32 tag, transfer, got, cdb_bytes, command_lba;
    u16 command_blocks;
    int data_in;
    u8 cdb_len, status;
    if (!bot || !sector || !bot->io.bulk_out || !bot->io.bulk_in ||
        (bot->blocks && lba >= bot->blocks)) return MB_MSC_MALFORMED;
    {
        u8 cdb[10] = {0x28};
        bs32(cdb + 2, lba);
        cdb[8] = 1;
        mb_msc_build_cbw(cbw, ++bot->tag, cdb, sizeof cdb, 512, 1);
    }
    if (mb_msc_validate_cbw(cbw, sizeof cbw, &tag, &transfer, &data_in, &cdb_len) != MB_MSC_OK ||
        !data_in || !mb_msc_read_only_cdb(cbw + 15, cdb_len, &command_lba, &command_blocks, &cdb_bytes) ||
        command_lba != lba || command_blocks != 1 || cdb_bytes != 512)
        return MB_MSC_MALFORMED;
    if (bot->io.bulk_out(bot->io.context, cbw, sizeof cbw) != MB_MSC_OK) return MB_MSC_TRANSPORT;
    got = 0;
    if (bot->io.bulk_in(bot->io.context, data, sizeof data, &got) != MB_MSC_OK || got != sizeof data)
        return MB_MSC_TRANSPORT;
    got = 0;
    if (bot->io.bulk_in(bot->io.context, csw, sizeof csw, &got) != MB_MSC_OK || got != sizeof csw)
        return MB_MSC_TRANSPORT;
    if (mb_msc_validate_csw(csw, sizeof csw, bot->tag, 512, 512, &status) != MB_MSC_OK || status != 0)
        return MB_MSC_DEVICE;
    for (cdb_bytes = 0; cdb_bytes < 512; ++cdb_bytes) sector[cdb_bytes] = data[cdb_bytes];
    return MB_MSC_OK;
}
