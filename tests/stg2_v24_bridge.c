/* SPDX-License-Identifier: MIT */
/* Reuse the exact v2.4 upstream MMIO/card fixture and loader, without executing target instructions. */
#define main mboot_v24_synthetic_cases
#include "sdboot_test.c"
#undef main

static uint8_t binary[SD_STAGE2_MAX];

int main(int argc, char **argv)
{
    assert(argc == 2);
    assert(mboot_v24_synthetic_cases() == 0);
    FILE *input = fopen(argv[1], "rb");
    assert(input);
    size_t length = fread(binary, 1, sizeof binary, input);
    assert(fgetc(input) == EOF && !ferror(input) && !fclose(input));
    assert(length >= 16 && length <= SD_STAGE2_MAX);
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
