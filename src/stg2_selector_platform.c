/* SPDX-License-Identifier: MIT */
/* PC7/PC8 ownership follows the FM-1 branch of m-bootloader 395e6cc src/sdboot.c. */
#include "stg2_selector.h"
#include "stg2_sd.h"
#include "embedded_clock.h"
#include "embedded_keys.h"
#include "hw.h"
#include "lcd.h"
#include "keys.h"
#include "usbio.h"

#define SD_SLOW_DIVIDER 149u /* 60 MHz / 150 = 400 kHz */
#define SD_READ_DIVIDER 14u  /* 60 MHz / 15 = 4 MHz */

static struct stg2_selector selector;
static struct stg2_sd card;
static struct mb_lcd_list_renderer lcd;
static u32 read_poll_at;
static int checking, cancelled, poll_armed;
extern u8 stg2_bss_start[], stg2_bss_end[];

static int spi_exchange(void *context, u8 out, u8 *in)
{
    (void)context;
    WR(SPI1 + SPI_CON, (RD(SPI1 + SPI_CON) & ~SPI_DONE) | SPI_CLR);
    WR(SPI1 + SPI_BUF, out);
    csync();
    for (u32 wait = 0; wait < 400000u; ++wait) {
        if (RD(SPI1 + SPI_CON) & SPI_DONE) {
            *in = (u8)RD(SPI1 + SPI_BUF);
            return 1;
        }
    }
    return 0;
}

static void select_card(void *context, int enabled)
{
    (void)context;
    gp_set(PC, 7, enabled); /* m-card inverts the LCD select line for the card. */
}

static int spi_open(u32 divider)
{
    u8 unused;
    gp_set(PC, 8, 0);
    select_card(0, 0);
    WR(SPI1 + SPI_CON, 0x4021);
    if (!spi_exchange(0, 0, &unused)) return 0; /* ST7789 NOP */
    gp_dir_in(PC, 8);
    SETB(GPIO(PC) + GP_DIE, 1u << 8);
    SETB(GPIO(PC) + GP_PU, 1u << 8);
    CLRB(GPIO(PC) + GP_PD, 1u << 8);
    WR(SPI1 + SPI_CON, 0x4029);
    WR(SPI1 + SPI_BAUD, divider);
    return 1;
}

static void spi_close(int opened)
{
    u8 unused;
    select_card(0, 0);
    if (opened) (void)spi_exchange(0, 0xff, &unused);
    WR(SPI1 + SPI_CON, 0x4021);
    WR(SPI1 + SPI_BAUD, 4);
    gp_set(PC, 8, 1);
    gp_dir_out(PC, 8);
    select_card(0, 1); /* Physical LCD idle. No clock edges while returning ownership. */
}

static void card_wait(void *context)
{
    (void)context;
    delay_us(1000);
}

static enum diag_result card_read(void *context, u32 lba, u8 *bytes)
{
    if (checking && tb_ms_since(read_poll_at) >= 25) {
        read_poll_at = tb_now();
        int rec = !!(plat_boot_keys() & K_REC);
        if (!rec) poll_armed = 1;
        if (rec && poll_armed) cancelled = 1;
    }
    if (cancelled) return DIAG_DISCONNECTED;
    return stg2_sd_read(context, lba, bytes);
}

static void load_catalog(void)
{
    mb_lcd_list_row(&lcd, 1, "BUSCANDO SD SPI1", C_YELLOW);
    card.sectors = 0;
    int opened = spi_open(SD_SLOW_DIVIDER);
    enum diag_result result = opened ? stg2_sd_start(&card) : DIAG_TIMEOUT;
    if (result == DIAG_OK) WR(SPI1 + SPI_BAUD, SD_READ_DIVIDER);
    stg2_selector_mount(&selector, (struct block_device){&card, card.sectors, card_read}, result);
    spi_close(opened);
}

static void verify_file(void)
{
    selector.notice = "LEYENDO SHA-256";
    stg2_selector_draw(&selector, &lcd);
    int opened = spi_open(SD_READ_DIVIDER);
    if (opened) {
        checking = 1;
        poll_armed = !(plat_boot_keys() & K_REC);
        read_poll_at = tb_now();
        stg2_selector_verify(&selector);
        checking = 0;
        if (cancelled) selector.notice = "VERIF. CANCELADA";
        cancelled = 0;
    } else {
        selector.core.catalog.entries[selector.core.selected].integrity_verified = 0;
        selector.notice = "ERROR SPI1";
    }
    spi_close(opened);
}

static u32 scan_keys(void)
{
    return plat_menu_scan() | (plat_boot_keys() & K_REC);
}

static void run_selector(void)
{
    if (!lcd_init(BRD_FM1)) return;
    mb_lcd_list_renderer_init(&lcd, mb_lcd_physical_sink());
    card.bus = (struct stg2_sd_bus){0, spi_exchange, select_card, card_wait};
    load_catalog();
    stg2_selector_draw(&selector, &lcd);

    struct mb_key_adapter keys;
    struct mb_clock clock;
    mb_keys_init(&keys);
    mb_clock_init(&clock, timer_ticks_per_ms(), tb_now());
    u32 latch = scan_keys(); /* Ignore the PLAY held while the bootloader entered STG2. */
    while (lcd_operational()) {
        u32 raw = scan_keys();
        latch &= raw;
        enum mb_event event;
        if (!mb_clock_update(&clock, tb_now())) break;
        if (mb_keys_poll(&keys, raw & ~latch, clock.now_ms, &event)) {
            latch |= raw & (K_SEL | K_REC);
            enum stg2_action action = stg2_selector_event(&selector, event);
            if (action == STG2_EXIT) break;
            if (action == STG2_RELOAD) load_catalog();
            if (action == STG2_VERIFY) verify_file();
            if (action != STG2_NO_CHANGE) stg2_selector_draw(&selector, &lcd);
            /* A synchronous SD read can span multiple timer wraps. Rearm from now. */
            if (action == STG2_VERIFY || action == STG2_RELOAD) {
                latch |= scan_keys();
                mb_keys_init(&keys);
                mb_clock_init(&clock, timer_ticks_per_ms(), tb_now());
            }
        }
        delay_us(7000);
    }
    plat_keys_off();
}

void fm1_stg2_main(u32 lsb_mhz)
{
    if (lsb_mhz != STG2_LSB_MHZ) return;
    for (u8 *p = stg2_bss_start; p < stg2_bss_end; ++p) *p = 0;
    const u32 control = RD(TMR0), count = RD(TMR0 + 4), period = RD(TMR0 + 8);
    if (timer_start_unconfigured() && timer_set_ticks_per_ms(lsb_mhz * 1000u)) run_selector();
    WR(TMR0, 0x4000);
    WR(TMR0 + 8, period);
    WR(TMR0 + 4, count);
    WR(TMR0, control & ~(1u << 15));
    csync();
}
