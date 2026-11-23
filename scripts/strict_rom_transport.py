#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""One-shot FM-1 ROM transport over an in-memory Bulk-Only Transport model."""
import argparse
from binascii import crc_hqx
from dataclasses import dataclass
from hashlib import sha256
import json
from pathlib import Path
import struct
from threading import Lock

from validate import APP, RETAINED

IMAGE_DIGEST = "a0012d9f32b414ee38aa8b76e0f412b64a77c4163001037a44b77ac8eda91cbb"
IMAGE_SIZE = 4100
BLOCK_SIZE = 512
INQUIRY = bytes.fromhex("12 00 00 00 24 00")
PROBE, WRITE_RAM, JUMP = (bytes.fromhex(op) for op in ("FC40", "FB06", "FB08"))
CBW = struct.Struct("<4sIIBBB16s")
CSW = struct.Struct("<4sIIB")
ROM_ID = (0x4C4A, 0x8057, 0x08, 0x06, 0x50)
CIPHER = bytes.fromhex("c3 cf c0 e8 ce d2 b0 ae c4 e3 a3 ac d3 f1 c1 d6")
INQUIRY_DATA = b"\x00\x80\x00\x01\x1f\x00\x00\x00WL82    UBOOT1.00       1.00"


class Stop(ValueError):
    """A terminal decision. No recovery transfer or retry is permitted."""


def require(condition, reason):
    if not condition:
        raise Stop(reason)


def verify_candidate(image):
    require(type(image) is bytes, "ARTIFACT_TYPE")
    require(len(image) == IMAGE_SIZE, "ARTIFACT_SIZE")
    require(sha256(image).hexdigest() == IMAGE_DIGEST, "ARTIFACT_SHA256")
    require(image[-8:] == b"APP1\x86\xb7\x01\x01" and crc_hqx(image[:-8], 0) == 0xB786,
            "ARTIFACT_APP1")


def transform(block):
    """Each block starts from the documented ROM seed, independent of earlier blocks."""
    crc = crc_hqx(b"\xff\xff", 0xFFFF)
    output = bytearray()
    for index, value in enumerate(block):
        addr = index % len(CIPHER)
        crc = crc_hqx(CIPHER[addr:addr + 1], crc)
        output.append(value ^ (crc & 0xFF))
    return bytes(output)


@dataclass(frozen=True)
class Command:
    cdb: bytes
    size: int
    payload: bytes = b""

    def frame(self, tag):
        flags = 0 if self.payload else 0x80
        return CBW.pack(b"USBC", tag, self.size, flags, 0, len(self.cdb), self.cdb)


def private(opcode, args=b""):
    return (opcode + args).ljust(16, b"\xff")


def upload(address, raw):
    require(APP <= address and address + len(raw) <= APP + IMAGE_SIZE <= RETAINED and
            (address - APP) % BLOCK_SIZE == 0 and len(raw) == min(BLOCK_SIZE, APP + IMAGE_SIZE - address),
            "REQUEST_RANGE")
    data = transform(raw)
    cdb = private(WRITE_RAM, struct.pack(">IHB", address, len(data), 0) + struct.pack("<H", crc_hqx(data, 0)))
    return Command(cdb, len(data), data)


def plan(image):
    yield Command(INQUIRY, 36)
    yield Command(private(PROBE), 16)
    for offset in range(0, len(image), BLOCK_SIZE):
        yield upload(APP + offset, image[offset:offset + BLOCK_SIZE])
    yield Command(private(JUMP, struct.pack(">IH", APP, 1)), 16)


def check_request(expected, tag, frame, data):
    # Exact equality covers signature, tag, LUN, direction, length, opcode, fields,
    # padding, address, CRC and payload. No packet goes to the backend before this.
    require(frame == expected.frame(tag) and data == expected.payload, "REQUEST_DENIED")


@dataclass(frozen=True)
class Reply:
    cbw_count: int
    out_count: int
    data: bytes
    csw: bytes


def check_reply(command, tag, reply):
    require(reply.cbw_count == CBW.size and reply.out_count == len(command.payload), "SHORT_WRITE")
    require(len(reply.csw) == CSW.size, "CSW_LENGTH")
    signature, sequence, residue, code = CSW.unpack(reply.csw)
    require(signature == b"USBS" and sequence == tag, "CSW_IDENTITY")
    require(code != 2, "CSW_PHASE_ERROR")
    if command.cdb.startswith(PROBE):
        if reply.data[2:6] == b"MFLR":
            raise Stop("RESIDENT_DETECTED")
        # Unknown, malformed or timed-out responses never count as a refusal.
        require((code, residue, reply.data) == (1, 16, b""), "RESIDENT_AMBIGUOUS")
        return
    require(code == 0, "CSW_FAILURE")
    require(residue == 0 and len(reply.data) == (0 if command.payload else command.size), "SHORT_TRANSFER")
    if command.cdb == INQUIRY:
        require(reply.data[8:16].rstrip(b" ") == b"WL82" and
                reply.data[16:32].rstrip(b" ") == b"UBOOT1.00", "INQUIRY_IDENTITY")
    if command.cdb.startswith(JUMP):
        require(reply.data[:2] == JUMP, "JUMP_UNCONFIRMED")


FAULTS = ("device-absent", "device-ambiguous", "wrong-interface", "unqualified-rom", "resident",
          "probe-ambiguous", "probe-timeout", "bad-inquiry", "bad-signature", "bad-tag", "short-csw",
          "phase-error", "failed-status", "short-cbw", "short-out", "residue", "extra-data",
          "timeout", "disconnect", "connect-error", "jump-no-ack", "jump-bad-echo", "forbidden-request")
STAGED_FAULTS = {
    "resident": 2, "probe-ambiguous": 2, "probe-timeout": 2, "bad-inquiry": 1,
    "bad-signature": 4, "bad-tag": 4, "short-csw": 4, "phase-error": 4, "failed-status": 4,
    "short-cbw": 4, "short-out": 4, "residue": 4, "extra-data": 4,
    "timeout": 4, "disconnect": 4, "connect-error": 1, "jump-no-ack": 12, "jump-bad-echo": 12,
}


class SimulatedRom:
    """BOT replies and fault injection for software tests, with volatile RAM in a bytearray."""

    def __init__(self, fault=None):
        self.fault = fault
        self.ram = bytearray()
        self.closed = False
        self.count = 0
        self.jumps = 0

    def qualify(self):
        require(self.fault not in ("device-absent", "device-ambiguous"), "DEVICE_COUNT")
        require(self.fault != "wrong-interface", "INTERFACE_IDENTITY")
        require(self.fault != "unqualified-rom", "ROM_UNQUALIFIED")
        # Only a simulated cold-ROM observation. A SCSI response is not a qualification.
        return ROM_ID

    def exchange(self, frame, out):
        require(not self.closed, "BACKEND_CLOSED")
        _, tag, length, direction, _, n, cdb = CBW.unpack(frame)
        self.count += 1
        op = cdb[:2]
        fault = self.fault if STAGED_FAULTS.get(self.fault) == self.count else None
        if op == JUMP:
            self.jumps += 1
        if fault in ("timeout", "probe-timeout", "jump-no-ack"):
            raise TimeoutError
        if fault in ("disconnect", "connect-error"):
            raise ConnectionError(fault)
        status, residue = 0, 0
        data = b""
        if cdb[:n] == INQUIRY:
            data = INQUIRY_DATA if fault != "bad-inquiry" else bytes(36)
        elif op == PROBE:
            status, residue = 1, 16
            if fault in ("resident", "probe-ambiguous"):
                status, residue = 0, 0
                data = (PROBE + b"MFLR").ljust(16, b"\0") if fault == "resident" else bytes(16)
        elif op == WRITE_RAM:
            at, size, reserved = struct.unpack_from(">IHB", cdb, 2)
            checksum, = struct.unpack_from("<H", cdb, 9)
            require(not direction and not reserved and size == length == len(out) and
                    at == APP + len(self.ram) and crc_hqx(out, 0) == checksum, "MODEL_WRITE_INVALID")
            self.ram.extend(transform(out))
        elif op == JUMP:
            require(len(self.ram) == IMAGE_SIZE and sha256(self.ram).hexdigest() == IMAGE_DIGEST,
                    "MODEL_RAM_MISMATCH")
            data = (b"\x00\x00" if fault == "jump-bad-echo" else JUMP).ljust(16, b"\0")
        else:
            raise Stop("MODEL_OPCODE_DENIED")
        signature = b"NOPE" if fault == "bad-signature" else b"USBS"
        if fault == "phase-error":
            status = 2
        if fault == "failed-status":
            status = 1
        if fault == "residue":
            residue = 1
        csw = CSW.pack(signature, tag + (fault == "bad-tag"), residue, status)
        return Reply(CBW.size - (fault == "short-cbw"), len(out) - (fault == "short-out"),
                     data + (b"x" if fault == "extra-data" else b""), csw[:-1] if fault == "short-csw" else csw)

    def close(self):
        self.ram.clear()
        self.closed = True


class Session:
    """Single-use capability over the in-memory backend. Never enumerates devices."""

    def __init__(self, fault=None):
        if fault is not None and fault not in FAULTS:
            raise ValueError("unknown simulation fault")
        self.fault = fault
        self.used = False
        self.lock = Lock()

    def run(self, image):
        with self.lock:
            if self.used:
                return {"result": "SESSION_TERMINAL", "attempted": [], "blocks": 0, "jumps": 0, "hardware_io": False}
            self.used = True
        rom = SimulatedRom(self.fault)
        attempted = []
        blocks = 0
        try:
            verify_candidate(image)
            require(rom.qualify() == ROM_ID, "INTERFACE_IDENTITY")
            for tag, command in enumerate(plan(image), 1):
                frame = command.frame(tag)
                if self.fault == "forbidden-request" and tag == 3:
                    frame = frame[:15] + b"\xfb\x04" + frame[17:]
                check_request(command, tag, frame, command.payload)
                op = command.cdb[:2]
                attempted.append(op.hex().upper())
                reply = rom.exchange(frame, command.payload)
                check_reply(command, tag, reply)
                blocks += op == WRITE_RAM
            result = "JUMP_ACKNOWLEDGED_IN_MODEL"
        except TimeoutError:
            result = "JUMP_UNCONFIRMED" if attempted[-1:] == ["FB08"] else "TIMEOUT"
        except ConnectionError as error:
            result = "CONNECTION_ERROR" if error.args == ("connect-error",) else "DISCONNECTED"
        except Stop as error:
            result = str(error)
        finally:
            rom.close()
        return {"result": result, "attempted": attempted, "blocks": blocks, "jumps": rom.jumps, "hardware_io": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", type=Path)
    parser.add_argument("--fault", choices=FAULTS)
    args = parser.parse_args()
    with args.image.open("rb") as stream:
        result = Session(args.fault).run(stream.read(IMAGE_SIZE + 1))
    print(json.dumps(result, sort_keys=True))
    return 0 if result["result"] == "JUMP_ACKNOWLEDGED_IN_MODEL" else 1


if __name__ == "__main__":
    raise SystemExit(main())
