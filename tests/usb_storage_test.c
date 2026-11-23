/* SPDX-License-Identifier: MIT */
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "storage.h"

static const u8 descriptor[] = {18,1,0,2,0,0,0,64,0x34,0x12,0x78,0x56,0,1,0,0,0,1};
static const u8 config[] = {9,2,32,0,1,1,0,0x80,50,9,4,0,0,2,8,6,0x50,0,7,5,0x81,2,64,0,0,7,5,2,2,64,0,0};
static u8 mock_config[sizeof config];
static unsigned attached = 1, configured, resets, recoveries, bulk_count;
static u32 bot_tag, bot_size, disk_lba;
#ifdef FM1_USBC_EXPLORER
#include "usbc_explorer.h"
static unsigned inits, short_read, detached_during_read;
void husb_init(void) { ++inits; }
int husb_wait_attach(u32 ms) { assert(ms == 1500); return attached != 0; }
#endif
static unsigned bot_failed, have_data, inject_timeout, invalid_csw, pending_attention;
static enum husb_res next_control;
static u32 block_size = 512, volume_lba = 2048;
static u32 disk_end = 62521344;
static int damaged_fat, short_partition, missing_fm1, invalid_fm1_cluster, oversized_index;

#define RESERVED 32u
#define FAT_SIZE 8000u
#define FAT_DATA (RESERVED + 2u * FAT_SIZE)
static const char index_contents[] =
    "FM1CAT|1\nID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\n"
    "FELUCCA|FELUCCA|1.8.2|FELUCCA.FWS|123|"
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa|UNVERIFIED\n";

void delay_us(u32 us) { assert(us == 10000 || us == 200000); }
int husb_attached(void) { return attached != 0; }
void husb_bus_reset(void) { resets++; configured = 0; }
void husb_reset_toggle(int in) { assert(in == 0 || in == 1); recoveries++; }

void husb_bulk_setup(u8 address, u8 in, u8 out, u16 mps)
{
    assert(address == 1 && in == 1 && out == 2 && mps == 64);
    configured = 1;
}

enum husb_res husb_control(u8 address, u8 mps, const u8 setup[8], u8 *out, u32 *got)
{
    if (next_control) { enum husb_res res = next_control; next_control = HU_OK; return res; }
    *got = 0;
    assert(mps == 8 || mps == 64);
    const u16 value = ld16(setup + 2), length = ld16(setup + 6);
    if (setup[1] == 6) {
        const u8 *src = value == 0x100 ? descriptor : mock_config;
        assert((value == 0x100 && length <= sizeof descriptor) ||
               (value == 0x200 && length <= sizeof mock_config));
        memcpy(out, src, length);
        *got = length;
    } else {
        assert(length == 0);
        assert((setup[1] == 5 && address == 0 && value == 1) ||
               (setup[1] == 9 && address == 1 && value == 1) ||
               (setup[1] == 1 && setup[0] == 2) ||
               (setup[1] == 0xff && setup[0] == 0x21));
    }
    return HU_OK;
}

enum husb_res husb_bulk_out(const u8 *p, u32 len, u32 timeout)
{
    assert(configured && timeout > 0);
    assert(len == 31 && ld32(p) == 0x43425355u); /* No bulk OUT payloads. */
    bulk_count++;
    bot_tag = ld32(p + 4);
    bot_size = ld32(p + 8);
    bot_failed = 0;
    have_data = bot_size != 0;
    assert(p[13] == 0);
    switch (p[15]) {
        case 0x00:
            assert(bot_size == 0 && p[14] == 6);
            bot_failed = pending_attention;
            pending_attention = 0;
            break;
        case 0x03: assert(bot_size == 18 && p[14] == 6); break;
#ifdef FM1_USBC_EXPLORER
        case 0x12: assert(bot_size == 36 && p[14] == 6); break;
#endif
        case 0x25: assert(bot_size == 8 && p[14] == 10); break;
        case 0x28:
            disk_lba = bd32(p + 17);
            assert(bot_size == 512 && p[14] == 10 && bd16(p + 22) == 1 && disk_lba < disk_end);
            break;
        default: assert(!"Unexpected SCSI command");
    }
    assert(p[12] == (have_data ? 0x80 : 0));
    return HU_OK;
}

static void sector(u8 *out, u32 lba)
{
    memset(out, 0, 512);
    if (lba == 0 && volume_lba) {
        out[450] = 0x0c;
        st32(out + 454, volume_lba);
        st32(out + 458, short_partition ? 50 : disk_end - volume_lba);
        st16(out + 510, 0xaa55);
    } else if (lba == volume_lba) {
        out[0] = 0xeb;
        st16(out + 11, 512);
        out[13] = 1;
        st16(out + 14, RESERVED);
        out[16] = 2;
        st32(out + 32, 1000000);
        st32(out + 36, FAT_SIZE);
        st32(out + 44, 2);
        st16(out + 510, 0xaa55);
    } else if (lba == volume_lba + RESERVED) {
        st32(out + 12, damaged_fat ? 3 : 6); /* file deliberately fragmented */
        st32(out + 16, 0x0fffffff); /* FM1 directory */
        st32(out + 20, 0x0fffffff); /* INDEX.CSV */
        st32(out + 24, 0x0fffffff);
    } else if (lba == volume_lba + FAT_DATA) {
        out[0] = 0xe5;
        out[32] = 'V';
        out[43] = 8; /* ignore volume label */
        out[64] = 'D';
        out[75] = 0x10; /* ignore directory */
        memcpy(out + 96, "TEST    TXT", 11);
        st16(out + 96 + 26, 3);
        st32(out + 96 + 28, 700);
        if (!missing_fm1) {
            memcpy(out + 128, "FM1        ", 11);
            out[128 + 11] = 0x10;
            st16(out + 128 + 26, invalid_fm1_cluster ? 1 : 4);
        }
    } else if (lba == volume_lba + FAT_DATA + 1) {
        memset(out, 'A', 512);
        memcpy(out, "FM1 READ ONLY", 13);
    } else if (lba == volume_lba + FAT_DATA + 2) {
        memcpy(out, "INDEX   CSV", 11);
        out[11] = 0x20;
        st16(out + 26, 5);
        st32(out + 28, oversized_index ? 24577 : (u32)(sizeof index_contents - 1));
    } else if (lba == volume_lba + FAT_DATA + 3) {
        memcpy(out, index_contents, sizeof index_contents - 1);
    } else if (lba == volume_lba + FAT_DATA + 4) {
        memset(out, 'B', 512);
    } else {
        assert(!"unexpected disk read");
    }
}

enum husb_res husb_bulk_in(u8 *out, u32 len, u32 *got, u32 timeout)
{
    assert(configured && timeout > 0);
    *got = 0;
    if (inject_timeout) { inject_timeout = 0; return HU_TIMEOUT; }
    if (have_data) {
        have_data = 0;
        assert(len == bot_size);
        memset(out, 0, len);
        if (len == 8) {
            bs32(out, disk_end - 1);
            bs32(out + 4, block_size);
        }
#ifdef FM1_USBC_EXPLORER
        if (len == 36) out[4] = 31;
        if (len == 512 && detached_during_read) { attached = 0; return HU_GONE; }
        if (len == 512 && short_read) { *got = 511; return HU_OK; }
#endif
        if (len == 512) sector(out, disk_lba);
    } else {
        assert(len == 13);
        st32(out, 0x53425355);
        st32(out + 4, invalid_csw ? bot_tag + 1 : bot_tag);
        st32(out + 8, 0);
        out[12] = (u8)bot_failed;
    }
    *got = len;
    return HU_OK;
}

int main(void)
{
    struct usb_device reader;
    struct fat32 fs;
    struct fat_file file, index;
    struct fat_dir dir;
    u32 blocks, length;
    u8 output[1024];
    memcpy(mock_config, config, sizeof config);
    next_control = HU_GONE;
    assert(usb_enumerate(&reader) == DIAG_DISCONNECTED);
    mock_config[9] = 0;
    assert(usb_enumerate(&reader) == DIAG_DESCRIPTOR);
    memcpy(mock_config, config, sizeof config);
    mock_config[23] = 3;
    assert(usb_enumerate(&reader) == DIAG_UNSUPPORTED && reader.vid == 0x1234);
    memcpy(mock_config, config, sizeof config);
    assert(usb_enumerate(&reader) == DIAG_OK && resets == 4);
    assert(reader.vid == 0x1234 && reader.pid == 0x5678 && reader.class_code == 8 && reader.subclass == 6);

    block_size = 4096;
    assert(disk_open(&reader, &blocks) == DIAG_BLOCK_SIZE);
    block_size = 512;
    pending_attention = 1;
    assert(disk_open(&reader, &blocks) == DIAG_OK && blocks == disk_end);
    assert(disk_read(0, disk_end, output) == DIAG_RANGE);
    invalid_csw = 1;
    assert(disk_read(0, 0, output) == DIAG_TRANSFER && recoveries == 2);
    invalid_csw = 0;
    inject_timeout = 1;
    assert(disk_read(0, 0, output) == DIAG_TIMEOUT && recoveries == 4);

    struct block_device disk = {0, blocks, disk_read};
    short_partition = 1;
    assert(fat32_mount(&fs, disk) == DIAG_FAT);
    short_partition = 0;
    for (unsigned layout = 0; layout < 2; ++layout, volume_lba = 0) {
        assert(fat32_mount(&fs, disk) == DIAG_OK);
        fat32_root(&fs, &dir);
        assert(fat32_next(&fs, &dir, &file) == DIAG_OK);
        assert(strcmp(file.name, "TEST.TXT") == 0 && file.size == 700);
        assert(fat32_open_fm1_index(&fs, &index) == DIAG_OK);
        assert(strcmp(index.name, "INDEX.CSV") == 0 &&
               index.size == sizeof index_contents - 1);
        assert(fat32_read_prefix(&fs, &index, output, sizeof output, &length) == DIAG_OK);
        assert(length == sizeof index_contents - 1);
        assert(memcmp(output, "FM1CAT|1\n", 9) == 0);
        missing_fm1 = 1;
        assert(fat32_open_fm1_index(&fs, &index) == DIAG_END);
        missing_fm1 = 0;
        invalid_fm1_cluster = 1;
        assert(fat32_open_fm1_index(&fs, &index) == DIAG_FAT);
        invalid_fm1_cluster = 0;
        oversized_index = 1;
        assert(fat32_open_fm1_index(&fs, &index) == DIAG_LIMIT);
        oversized_index = 0;
        assert(fat32_next(&fs, &dir, &file) == DIAG_END);
        assert(fat32_read_prefix(&fs, &file, output, sizeof output, &length) == DIAG_OK && length == 700);
        assert(memcmp(output, "FM1 READ ONLY", 13) == 0 && output[511] == 'A' && output[512] == 'B');
        damaged_fat = 1;
        assert(fat32_read_prefix(&fs, &file, output, sizeof output, &length) == DIAG_FAT);
        damaged_fat = 0;
    }
#ifdef FM1_USBC_EXPLORER
    mock_config[13] = 3; /* declares three endpoints, provides two */
    assert(usb_enumerate(&reader) == DIAG_DESCRIPTOR);
    mock_config[13] = 2;
    assert(usbc_connect(&reader, &disk, USB_POWER_ABSENT) == DIAG_NO_VBUS && inits == 0);
    assert(usbc_connect(&reader, &disk, USB_POWER_UNKNOWN) == DIAG_POWER_UNVERIFIED && inits == 0);
    attached = 0;
    assert(usbc_connect(&reader, &disk, USB_POWER_QUALIFIED) == DIAG_DISCONNECTED && inits == 1);
    attached = 1;
    assert(usbc_connect(&reader, &disk, USB_POWER_QUALIFIED) == DIAG_OK && disk.count == disk_end && inits == 2);
    short_read = 1;
    assert(disk.read(disk.context, 0, output) == DIAG_TRANSFER);
    short_read = 0;
    detached_during_read = 1;
    assert(disk.read(disk.context, 0, output) == DIAG_DISCONNECTED);
#endif
    printf("USB MSC simulado: VID/PID, BOT, timeout, CSW, FAT32 MBR/superfloppy, fichero fragmentado (%u CBW de solo lectura).\n", bulk_count);
}
