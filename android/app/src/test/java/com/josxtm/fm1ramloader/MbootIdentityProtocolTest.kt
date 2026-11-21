package com.josxtm.fm1ramloader

import org.junit.Assert.*
import org.junit.Test

class MbootIdentityProtocolTest {
    private fun put32(out: ByteArray, at: Int, value: Long) {
        for (i in 0 until 4) out[at + i] = (value ushr (8 * i)).toByte()
    }

    private fun reply(command: Int = 0x12, version: String = "2.4-395e6cc"): ByteArray {
        val body = ByteArray(28 + version.length + 1)
        "MBOOT".toByteArray(Charsets.US_ASCII).copyInto(body)
        body[5] = 1
        body[6] = 1
        body[7] = 1
        put32(body, 10, 1_048_576)
        put32(body, 14, 0x856014)
        version.toByteArray(Charsets.US_ASCII).copyInto(body, 28)
        val raw = ByteArray(7 + body.size)
        raw[1] = 0x59
        raw[2] = command.toByte()
        raw[3] = body.size.toByte()
        raw[4] = (body.size ushr 8).toByte()
        body.copyInto(raw, 6)
        raw.lastIndex.let { pos -> raw[pos] = body.sumOf { it.toInt() and 255 }.inv().toByte() }
        return MbootIdentityProtocol.packFrame(raw)
    }

    @Test fun identityQueryContainsOnlyTheHardcodedSafeOpcode() {
        val request = MbootIdentityProtocol.identityQuery()
        assertEquals(0xf0.toByte(), request.first())
        assertEquals(0xf7.toByte(), request.last())
        assertTrue(request.size >= 9)
        assertTrue(request.all { (it.toInt() and 255) < 128 || it == 0xf0.toByte() || it == 0xf7.toByte() })
    }

    @Test fun strictFrameDecodesOfficialMbootLayout() {
        val identity = MbootIdentityProtocol.decodeFrame(reply())
        assertEquals("2.4-395e6cc", identity.version)
        assertEquals(1_048_576L, identity.capacity)
        assertEquals(0x856014L, identity.jedec)
        assertTrue(identity.ramApps)
        assertEquals(1, identity.kind)
    }

    @Test fun rejectsCommandsOtherThanIdentityAndMalformedPayloads() {
        assertThrows(IllegalArgumentException::class.java) {
            MbootIdentityProtocol.decodeFrame(reply(command = 0x21))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MbootIdentityProtocol.decodeFrame(reply(command = 0x23))
        }
        val corrupt = reply().copyOf()
        corrupt[5] = (corrupt[5].toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            MbootIdentityProtocol.decodeFrame(corrupt)
        }
    }

    @Test fun collectsSplitMessagesAndBoundsTheBuffer() {
        val msg = reply()
        val collector = MbootIdentityCollector()
        assertNull(collector.receive(msg.copyOfRange(0, 3)))
        assertNull(collector.receive(msg.copyOfRange(3, msg.size - 1)))
        assertArrayEquals(msg, collector.receive(byteArrayOf(0xf7.toByte())))
    }

    @Test fun rejectsWrongBoardFlashAndInvalidVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            MbootIdentityProtocol.decodeFrame(reply(version = ""))
        }
    }

    @Test fun rejectsNonCanonicalPaddingBits() {
        val frame = reply().copyOf()
        frame[frame.lastIndex - 1] = (frame[frame.lastIndex - 1].toInt() or 0x40).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            MbootIdentityProtocol.decodeFrame(frame)
        }
    }

    @Test fun rejectsTruncationAndBytesAfterTerminator() {
        val frame = reply()
        assertThrows(IllegalArgumentException::class.java) {
            MbootIdentityProtocol.decodeFrame(frame.copyOf(frame.lastIndex))
        }
        val collector = MbootIdentityCollector()
        assertThrows(IllegalArgumentException::class.java) {
            collector.receive(frame + byteArrayOf(0x01))
        }
    }

    @Test fun rejectsSecondStartWhileFirstMessageIsInFlight() {
        val frame = reply()
        val collector = MbootIdentityCollector()
        assertNull(collector.receive(frame.copyOfRange(0, 4)))
        assertThrows(IllegalArgumentException::class.java) {
            collector.receive(byteArrayOf(0xf0.toByte()))
        }
    }

    @Test fun enforcesOffsetCountAndMaximumCollectorFrame() {
        val collector = MbootIdentityCollector()
        assertThrows(IllegalArgumentException::class.java) {
            collector.receive(byteArrayOf(1, 2), offset = 2, count = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            collector.receive(ByteArray(193) { if (it == 0) 0xf0.toByte() else 0 })
        }
    }

    @Test fun detectsOneBitOpcodeMutationEvenWithOriginalChecksum() {
        val original = reply()
        // Decode the frame only to model a wire mutation. The checksum covers
        // the body, not opcode; the explicit command allow-list must catch it.
        val decoded = decodePackedForTest(original)
        decoded[2] = (decoded[2].toInt() xor 0x02).toByte() // 0x12 -> 0x10
        val mutated = MbootIdentityProtocol.packFrame(decoded)
        assertThrows(IllegalArgumentException::class.java) {
            MbootIdentityProtocol.decodeFrame(mutated)
        }
    }

    private fun decodePackedForTest(frame: ByteArray): ByteArray {
        var acc = 0
        var bits = 0
        val out = java.io.ByteArrayOutputStream()
        for (i in 1 until frame.lastIndex) {
            acc = acc or ((frame[i].toInt() and 255) shl bits)
            bits += 7
            while (bits >= 8) {
                out.write(acc and 255)
                acc = acc ushr 8
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}
