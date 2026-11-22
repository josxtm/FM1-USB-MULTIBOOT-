/* SPDX-License-Identifier: MIT */
/* Exercise the platform's actual SPI1/GPIO handoff against mocked registers. */
#define HOST_TEST
#include "../src/stg2_selector_platform.c"
#include <assert.h>
#include <stdio.h>

static u32 port[9], spi[5];
static unsigned bytes, nop, deselected_clocks;
static int stalled;

u32 sim_rd(u32 address)
{
    if (address >= GPIO(PC) && address <= GPIO(PC) + GP_DIEH)
        return port[(address - GPIO(PC)) / 4];
    assert(address >= SPI1 && address <= SPI1 + SPI_CNT);
    return spi[(address - SPI1) / 4];
}

void sim_wr(u32 address, u32 value)
{
    if (address >= GPIO(PC) && address <= GPIO(PC) + GP_DIEH) {
        port[(address - GPIO(PC)) / 4] = value;
        return;
    }
    assert(address >= SPI1 && address <= SPI1 + SPI_CNT);
    if (address == SPI1 + SPI_CON) spi[0] = value & ~(SPI_CLR | SPI_DONE);
    else if (address == SPI1 + SPI_BUF) {
        ++bytes;
        if (!stalled) spi[0] |= SPI_DONE;
        if (!(port[0] & (1u << 7))) {
            if (spi[0] & SPI_FULL) {
                assert((port[2] & (1u << 8)) && value == 0xff);
                ++deselected_clocks;
            } else {
                assert(!(port[0] & (1u << 8)) && !(port[2] & (1u << 8)) && value == 0);
                ++nop;
            }
        } else {
            assert((spi[0] & SPI_FULL) && (port[2] & (1u << 8)));
        }
        spi[2] = 0xff;
    } else spi[(address - SPI1) / 4] = value;
}

int main(void)
{
    u8 input;
    assert(spi_open(SD_SLOW_DIVIDER));
    assert(nop == 1 && spi[1] == 149 && (spi[0] & SPI_FULL));
    assert(!(port[0] & (1u << 7)) && (port[2] & (1u << 8)));
    select_card(0, 1);
    assert(spi_exchange(0, 0xff, &input) && input == 0xff);
    spi_close(1);
    assert(bytes == 3 && deselected_clocks == 1);
    assert(spi[1] == 4 && !(spi[0] & SPI_FULL) && !(port[2] & (1u << 8)));
    assert((port[0] & 0x180u) == 0x180u);

    stalled = 1;
    assert(!spi_open(SD_SLOW_DIVIDER));
    spi_close(0);
    assert(bytes == 4 && nop == 2);
    assert(spi[1] == 4 && !(spi[0] & SPI_FULL) && !(port[2] & (1u << 8)));
    assert((port[0] & 0x180u) == 0x180u);
    puts("STG2 SPI1 MMIO simulation: panel NOP, bus ownership, exit and timeout restore OK");
    return 0;
}
