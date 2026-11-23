/* SPDX-License-Identifier: MIT
 * Full-speed MSC reader double. Physical data is never accessed by the harness. */
#include <assert.h>
#include <string.h>
#include "storage.h"

static struct block_device media;
static u32 tag, size, lba;
static u8 command;
static int configured, data_pending;

void usbc_mock_reader(struct block_device source)
{
    media = source;
}

void delay_us(u32 us) { assert(us == 10000 || us == 200000); }
void husb_init(void) { configured = 0; }
int husb_wait_attach(u32 timeout_ms) { return timeout_ms == 1500; }
int husb_attached(void) { return 1; }
void husb_bus_reset(void) { configured = 0; }
void husb_reset_toggle(int direction) { assert(direction == 0 || direction == 1); }

void husb_bulk_setup(u8 address, u8 in, u8 out, u16 mps)
{
    assert(address == 1 && in == 1 && out == 2 && mps == 64);
    configured = 1;
}

enum husb_res husb_control(u8 address, u8 mps, const u8 setup[8], u8 *out, u32 *got)
{
    static const u8 device[] = {18,1,0,2,0,0,0,64,0x34,0x12,0x78,0x56,0,1,0,0,0,1};
    static const u8 config[] = {9,2,32,0,1,1,0,0x80,50,9,4,0,0,2,8,6,0x50,0,7,5,0x81,2,64,0,0,7,5,2,2,64,0,0};
    const u16 value = ld16(setup + 2), length = ld16(setup + 6);
    assert(mps == 8 || mps == 64);
    *got = 0;
    if (setup[1] == 6) {
        assert((value == 0x100 && length <= sizeof device) || (value == 0x200 && length <= sizeof config));
        memcpy(out, value == 0x100 ? device : config, length);
        *got = length;
    } else {
        assert(!length && ((setup[1] == 5 && address == 0 && value == 1) ||
                           (setup[1] == 9 && address == 1 && value == 1)));
    }
    return HU_OK;
}

enum husb_res husb_bulk_out(const u8 *p, u32 length, u32 timeout)
{
    assert(configured && timeout && length == 31 && ld32(p) == 0x43425355u && !p[13]);
    tag = ld32(p + 4);
    size = ld32(p + 8);
    command = p[15];
    assert((command == 0x12 && size == 36 && p[14] == 6) ||
           (command == 0 && !size && p[14] == 6) ||
           (command == 0x25 && size == 8 && p[14] == 10) ||
           (command == 0x28 && size == 512 && p[14] == 10 && bd16(p + 22) == 1));
    assert(p[12] == (size ? 0x80 : 0));
    lba = bd32(p + 17);
    data_pending = size != 0;
    return HU_OK;
}

enum husb_res husb_bulk_in(u8 *out, u32 length, u32 *got, u32 timeout)
{
    assert(configured && timeout);
    memset(out, 0, length);
    *got = length;
    if (!data_pending) {
        assert(length == 13);
        st32(out, 0x53425355);
        st32(out + 4, tag);
        return HU_OK;
    }
    data_pending = 0;
    assert(length == size);
    if (command == 0x12) out[4] = 31;
    if (command == 0x25) { bs32(out, media.count - 1); bs32(out + 4, 512); }
    if (command == 0x28) {
        assert(lba < media.count);
        enum diag_result r = media.read(media.context, lba, out);
        if (r != DIAG_OK) {
            *got = 0;
            return r == DIAG_DISCONNECTED ? HU_GONE : HU_TIMEOUT;
        }
    }
    return HU_OK;
}
