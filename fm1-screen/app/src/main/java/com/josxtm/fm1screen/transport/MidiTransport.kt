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
import com.josxtm.fm1screen.protocol.Wire
import java.io.IOException
import java.util.UUID

enum class LinkKind(val title: String) { USB("USB-C"), BLE("Bluetooth LE MIDI") }

data class Target(val id: String, val name: String, val recognized: Boolean, val details: String = "")

object DeviceIdentity {
    private val fm1 = Regex("""^FM[- ]?1(?:_[0-9A-F]{2,12}|[ ._-]+MIDI)?$""", RegexOption.IGNORE_CASE)
    private val verifiedProducts = setOf(
        "FRASIL FM-1", "FM1 COMMUNITY", "M-VAVE FM-1", "FM-1 BIG SCREEN", "LOOM-8 FM-1", "POLLY FM-1",
    )
    fun allowsRequests(name: String): Boolean =
        !isLoader(name) && (fm1.matches(name.trim()) || name.trim().uppercase(java.util.Locale.ROOT) in verifiedProducts)
    fun isLoader(name: String): Boolean = listOf("update", "download", "boot", "loader", "rescue").any {
        name.contains(it, ignoreCase = true)
    }
}

interface TransportListener : MirrorListener {
    fun devices(devices: List<Target>)
    fun connection(details: String, connected: Boolean, recognized: Boolean)
}

@SuppressLint("MissingPermission", "NewApi")
@Suppress("DEPRECATION")
class MidiTransport(
    context: Context,
    private val listener: TransportListener,
    preferredUsb: String?,
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
    private var usbPreference = preferredUsb
    private var selectedBle: Pair<BluetoothDevice, Target>? = null
    private var availableUsb = emptyList<MidiDeviceInfo>()
    private val availableBle = linkedMapOf<String, Pair<BluetoothDevice, Target>>()
    private var scan: ScanCallback? = null
    private var generation = 0
    private var opening = false
    private var openSince = 0L
    private var lastRetry = 0L
    private var device: MidiDevice? = null
    private var output: MidiOutputPort? = null
    private var input: MidiInputPort? = null
    private var session: MirrorSession? = null
    private val sessionClock = object : SessionClock {
        override fun elapsedMillis() = SystemClock.elapsedRealtime()
        override fun nanoTime() = System.nanoTime()
    }
    private val tick = object : Runnable {
        override fun run() {
            val active = session ?: return
            active.tick()
            worker.postDelayed(this, 16)
        }
    }
    private var healthy = true
    private var permitted = false
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
            if (opening && SystemClock.elapsedRealtime() - openSince > 5000) drop("Tiempo de conexión agotado")
            if (!healthy && device != null) drop("Enlace MIDI interrumpido")
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
            session?.configure(choice, permitted, watch, remote)
        }
    }

    fun select(target: Target) {
        worker.post {
            drop("Conectando a ${target.name}")
            if (mode == LinkKind.USB) usbPreference = target.id
            else selectedBle = availableBle[target.id]
            stopScan()
            lastRetry = 0
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
                            val target = Target(peer.address, name, DeviceIdentity.allowsRequests(name), "${result.rssi} dBm")
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
                listener.connection("Activa Bluetooth para buscar dispositivos MIDI", device != null, permitted)
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
            session?.configure(choice, permitted, false, false)
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
        return when {
            DeviceIdentity.isLoader("$name $product") -> "$name $product".trim()
            DeviceIdentity.allowsRequests(product) -> product
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
        return Target("${ids.orEmpty()}/$name", name, DeviceIdentity.allowsRequests(name), display)
    }

    private fun emitTargets() {
        listener.devices(if (mode == LinkKind.USB) availableUsb.map(::usbTarget) else availableBle.values.map { it.second })
    }

    private var knownUsb = emptyList<Target>()
    private var descriptors = ""

    private fun refresh() {
        if (disposed) return
        val service = midi ?: run {
            listener.connection("Android MIDI no disponible", false, false)
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
                ?: if (usbPreference == null) availableUsb.filter { usbTarget(it).recognized }.singleOrNull() else null
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
        if (now - lastRetry < 1400) return
        lastRetry = now
        openSince = now
        opening = true
        val token = ++generation
        listener.connection("Conectando a ${target.name}", false, false)
        runCatching {
            start(MidiManager.OnDeviceOpenedListener { opened ->
                if (token != generation || disposed) {
                    runCatching { opened?.close() }
                    return@OnDeviceOpenedListener
                }
                opening = false
                if (opened == null) {
                    listener.connection("Android no pudo abrir el puerto MIDI", false, false)
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
                permitted = target.recognized && input != null
                healthy = true
                val active = MirrorSession(sessionClock, ::write, listener)
                session = active
                worker.post(tick)
                output?.connect(object : MidiReceiver() {
                    override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                        if (count <= 0 || count > 16_384) return
                        val receiveAt = System.nanoTime()
                        val bytes = msg.copyOfRange(offset, offset + count)
                        worker.post { if (session === active) active.receive(bytes, receiveAt) }
                    }
                })
                active.configure(choice, permitted, watch, remote)
                listener.connection("${target.name} · ${mode.title}", true, permitted)
                listener.log("${target.name} · ${target.details}")
            })
        }.onFailure {
            opening = false
            listener.connection("Permiso MIDI no disponible", false, false)
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
        session?.close()
        session = null
        runCatching { output?.close() }
        runCatching { input?.close() }
        runCatching { device?.close() }
        output = null
        input = null
        device = null
        permitted = false
        listener.connection(reason, false, false)
    }

    companion object {
        private val BLE_MIDI: UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
    }
}
