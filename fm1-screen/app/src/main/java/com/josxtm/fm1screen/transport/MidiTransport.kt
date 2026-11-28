// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import android.os.SystemClock
import com.josxtm.fm1screen.performance.PerformanceMonitor
import com.josxtm.fm1screen.performance.PresentationMode
import com.josxtm.fm1screen.performance.Stage
import com.josxtm.fm1screen.protocol.Wire
import java.io.IOException
import java.util.UUID

enum class LinkKind(val title: String) { USB("USB-C"), BLE("Bluetooth LE MIDI") }

data class Target(val id: String, val name: String, val scope: MirrorScope, val details: String = "") {
    val recognized get() = scope != MirrorScope.NONE
}

data class MidiPorts(
    val scope: MirrorScope = MirrorScope.NONE, val rxOpen: Boolean = false, val txOpen: Boolean = false,
    val txAvailable: Boolean = false, val reconnections: Int = 0, val hyp1: Boolean = false,
)

interface TransportListener : MirrorListener {
    fun ports(status: MidiPorts)
    fun devices(devices: List<Target>)
    fun connection(details: String, connected: Boolean)
}

@SuppressLint("MissingPermission", "NewApi")
@Suppress("DEPRECATION")
class MidiTransport(
    context: Context,
    private val listener: TransportListener,
    preferredUsb: String?,
    private val performance: PerformanceMonitor,
) : PanelOutput {
    private val app = context.applicationContext
    private val midi = app.getSystemService(MidiManager::class.java)
    private val usb = app.getSystemService(UsbManager::class.java)
    private val bluetooth = app.getSystemService(BluetoothManager::class.java)
    private val thread = HandlerThread("FM1-MIDI").apply { start() }
    private val worker = Handler(thread.looper)
    private var mode = LinkKind.USB
    private var choice = Wire.AUTO
    private var watch = false
    private var remote = false
    private var telemetry = false
    private var usbPreference = preferredUsb
    private var selectedBle: Pair<BluetoothDevice, Target>? = null
    private var availableUsb = emptyList<MidiDeviceInfo>()
    private val availableBle = linkedMapOf<String, Pair<BluetoothDevice, Target>>()
    private var scan: ScanCallback? = null
    private var generation = 0
    private var opening = false
    private var openSince = 0L
    private var nextOpen = 0L
    private var openDelay = 1500L
    private var lastOpenedId: String? = null
    private var reconnections = 0
    private var device: MidiDevice? = null
    private var output: MidiOutputPort? = null
    private var input: MidiInputPort? = null
    private var session: MirrorSession? = null
    private var inbox: MidiIngress? = null
    private var modePresentation = PresentationMode.BALANCED
    private var lastDepthAt = 0L
    private val sessionClock = object : SessionClock {
        override fun elapsedMillis() = SystemClock.elapsedRealtime()
        override fun nanoTime() = System.nanoTime()
    }
    private val tick = object : Runnable {
        override fun run() {
            val active = session ?: return
            active.tick()
            val now = SystemClock.elapsedRealtime()
            if (now - lastDepthAt >= 1000) {
                performance.ingress = inbox?.depth() ?: MidiIngress.Depth()
                lastDepthAt = now
            }
            worker.postDelayed(this, 16)
        }
    }
    private var presentAt = Long.MAX_VALUE
    private val present: Runnable = Runnable {
        presentAt = Long.MAX_VALUE
        session?.publishDueFrame()
        schedulePresentation()
    }

    private fun schedulePresentation() {
        val active = session ?: return
        val delay = active.frameDelayMillis() ?: return
        if (delay == 0L) {
            cancelPresentation()
            active.publishDueFrame()
            return
        }
        val due = SystemClock.uptimeMillis() + delay
        if (due >= presentAt) return
        cancelPresentation()
        presentAt = due
        worker.postAtTime(present, due)
    }

    private fun cancelPresentation() {
        if (presentAt == Long.MAX_VALUE) return
        worker.removeCallbacks(present)
        presentAt = Long.MAX_VALUE
    }

    fun setPresentationMode(value: PresentationMode) {
        worker.post {
            modePresentation = value
            session?.presentationMode = value
            schedulePresentation()
        }
    }

    private var healthy = true
    private var permitted = false
    private var scope = MirrorScope.NONE
    private var disposed = false

    private val changes = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) = refresh()
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            if (device?.info?.id == info.id) drop("FM-1 desconectado")
            refresh()
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            if (disposed) return
            if (opening && SystemClock.elapsedRealtime() - openSince > 5000) {
                drop("Tiempo de conexión agotado")
            }
            if (!healthy && device != null) {
                drop("Enlace MIDI interrumpido")
            }
            if (healthy && device != null && SystemClock.elapsedRealtime() - openSince >= 5000) {
                openDelay = 1500L
                nextOpen = 0L
            }
            refresh()
            worker.postDelayed(this, 1500)
        }
    }

    init {
        worker.post {
            midi?.registerDeviceCallback(changes, worker)
            poll.run()
        }
    }

    fun use(kind: LinkKind) {
        worker.post {
            if (mode == kind) return@post
            stopScan()
            knownUsb = emptyList()
            drop("Buscando FM-1")
            mode = kind
            refresh()
        }
    }

    fun configure(protocol: Wire, watching: Boolean, control: Boolean) {
        worker.post {
            choice = protocol
            watch = watching
            remote = control
            session?.configure(choice, permitted, watch, remote, scope)
        }
    }

    fun setPanelTelemetry(enabled: Boolean) {
        worker.post {
            telemetry = enabled
            session?.panelTelemetry = enabled
        }
    }

    fun refreshPicture() { worker.post { session?.requestRefresh() } }

    fun reopenPorts() {
        worker.post {
            drop("Reabriendo puertos MIDI")
            nextOpen = 0L
            openDelay = 1500L
            refresh()
        }
    }

    fun select(target: Target) {
        worker.post {
            drop("Conectando a ${target.name}")
            if (mode == LinkKind.USB) usbPreference = target.id
            else selectedBle = availableBle[target.id]
            stopScan()
            nextOpen = 0
            openDelay = 1500
            refresh()
        }
    }

    fun restoreBle(target: Target) {
        worker.post {
            if (mode != LinkKind.BLE || selectedBle != null || DeviceIdentity.isLoader(target.name)) return@post
            val peer = runCatching { bluetooth?.adapter?.getRemoteDevice(target.id) }.getOrNull() ?: return@post
            val entry = peer to target
            availableBle[target.id] = entry
            selectedBle = entry
            emitTargets()
            refresh()
        }
    }

    fun scanBle() {
        worker.post {
            if (mode != LinkKind.BLE) return@post
            stopScan()
            availableBle.clear()
            emitTargets()
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    worker.post {
                        if (scan !== this) return@post
                        val peer = result.device
                        val name = result.scanRecord?.deviceName ?: runCatching { peer.name }.getOrNull() ?: "Dispositivo BLE MIDI"
                        if (!DeviceIdentity.isLoader(name)) {
                            val target = Target(peer.address, name, DeviceIdentity.scope(name), "${result.rssi} dBm")
                            availableBle[peer.address] = peer to target
                            emitTargets()
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    worker.post {
                        if (scan === this) listener.log("Error de escaneo BLE MIDI $errorCode")
                    }
                }
            }
            val scanner = runCatching { bluetooth?.adapter?.bluetoothLeScanner }.getOrNull()
            if (scanner == null) {
                listener.connection("Activa Bluetooth para buscar dispositivos MIDI", device != null)
                return@post
            }
            scan = callback
            val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BLE_MIDI)).build()
            runCatching {
                scanner.startScan(listOf(filter), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
                listener.log("Buscando anuncios BLE MIDI")
            }.onFailure { listener.log("Permiso o escáner BLE no disponible"); scan = null }
            worker.postDelayed({ if (scan === callback) stopScan() }, 12_000)
        }
    }

    override fun key(index: Int, down: Boolean) { worker.post { session?.key(index, down) } }
    override fun button(index: Int, down: Boolean) { worker.post { session?.button(index, down) } }
    override fun turn(index: Int, delta: Int) { worker.post { session?.turn(index, delta) } }
    override fun master(position: Int) { worker.post { session?.master(position) } }
    override fun releaseControls() { worker.post { session?.clearControls() } }

    fun pause() {
        worker.post {
            watch = false
            stopScan()
            session?.configure(choice, permitted, false, false, scope)
        }
    }

    fun close() {
        worker.post {
            disposed = true
            stopScan()
            midi?.unregisterDeviceCallback(changes)
            drop("Sesión cerrada")
            worker.removeCallbacks(poll)
            thread.quitSafely()
        }
    }

    private fun stopScan() {
        val callback = scan ?: return
        scan = null
        runCatching { bluetooth?.adapter?.bluetoothLeScanner?.stopScan(callback) }
    }

    private fun name(info: MidiDeviceInfo): String {
        val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME).orEmpty()
        val product = info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT).orEmpty()
        val ids = usbDevice(info)
        return when {
            DeviceIdentity.isLoader("$name $product") -> "$name $product".trim()
            DeviceIdentity.scope(product, ids?.vendorId, ids?.productId) != MirrorScope.NONE ||
                DeviceIdentity.isHyp1(product) -> product
            name.isNotBlank() -> name
            product.isNotBlank() -> product
            else -> "Dispositivo MIDI"
        }
    }

    private fun usbDevice(info: MidiDeviceInfo): UsbDevice? =
        info.properties.getParcelable(MidiDeviceInfo.PROPERTY_USB_DEVICE)

    private fun usbTarget(info: MidiDeviceInfo): Target {
        val name = name(info)
        val device = usbDevice(info)
        val ids = device?.let { "%04X:%04X".format(it.vendorId, it.productId) }
        val display = listOfNotNull(ids, "MIDI IN ${info.inputPortCount} · OUT ${info.outputPortCount}").joinToString(" · ")
        return Target("${ids.orEmpty()}/$name", name,
            DeviceIdentity.scope(name, device?.vendorId, device?.productId), display)
    }

    private fun emitTargets() {
        listener.devices(if (mode == LinkKind.USB) availableUsb.map(::usbTarget) else availableBle.values.map { it.second })
    }

    private var knownUsb = emptyList<Target>()
    private var descriptors = ""

    private fun refresh() {
        if (disposed) return
        val service = midi ?: run {
            listener.connection("Android MIDI no disponible", false)
            return
        }
        if (mode == LinkKind.USB) {
            availableUsb = service.devices.filter { it.type == MidiDeviceInfo.TYPE_USB && it.outputPortCount > 0 &&
                !DeviceIdentity.isLoader(name(it)) }
            val targets = availableUsb.map(::usbTarget)
            if (targets != knownUsb) {
                knownUsb = targets
                emitTargets()
            }
            val report = usb?.deviceList?.values?.sortedBy { it.deviceId }?.joinToString("\n") { dev ->
                val interfaces = (0 until dev.interfaceCount).joinToString(" ") { i ->
                    val face = dev.getInterface(i)
                    val endpoints = (0 until face.endpointCount).joinToString(",") { j ->
                        val ep = face.getEndpoint(j)
                        "%02X/${ep.type}/${ep.maxPacketSize}".format(ep.address)
                    }
                    "${face.interfaceClass}:${face.interfaceSubclass}[$endpoints]"
                }
                "USB %04X:%04X · %s".format(dev.vendorId, dev.productId, interfaces)
            }.orEmpty()
            if (report != descriptors) {
                descriptors = report
                report.lineSequence().filter(String::isNotEmpty).forEach(listener::log)
            }
            if (device?.info?.let { current -> availableUsb.none { it.id == current.id } } == true) drop("FM-1 desconectado")
            val picked = availableUsb.filter { usbTarget(it).id == usbPreference }.singleOrNull()
                ?: if (usbPreference == null) availableUsb.filter { usbTarget(it).recognized || DeviceIdentity.isHyp1(name(it)) }.singleOrNull() else null
            if (!opening && device == null && picked != null) {
                val target = usbTarget(picked)
                open(target) { callback -> service.openDevice(picked, callback, worker) }
            }
        } else {
            val (peer, target) = selectedBle ?: return
            if (!opening && device == null) {
                open(target) { callback -> service.openBluetoothDevice(peer, callback, worker) }
            }
        }
    }

    private fun open(target: Target, start: (MidiManager.OnDeviceOpenedListener) -> Unit) {
        val now = SystemClock.elapsedRealtime()
        if (now < nextOpen) return
        nextOpen = now + openDelay
        openDelay = (openDelay * 2).coerceAtMost(12_000)
        openSince = now
        opening = true
        val token = ++generation
        listener.connection("Conectando a ${target.name}", false)
        runCatching {
            start(MidiManager.OnDeviceOpenedListener { opened ->
                if (token != generation || disposed) {
                    runCatching { opened?.close() }
                    return@OnDeviceOpenedListener
                }
                opening = false
                if (opened == null) {
                    listener.connection("Android no pudo abrir el puerto MIDI", false)
                    return@OnDeviceOpenedListener
                }
                device = opened
                output = runCatching { opened.openOutputPort(0) }.getOrNull()
                if (output == null) {
                    drop("Puerto MIDI de recepción ocupado")
                    return@OnDeviceOpenedListener
                }
                // Android MidiManager owns the USB interface; no direct claims or raw USB transfers.
                input = if (target.recognized && opened.info.inputPortCount > 0)
                    runCatching { opened.openInputPort(0) }.getOrNull() else null
                scope = target.scope
                permitted = target.recognized && input != null
                if (lastOpenedId == target.id) reconnections++
                lastOpenedId = target.id
                listener.ports(MidiPorts(scope, rxOpen = true, txOpen = input != null,
                    txAvailable = opened.info.inputPortCount > 0, reconnections = reconnections,
                    hyp1 = DeviceIdentity.isHyp1(target.name)))
                healthy = true
                val active = MirrorSession(sessionClock, ::write, listener).apply {
                    presentationMode = modePresentation
                    panelTelemetry = telemetry
                    performance = this@MidiTransport.performance
                }
                session = active
                worker.post(tick)
                val queue = MidiIngress(nanoTime = System::nanoTime)
                inbox = queue
                lastDepthAt = 0L
                val drain = object : Runnable {
                    override fun run() {
                        if (session !== active) return
                        repeat(48) {
                            when (val entry = queue.take()) {
                                is MidiIngress.Entry.Chunk -> active.receive(entry.bytes, entry.receivedNanos, entry.queuedNanos)
                                is MidiIngress.Entry.Loss -> active.inputLost(entry.bytes)
                                null -> {
                                    schedulePresentation()
                                    return
                                }
                            }
                        }
                        schedulePresentation()
                        worker.post(this)
                    }
                }
                output?.connect(object : MidiReceiver() {
                    override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                        val received = System.nanoTime()
                        if (queue.offer(msg, offset, count, received)) worker.post(drain)
                        performance.record(Stage.MIDI_CALLBACK, System.nanoTime() - received)
                        if (timestamp > 0 && received - timestamp in 0..1_000_000_000L) {
                            performance.record(Stage.MIDI_TIMESTAMP, received - timestamp)
                        }
                    }
                })
                active.configure(choice, permitted, watch, remote, scope)
                listener.connection("${target.name} · ${mode.title}", true)
                listener.log("${target.name} · ${target.details}")
            })
        }.onFailure {
            opening = false
            listener.connection("Permiso MIDI no disponible", false)
        }
    }

    private fun write(bytes: ByteArray): Boolean {
        val port = input ?: return false
        return try {
            port.send(bytes, 0, bytes.size)
            true
        } catch (_: IOException) {
            healthy = false
            false
        }
    }

    private fun drop(reason: String) {
        remote = false
        generation++
        opening = false
        worker.removeCallbacks(tick)
        cancelPresentation()
        inbox = null
        performance.ingress = MidiIngress.Depth()
        session?.close()
        session = null
        runCatching { output?.close() }
        runCatching { input?.close() }
        runCatching { device?.close() }
        output = null
        input = null
        device = null
        permitted = false
        scope = MirrorScope.NONE
        listener.ports(MidiPorts(reconnections = reconnections))
        listener.connection(reason, false)
    }

    companion object {
        private val BLE_MIDI: UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
    }
}
