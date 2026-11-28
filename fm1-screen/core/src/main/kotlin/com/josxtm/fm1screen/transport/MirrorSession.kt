// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.transport

import com.josxtm.fm1screen.protocol.FrameBuffer
import com.josxtm.fm1screen.protocol.MirrorEvent
import com.josxtm.fm1screen.protocol.MirrorPackets
import com.josxtm.fm1screen.protocol.PanelState
import com.josxtm.fm1screen.protocol.SIDE
import com.josxtm.fm1screen.protocol.ScreenFrame
import com.josxtm.fm1screen.protocol.SysExFramer
import com.josxtm.fm1screen.protocol.Wire

data class MirrorStats(
    val wire: Wire? = null, val protocolVersion: Int = 0, val coverage: Int = 0,
    val patchesPerSecond: Int = 0, val syncsPerSecond: Int = 0, val displaysPerSecond: Int = 0,
    val bad: Int = 0, val gaps: Int = 0, val processingMs: Double? = null,
    val bytesPerSecond: Long = 0, val midiMessages: Long = 0, val discardedPackets: Long = 0,
    val decodingMs: Double? = null,
)

interface MirrorListener {
    fun frame(pixels: ScreenFrame?, receivedNanos: Long)
    fun panel(state: PanelState?)
    fun stats(stats: MirrorStats)
    fun log(line: String)
    fun controlSuspended(reason: String)
}

interface PanelOutput {
    fun key(index: Int, down: Boolean)
    fun button(index: Int, down: Boolean)
    fun turn(index: Int, delta: Int)
    fun master(position: Int)
    fun releaseControls()
}

interface SessionClock {
    fun elapsedMillis(): Long
    fun nanoTime(): Long
}

/** The adapter serializes all calls and schedules ticks. Only validated patches reach the framebuffer. */
class MirrorSession(
    private val clock: SessionClock,
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
    private var lastMessage = -1L
    private var nextProbe = Long.MAX_VALUE
    private var probe = 0
    private var refresh = true
    private var expected = -1
    private var dirtySince = -1L
    private var lastPublish = 0L
    private var rxNanos = 0L
    private var patchNanos = 0L
    private var patches = 0
    private var completed = 0
    private var published = 0
    private var invalid = 0
    private var gaps = 0
    private var metricsAt = clock.elapsedMillis()
    private var processingMs: Double? = null
    private var sequence = 0
    private data class Command(val op: Int, val packet: (Int) -> ByteArray)
    private data class Pending(val command: Command, val sequence: Int, val sentAt: Long, val attempts: Int)
    private val commands = ArrayDeque<Command>()
    private var pending: Pending? = null
    private var nextLog = 0L

    private var bytesReceived = 0L
    private var bytesPerSecond = 0L
    private var duplicates = 0L
    private var decodingMs: Double? = null

    fun configure(wireChoice: Wire, maySend: Boolean, watch: Boolean, remote: Boolean) {
        val changed = choice != wireChoice
        if (!maySend || !watch || changed) {
            commands.clear()
            pending = null
        } else if (controls && !remote && watch && !changed) {
            clearControls()
        }
        if (watching && (changed || !watch) && wire == Wire.BASE && compatibleOutput) {
            send(MirrorPackets.releaseMirror())
        }
        choice = wireChoice
        compatibleOutput = maySend
        watching = watch
        controls = remote
        if (changed) resetStream()
        refresh = true
        nextProbe = clock.elapsedMillis() + 500
    }

    fun receive(data: ByteArray, receivedNanos: Long) {
        if (!alive) return
        rxNanos = receivedNanos
        bytesReceived += data.size
        val now = clock.elapsedMillis()
        if (now >= nextLog && data.isNotEmpty()) {
            val prefix = data.take(16).joinToString(" ") {
                (it.toInt() and 255).toString(16).padStart(2, '0').uppercase()
            }
            observer.log("MIDI RX ${data.size} B · $prefix")
            nextLog = now + 350
        }
        framer.feed(data, 0, data.size)
    }

    fun button(index: Int, down: Boolean) {
        // SAVE remains unavailable as a remote physical gesture.
        if (index in 0..13 && index != 7) enqueue(1) { MirrorPackets.button(it, index, down) }
    }

    fun key(index: Int, down: Boolean) {
        if (index in 0..26) enqueue(2) { MirrorPackets.key(it, index, down) }
    }

    fun turn(index: Int, delta: Int) {
        if (index in 0..6 && delta != 0) enqueue(3) { MirrorPackets.encoder(it, index, delta.coerceIn(-127, 127)) }
    }

    fun master(position: Int) {
        if (position in 0..4096 || position == 0xFFFF) enqueue(4) { MirrorPackets.master(it, position) }
    }

    private fun enqueue(op: Int, packet: (Int) -> ByteArray) {
        if (!controls || !canControl()) return
        if (op == 4) commands.removeAll { it.op == 4 }
        if (commands.size >= 64) {
            clearControls()
            return
        }
        commands.addLast(Command(op, packet))
        advance(clock.elapsedMillis())
    }

    private fun canControl() = wire == Wire.BASE && version >= 2 && compatibleOutput && watching

    fun clearControls() {
        commands.clear()
        if (canControl()) {
            commands.addLast(Command(0, MirrorPackets::releasePanel))
            advance(clock.elapsedMillis())
        }
    }

    fun close() {
        if (!alive) return
        if (wire == Wire.BASE && compatibleOutput && watching) send(MirrorPackets.releaseMirror())
        alive = false
        resetStream()
    }

    private fun resetStream() {
        wire = null
        version = 0
        probe = 0
        expected = -1
        framebuffer.reset()
        framer.reset()
        dirtySince = -1L
        lastMessage = -1L
        processingMs = null
        decodingMs = null
        commands.clear()
        pending = null
        observer.frame(null, 0L)
        observer.panel(null)
        publishStats()
    }

    private fun message(bytes: ByteArray) {
        val start = clock.nanoTime()
        val protocol = MirrorPackets.identify(bytes) ?: return
        if ((choice != Wire.AUTO && protocol != choice) || (wire != null && wire != protocol)) return
        val event = MirrorPackets.decodeIdentified(protocol, bytes) ?: return
        decodingMs = (clock.nanoTime() - start).coerceAtLeast(0) / 1_000_000.0
        if (event == MirrorEvent.Invalid) {
            invalid++
            if (protocol == wire) refresh = true
            return
        }
        if (event is MirrorEvent.Ack) {
            val command = pending ?: return
            if (command.sequence == event.sequence && command.command.op == event.operation) {
                pending = null
                if (event.ok) {
                    advance(clock.elapsedMillis())
                } else {
                    recoverControls("El FM-1 rechazó el control ${event.operation}", clock.elapsedMillis())
                }
            }
            return
        }
        if (wire == null && (event is MirrorEvent.Panel || event == MirrorEvent.Sync)) return
        if (event is MirrorEvent.Hello && !event.enabled) {
            if (wire == protocol) resetStream()
            return
        }
        val now = clock.elapsedMillis()
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
                    if (expected >= 0 && it == ((expected - 1) and 255)) {
                        duplicates++
                        return
                    }
                    if (expected >= 0 && it != expected) {
                        gaps++
                        refresh = true
                    }
                    expected = (it + 1) and 255
                }
                framebuffer.apply(event.patch)
                patchNanos = rxNanos
                patches++
                if (dirtySince < 0L) dirtySince = now
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
        if (dirtySince < 0L) return
        dirtySince = -1L
        lastPublish = now
        published++
        processingMs = (clock.nanoTime() - patchNanos).coerceAtLeast(0) / 1_000_000.0
        observer.frame(framebuffer.freeze(), patchNanos)
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
            recoverControls("Sin ACK del panel · cierre de la sesión de control", now)
            return
        }
        val command = commands.removeFirstOrNull() ?: return
        val seq = sequence++ and 255
        if (send(command.packet(seq))) pending = Pending(command, seq, now, 1)
    }

    private fun recoverControls(reason: String, now: Long) {
        observer.log(reason)
        observer.controlSuspended(reason)
        send(MirrorPackets.releaseMirror())
        controls = false
        resetStream()
        refresh = true
        nextProbe = now + 1200
    }

    fun tick() {
        if (!alive) return
        val now = clock.elapsedMillis()
        if (lastMessage >= 0L && now - lastMessage > 7500 && watching) {
            resetStream()
            refresh = true
            nextProbe = now
        }
        val cadence = if (wire == Wire.BIG2) 60L else 28L
        if (dirtySince >= 0L && now - dirtySince >= cadence && now - lastPublish >= 16) publish(now)
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
            bytesPerSecond = bytesReceived * 1000 / (now - metricsAt)
            bytesReceived = 0
            publishStats()
            patches = 0
            completed = 0
            published = 0
            metricsAt = now
        }
    }

    private fun publishStats() = observer.stats(MirrorStats(wire, version, framebuffer.covered, patches, completed,
        published, invalid, gaps, processingMs, bytesPerSecond, framer.messages, duplicates + framer.discarded, decodingMs))

    companion object {
        private val PROBES = listOf(Wire.BIG2, Wire.BIG2, Wire.BIG2, Wire.BIG1, Wire.BASE)
    }
}
