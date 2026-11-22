/* SPDX-License-Identifier: MIT */
/* Read-only protocol, after m-bootloader 395e6cc and m-apps 748bf00 (Julius Vitkauskas). */
#include "stg2_sd.h"

enum sd_command { SD_IDLE = 0, SD_IF_COND = 8, SD_CSD = 9, SD_READ = 17,
                  SD_INIT = 41, SD_APP = 55, SD_OCR = 58 };

static u8 exchange(struct stg2_sd *sd, u8 out)
{
    u8 in = 0xff;
    if (!sd->transfer_failed && !sd->bus.exchange(sd->bus.context, out, &in))
        sd->transfer_failed = 1;
    return in;
}

static void end_transaction(struct stg2_sd *sd)
{
    sd->bus.select(sd->bus.context, 0);
    exchange(sd, 0xff);
}

static u8 command(struct stg2_sd *sd, enum sd_command cmd, u32 argument)
{
    u8 frame[5] = {(u8)(0x40u | cmd)};
    bs32(frame + 1, argument);
    end_transaction(sd);
    sd->bus.select(sd->bus.context, 1);
    exchange(sd, 0xff);
    u8 crc = 0;
    for (u32 i = 0; i < sizeof frame; ++i) {
        u8 byte = frame[i];
        exchange(sd, byte);
        for (u32 bit = 0; bit < 8; ++bit, byte = (u8)(byte << 1)) {
            crc = (u8)(crc << 1);
            if ((byte ^ crc) & 0x80u) crc ^= 9;
        }
    }
    exchange(sd, (u8)((crc << 1) | 1));
    for (u32 i = 0; i < 16 && !sd->transfer_failed; ++i) {
        u8 response = exchange(sd, 0xff);
        if (!(response & 0x80u)) return response;
    }
    return 0xff;
}

static u32 response32(struct stg2_sd *sd)
{
    u32 value = 0;
    for (u32 i = 0; i < 4; ++i) value = (value << 8) | exchange(sd, 0xff);
    return value;
}

static enum diag_result read_block(struct stg2_sd *sd, u8 *data, u32 size)
{
    u8 token = 0xff;
    for (u32 i = 0; i < 65536 && token == 0xff && !sd->transfer_failed; ++i)
        token = exchange(sd, 0xff);
    if (sd->transfer_failed || token == 0xff) return DIAG_TIMEOUT;
    if (token != 0xfe) return DIAG_TRANSFER;
    u16 crc = 0;
    for (u32 i = 0; i < size && !sd->transfer_failed; ++i) {
        data[i] = exchange(sd, 0xff);
        crc ^= (u16)data[i] << 8;
        for (u32 bit = 0; bit < 8; ++bit)
            crc = (u16)((crc << 1) ^ ((crc & 0x8000u) ? 0x1021u : 0));
    }
    u16 actual = (u16)exchange(sd, 0xff) << 8;
    actual |= exchange(sd, 0xff);
    if (sd->transfer_failed) return DIAG_TIMEOUT;
    return actual == crc ? DIAG_OK : DIAG_TRANSFER;
}

static enum diag_result initialise(struct stg2_sd *sd)
{
    sd->bus.select(sd->bus.context, 0);
    for (u32 i = 0; i < 10; ++i) exchange(sd, 0xff);
    u8 response = 0xff;
    for (u32 i = 0; i < 20 && !sd->transfer_failed; ++i)
        if ((response = command(sd, SD_IDLE, 0)) == 1) break;
    if (response != 1) return sd->transfer_failed ? DIAG_TIMEOUT : DIAG_DISCONNECTED;
    if (command(sd, SD_IF_COND, 0x1aa) != 1 || response32(sd) != 0x1aa)
        return DIAG_UNSUPPORTED;
    for (u32 i = 0; i < 1000 && !sd->transfer_failed; ++i) {
        if (command(sd, SD_APP, 0) > 1) return DIAG_DISCONNECTED;
        response = command(sd, SD_INIT, 1u << 30);
        if (response == 0) break;
        if (response != 1) return DIAG_TRANSFER;
        sd->bus.wait_ms(sd->bus.context);
    }
    if (response != 0 || sd->transfer_failed) return DIAG_TIMEOUT;
    if (command(sd, SD_OCR, 0) || (response32(sd) & 0xc0000000u) != 0xc0000000u)
        return DIAG_UNSUPPORTED;
    if (command(sd, SD_CSD, 0)) return DIAG_TRANSFER;
    u8 csd[16];
    enum diag_result result = read_block(sd, csd, sizeof csd);
    if (result != DIAG_OK) return result;
    u32 size = (((u32)csd[7] & 0x3fu) << 16) | ((u32)csd[8] << 8) | csd[9];
    if (csd[0] >> 6 != 1 || size == 0x3fffffu) return DIAG_UNSUPPORTED;
    sd->sectors = (size + 1u) << 10;
    return DIAG_OK;
}

enum diag_result stg2_sd_start(struct stg2_sd *sd)
{
    sd->sectors = 0;
    sd->transfer_failed = 0;
    enum diag_result result = initialise(sd);
    end_transaction(sd);
    if (sd->transfer_failed) result = DIAG_TIMEOUT;
    if (result != DIAG_OK) sd->sectors = 0;
    return result;
}

enum diag_result stg2_sd_read(void *context, u32 lba, u8 *out)
{
    struct stg2_sd *sd = context;
    if (!sd->sectors || lba >= sd->sectors || !out) return DIAG_RANGE;
    sd->transfer_failed = 0;
    u8 response = command(sd, SD_READ, lba);
    enum diag_result result = response == 0 ? read_block(sd, out, 512) :
        response == 0xff ? DIAG_DISCONNECTED : DIAG_TRANSFER;
    end_transaction(sd);
    return sd->transfer_failed ? DIAG_TIMEOUT : result;
}
