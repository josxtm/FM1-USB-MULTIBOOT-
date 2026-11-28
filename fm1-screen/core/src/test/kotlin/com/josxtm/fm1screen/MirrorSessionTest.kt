package com.josxtm.fm1screen

import com.josxtm.fm1screen.protocol.ControlOrigin
import com.josxtm.fm1screen.protocol.FrameBuffer
import com.josxtm.fm1screen.protocol.FrameTiming
import com.josxtm.fm1screen.protocol.MirrorPackets
import com.josxtm.fm1screen.protocol.PanelState
import com.josxtm.fm1screen.protocol.SIDE
import com.josxtm.fm1screen.protocol.ScreenFrame
import com.josxtm.fm1screen.protocol.Wire
import com.josxtm.fm1screen.protocol.controlOrigin
import com.josxtm.fm1screen.transport.MirrorListener
import com.josxtm.fm1screen.transport.MirrorSession
import com.josxtm.fm1screen.transport.MirrorStats
import com.josxtm.fm1screen.transport.SessionClock
import org.junit.Assert.*
import org.junit.Test

class MirrorSessionTest {
    @Test fun passiveInputNeverEchoesMidiOrSendsControl() {
        val f = Fixture()
        f.session.configure(Wire.AUTO, maySend = true, watch = false, remote = false)
        f.input(hello())
        f.input(panel())
        f.input(byteArrayOf(0x90.toByte(), 61, 99, 62, 0, 0xF8.toByte()))
        f.session.key(3, true)
        f.advance(1800)
        assertTrue(f.writes.isEmpty())
        assertEquals(3, f.panel?.heldKeys)
        assertEquals(5L, f.stats.midiMessages)
        assertTrue(f.stats.bytesPerSecond > 0)
    }

    @Test fun staticModeKeepsPixelsAndAuthorizedCommandsWhileSkippingPhysicalTelemetry() {
        val static = Fixture()
        val dynamic = Fixture()
        static.session.panelTelemetry = false
        for (f in listOf(static, dynamic)) {
            f.authorize()
            f.input(panel())
            f.input(pixel(0, 0x7654))
            f.input(byteArrayOf(0x90.toByte(), 61, 99))
            f.advance(50)
            f.session.key(1, true)
        }
        assertNull(static.panel)
        assertEquals(3, dynamic.panel?.heldKeys)
        val pixels = ShortArray(SIDE * SIDE)
        val other = ShortArray(SIDE * SIDE)
        requireNotNull(static.frame).copyRgb565To(pixels)
        requireNotNull(dynamic.frame).copyRgb565To(other)
        assertArrayEquals(pixels, other)
        assertEquals(0x7654.toShort(), pixels[0])
        assertEquals(static.writes.map(ByteArray::toList), dynamic.writes.map(ByteArray::toList))
        assertArrayEquals(MirrorPackets.key(0, 1, true), static.writes.last())
    }

    @Test fun telemetryCoalescingRetainsRelativeStepsAndLatestAbsoluteState() {
        val updates = com.josxtm.fm1screen.protocol.PanelUpdates()
        val first = PanelState(1, 2, listOf(1, -1, 0, 0, 0, 0, 0), 10, 5, 0, false, 0, 0)
        val second = first.copy(heldButtons = 0, heldKeys = 4, master = 20, encoderSteps = List(7) { 2 })
        assertTrue(updates.offer(first))
        assertFalse(updates.offer(second))
        assertEquals(second.copy(encoderSteps = listOf(3, 1, 2, 2, 2, 2, 2)), updates.take())
        assertNull(updates.take())
        assertTrue(updates.offer(first))
        updates.clear()
        assertNull(updates.take())
    }

    @Test fun commandsWaitForCompatibilityAndAckAndCannotSave() {
        val f = Fixture()
        f.session.configure(Wire.AUTO, maySend = true, watch = true, remote = true)
        f.session.key(2, true)
        assertTrue(f.writes.isEmpty())
        f.input(hello())
        f.session.button(7, true)
        f.session.key(-1, true)
        f.session.turn(0, 0)
        assertTrue(f.writes.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { MirrorPackets.button(0, 7, true) }
        f.session.key(2, true)
        f.session.key(2, false)
        f.input(base(0x54, 3, 2, 1))
        assertEquals(1, f.writes.size)
        f.input(base(0x54, 0, 2, 1))
        assertEquals(2, f.writes.size)
        assertArrayEquals(MirrorPackets.key(1, 2, false), f.writes.last())
    }

    @Test fun rejectedCommandDisarmsAndDropsQueuedTouches() {
        val f = Fixture()
        f.authorize()
        f.session.key(0, true)
        f.session.key(1, true)
        f.input(base(0x54, 0, 2, 0))
        assertArrayEquals(MirrorPackets.releaseMirror(), f.writes.last())
        assertEquals(1, f.suspensions)
        f.input(hello())
        f.session.key(1, false)
        assertEquals(2, f.writes.size)
    }

    @Test fun missingAckRetriesSameSequenceThenReleases() {
        val f = Fixture()
        f.authorize()
        f.session.key(0, true)
        repeat(3) { f.advance(180) }
        assertEquals(4, f.writes.size)
        repeat(3) { assertArrayEquals(MirrorPackets.key(0, 0, true), f.writes[it]) }
        assertArrayEquals(MirrorPackets.releaseMirror(), f.writes.last())
        assertEquals(1, f.suspensions)
    }

    @Test fun duplicatePatchCannotOverwriteAndOldFrameRemainsImmutable() {
        val f = Fixture()
        f.input(hello())
        f.input(pixel(12, 0xF800))
        f.input(pixel(12, 0x07E0))
        f.advance(50)
        val first = requireNotNull(f.frame)
        f.input(pixel(13, 0x001F))
        f.advance(1000)
        val original = ShortArray(SIDE * SIDE)
        val latest = ShortArray(SIDE * SIDE)
        first.copyRgb565To(original)
        requireNotNull(f.frame).copyRgb565To(latest)
        assertEquals(0xF800.toShort(), original[0])
        assertEquals(0x001F.toShort(), latest[0])
        assertEquals(1L, f.stats.discardedPackets)
        assertEquals(0, f.stats.gaps)
    }

    @Test fun disconnectClosesOldSessionAndNextSessionStartsUnknown() {
        val f = Fixture()
        f.authorize()
        f.input(panel())
        f.input(pixel(4, 0xFFFF))
        f.advance(40)
        assertNotNull(f.frame)
        f.session.close()
        assertNull(f.frame)
        assertNull(f.panel)
        assertNull(f.stats.wire)
        assertArrayEquals(MirrorPackets.releaseMirror(), f.writes.last())
        f.input(hello())
        f.session.key(4, true)
        f.advance(8500)
        assertNull(f.stats.wire)
        assertEquals(1, f.writes.size)
        val replacement = Fixture()
        replacement.input(panel())
        assertNull(replacement.panel)
        assertTrue(replacement.writes.isEmpty())
    }

    @Test fun truncatedSysexCannotBeCompletedAcrossStreamReset() {
        val f = Fixture()
        val packet = hello()
        f.input(packet.copyOfRange(0, 6))
        f.session.configure(Wire.BASE, maySend = false, watch = false, remote = false)
        f.input(packet.copyOfRange(6, packet.size))
        assertNull(f.stats.wire)
        f.input(packet)
        assertEquals(Wire.BASE, f.stats.wire)
        assertTrue(f.writes.isEmpty())
    }

    @Test fun controlOriginSeparatesHardwareLocalAndUnknown() {
        assertEquals(ControlOrigin.UNKNOWN, controlOrigin(false, false, false))
        assertEquals(ControlOrigin.LOCAL, controlOrigin(false, true, true))
        assertEquals(ControlOrigin.DEVICE, controlOrigin(true, true, true))
        assertEquals(ControlOrigin.DEVICE, controlOrigin(false, false, true))
    }

    private class Fixture : MirrorListener, SessionClock {
        private var time = 1L
        val writes = mutableListOf<ByteArray>()
        var frame: ScreenFrame? = null
        var panel: PanelState? = null
        var stats = MirrorStats()
        var suspensions = 0
        val session = MirrorSession(this, { writes.add(it) }, this).apply { panelTelemetry = true }
        override fun elapsedMillis() = time
        override fun nanoTime() = time * 1_000_000L
        override fun frameBuffer(source: FrameBuffer?, timing: FrameTiming?) { frame = source?.freeze() }
        override fun panel(state: PanelState?) { panel = state }
        override fun stats(stats: MirrorStats) { this.stats = stats }
        override fun log(line: String) = Unit
        override fun controlSuspended(reason: String) { suspensions++ }
        fun advance(ms: Long) { time += ms; session.tick() }
        fun authorize() {
            session.configure(Wire.BASE, maySend = true, watch = true, remote = true)
            input(hello())
        }
        fun input(bytes: ByteArray) = session.receive(bytes, nanoTime())
    }
}

private fun hello() = base(0x50, 2, 1, 239, 239)
private fun pixel(sequence: Int, value: Int) = base(0x52, 0, 0, 1, 1, sequence, value ushr 8, value and 255)
private fun panel() = base(0x53, *IntArray(27).apply { this[2] = 3 })

/** Independent test encoder for documented inbound packets. */
private fun base(command: Int, vararg body: Int): ByteArray {
    val bytes = intArrayOf(0, 0x59, command, body.size, 0, 0) + body + (body.sum().inv() and 255)
    val bits = bytes.flatMap { value -> List(8) { shift -> value ushr shift and 1 } }
    val packed = bits.chunked(7).map { chunk ->
        chunk.withIndex().sumOf { (shift, bit) -> bit shl shift }.toByte()
    }
    return byteArrayOf(0xF0.toByte()) + packed.toByteArray() + byteArrayOf(0xF7.toByte())
}
