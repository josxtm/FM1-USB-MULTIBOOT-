package com.josxtm.fm1screen

import com.josxtm.fm1screen.performance.PerformanceMonitor
import com.josxtm.fm1screen.performance.PresentationMode
import com.josxtm.fm1screen.performance.Stage
import com.josxtm.fm1screen.protocol.FrameBuffer
import com.josxtm.fm1screen.protocol.FrameTiming
import com.josxtm.fm1screen.protocol.LatestFrame
import com.josxtm.fm1screen.protocol.PanelState
import com.josxtm.fm1screen.protocol.Patch
import com.josxtm.fm1screen.protocol.SIDE
import com.josxtm.fm1screen.protocol.Wire
import com.josxtm.fm1screen.transport.MirrorListener
import com.josxtm.fm1screen.transport.MirrorScope
import com.josxtm.fm1screen.transport.MirrorSession
import com.josxtm.fm1screen.transport.MirrorStats
import com.josxtm.fm1screen.transport.SessionClock
import org.junit.Assert.*
import org.junit.Test

class PresentationTest {
    @Test fun mergesUnseenRegionsAndKeepsTheFirstTimestamp() {
        val source = FrameBuffer()
        val handoff = LatestFrame()
        val displayed = ShortArray(SIDE * SIDE)
        source.apply(Patch(3, 1, 2, 1, shortArrayOf(9, 8)))
        assertTrue(handoff.offer(source, FrameTiming(10, 15, 20)))
        source.clearDirty()
        source.apply(Patch(9, 2, 1, 1, shortArrayOf(4)))
        assertFalse(handoff.offer(source, FrameTiming(25, 25, 30)))
        val update = requireNotNull(handoff.consume(displayed))
        assertEquals(FrameTiming(10, 25, 30), update.timing)
        assertEquals(9.toShort(), displayed[SIDE + 3])
        assertEquals(8.toShort(), displayed[SIDE + 4])
        assertEquals(4.toShort(), displayed[2 * SIDE + 9])
        assertEquals(1L, handoff.superseded())
        assertEquals(12L, handoff.copiedBytes())
        assertNull(handoff.consume(displayed))
    }

    @Test fun resetBeforeNextPresentationDoesNotLeakPixelsFromOldDevice() {
        val source = FrameBuffer()
        val handoff = LatestFrame()
        val displayed = ShortArray(SIDE * SIDE)
        source.apply(Patch(136, 239, 2, 1, shortArrayOf(8, 9)))
        handoff.offer(source, FrameTiming(10, 10, 12))
        handoff.consume(displayed)
        assertEquals(9.toShort(), displayed[239 * SIDE + 137])
        assertTrue(handoff.clear())
        source.reset()
        source.apply(Patch(0, 0, 1, 1, shortArrayOf(7)))
        assertFalse(handoff.offer(source, FrameTiming(20, 20, 28)))
        assertNotNull(handoff.consume(displayed)?.timing)
        assertEquals(7.toShort(), displayed[0])
        assertEquals(0.toShort(), displayed[239 * SIDE + 137])
        assertTrue(handoff.clear())
        assertNull(requireNotNull(handoff.consume(displayed)).timing)
        assertTrue(displayed.all { it == 0.toShort() })
    }

    @Test fun unchangedPatchKeepsCoverageButDoesNotInvalidateTheImage() {
        val f = Fixture(PresentationMode.ULTRA)
        f.rx(row(0, 31))
        f.session.publishDueFrame()
        assertEquals(1, f.offers)
        f.time += 20
        f.rx(row(0, 31))
        assertNull(f.session.frameDelayMillis())
        f.session.publishDueFrame()
        assertEquals(1, f.offers)
        f.rx(row(0, 32))
        f.session.publishDueFrame()
        assertEquals(2, f.offers)
    }

    @Test fun idleTickIsIndependentOfModeAndPresentationWakeDoesNotSendMidi() {
        fun trace(mode: PresentationMode) = Fixture(mode).also { f ->
            f.session.configure(Wire.AUTO, true, true, false, MirrorScope.FELUCCA)
            repeat(8_200) { offset ->
                f.time = 1_000L + offset
                if (offset < 8_000 && offset % 13 == 7) f.rx(row((offset / 13) % SIDE, offset % 127 + 1))
                if (offset % 16 == 0) f.session.tick()
                f.session.publishDueFrame()
                f.consume()
            }
        }
        val ultra = trace(PresentationMode.ULTRA)
        val balanced = trace(PresentationMode.BALANCED)
        val eco = trace(PresentationMode.ECO)
        assertTrue(ultra.offers > balanced.offers)
        assertTrue(balanced.offers > eco.offers)
        assertTrue(ultra.writes.isNotEmpty())
        assertEquals(ultra.writes, balanced.writes)
        assertEquals(ultra.writes, eco.writes)
        assertArrayEquals(ultra.displayed, balanced.displayed)
        assertArrayEquals(ultra.displayed, eco.displayed)
    }

    @Test fun v2WaitsForSyncWithoutMakingUltraPublishAnIncompleteV2Frame() {
        val f = Fixture(PresentationMode.ULTRA, Wire.BIG2)
        // Big Screen 2 RECT with a red 2 × 2 run, then SYNC.
        val rect = bytes(0xf0, 0x7d, 0x42, 0x53, 2, 7, 0, 8, 0, 2, 0, 2, 0, 115, 112, 0, 66, 0xf7)
        f.rx(rect)
        assertEquals(60L, f.session.frameDelayMillis())
        f.session.publishDueFrame()
        assertEquals(0, f.offers)
        f.rx(bytes(0xf0, 0x7d, 0x42, 0x53, 3, 0xf7))
        f.session.publishDueFrame()
        assertEquals(1, f.offers)
    }

    @Test fun percentilesUseABoundedRollingWindowAndIgnoreInvalidTimestamp() {
        val monitor = PerformanceMonitor()
        repeat(600) { i -> monitor.record(Stage.RX_QUEUE, i * 1_000_000L) }
        monitor.record(Stage.RX_QUEUE, -1)
        val p = requireNotNull(monitor.snapshot()[Stage.RX_QUEUE])
        assertEquals(512, p.count)
        assertEquals(343.0, p.p50Ms, 0.0)
        assertEquals(574.0, p.p95Ms, 0.0)
        assertEquals(594.0, p.p99Ms, 0.0)
        monitor.clearSamples()
        assertTrue(monitor.snapshot().isEmpty())
    }

    @Test fun firstFragmentTimeSurvivesInterleavedClockAndReachesThePresentation() {
        val f = Fixture(PresentationMode.ULTRA)
        val monitor = PerformanceMonitor()
        f.session.performance = monitor
        val packet = row(0, 63)
        val first = f.nanoTime()
        f.rx(packet.copyOfRange(0, 6))
        f.time += 3
        f.rx(bytes(0xf8))
        f.time += 7
        f.rx(packet.copyOfRange(6, packet.size))
        f.session.publishDueFrame()
        assertEquals(FrameTiming(first, f.nanoTime(), f.nanoTime()), f.handoff.consume(f.displayed)?.timing)
        assertEquals(10.0, requireNotNull(monitor.snapshot()[Stage.SYSEX_SPAN]).p95Ms, 0.0)
    }

    private class Fixture(mode: PresentationMode, wire: Wire = Wire.BIG1) : SessionClock, MirrorListener {
        var time = 1_000L
        val handoff = LatestFrame()
        val displayed = ShortArray(SIDE * SIDE)
        val writes = mutableListOf<Pair<Long, List<Byte>>>()
        var offers = 0
        val session = MirrorSession(this, { packet -> writes.add(time to packet.toList()) }, this)

        init {
            session.presentationMode = mode
            session.configure(wire, false, false, false)
            handoff.consume(displayed)
        }

        override fun elapsedMillis() = time
        override fun nanoTime() = time * 1_000_000
        override fun frameBuffer(source: FrameBuffer?, timing: FrameTiming?) {
            if (source != null && timing != null) {
                handoff.offer(source, timing)
                offers++
            } else handoff.clear()
        }
        override fun panel(state: PanelState?) = Unit
        override fun stats(stats: MirrorStats) = Unit
        override fun log(line: String) = Unit
        override fun controlSuspended(reason: String) = Unit
        fun consume() { handoff.consume(displayed) }
        fun rx(packet: ByteArray) = session.receive(packet, nanoTime())
    }

    companion object {
        private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
        private fun row(y: Int, value: Int) = bytes(0xf0, 0x7d, 0x46, 0x53, 1, y and 127, y ushr 7,
            0, 0, 1, value, 0, 0, 0xf7)
    }
}
