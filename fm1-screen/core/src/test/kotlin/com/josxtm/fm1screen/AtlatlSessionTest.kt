package com.josxtm.fm1screen

import com.josxtm.fm1screen.performance.PresentationMode
import com.josxtm.fm1screen.protocol.LatestFrame
import com.josxtm.fm1screen.protocol.FrameBuffer
import com.josxtm.fm1screen.protocol.FrameTiming
import com.josxtm.fm1screen.protocol.MirrorPackets
import com.josxtm.fm1screen.protocol.PanelState
import com.josxtm.fm1screen.protocol.ScreenFrame
import com.josxtm.fm1screen.protocol.Wire
import com.josxtm.fm1screen.transport.DeviceIdentity
import com.josxtm.fm1screen.transport.MidiIngress
import com.josxtm.fm1screen.transport.MirrorListener
import com.josxtm.fm1screen.transport.MirrorScope
import com.josxtm.fm1screen.transport.MirrorSession
import com.josxtm.fm1screen.transport.MirrorStats
import com.josxtm.fm1screen.transport.SessionClock
import com.josxtm.fm1screen.transport.StreamState
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class AtlatlSessionTest {
    @Test fun sourceVerifiedIdentityDoesNotTrustVidPidAlone() {
        assertEquals(MirrorScope.FELUCCA, DeviceIdentity.scope("Felucca", 0x1209, 1))
        assertEquals(MirrorScope.FELUCCA, DeviceIdentity.scope("Hegelton Instruments – Félùcca", 0x1209, 1))
        assertEquals(MirrorScope.NONE, DeviceIdentity.scope("Hegelton Instruments Felucca", 0x1209, 2))
        assertEquals(MirrorScope.NONE, DeviceIdentity.scope("Unknown Felucca", 0x1209, 1))
        assertEquals(MirrorScope.FELUCCA, DeviceIdentity.scope("FM-1_742125", 0x1209, 1))
        assertEquals(MirrorScope.NONE, DeviceIdentity.scope("Generic USB MIDI", 0x1209, 1))
        assertEquals(MirrorScope.NONE, DeviceIdentity.scope("Felucca Update", 0x1209, 1))
        assertEquals(MirrorScope.NONE, DeviceIdentity.scope("Felucca", 0x1234, 1))
        assertEquals(MirrorScope.NONE, DeviceIdentity.scope("RedtailWorks HYP-1 FM-1", 0x1209, 1))
    }

    @Test fun initialV1RequestNeedsOpenPortAndConsentButNoPicture() {
        val f = Fixture()
        f.session.configure(Wire.AUTO, false, true, false, MirrorScope.FELUCCA)
        f.advance(1100)
        assertTrue(f.writes.isEmpty())
        f.session.configure(Wire.AUTO, true, false, false, MirrorScope.FELUCCA)
        f.advance(1100)
        assertTrue(f.writes.isEmpty())
        f.session.configure(Wire.BIG2, true, true, false, MirrorScope.FELUCCA)
        f.advance(1100)
        assertTrue(f.writes.isEmpty())
        f.session.configure(Wire.AUTO, true, true, false, MirrorScope.FELUCCA)
        f.advance(90)
        assertArrayEquals(bytes(0xf0, 0x7d, 0x46, 0x53, 1, 0xf7), f.writes.single())
        assertNull(f.stats.wire)
        f.session.configure(Wire.AUTO, true, true, true, MirrorScope.FELUCCA)
        f.session.key(0, true)
        f.advance(900)
        assertArrayEquals(bytes(0xf0, 0x7d, 0x46, 0x53, 0, 0xf7), f.writes.last())
    }

    @Test fun identifiesAtlatlByPackageAndVersionSeparatelyFromLcd() {
        val f = Fixture()
        f.watch()
        f.advance(100)
        f.advance(900)
        f.advance(250)
        assertArrayEquals(bytes(0xf0, 0x7d, 0x46, 0x4c, 1, 0xf7), f.writes.last())
        f.rx(bytes(0xf0, 0x7d, 0x46, 0x4c, 1) + "FELUCCA v1.2.5".toByteArray() + bytes(0, 0x7f, 0xf7))
        assertFalse(f.stats.identity.atlatl)
        f.advance(900)
        f.advance(250)
        assertArrayEquals(MirrorPackets.feluccaPackage(), f.writes.last())
        f.rx(packageIdentity("FM-1_742125"))
        assertTrue(f.stats.identity.atlatl)
        assertEquals("v1.2.5", f.stats.identity.version)
        assertNull(f.stats.wire)
    }

    @Test fun loaderReplyDisarmsEvenAfterRepeatedConfiguration() {
        val f = Fixture()
        f.watch()
        f.advance(100)
        f.rx(packageIdentity("ota-FM-1_74200"))
        f.watch()
        f.advance(20_000)
        assertEquals(1, f.writes.size)
        assertEquals(StreamState.BLOCKED, f.stats.state)
    }

    @Test fun fragmentedAndConcatenatedRowsAcceptRealtimeClockAtAnyBoundary() {
        val f = Fixture()
        f.watch()
        f.advance(100)
        val first = row(0)
        val next = row(1, 0xFFFF)
        f.rx(first.take(8).toByteArray())
        f.rx(bytes(0xf8, 0xf8) + first.copyOfRange(8, first.size) + next)
        f.advance(1100)
        assertEquals(Wire.BIG1, f.stats.wire)
        assertEquals(480, f.stats.coverage)
        assertEquals(2L, f.stats.clockMessages)
        assertEquals(0L, f.stats.discardedPackets)
        val image = ShortArray(240 * 240)
        requireNotNull(f.frame).copyRgb565To(image)
        assertEquals(0xF800.toShort(), image[0])
        assertEquals(0xFFFF.toShort(), image[240])
    }

    @Test fun staticOnAtlatlUsesSoundRepliesAndNeverRepollsFullPicture() {
        val f = Fixture()
        f.watch()
        f.advance(100)
        f.fullPicture()
        repeat(60) {
            f.advance(900)
            f.rx(sound())
        }
        assertEquals(1, f.fullRequests)
        assertEquals(1L, f.stats.fullFrames)
        assertEquals(StreamState.STATIC, f.stats.state)
        assertTrue(f.stats.replies >= 50)
        assertNotNull(f.frame)
    }

    @Test fun sparserV1WithoutHeartbeatKeepsStaticImage() {
        val f = Fixture()
        f.watch()
        f.advance(100)
        f.fullPicture()
        repeat(15) { f.advance(900) }
        assertEquals(1, f.fullRequests)
        assertEquals(StreamState.NO_CHANGES, f.stats.state)
        assertNotNull(f.frame)
    }

    @Test fun silenceAfterHeartbeatRepairsWithoutClearingVisibleFrame() {
        val f = Fixture()
        f.watch()
        f.advance(100)
        f.fullPicture()
        f.rx(sound())
        repeat(7) { f.advance(900) }
        assertEquals(2, f.fullRequests)
        assertNotNull(f.frame)
        assertEquals(StreamState.RECOVERING, f.stats.state)
        f.fullPicture()
        f.rx(sound())
        f.advance(1200)
        assertEquals(240 * 240, f.stats.coverage)
        assertEquals(2L, f.stats.fullFrames)
        assertEquals(1, f.stats.retries)
    }

    @Test fun partialFrameNoReplyAndInputLossUseBoundedBackoff() {
        val f = Fixture()
        f.watch()
        f.advance(100)
        f.rx(row(0))
        f.rx(row(1).copyOfRange(0, 10))
        f.session.inputLost(90)
        f.rx(row(1).copyOfRange(10, row(1).size))
        repeat(90) { f.advance(900) }
        assertEquals(4, f.fullRequests)
        assertEquals(1L, f.stats.blocks)
        assertEquals(90L, f.stats.lostBytes)
        f.session.requestRefresh()
        f.advance(900)
        assertEquals(5, f.fullRequests)
    }

    @Test fun unknownSilentSourceStopsAfterLimitedRequestsAndResumesByExplicitRequest() {
        val f = Fixture()
        f.watch()
        repeat(90) { f.advance(900) }
        assertEquals(4, f.fullRequests)
        assertEquals(StreamState.NO_RESPONSE, f.stats.state)
        val requests = f.writes.size
        repeat(40) { f.advance(900) }
        assertEquals(requests, f.writes.size)
        f.session.requestRefresh()
        f.advance(900)
        assertEquals(5, f.fullRequests)
    }

    @Test fun ingressNeverSplicesDiscardedFragmentsAndReportsLossBeforeNextChunk() {
        val inbox = MidiIngress(maxBytes = 12, maxChunks = 2)
        assertTrue(inbox.offer(bytes(0xf0, 0x7d), 0, 2, 10))
        assertFalse(inbox.offer(bytes(0x46, 0x53, 1), 0, 3, 11))
        assertFalse(inbox.offer(bytes(1, 2), 0, 2, 12))
        assertFalse(inbox.offer(bytes(0xf8, 3), 0, 1, 13))
        assertEquals(MidiIngress.Entry.Loss(7), inbox.take())
        val entry = inbox.take() as MidiIngress.Entry.Chunk
        assertArrayEquals(bytes(0xf8), entry.bytes)
        assertEquals(13L, entry.receivedNanos)
        assertNull(inbox.take())
        assertTrue(inbox.offer(bytes(0xf8), 0, 1, 14))
    }

    @Test fun tenMinutesOfScriptedAtlatlTrafficFast() = endurance(realSeconds = 0)

    @Test fun tenMinuteWallClockRun() {
        assumeTrue("Execute with FM1_WALL_CLOCK=1", System.getenv("FM1_WALL_CLOCK") == "1")
        endurance(realSeconds = 600)
    }

    private fun endurance(realSeconds: Int) {
        val f = Fixture(useHandoff = true)
        f.session.presentationMode = PresentationMode.ULTRA
        val started = System.nanoTime()
        f.watch()
        f.advance(100)
        f.fullPicture()
        var seenWrites = 0
        val inputQueue = MidiIngress()
        val step = 20L
        val expected = ShortArray(240 * 240) { 0xF800.toShort() }
        repeat(600_000 / step.toInt()) { tick ->
            f.advance(step)
            var heartbeat = false
            while (seenWrites < f.writes.size) {
                if (isV1Request(f.writes[seenWrites++])) heartbeat = true
            }
            if (heartbeat) f.rx(sound())
            if (tick % 2 == 0) {
                val y = tick % 240
                val color = tick and 65535
                val row = row(y, color)
                expected.fill(color.toShort(), y * 240, (y + 1) * 240)
                inputQueue.offer(row, 0, 5, f.nanoTime())
                inputQueue.offer(bytes(0xf8), 0, 1, f.nanoTime())
                inputQueue.offer(row, 5, row.size - 5, f.nanoTime())
            } else inputQueue.offer(bytes(0xf8), 0, 1, f.nanoTime())
            while (true) {
                when (val item = inputQueue.take()) {
                    is MidiIngress.Entry.Chunk -> f.session.receive(item.bytes, item.receivedNanos)
                    is MidiIngress.Entry.Loss -> fail("Fallo de entrada durante ensayo continuo: ${item.bytes}")
                    null -> break
                }
            }
            f.session.publishDueFrame()
            f.handoff.consume(f.latestPixels)
            if (realSeconds > 0) {
                val sleepMs = ((started + (tick + 1L) * step * 1_000_000L) - System.nanoTime()) / 1_000_000
                if (sleepMs > 0) Thread.sleep(sleepMs)
            }
        }
        if (realSeconds > 0) {
            val remaining = started + realSeconds * 1_000_000_000L - System.nanoTime()
            if (remaining > 0) Thread.sleep(remaining / 1_000_000 + 1)
            assertTrue(System.nanoTime() - started >= 600_000_000_000L)
        }
        assertEquals(1, f.fullRequests)
        assertEquals(0L, f.stats.discardedPackets)
        assertEquals(0L, f.stats.lostBytes)
        assertEquals(0, f.stats.bad)
        assertEquals(240 * 240, f.stats.coverage)
        assertTrue(f.stats.blocks >= 15_000)
        assertTrue(f.stats.clockMessages >= 29_900)
        assertTrue(f.stats.requests >= 650)
        assertArrayEquals(expected, f.latestPixels)
    }

    private class Fixture(private val useHandoff: Boolean = false) : SessionClock, MirrorListener {
        val handoff = LatestFrame()
        val latestPixels = ShortArray(240 * 240)
        private var time = 1000L
        val writes = mutableListOf<ByteArray>()
        val fullRequests get() = writes.count { isV1Request(it) && it[4] == 1.toByte() }
        var frame: ScreenFrame? = null
        var stats = MirrorStats()
        val session = MirrorSession(this, { writes.add(it) }, this)
        fun watch() = session.configure(Wire.AUTO, true, true, false, MirrorScope.FELUCCA)
        fun rx(b: ByteArray) = session.receive(b, nanoTime())
        fun advance(ms: Long) { time += ms; session.tick() }
        fun fullPicture() { for (y in 0 until 240) rx(row(y)) }
        override fun elapsedMillis() = time
        override fun nanoTime() = time * 1_000_000L
        override fun frameBuffer(source: FrameBuffer?, timing: FrameTiming?) {
            if (!useHandoff) frame = source?.freeze()
            else if (source != null && timing != null) handoff.offer(source, timing)
            else handoff.clear()
        }
        override fun panel(state: PanelState?) = Unit
        override fun stats(stats: MirrorStats) { this.stats = stats }
        override fun log(line: String) = Unit
        override fun controlSuspended(reason: String) = Unit
    }

    companion object {
        private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
        private fun isV1Request(b: ByteArray) = b.size == 6 && b[2] == 0x46.toByte() && b[3] == 0x53.toByte()
        // Synthetic reference packets from Atlatl V1.2.5 screen_mirror README/mirror.c, never hardware captures.
        private fun row(y: Int, color: Int = 0xF800): ByteArray {
            val a = color and 127
            val b = (color ushr 7) and 127
            val c = color ushr 14
            return bytes(0xf0, 0x7d, 0x46, 0x53, 1, y and 127, y ushr 7, 0, 0, 127, a, b, c, 113, a, b, c, 0xf7)
        }
        private fun sound() = bytes(0xf0, 0x7d, 0x46, 0x53, 2) + "T1 SYNTH".toByteArray() + bytes(0xf7)
        private fun packageIdentity(name: String): ByteArray {
            val fields = intArrayOf(0, 0x59, 0x11, 27, 0, 0)
            val body = IntArray(27) { if (it < name.length) name[it].code else 0 }
            val bits = (fields + body + (body.sum().inv() and 255)).flatMap { value -> List(8) { value ushr it and 1 } }
            return bytes(0xf0) + bits.chunked(7).map { chunk ->
                chunk.withIndex().sumOf { (shift, value) -> value shl shift }.toByte()
            }.toByteArray() + bytes(0xf7)
        }
    }
}
