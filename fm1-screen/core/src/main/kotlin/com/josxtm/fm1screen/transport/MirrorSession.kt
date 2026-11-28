// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.transport

import com.josxtm.fm1screen.performance.PerformanceMonitor
import com.josxtm.fm1screen.performance.PresentationMode
import com.josxtm.fm1screen.performance.Stage
import com.josxtm.fm1screen.protocol.FrameBuffer
import com.josxtm.fm1screen.protocol.FrameTiming
import com.josxtm.fm1screen.protocol.MirrorEvent
import com.josxtm.fm1screen.protocol.MirrorPackets
import com.josxtm.fm1screen.protocol.PanelState
import com.josxtm.fm1screen.protocol.SIDE
import com.josxtm.fm1screen.protocol.SysExFramer
import com.josxtm.fm1screen.protocol.Wire

enum class MirrorScope {
    NONE, FM1, FELUCCA;

    fun allows(wire: Wire): Boolean = when (this) {
        NONE -> false
        FM1 -> true
        FELUCCA -> wire == Wire.AUTO || wire == Wire.BIG1
    }
}

enum class MidiCategory(val title: String) {
    CLOCK("Clock F8"), NOTE_ON("Note On"), NOTE_OFF("Note Off"), CONTROL("Control Change"),
    SYSEX("SysEx"), GRAPHICS("Gráficos"), ERROR("Errores"), SESSION("Sesión"), OTHER("Otro MIDI")
}

enum class StreamState(val title: String) {
    PASSIVE("Escucha pasiva"), BLOCKED("Solicitud bloqueada"), STARTING("Esperando gráficos"),
    UPDATING("Recibiendo píxeles"), STATIC("Imagen estática, firmware responde"),
    NO_CHANGES("Sin cambios, protocolo sin confirmación periódica"),
    RECOVERING("Resincronizando"), NO_RESPONSE("Sin respuesta gráfica comprobada")
}

data class FirmwareIdentity(val reportedName: String? = null, val packageId: String? = null) {
    val atlatl get() = packageId?.matches(Regex("FM-1_742[0-9A-Fa-f]{2,8}")) == true
    val version get() = reportedName?.removePrefix("FELUCCA ")?.takeIf { it.startsWith("v") }
}

data class MirrorStats(
    val wire: Wire? = null, val protocolVersion: Int = 0, val coverage: Int = 0,
    val patchesPerSecond: Int = 0, val syncsPerSecond: Int = 0, val displaysPerSecond: Int = 0,
    val bad: Int = 0, val gaps: Int = 0, val processingMs: Double? = null,
    val bytesPerSecond: Long = 0, val midiMessages: Long = 0, val discardedPackets: Long = 0,
    val decodingMs: Double? = null,
    val state: StreamState = StreamState.PASSIVE,
    val txMessages: Long = 0, val txBytesPerSecond: Long = 0, val requests: Long = 0,
    val replies: Long = 0, val lastSysExTx: String? = null, val lastSysExRx: String? = null,
    val sinceGraphicsMs: Long? = null, val sinceSessionMs: Long? = null, val blocks: Long = 0, val fullFrames: Long = 0,
    val retries: Int = 0, val lostBytes: Long = 0, val clockMessages: Long = 0,
    val identity: FirmwareIdentity = FirmwareIdentity(),
    val sound: String? = null,
    val sessionMillis: Long? = null,
    val sinceRenewalMs: Long? = null,
    val manualResync: Long = 0,
)

interface MirrorListener {
    // The mutable framebuffer is owned by the MIDI worker and usable only during this synchronous callback.
    fun frameBuffer(source: FrameBuffer?, timing: FrameTiming?)
    fun panel(state: PanelState?)
    fun stats(stats: MirrorStats)
    fun log(line: String)
    fun midiLog(category: MidiCategory, line: String) = log(line)
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
    private val framer = SysExFramer(::framedShort, ::framedSysEx)
    var presentationMode = PresentationMode.BALANCED
    var panelTelemetry = false
    private var watchStarted: Long? = null
    private var manualResync = 0L
    var performance: PerformanceMonitor? = null
    private var framerCallbackNanos = 0L
    private var wire: Wire? = null
    private var choice = Wire.AUTO
    private var compatibleOutput = false
    private var scope = MirrorScope.FM1
    private var identity = FirmwareIdentity()
    private var sound: String? = null
    private var identityAt = Long.MAX_VALUE
    private var identitySent = 0
    private var heartbeat = false
    private var lastPixels = -1L
    private var lastHello = -1L
    private var nextFull = 0L
    private var fullSent = false
    private var repairs = 0
    private var retries = 0
    private var exhausted = false
    private var awaitingRepair = false
    private var completeFrames = 0L
    private var blocks = 0L
    private var waitingForFull = false
    private var lostBytes = 0L
    private var watching = false
    private var controls = false
    private var alive = true
    private var version = 0
    private var lastMessage = -1L
    private var nextProbe = Long.MAX_VALUE
    private var probe = 0
    private var lastProbed: Wire? = null
    private var refresh = true
    private var expected = -1
    private var dirtySince = -1L
    private var lastPublish = 0L
    private var rxNanos = 0L
    private var patchNanos = 0L
    private var firstPatchNanos = 0L
    private var firstAppliedNanos = 0L
    private var frameSynced = false
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
    private val nextLog = LongArray(MidiCategory.entries.size)
    private var clockCount = 0L
    private var loggedClock = 0L
    private var txMessages = 0L
    private var txBytes = 0L
    private var txRate = 0L
    private var requests = 0L
    private var replies = 0L
    private var lastSysExRx: ByteArray? = null
    private var lastSysExTx: ByteArray? = null

    private var bytesReceived = 0L
    private var bytesPerSecond = 0L
    private var duplicates = 0L
    private var decodingMs: Double? = null

    fun configure(
        wireChoice: Wire, maySend: Boolean, watch: Boolean, remote: Boolean, policy: MirrorScope = MirrorScope.FM1,
    ) {
        if (!alive) return
        val changed = choice != wireChoice || scope != policy
        val begin = watch && (!watching || changed || maySend != compatibleOutput)
        if (!maySend || !watch || changed) {
            commands.clear()
            pending = null
        } else if (controls && !remote) {
            clearControls()
        }
        if (watching && (changed || !watch) && wire == Wire.BASE && compatibleOutput && scope.allows(Wire.BASE)) {
            transmit(MirrorPackets.releaseMirror())
        }
        choice = wireChoice
        scope = policy
        compatibleOutput = maySend
        watching = watch
        controls = remote
        if (changed) resetStream()
        if (begin) {
            val now = clock.elapsedMillis()
            watchStarted = now
            refresh = true
            fullSent = false
            repairs = 0
            exhausted = false
            awaitingRepair = false
            nextProbe = now + 80
            nextFull = now
            probe = 0
            lastProbed = null
            if (scope == MirrorScope.FELUCCA && identitySent < 2) identityAt = now + 1150
        }
        if (!watch) {
            identityAt = Long.MAX_VALUE
            watchStarted = null
        }
        if (changed || begin || !watch) publishStats()
    }

    fun requestRefresh() {
        if (!waiting) return
        manualResync++
        exhausted = false
        repairs = 0
        refresh = true
        awaitingRepair = true
        val now = clock.elapsedMillis()
        nextFull = maxOf(now, lastHello + MIN_HELLO_GAP)
        nextProbe = minOf(nextProbe, nextFull)
    }

    fun inputLost(bytes: Long) {
        lostBytes += bytes
        framer.reset()
        expected = -1
        refresh = true
        loseLiveness("Se perdieron fragmentos en la entrada MIDI")
        observer.midiLog(MidiCategory.ERROR, "RX saturado · $bytes B descartados · resincronización solicitada")
    }

    @JvmOverloads
    fun receive(data: ByteArray, receivedNanos: Long, queuedNanos: Long = receivedNanos) {
        if (!alive) return
        rxNanos = receivedNanos
        bytesReceived += data.size
        val monitor = performance
        if (monitor == null) {
            framer.feed(data, 0, data.size, receivedNanos)
            return
        }
        val started = clock.nanoTime()
        monitor.record(Stage.RX_QUEUE, started - queuedNanos)
        framerCallbackNanos = 0L
        framer.feed(data, 0, data.size, receivedNanos)
        monitor.record(Stage.SYSEX, clock.nanoTime() - started - framerCallbackNanos)
    }

    private fun framedSysEx(bytes: ByteArray) = inFramer { message(bytes) }

    private fun framedShort(status: Int, data1: Int, data2: Int) = inFramer { shortMessage(status, data1, data2) }

    private inline fun inFramer(handle: () -> Unit) {
        if (performance == null) {
            handle()
            return
        }
        val began = clock.nanoTime()
        handle()
        framerCallbackNanos += clock.nanoTime() - began
    }

    private fun shortMessage(status: Int, data1: Int, data2: Int) {
        if (status == 0xF8) {
            clockCount++
            return
        }
        val category = when (status and 0xF0) {
            0x80 -> MidiCategory.NOTE_OFF
            0x90 -> if (data2 == 0) MidiCategory.NOTE_OFF else MidiCategory.NOTE_ON
            0xB0 -> MidiCategory.CONTROL
            else -> MidiCategory.OTHER
        }
        sample(category) {
            "RX ${hexByte(status)}${if (data1 >= 0) " ${hexByte(data1)}" else ""}" +
                "${if (data2 >= 0) " ${hexByte(data2)}" else ""}"
        }
    }

    private inline fun sample(category: MidiCategory, line: () -> String) {
        val now = clock.elapsedMillis()
        if (now >= nextLog[category.ordinal]) {
            nextLog[category.ordinal] = now + 1000
            observer.midiLog(category, line())
        }
    }

    private fun transmit(data: ByteArray, label: String = "panel", request: Boolean = false): Boolean {
        if (!send(data)) {
            observer.midiLog(MidiCategory.ERROR, "TX falló · $label · ${data.hex()}")
            return false
        }
        txMessages++
        txBytes += data.size
        lastSysExTx = data
        if (request) requests++
        observer.midiLog(MidiCategory.SYSEX, "TX $label · ${data.hex()} (aceptado por la API MIDI)")
        return true
    }

    private fun ByteArray.hex(limit: Int = size): String =
        take(limit).joinToString(" ") { hexByte(it.toInt() and 255) }

    private fun hexByte(value: Int) = value.toString(16).padStart(2, '0').uppercase()

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

    private fun canControl() = wire == Wire.BASE && version >= 2 && waiting && !awaitingRepair && !exhausted

    fun clearControls() {
        commands.clear()
        if (canControl()) {
            commands.addLast(Command(0, MirrorPackets::releasePanel))
            advance(clock.elapsedMillis())
        }
    }

    fun close() {
        if (!alive) return
        if (wire == Wire.BASE && compatibleOutput && watching && scope.allows(Wire.BASE)) transmit(MirrorPackets.releaseMirror())
        alive = false
        watching = false
        resetStream()
    }

    private fun resetStream() {
        wire = null
        version = 0
        probe = 0
        lastProbed = null
        expected = -1
        framebuffer.reset()
        framer.reset()
        dirtySince = -1L
        frameSynced = false
        lastMessage = -1L
        lastPixels = -1L
        heartbeat = false
        awaitingRepair = false
        waitingForFull = false
        processingMs = null
        decodingMs = null
        commands.clear()
        pending = null
        observer.frameBuffer(null, null)
        observer.panel(null)
        publishStats()
    }

    private fun message(bytes: ByteArray) {
        lastSysExRx = bytes
        val now = clock.elapsedMillis()
        if (scope == MirrorScope.FELUCCA) {
            val name = MirrorPackets.feluccaName(bytes)
            val packageId = MirrorPackets.feluccaPackageReply(bytes)
            if (name != null || packageId != null) {
                identity = FirmwareIdentity(name ?: identity.reportedName, packageId ?: identity.packageId)
                observer.midiLog(MidiCategory.SYSEX, "RX identificación · ${identity.reportedName ?: "?"} · ${identity.packageId ?: "?"}")
                // A read-only identity answer can expose a loader whose descriptor was misleading.
                if (packageId?.startsWith("ota-", ignoreCase = true) == true) {
                    compatibleOutput = false
                    exhausted = true
                    observer.midiLog(MidiCategory.ERROR, "Identidad de cargador · envío bloqueado")
                }
                publishStats()
                return
            }
        }
        val soundReply = MirrorPackets.big1Sound(bytes)
        if (soundReply != null && (choice == Wire.AUTO || choice == Wire.BIG1) &&
            (wire == null || wire == Wire.BIG1) && scope.allows(Wire.BIG1)) {
            sound = soundReply
            if (lastHello >= 0 && now - lastHello <= 3000) {
                replies++
                heartbeat = true
                signal(now)
            }
            sample(MidiCategory.GRAPHICS) { "RX Big Screen V1 · respuesta de sonido · ${bytes.hex(52)}" }
            return
        }
        val start = clock.nanoTime()
        val protocol = MirrorPackets.identify(bytes)
        if (protocol == null || (choice != Wire.AUTO && protocol != choice) || (wire != null && wire != protocol)) {
            sample(MidiCategory.SYSEX) { "RX SysEx · ${bytes.size} B · ${bytes.hex(32)}" }
            return
        }
        val event = MirrorPackets.decodeIdentified(protocol, bytes, panelTelemetry) ?: return
        val decodedIn = (clock.nanoTime() - start).coerceAtLeast(0)
        decodingMs = decodedIn / 1_000_000.0
        if (event is MirrorEvent.Pixels) {
            performance?.record(Stage.DECODE, decodedIn)
            performance?.record(Stage.SYSEX_SPAN, rxNanos - framer.completedStartedNanos)
        }
        if (event == MirrorEvent.Invalid) {
            invalid++
            if (protocol == wire) {
                refresh = true
                awaitingRepair = true
            }
            sample(MidiCategory.ERROR) { "RX ${protocol.title} · trama inválida · ${bytes.hex(24)}" }
            return
        }
        if (event is MirrorEvent.Ack) {
            val command = pending ?: return
            if (command.sequence == event.sequence && command.command.op == event.operation) {
                pending = null
                if (event.ok) advance(now)
                else recoverControls("El FM-1 rechazó el control ${event.operation}", now)
            }
            return
        }
        if (wire == null && (event is MirrorEvent.Panel || event == MirrorEvent.PanelIgnored || event == MirrorEvent.Sync)) return
        if (event is MirrorEvent.Hello && !event.enabled) {
            if (wire == protocol && waiting) loseLiveness("El firmware confirmó que el espejo se detuvo")
            return
        }
        if (event is MirrorEvent.Hello) {
            replies++
            heartbeat = true
        }
        signal(now)
        if (event == MirrorEvent.PanelIgnored) return
        if (wire == null) {
            wire = protocol
            if (protocol == lastProbed) {
                fullSent = true
                refresh = false
                waitingForFull = true
            }
            version = when (protocol) { Wire.BIG1 -> 1; Wire.BIG2 -> 2; else -> 0 }
            observer.midiLog(MidiCategory.SESSION, "SysEx verificado · ${protocol.title}")
            publishStats()
        }
        sample(MidiCategory.GRAPHICS) { "RX ${protocol.title} · ${bytes.size} B · ${bytes.hex(24)}" }
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
                        gaps += (it - expected) and 255
                        refresh = true
                        awaitingRepair = true
                    }
                    expected = (it + 1) and 255
                }
                val wasFull = framebuffer.covered == SIDE * SIDE
                val applyAt = if (performance != null) clock.nanoTime() else 0
                val changed = framebuffer.apply(event.patch)
                performance?.record(Stage.FRAMEBUFFER, clock.nanoTime() - applyAt)
                if (changed) {
                    patchNanos = rxNanos
                    frameSynced = false
                    if (dirtySince < 0L) {
                        dirtySince = now
                        firstPatchNanos = framer.completedStartedNanos.coerceAtMost(rxNanos)
                        if (performance != null) firstAppliedNanos = clock.nanoTime()
                    }
                }
                lastPixels = now
                patches++
                blocks++
                if (!wasFull && framebuffer.covered == SIDE * SIDE) {
                    if (protocol != Wire.BIG2) completeFrames++
                    repairs = 0
                    exhausted = false
                    awaitingRepair = false
                    waitingForFull = false
                }
            }
            is MirrorEvent.Panel -> if (version >= 2) observer.panel(event.state)
            MirrorEvent.Sync -> {
                completed++
                if (framebuffer.covered == SIDE * SIDE) completeFrames++
                frameSynced = true
            }
            else -> Unit
        }
    }

    private val waiting get() = watching && compatibleOutput && scope.allows(choice) &&
        identity.packageId?.startsWith("ota-", ignoreCase = true) != true

    private fun signal(now: Long) {
        if (exhausted || awaitingRepair && !waitingForFull && !refresh) {
            repairs = 0
            exhausted = false
            awaitingRepair = false
        }
        lastMessage = now
    }

    private fun loseLiveness(reason: String) {
        if (!awaitingRepair && !exhausted) observer.midiLog(MidiCategory.ERROR, reason)
        awaitingRepair = true
        if (wire == Wire.BASE && (controls || pending != null)) {
            controls = false
            commands.clear()
            pending = null
            if (waiting) transmit(MirrorPackets.releaseMirror(), "liberar panel tras interrupción")
            refresh = true
            observer.panel(null)
            observer.controlSuspended(reason)
        }
    }

    fun frameDelayMillis(): Long? {
        if (!alive || dirtySince < 0) return null
        val batch = when {
            wire != Wire.BIG2 -> presentationMode.batchMs
            frameSynced -> 0L
            else -> maxOf(60L, presentationMode.batchMs)
        }
        val due = maxOf(dirtySince + batch, lastPublish + presentationMode.intervalMs)
        return (due - clock.elapsedMillis()).coerceAtLeast(0L)
    }

    /** Presentation can wake independently of the tick that services protocol renewal and control ACKs. */
    fun publishDueFrame() {
        if (frameDelayMillis() != 0L) return
        val now = clock.elapsedMillis()
        val publishedAt = clock.nanoTime()
        dirtySince = -1L
        frameSynced = false
        lastPublish = now
        published++
        processingMs = (publishedAt - patchNanos).coerceAtLeast(0) / 1_000_000.0
        performance?.record(Stage.COALESCE, publishedAt - firstAppliedNanos)
        observer.frameBuffer(framebuffer, FrameTiming(firstPatchNanos, patchNanos, publishedAt))
        framebuffer.clearDirty()
    }

    private fun advance(now: Long) {
        if (!canControl()) return
        val current = pending
        if (current != null) {
            if (now - current.sentAt < 180) return
            if (current.attempts < 3) {
                transmit(current.command.packet(current.sequence))
                pending = current.copy(sentAt = now, attempts = current.attempts + 1)
                return
            }
            recoverControls("Sin ACK del panel · cierre de la sesión de control", now)
            return
        }
        val command = commands.removeFirstOrNull() ?: return
        val seq = sequence++ and 255
        if (transmit(command.packet(seq))) pending = Pending(command, seq, now, 1)
    }

    private fun recoverControls(reason: String, now: Long) {
        observer.log(reason)
        observer.controlSuspended(reason)
        transmit(MirrorPackets.releaseMirror())
        controls = false
        resetStream()
        refresh = true
        nextProbe = now + 1200
    }

    fun tick() {
        if (!alive) return
        val now = clock.elapsedMillis()
        publishDueFrame()
        if (waiting && !exhausted && pending == null && now >= nextProbe) renew(now)
        if (waiting && scope == MirrorScope.FELUCCA && now >= identityAt && now - lastHello >= 220) {
            val command = if (identitySent == 0) MirrorPackets.feluccaInfo() else MirrorPackets.feluccaPackage()
            transmit(command, if (identitySent == 0) "Felucca INFO" else "Identidad de paquete")
            identitySent++
            identityAt = if (identitySent == 1) now + 900 else Long.MAX_VALUE
        }
        advance(now)
        if (now - metricsAt >= 1000L) {
            bytesPerSecond = bytesReceived * 1000 / (now - metricsAt)
            txRate = txBytes * 1000 / (now - metricsAt)
            bytesReceived = 0
            txBytes = 0
            if (clockCount != loggedClock && now >= nextLog[MidiCategory.CLOCK.ordinal]) {
                observer.midiLog(MidiCategory.CLOCK, "RX Clock F8 ×${clockCount - loggedClock} · acumulado $clockCount")
                loggedClock = clockCount
                nextLog[MidiCategory.CLOCK.ordinal] = now + 5000
            }
            publishStats()
            patches = 0
            completed = 0
            published = 0
            metricsAt = now
        }
    }

    private fun renew(now: Long) {
        val candidate = wire?.takeIf(scope::allows) ?: choice.takeUnless { it == Wire.AUTO }
            ?: if (scope == MirrorScope.FELUCCA) Wire.BIG1 else PROBES[probe % PROBES.size]
        if (wire == null && choice == Wire.AUTO && scope == MirrorScope.FM1) {
            if (probe >= PROBES.size * 2) {
                exhausted = true
                publishStats()
                return
            }
            probe++
            if (transmit(MirrorPackets.request(candidate, true), "${candidate.title} · sondeo", request = true)) {
                lastProbed = candidate
                lastHello = now
                nextFull = now + 4000
            }
            nextProbe = now + 900
            return
        }
        // V1 without a heartbeat can stay silent indefinitely on an unchanged LCD. Only heartbeat-equipped V1
        // (Atlatl) and the documented v2/fm1-base hello responses provide proof that a session has gone quiet.
        val livenessExpected = heartbeat || identity.atlatl && wire == Wire.BIG1 || wire == Wire.BIG2 || wire == Wire.BASE
        val silent = livenessExpected && lastMessage >= 0 && now - lastMessage >= 4600
        val incomplete = framebuffer.covered < SIDE * SIDE && now - lastPixels >= 3500
        if (silent) loseLiveness("Sin respuestas de sesión tras las renovaciones MIDI")
        val repair = fullSent && (refresh || silent || incomplete) && now >= nextFull
        if (repair && repairs >= 3 && (silent || lastMessage < 0)) {
            exhausted = true
            observer.midiLog(MidiCategory.ERROR, "Límite de reintentos sin respuesta · reactivar o resincronizar manualmente")
            publishStats()
            return
        }
        val full = !fullSent || repair && repairs < 3
        if (full) {
            if (fullSent) {
                repairs++
                retries++
                awaitingRepair = true
            }
            nextFull = now + (4000L shl repairs.coerceAtMost(3))
        }
        val sent = transmit(MirrorPackets.request(candidate, full), "${candidate.title} · ${if (full) "pantalla completa" else "renovación"}", request = true)
        nextProbe = now + if (candidate == Wire.BASE) 1500L else 900L
        if (sent) {
            lastHello = now
            if (full) {
                fullSent = true
                refresh = false
                waitingForFull = true
                framebuffer.forgetCoverage()
            }
        }
    }

    private fun publishStats() {
        val now = clock.elapsedMillis()
        val state = when {
            !watching -> StreamState.PASSIVE
            !waiting -> StreamState.BLOCKED
            exhausted -> StreamState.NO_RESPONSE
            awaitingRepair -> StreamState.RECOVERING
            wire == null || lastPixels < 0 -> StreamState.STARTING
            now - lastPixels < 1800 -> StreamState.UPDATING
            heartbeat && lastMessage >= 0 && now - lastMessage < 4600 -> StreamState.STATIC
            else -> StreamState.NO_CHANGES
        }
        observer.stats(MirrorStats(
            wire, version, framebuffer.covered, patches, completed, published, invalid, gaps, processingMs,
            bytesPerSecond, framer.messages, duplicates + framer.discarded, decodingMs, state, txMessages,
            txRate, requests, replies, lastSysExTx?.hex(), lastSysExRx?.hex(),
            lastPixels.takeIf { it >= 0 }?.let { now - it }, lastMessage.takeIf { it >= 0 }?.let { now - it },
            blocks, completeFrames, retries, lostBytes,
            clockCount, identity, sound, watchStarted?.let { now - it },
            lastHello.takeIf { watchStarted?.let { started -> it >= started } == true }?.let { now - it }, manualResync,
        ))
    }

    companion object {
        private const val MIN_HELLO_GAP = 900L
        private val PROBES = listOf(Wire.BIG2, Wire.BIG2, Wire.BIG2, Wire.BIG1, Wire.BASE)
    }
}
