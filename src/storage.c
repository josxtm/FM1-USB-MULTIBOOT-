/* SPDX-License-Identifier: MIT */
#include "storage.h"
#include "usbio.h"
#include "msc_bot.h"

#define ADDRESS 1u
#define MASS_STORAGE 0x08u
#define SCSI_TRANSPARENT 0x06u
#define BULK_ONLY 0x50u
#define USB_TIMEOUT 800u

static const struct usb_device *active;
static u32 tag, block_count;
static u8 configuration[512];

static enum diag_result transport(enum husb_res result)
{
    if (result == HU_OK) return DIAG_OK;
    if (result == HU_GONE) return DIAG_DISCONNECTED;
    if (result == HU_TIMEOUT) return DIAG_TIMEOUT;
    return DIAG_TRANSFER;
}

static enum husb_res control(u8 address, u8 mps, u8 type, u8 request, u16 value,
                             u16 index, u8 *data, u16 size, u32 *received)
{
    u8 setup[8] = {type, request};
    st16(setup + 2, value);
    st16(setup + 4, index);
    st16(setup + 6, size);
    return husb_control(address, mps, setup, data, received);
}

static int full_speed_bulk(u16 size)
{
    return size == 8 || size == 16 || size == 32 || size == 64;
}

static enum diag_result parse_configuration(struct usb_device *dev, const u8 *bytes, u32 length)
{
    u8 is_storage = 0, have_interface = 0, ep_in = 0, ep_out = 0;
    u16 in_size = 0, out_size = 0;
    for (u32 pos = 0; pos < length; ) {
        const u8 *d = bytes + pos;
        if (length - pos < 2 || d[0] < 2 || d[0] > length - pos) return DIAG_DESCRIPTOR;
        if (d[1] == 4) {
            if (d[0] < 9) return DIAG_DESCRIPTOR;
            is_storage = d[3] == 0 && d[5] == MASS_STORAGE &&
                         d[6] == SCSI_TRANSPARENT && d[7] == BULK_ONLY;
            if ((!have_interface && d[3] == 0) || is_storage) {
                have_interface = 1;
                dev->interface = d[2];
                dev->class_code = d[5];
                dev->subclass = d[6];
                dev->protocol = d[7];
            }
            ep_in = ep_out = 0;
            in_size = out_size = 0;
        } else if (d[1] == 5 && is_storage) {
            if (d[0] < 7) return DIAG_DESCRIPTOR;
            const u16 size = ld16(d + 4);
            if ((d[3] & 3u) == 2u && (d[2] & 15u) && !(d[2] & 0x70u) && full_speed_bulk(size)) {
                if (d[2] & 0x80u) { ep_in = d[2]; in_size = size; }
                else { ep_out = d[2]; out_size = size; }
            }
            if (ep_in && ep_out && in_size == out_size) {
                dev->ep_in = ep_in;
                dev->ep_out = ep_out;
                dev->bulk_mps = in_size;
                return DIAG_OK;
            }
        }
        pos += d[0];
    }
    return DIAG_UNSUPPORTED;
}

enum diag_result usb_enumerate(struct usb_device *dev)
{
    u8 descriptor[18];
    u32 got = 0;
    *dev = (struct usb_device){0};
    husb_bus_reset();
    enum husb_res res = control(0, 8, 0x80, 6, 0x0100, 0, descriptor, 8, &got);
    if (res != HU_OK) return transport(res);
    if (got != 8 || descriptor[0] != 18 || descriptor[1] != 1 || !full_speed_bulk(descriptor[7]))
        return DIAG_DESCRIPTOR;
    dev->mps0 = descriptor[7];
    res = control(0, dev->mps0, 0, 5, ADDRESS, 0, 0, 0, &got);
    if (res != HU_OK) return transport(res);
    delay_us(10000);
    res = control(ADDRESS, dev->mps0, 0x80, 6, 0x0100, 0, descriptor, 18, &got);
    if (res != HU_OK) return transport(res);
    if (got != 18 || descriptor[0] != 18 || descriptor[1] != 1 || !descriptor[17]) return DIAG_DESCRIPTOR;
    dev->vid = ld16(descriptor + 8);
    dev->pid = ld16(descriptor + 10);
    dev->device_class = descriptor[4];
    dev->device_subclass = descriptor[5];
    res = control(ADDRESS, dev->mps0, 0x80, 6, 0x0200, 0, configuration, 9, &got);
    if (res != HU_OK) return transport(res);
    if (got != 9 || configuration[0] != 9 || configuration[1] != 2) return DIAG_DESCRIPTOR;
    const u16 length = ld16(configuration + 2);
    if (length < 9 || length > sizeof configuration) return DIAG_DESCRIPTOR;
    res = control(ADDRESS, dev->mps0, 0x80, 6, 0x0200, 0, configuration, length, &got);
    if (res != HU_OK) return transport(res);
    if (got != length || configuration[0] != 9 || configuration[1] != 2 ||
        ld16(configuration + 2) != length || !configuration[5]) return DIAG_DESCRIPTOR;
    enum diag_result result = parse_configuration(dev, configuration, length);
    if (result != DIAG_OK) return result;
    res = control(ADDRESS, dev->mps0, 0, 9, configuration[5], 0, 0, 0, &got);
    if (res != HU_OK) return transport(res);
    husb_bulk_setup(ADDRESS, dev->ep_in & 15u, dev->ep_out & 15u, dev->bulk_mps);
    return DIAG_OK;
}

static void clear_halt(int in)
{
    u32 got = 0;
    if (control(ADDRESS, active->mps0, 2, 1, 0, in ? active->ep_in : active->ep_out, 0, 0, &got) == HU_OK)
        husb_reset_toggle(in);
}

static void recover(void)
{
    u32 got = 0;
    if (!husb_attached()) return;
    control(ADDRESS, active->mps0, 0x21, 0xff, 0, active->interface, 0, 0, &got);
    clear_halt(1);
    clear_halt(0);
}

/* The only bulk OUT is the 31-byte command wrapper. Storage data travels IN. */
static enum diag_result read_command(const u8 *cdb, u8 cdb_size, u8 *data, u32 size)
{
    u8 cbw[31], csw[13];
    u32 got = 0, status_size = 0;
    mb_msc_build_cbw(cbw, ++tag, cdb, cdb_size, size, size != 0);
    u32 command_lba = 0, command_bytes = 0;
    u16 command_blocks = 0;
    u8 command_len = 0;
    int command_in = 0;
    if (mb_msc_validate_cbw(cbw, sizeof cbw, &tag, &command_bytes, &command_in,
                            &command_len) != MB_MSC_OK || command_len != cdb_size ||
        !mb_msc_read_only_cdb(cdb, cdb_size, &command_lba, &command_blocks, &command_bytes) ||
        command_bytes != size || (size && !command_in) || (!size && command_in))
        return DIAG_UNSUPPORTED;

    enum husb_res res = husb_bulk_out(cbw, sizeof cbw, USB_TIMEOUT);
    if (res != HU_OK) goto bad;
    if (size) {
        res = husb_bulk_in(data, size, &got, USB_TIMEOUT);
        if (res == HU_STALL) {
            clear_halt(1);
            got = 0;
            res = husb_bulk_in(data, size, &got, USB_TIMEOUT);
        }
        if (res != HU_OK) goto bad;
    }
    res = husb_bulk_in(csw, sizeof csw, &status_size, USB_TIMEOUT);
    if (res == HU_STALL) {
        clear_halt(1);
        res = husb_bulk_in(csw, sizeof csw, &status_size, USB_TIMEOUT);
    }
    if (res != HU_OK) goto bad;
    u8 status = 0;
    enum mb_msc_result framing = mb_msc_validate_csw(csw, status_size, tag, size, got, &status);
    if (framing == MB_MSC_MALFORMED) {
        recover();
        return DIAG_TRANSFER;
    }
    if (framing == MB_MSC_DEVICE) return DIAG_SCSI;
    if (framing != MB_MSC_OK) {
        recover();
        return DIAG_TRANSFER;
    }
    if (got == size) return DIAG_OK;
    recover();
    return DIAG_TRANSFER;

bad:
    if (res != HU_GONE) recover();
    return transport(res);
}

enum diag_result disk_open(const struct usb_device *dev, u32 *blocks)
{
    static const u8 ready[6] = {0};
    static const u8 request_sense[6] = {0x03, 0, 0, 0, 18};
    static const u8 read_capacity[10] = {0x25};
    u8 capacity[8], sense[18];
    active = dev;
    *blocks = block_count = 0;
    enum diag_result result = read_command(ready, sizeof ready, 0, 0);
    /* A newly inserted card may raise UNIT ATTENTION on the first TUR. */
    if (result == DIAG_SCSI) {
        result = read_command(request_sense, sizeof request_sense, sense, sizeof sense);
        if (result != DIAG_OK) return result;
        delay_us(200000);
        result = read_command(ready, sizeof ready, 0, 0);
    }
    if (result != DIAG_OK) return result;
    result = read_command(read_capacity, sizeof read_capacity, capacity, sizeof capacity);
    if (result != DIAG_OK) return result;
    if (bd32(capacity + 4) != 512) return DIAG_BLOCK_SIZE;
    const u32 last = bd32(capacity);
    if (last == 0xffffffffu) return DIAG_RANGE;
    *blocks = block_count = last + 1;
    return DIAG_OK;
}

enum diag_result disk_read(void *disk, u32 lba, u8 *out)
{
    (void)disk;
    if (lba >= block_count) return DIAG_RANGE;
    u8 cdb[10] = {0x28};
    bs32(cdb + 2, lba);
    cdb[8] = 1;
    return read_command(cdb, sizeof cdb, out, 512);
}

const char *diag_error(enum diag_result result)
{
    static const char *const names[] = {
        "OK", "DESCONECTADO", "TIEMPO AGOTADO", "ERROR USB/BOT", "DESCRIPTOR INVALIDO",
        "INTERFAZ NO COMPATIBLE", "LECTOR NO LISTO", "BLOQUE DISTINTO A 512", "FUERA DE RANGO",
        "FAT32 INVALIDA", "FIN DEL DIRECTORIO", "LIMITE DE DIAGNOSTICO"
    };
    return (unsigned)result < ARRAY_LEN(names) ? names[result] : "ERROR";
}
