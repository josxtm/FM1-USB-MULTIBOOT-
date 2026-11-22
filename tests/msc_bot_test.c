/* SPDX-License-Identifier: MIT
 * Host-only BOT framing and read-only policy tests; no USB hardware. */
#include <assert.h>
#include <string.h>
#include "msc_bot.h"

static void cbw(u8 *p, u32 tag, u32 size, u8 flags, u8 op)
{
    memset(p, 0, 31); st32(p, MB_MSC_SIGNATURE_CBW); st32(p + 4, tag);
    st32(p + 8, size); p[12] = flags; p[14] = 10; p[15] = op;
}
int main(void)
{
    u8 p[31] = {0}, s[13] = {0}; u32 tag, size, lba, bytes; int in; u8 n, status; u16 blocks;
    cbw(p, 7, 512, 0x80, 0x28); bs32(p + 17, 12); p[22] = 0; p[23] = 1;
    assert(mb_msc_validate_cbw(p, sizeof p, &tag, &size, &in, &n) == MB_MSC_OK);
    assert(tag == 7 && size == 512 && in && n == 10);
    assert(mb_msc_read_only_cdb(p + 15, n, &lba, &blocks, &bytes) && lba == 12 && blocks == 1 && bytes == 512);
    p[0] = 0; assert(mb_msc_validate_cbw(p, sizeof p, &tag, &size, &in, &n) == MB_MSC_MALFORMED);
    cbw(p, 7, 512, 0x80, 0x28); p[13] = 1; assert(mb_msc_validate_cbw(p, sizeof p, &tag, &size, &in, &n) == MB_MSC_MALFORMED);
    cbw(p, 7, 512, 0x00, 0x2a); assert(!mb_msc_read_only_cdb(p + 15, 10, &lba, &blocks, &bytes));
    cbw(p, 7, 512, 0x80, 0x28); p[14] = 0; assert(mb_msc_validate_cbw(p, sizeof p, &tag, &size, &in, &n) == MB_MSC_MALFORMED);
    memset(s, 0, sizeof s); st32(s, MB_MSC_SIGNATURE_CSW); st32(s + 4, 7); st32(s + 8, 0); s[12] = 0;
    assert(mb_msc_validate_csw(s, sizeof s, 7, 512, 512, &status) == MB_MSC_OK);
    s[4] = 8; assert(mb_msc_validate_csw(s, sizeof s, 7, 512, 512, &status) == MB_MSC_MALFORMED);
    st32(s + 4, 7); st32(s + 8, 1); assert(mb_msc_validate_csw(s, sizeof s, 7, 512, 512, &status) == MB_MSC_MALFORMED);
    st32(s + 8, 0); s[12] = 2; assert(mb_msc_validate_csw(s, sizeof s, 7, 512, 512, &status) == MB_MSC_DEVICE);
    s[12] = 3; assert(mb_msc_validate_csw(s, sizeof s, 7, 512, 512, &status) == MB_MSC_MALFORMED);
    assert(!mb_msc_read_only_cdb((const u8 *)"\x2a", 1, &lba, &blocks, &bytes));
    return 0;
}
