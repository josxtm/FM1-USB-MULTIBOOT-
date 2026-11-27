// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.transport

import android.os.Handler
import android.os.SystemClock
import com.josxtm.fm1screen.protocol.FrameBuffer
import com.josxtm.fm1screen.protocol.MirrorEvent
import com.josxtm.fm1screen.protocol.MirrorPackets
import com.josxtm.fm1screen.protocol.PanelState
import com.josxtm.fm1screen.protocol.SIDE
import com.josxtm.fm1screen.protocol.SysExFramer
import com.josxtm.fm1screen.protocol.Wire

data class MirrorStats(
    val wire: Wire? = null, val protocolVersion: Int = 0, val coverage: Int = 0,
    val patchesPerSecond: Int = 0, val syncsPerSecond: Int = 0, val displaysPerSecond: Int = 0,
    val bad: Int = 0, val gaps: Int = 0, val processingMs: Double? = null,
)

interface MirrorListener {
    fun frame(pixels: IntArray?, receivedNanos: Long)
    fun panel(state: PanelState?)
    fun stats(stats: MirrorStats)
    fun log(line: String)
}

/** All mutable state belongs to the MIDI handler. Only complete, validated patches reach the framebuffer. */
class MirrorSession(
    private val worker: Handler,
    private val send: (ByteArray) -> Boolean,
    private val observer: MirrorListener,
) {
    private val framebuffer = FrameBuffer()
    private val framer = SysExFramer(::message)
    private var wire: Wire? = null
    private var choice = Wire.AUTO
    private var compatibleOutput = false
    private var watching = false
    private var controls = false
    private var alive = true
    private var version = 0
    private var lastMessage = 0L
    private var nextProbe = Long.MAX_VALUE
    private var probe = 0
    private var refresh = true
    private var expected = -1
    private var dirtySince = 0L
    private var lastPublish = 0L
    private var rxNanos = 0L
    private var patchNanos = 0L
    private var patches = 0
    private var completed = 0
    private var published = 0
    private var invalid = 0
    private var gaps = 0
    private var metricsAt = SystemClock.elapsedRealtime()
    private var processingMs: Double? = null
    private var sequence = 0
    private data class Command(val op: Int, val packet: (Int) -> ByteArray)
    private data class Pending(val command: Command, val sequence: Int, val sentAt: Long, val attempts: Int)
    private val commands = ArrayDeque<Command>()
    private var pending: Pending? = null
    private var nextLog = 0L

    private val ticker = object : Runnable {
        override fun run() {
            if (!alive) return
            tick()
            worker.postDelayed(this, 16)
        }
    }

    init { worker.post(ticker) }

    fun configure(wireChoice: Wire, maySend: Boolean, watch: Boolean, remote: Boolean) {
        val changed = choice != wireChoice
        if (controls && (!remote || !watch || changed)) clearControls()
        if (watching && (changed || !watch) && wire == Wire.BASE && compatibleOutput) {
            send(MirrorPackets.releaseMirror())
        }
        choice = wireChoice
        compatibleOutput = maySend
        watching = watch
        controls = remote
        if (changed) resetStream()
        refresh = true
        nextProbe = SystemClock.elapsedRealtime() + 500
    }

    fun receive(data: ByteArray, receivedNanos: Long) {
        if (!alive) return
        rxNanos = receivedNanos
        val now = SystemClock.elapsedRealtime()
        if (now >= nextLog && data.isNotEmpty()) {
            val prefix = data.take(16).joinToString(" ") { "%02X".format(it.toInt() and 255) }
            observer.log("MIDI RX ${data.size} B · $prefix")
            nextLog = now + 350
        }
        framer.feed(data, 0, data.size)
    }

    fun button(index: Int, down: Boolean) {
        // SAVE remains unavailable as a remote physical gesture.
        if (index != 7) enqueue(1) { MirrorPackets.button(it, index, down) }
    }

    fun key(index: Int, down: Boolean) = enqueue(2) { MirrorPackets.key(it, index, down) }
    fun turn(index: Int, delta: Int) = enqueue(3) { MirrorPackets.encoder(it, index, delta.coerceIn(-127, 127)) }
    fun master(position: Int) = enqueue(4) { MirrorPackets.master(it, position) }

    private fun enqueue(op: Int, packet: (Int) -> ByteArray) {
        if (!controls || !canControl()) return
        if (op == 4) commands.removeAll { it.op == 4 }
        if (commands.size >= 64) {
            clearControls()
            return
        }
        commands.addLast(Command(op, packet))
        advance(SystemClock.elapsedRealtime())
    }

    private fun canControl() = wire == Wire.BASE && version >= 2 && compatibleOutput && watching

    fun clearControls() {
        commands.clear()
        if (canControl()) {
            commands.addLast(Command(0, MirrorPackets::releasePanel))
            advance(SystemClock.elapsedRealtime())
        }
    }

    fun close() {
        if (!alive) return
        if (wire == Wire.BASE && compatibleOutput && watching) send(MirrorPackets.releaseMirror())
        alive = false
        worker.removeCallbacks(ticker)
        resetStream()
    }

    private fun resetStream() {
        wire = null
        version = 0
        probe = 0
        expected = -1
        framebuffer.reset()
        dirtySince = 0L
        lastMessage = 0L
        commands.clear()
        pending = null
        observer.frame(null, 0L)
        observer.panel(null)
        publishStats()
    }

    private fun message(bytes: ByteArray) {
        val protocol = MirrorPackets.identify(bytes) ?: return
        if ((choice != Wire.AUTO && protocol != choice) || (wire != null && wire != protocol)) return
        val event = MirrorPackets.decode(protocol, bytes) ?: return
        if (event == MirrorEvent.Invalid) {
            invalid++
            if (protocol == wire) refresh = true
            return
        }
        if (event is MirrorEvent.Ack) {
            val command = pending ?: return
            if (command.sequence == event.sequence && command.command.op == event.operation) {
                pending = null
                if (!event.ok) observer.log("El FM-1 rechazó el control ${event.operation}")
                advance(SystemClock.elapsedRealtime())
            }
            return
        }
        if (wire == null && (event is MirrorEvent.Panel || event == MirrorEvent.Sync)) return
        if (event is MirrorEvent.Hello && !event.enabled) {
            if (wire == protocol) resetStream()
            return
        }
        val now = SystemClock.elapsedRealtime()
        lastMessage = now
        if (wire == null) {
            wire = protocol
            observer.log("SysEx verificado · ${protocol.title}")
            nextProbe = now + 1000
            publishStats()
        }
        when (event) {
            is MirrorEvent.Hello -> {
                if (version != event.version) {
                    version = event.version
                    publishStats()
                }
            }
            is MirrorEvent.Pixels -> {
                event.sequence?.let {
                    if (expected >= 0 && it != expected) {
                        gaps++
                        refresh = true
                    }
                    expected = (it + 1) and 255
                }
                framebuffer.apply(event.patch)
                patchNanos = rxNanos
                patches++
                if (dirtySince == 0L) dirtySince = now
            }
            is MirrorEvent.Panel -> observer.panel(event.state)
            MirrorEvent.Sync -> {
                completed++
                if (now - lastPublish >= 16) publish(now)
            }
            else -> Unit
        }
    }

    private fun publish(now: Long) {
        if (dirtySince == 0L) return
        dirtySince = 0L
        lastPublish = now
        published++
        processingMs = (System.nanoTime() - patchNanos).coerceAtLeast(0) / 1_000_000.0
        observer.frame(framebuffer.snapshot(), patchNanos)
    }

    private fun advance(now: Long) {
        if (!canControl()) return
        val current = pending
        if (current != null) {
            if (now - current.sentAt < 180) return
            if (current.attempts < 3) {
                send(current.command.packet(current.sequence))
                pending = current.copy(sentAt = now, attempts = current.attempts + 1)
                return
            }
            observer.log("Sin ACK del panel · controles liberados")
            commands.clear()
            pending = null
            send(MirrorPackets.releaseMirror())
            resetStream()
            refresh = true
            nextProbe = now + 1200
            return
        }
        val command = commands.removeFirstOrNull() ?: return
        val seq = sequence++ and 255
        if (send(command.packet(seq))) pending = Pending(command, seq, now, 1)
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        if (lastMessage != 0L && now - lastMessage > 7500 && watching) {
            resetStream()
            refresh = true
            nextProbe = now
        }
        val cadence = if (wire == Wire.BIG2) 60L else 28L
        if (dirtySince != 0L && now - dirtySince >= cadence && now - lastPublish >= 16) publish(now)
        if (watching && compatibleOutput && now >= nextProbe) {
            val active = wire ?: choice.takeUnless { it == Wire.AUTO } ?: PROBES[probe++ % PROBES.size]
            val first = wire == null
            val full = refresh || first || (active != Wire.BASE && framebuffer.covered < SIDE * SIDE &&
                now - lastPublish > 1200)
            send(MirrorPackets.request(active, full))
            nextProbe = now + if (first) 850L else 1000L
            if (!first) refresh = false
        }
        advance(now)
        if (now - metricsAt >= 1000L) {
            publishStats()
            patches = 0
            completed = 0
            published = 0
            metricsAt = now
        }
    }

    private fun publishStats() = observer.stats(MirrorStats(wire, version, framebuffer.covered, patches, completed,
        published, invalid, gaps, processingMs))

    companion object {
        private val PROBES = listOf(Wire.BIG2, Wire.BIG2, Wire.BIG2, Wire.BIG1, Wire.BASE)
    }
}
