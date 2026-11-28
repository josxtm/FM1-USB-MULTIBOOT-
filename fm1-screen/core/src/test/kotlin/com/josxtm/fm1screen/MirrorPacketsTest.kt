package com.josxtm.fm1screen

import com.josxtm.fm1screen.protocol.FrameBuffer
import com.josxtm.fm1screen.protocol.MirrorEvent
import com.josxtm.fm1screen.protocol.MirrorPackets
import com.josxtm.fm1screen.protocol.SIDE
import com.josxtm.fm1screen.protocol.SysExFramer
import com.josxtm.fm1screen.protocol.Wire
import org.junit.Assert.*
import org.junit.Test

class MirrorPacketsTest {
    private fun bytes(vararg values: Int) = values.map(Int::toByte).toByteArray()

    @Test fun baseRefreshRequestMatchesUpstreamFraming() {
        assertArrayEquals(bytes(0xF0, 0, 0x32, 0x41, 0x0A, 0, 0, 0, 1, 0x7D, 1, 0xF7),
            MirrorPackets.request(Wire.BASE, true))
        assertArrayEquals(bytes(0xF0, 0x7D, 0x42, 0x53, 0, 2, 1, 0xF7),
            MirrorPackets.request(Wire.BIG2, true))
        assertArrayEquals(bytes(0xF0, 0x7D, 0x46, 0x53, 0, 0xF7),
            MirrorPackets.request(Wire.BIG1, false))
    }

    @Test fun sysexCanCrossCallbacksAndRealtimeDoesNotEnterPayload() {
        val frames = mutableListOf<ByteArray>()
        val framer = SysExFramer(receive = frames::add)
        val splits = listOf(bytes(1, 0xF0, 0x7D), bytes(0xF8, 0x42), bytes(0x53, 3, 0xF7))
        splits.forEach { framer.feed(it, 0, it.size) }
        assertEquals(1, frames.size)
        assertEquals(MirrorEvent.Sync, MirrorPackets.decode(Wire.BIG2, frames.single()))
    }

    @Test fun overflowAndInterruptedSysexDiscardOnlyCurrentMessage() {
        val framer = SysExFramer { assertEquals(6, it.size) }
        val messages = bytes(0xF0, 4, 0x90, 0xF7, 0xF0) + ByteArray(5000) + bytes(0xF7, 0xF0, 0x7D, 0x42, 0x53, 3, 0xF7)
        framer.feed(messages, 0, messages.size)
    }

    @Test fun big1UpdatesOneRunAtRightEdge() {
        val message = bytes(0xF0, 0x7D, 0x46, 0x53, 1, 111, 1, 110, 1, 2, 31, 0, 0, 0xF7)
        val packet = MirrorPackets.decode(Wire.BIG1, message) as MirrorEvent.Pixels
        val fb = FrameBuffer()
        fb.apply(packet.patch)
        assertEquals(2, fb.covered)
        repeat(2) { assertEquals(0xFF0000FF.toInt(), fb.snapshot()[SIDE * SIDE - 1 - it]) }
        message[9] = 3
        assertEquals(MirrorEvent.Invalid, MirrorPackets.decode(Wire.BIG1, message))
    }

    @Test fun big2ValidatesInfoAndExpandsAColorAcrossRows() {
        val hello = bytes(0xF0, 0x7D, 0x42, 0x53, 1, 2, 112, 1, 112, 1, 0, 0xF7)
        assertEquals(MirrorEvent.Hello(2, true), MirrorPackets.decode(Wire.BIG2, hello))
        hello[10] = 1
        assertEquals(MirrorEvent.Invalid, MirrorPackets.decode(Wire.BIG2, hello))
        val redPatch = bytes(0xF0, 0x7D, 0x42, 0x53, 2, 7, 0, 8, 0, 2, 0, 2, 0, 115, 112, 0, 66, 0xF7)
        val packet = MirrorPackets.decode(Wire.BIG2, redPatch) as MirrorEvent.Pixels
        val fb = FrameBuffer()
        fb.apply(packet.patch)
        fb.apply(packet.patch)
        assertEquals(4, fb.covered)
        val image = fb.snapshot()
        assertEquals(0xFFFF0000.toInt(), image[7 + 8 * SIDE])
        assertEquals(0xFFFF0000.toInt(), image[8 + 9 * SIDE])
        assertEquals(0xFF000000.toInt(), image[9 + 9 * SIDE])
        redPatch[redPatch.lastIndex - 1] = 67
        assertEquals(MirrorEvent.Invalid, MirrorPackets.decode(Wire.BIG2, redPatch))
        fb.reset()
        assertEquals(0, fb.covered)
        assertEquals(0xFF000000.toInt(), fb.snapshot()[7 + 8 * SIDE])
    }

    @Test fun baseFillValidatesLengthAndChecksumBeforeChangingPixels() {
        val message = base(0x52, 0, 0, 2, 1, 255, 7, 224)
        val event = MirrorPackets.decode(Wire.BASE, message) as MirrorEvent.Pixels
        assertEquals(255, event.sequence)
        assertEquals(FrameBuffer.argb(0x07E0), 0xFF00FF00.toInt())
        assertArrayEquals(shortArrayOf(0x07E0, 0x07E0), event.patch.rgb565)
        message[message.lastIndex - 2] = (message[message.lastIndex - 2].toInt() xor 1).toByte()
        assertEquals(MirrorEvent.Invalid, MirrorPackets.decode(Wire.BASE, message))
    }

    @Test fun baseRectangleTruncatedColorIsRejectedAtomically() {
        val good = base(0x51, 2, 3, 3, 1, 0, 0xC2, 0xFF, 0xFF)
        assertArrayEquals(shortArrayOf(-1, -1, -1),
            (MirrorPackets.decode(Wire.BASE, good) as MirrorEvent.Pixels).patch.rgb565)
        val invalid = base(0x51, 2, 3, 3, 1, 0, 0xC2, 0xFF)
        assertEquals(MirrorEvent.Invalid, MirrorPackets.decode(Wire.BASE, invalid))
    }

    @Test fun basePanelAndAckGatingFieldsAreDecoded() {
        val body = IntArray(27)
        body[0] = 3
        body[6] = 0xFF
        body[14] = 16
        body[16] = 128
        body[23] = 1
        val panel = (MirrorPackets.decode(Wire.BASE, base(0x53, *body)) as MirrorEvent.Panel).state
        assertEquals(3, panel.heldButtons)
        assertEquals(-1, panel.encoderSteps.first())
        assertEquals(4096, panel.master)
        assertTrue(panel.playRed)
        assertEquals(1, panel.dimKeys)
        assertEquals(MirrorEvent.Ack(42, 2, true), MirrorPackets.decode(Wire.BASE, base(0x54, 42, 2, 1)))
    }

    @Test fun panelBuildersRejectUnmappedValues() {
        assertThrows(IllegalArgumentException::class.java) { MirrorPackets.encoder(0, 7, 1) }
        assertThrows(IllegalArgumentException::class.java) { MirrorPackets.key(0, 27, true) }
        assertThrows(IllegalArgumentException::class.java) { MirrorPackets.button(0, 14, true) }
        assertThrows(IllegalArgumentException::class.java) { MirrorPackets.master(0, 4097) }
        assertArrayEquals(base(0x54, 28, 2, 26, 0, 0), MirrorPackets.key(28, 26, false))
    }

    /** Independent reference packs consecutive bits into seven-bit MIDI bytes. */
    private fun base(command: Int, vararg payload: Int): ByteArray {
        val data = intArrayOf(0, 0x59, command, payload.size, 0, 0) + payload + (payload.sum().inv() and 0xFF)
        val bits = data.flatMap { value -> List(8) { i -> (value ushr i) and 1 } }
        val packed = bits.chunked(7).map { chunk -> chunk.withIndex().sumOf { (i, v) -> v shl i } }
        return bytes(0xF0, *packed.toIntArray(), 0xF7)
    }
}
