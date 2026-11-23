/* SPDX-License-Identifier: MIT */
/* Reuse the exact v2.4 upstream MMIO/card fixture and loader, without executing target instructions. */
#define main mboot_v24_synthetic_cases
#include "sdboot_test.c"
#undef main

static uint8_t binary[SD_STAGE2_MAX];

static void package_image(const char *path, size_t length)
{
    // The package fixtures place all used clusters within the SPI model's prefix.
    FILE *input = fopen(path, "rb");
    assert(input && fread(disk, 1, sizeof disk, input) == sizeof disk && !fclose(input));
    high_lba = 0;
    assert(!run() && loaded(binary, length));
    uint32_t base = core_le16(disk + 11) == 512 ? 0 : core_le32(disk + 454);
    assert(base < SECTORS);
    const uint8_t *bpb = disk + base * 512;
    uint32_t fat_sector = base + core_le16(bpb + 14);
    uint32_t first_data = fat_sector + bpb[16] * core_le32(bpb + 36);
    assert(first_data < SECTORS);
    uint8_t *root = disk + first_data * 512;
    assert(!memcmp(root + 32, "MBOOT      ", 11));
    uint32_t folder_lba = first_data + core_le16(root + 32 + 26) - 2;
    assert(folder_lba < SECTORS);
    uint8_t *menu_entry = disk + folder_lba * 512 + 64;
    assert(!memcmp(menu_entry, "MENU    BIN", 11));
    uint32_t first = core_le16(menu_entry + 26);
    assert(first >= 2 && first - 2 < SECTORS - first_data && first / 128 < SECTORS - fat_sector);
    uint32_t following = core_le32(disk + fat_sector * 512 + first * 4) & 0x0fffffff;
    assert(following > first + 1 && following < 0x0ffffff8);
    menu_entry[0] = 0xe5;
    assert(same(run(), "NO /MBOOT/MENU.BIN"));
    menu_entry[0] = 'M';
    disk[(first_data + first - 2) * 512] ^= 1;
    assert(same(run(), "BAD MENU.BIN"));
    printf("v2.4 loaded exact STG2 from 6D %s; fragmented, missing and corrupt MENU.BIN checked\n",
           base ? "MBR" : "superfloppy");
}

int main(int argc, char **argv)
{
    assert(argc == 2 || argc == 3);
    assert(mboot_v24_synthetic_cases() == 0);
    FILE *input = fopen(argv[1], "rb");
    assert(input);
    size_t length = fread(binary, 1, sizeof binary, input);
    assert(fgetc(input) == EOF && !ferror(input) && !fclose(input));
    assert(length >= 16 && length <= SD_STAGE2_MAX);
    if (argc == 3) {
        package_image(argv[2], length);
        return 0;
    }
    for (unsigned base = 0; base <= PARTITION; base += PARTITION) {
        format(base);
        uint32_t mboot = folder(2, 0, "MBOOT      ");
        file(mboot, 0, "MENU    BIN", binary, length, true);
        assert(!run() && loaded(binary, length));
    }
    format(PARTITION);
    uint32_t mboot = folder(2, 0, "MBOOT      ");
    file(mboot, 0, "MENU    BIN", binary, length, true);
    uint32_t first = core_le16(cluster(mboot) + 26);
    set_fat(first, 0x0fffffff);
    assert(same(run(), "BAD MENU.BIN"));
    binary[0] ^= 1;
    format(0);
    mboot = folder(2, 0, "MBOOT      ");
    file(mboot, 0, "MENU    BIN", binary, length, true);
    assert(same(run(), "BAD MENU.BIN"));
    puts("v2.4 loaded the exact compiled STG2 on fragmented FAT32 (MBR and superfloppy), rejected CRC/truncated chain");
    return 0;
}
