package com.josxtm.fm1ramloader

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal data class UsbPanel(
    val devices: List<DeviceDescriptor> = emptyList(),
    val selection: Int? = null,
    val permission: String = "Pendiente",
    val state: String = "Sin dispositivo",
    val identity: String? = null,
    val busy: Boolean = false,
    val exclusiveAttempt: Int? = null
) {
    val selected: DeviceDescriptor? get() = devices.firstOrNull { it.id == selection }
}

internal class AndroidUsb(private val context: Context, private val log: (String) -> Unit) : Closeable {
    private data class PermissionRequest(val device: UsbDevice, val id: Int, val callback: PendingIntent)

    private val manager = context.getSystemService(UsbManager::class.java)
    private val storage = context.getSystemService(StorageManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val permissionAction = "${context.packageName}.USB_PERMISSION"
    private var permissionRequest: PermissionRequest? = null
    private var exclusiveDevice: UsbDevice? = null
    @Volatile private var generation = 0
    @Volatile private var closed = false
    var panel by mutableStateOf(UsbPanel())
        private set

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                permissionAction -> {
                    val device = deviceExtra(intent) ?: return
                    val request = permissionRequest ?: return
                    if (!sameAttachment(request.device, device) ||
                        !UsbPolicy.requestMatches(request.id, intent.getIntExtra("request", -1))) return
                    val live = manager?.let { liveDevice(it, request.device) }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val accepted = UsbPolicy.permissionMatches(
                        request.device.deviceId, live?.deviceId, granted, live != null && manager?.hasPermission(live) == true
                    )
                    permissionRequest = null
                    log("USB · permiso callback=$granted · conexión vigente=${live != null} · confirmado=$accepted")
                    if (accepted && live != null) identify(live)
                    else {
                        panel = panel.copy(permission = "Denegado", state = "Android no concedió el permiso", busy = false)
                        if (live == null) refresh()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> refresh()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> refresh(deviceExtra(intent)?.deviceId)
            }
        }
    }

    init {
        val filter = IntentFilter(permissionAction).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    private fun devices(): Collection<UsbDevice> = manager?.deviceList?.values.orEmpty()

    fun refresh() = refresh(null)

    private fun refresh(detached: Int?) {
        val found = devices().filterNot { it.deviceId == detached }.map(::describe)
            .filter { it.mode != null || UsbPolicy.hasRomIds(it) }.sortedBy { it.id }
        val before = panel.selected
        val selected = found.firstOrNull { it.id == before?.id } ?: found.singleOrNull()
        if (selected != before) {
            if (before != null) log("USB · dispositivo retirado o reenumerado")
            resetSession()
            panel = UsbPanel(devices = found, selection = selected?.id, state = initial(found, selected),
                permission = permissionLabel(selected?.id))
        } else if (selected == null || panel.busy) {
            panel = panel.copy(devices = found, state = if (selected == null) initial(found, selected) else panel.state)
        } else {
            val granted = hasPermission(selected.id)
            if (!granted) exclusiveDevice = null
            panel = panel.copy(devices = found,
                permission = if (granted) "Otorgado" else panel.permission.takeIf { it == "Denegado" } ?: "Sin solicitar",
                exclusiveAttempt = panel.exclusiveAttempt.takeIf { granted })
        }
    }

    fun select(id: Int) {
        if (panel.selection == id || panel.busy) return
        val selected = panel.devices.firstOrNull { it.id == id } ?: return
        resetSession()
        panel = UsbPanel(devices = panel.devices, selection = id, state = initial(panel.devices, selected),
            permission = permissionLabel(id))
    }

    fun connect() {
        val manager = manager ?: return
        val descriptor = panel.selected ?: return
        if (panel.busy) return
        val device = devices().firstOrNull { describe(it) == descriptor } ?: return refresh()
        resetSession()
        panel = panel.copy(exclusiveAttempt = null)
        if (descriptor.mode == UsbMode.MIDI) {
            panel = panel.copy(
                permission = if (manager.hasPermission(device)) "Otorgado" else "Sin solicitar",
                state = "Interfaz MIDI detectada · ROM por separado"
            )
            log("USB · MIDI ${descriptor.ids} · sin comandos MIDI")
            return
        }
        if (manager.hasPermission(device)) {
            log("USB · hasPermission() → true · ${descriptor.ids}")
            identify(device)
            return
        }
        val request = requestIds.incrementAndGet()
        panel = panel.copy(state = "Esperando permiso de Android", permission = "Solicitado", busy = true)
        val result = PendingIntent.getBroadcast(
            context, request, Intent(permissionAction).setPackage(context.packageName).putExtra("request", request),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
        )
        permissionRequest = PermissionRequest(device, request, result)
        log("USB · hasPermission() → false · solicitando permiso para ${descriptor.ids}")
        try {
            manager.requestPermission(device, result)
        } catch (_: SecurityException) {
            resetSession()
            panel = panel.copy(state = "Android no permite solicitar acceso", busy = false, permission = "Denegado")
            log("USB · requestPermission() → SecurityException")
        }
        main.postDelayed({
            if (permissionRequest?.id == request) {
                resetSession()
                panel = panel.copy(state = "Solicitud de permiso sin respuesta", busy = false, permission = "Pendiente")
                log("USB · tiempo de espera del permiso agotado")
            }
        }, 30_000)
    }

    fun connectExclusive(attempt: Int) {
        val manager = manager ?: return
        val previous = exclusiveDevice ?: return
        if (panel.busy || panel.exclusiveAttempt != attempt || generation != attempt) return
        val live = liveDevice(manager, previous)
        resetSession()
        panel = panel.copy(exclusiveAttempt = null)
        if (live == null || !manager.hasPermission(live)) {
            panel = panel.copy(state = "La conexión o su permiso cambió · vuelve a conectar",
                permission = permissionLabel(panel.selection))
            log("USB · se descartó la autorización exclusiva tras cambiar la conexión o el permiso")
            return
        }
        log("USB · usuario confirmó un intento exclusivo para esta conexión")
        identify(live, exclusive = true)
    }

    fun disconnect() {
        resetSession()
        val selected = panel.selected
        panel = panel.copy(busy = false, identity = null, state = initial(panel.devices, selected),
            permission = permissionLabel(selected?.id), exclusiveAttempt = null)
        log("USB · sesión local cancelada · el USB en curso se cierra al retornar")
    }

    private fun identify(device: UsbDevice, exclusive: Boolean = false) {
        val manager = manager ?: return
        val token = ++generation
        panel = panel.copy(busy = true, state = "Consultando identidad ROM", permission = "Otorgado", identity = null)
        log("USB · INQUIRY 0x12 · ${describe(device).ids}")
        worker.execute {
            val result = runCatching {
                AndroidRomInquiry.identify(manager, device, exclusive, ::storageAllowsExclusive,
                    active = { token == generation },
                    log = { line -> main.post { if (!closed) log("USB #$token · $line") } })
            }
            main.post {
                if (token != generation) return@post
                result.fold(
                    onSuccess = { identity ->
                        panel = panel.copy(state = "Firma USB de ROM · consulta cerrada", identity = identity, busy = false)
                        log("USB · INQUIRY + CSW válidos · $identity")
                    },
                    onFailure = { error ->
                        val failure = error as? RomProbeError
                        val detail = failure?.message ?: "Android USB · ${error.javaClass.simpleName}"
                        val offer = !exclusive && failure?.fault == RomFault.CLAIM &&
                            AndroidRomInquiry.supportsExclusive(device) &&
                            liveDevice(manager, device)?.let(manager::hasPermission) == true
                        exclusiveDevice = device.takeIf { offer }
                        panel = panel.copy(state = detail, busy = false, permission = permissionLabel(panel.selection),
                            exclusiveAttempt = token.takeIf { offer })
                        log("USB · $detail")
                        refresh()
                    }
                )
            }
        }
    }

    private fun permissionLabel(selected: Int?): String {
        if (selected == null) return "Pendiente"
        return if (hasPermission(selected)) "Otorgado" else "Sin solicitar"
    }

    private fun hasPermission(id: Int): Boolean = devices().any { it.deviceId == id && manager?.hasPermission(it) == true }

    private fun storageAllowsExclusive(): Boolean = storage?.storageVolumes?.none {
        it.isRemovable && !it.isEmulated && it.state !in setOf(
            Environment.MEDIA_UNMOUNTED, Environment.MEDIA_REMOVED, Environment.MEDIA_BAD_REMOVAL,
            Environment.MEDIA_NOFS, Environment.MEDIA_UNMOUNTABLE
        )
    } == true

    private fun initial(devices: List<DeviceDescriptor>, selected: DeviceDescriptor?): String = when {
        manager == null -> "Servicio USB Host no disponible"
        devices.isEmpty() -> "Sin dispositivo compatible"
        selected == null -> "Selecciona un dispositivo"
        else -> "${selected.mode?.label ?: "ROM candidata"} detectado"
    }

    private fun resetSession() {
        generation++
        permissionRequest?.callback?.cancel()
        permissionRequest = null
        exclusiveDevice = null
    }

    override fun close() {
        closed = true
        resetSession()
        main.removeCallbacksAndMessages(null)
        context.unregisterReceiver(receiver)
        worker.shutdownNow()
    }

    private companion object {
        val requestIds = AtomicInteger()
    }
}

private object AndroidRomInquiry {
    fun identify(
        manager: UsbManager,
        device: UsbDevice,
        exclusive: Boolean,
        storageAllowsExclusive: () -> Boolean,
        active: () -> Boolean,
        log: (String) -> Unit
    ): String {
        if (!active() || liveDevice(manager, device) == null)
            throw RomProbeError(RomFault.DETACHED, "Enumeración")
        val descriptor = describe(device)
        val interfaces = (0 until device.interfaceCount).map(device::getInterface)
        log("${descriptor.ids} · ${device.configurationCount} configuración(es) · ${interfaces.size} interfaz(es)")
        for (iface in interfaces) {
            val c = classify(iface)
            val code = listOf(c.kind, c.subclass, c.protocol).joinToString("/") { hex(it.toLong(), 2) }
            val endpoints = pipes(iface).joinToString { "${it.label} ${if (it.bulk) "Bulk" else "no Bulk"} ${it.packetSize} B" }
            log("interfaz ${iface.id} · alt ${c.alternate} · $code · endpoints [$endpoints]")
        }
        val iface = interfaces.singleOrNull { UsbPolicy.isRomInterface(classify(it)) }
        if (!UsbPolicy.hasRomIds(descriptor) || iface == null)
            throw RomProbeError(RomFault.INTERFACE, "Selección USB")
        val pair = UsbPolicy.bulkPair(pipes(iface)) ?: throw RomProbeError(RomFault.ENDPOINTS, "Selección USB")
        val endpoints = (0 until iface.endpointCount).map(iface::getEndpoint)
        val input = endpoints.singleOrNull { pipe(it) == pair.input }
        val output = endpoints.singleOrNull { pipe(it) == pair.output }
        if (input == null || output == null) throw RomProbeError(RomFault.ENDPOINTS, "Selección USB")
        log("selección · interfaz ${iface.id} · IN ${pair.input.label} · OUT ${pair.output.label}")
        return probeRom(pair, exclusive, exclusiveAllowed = {
            val storage = runCatching(storageAllowsExclusive)
            val safe = storage.getOrDefault(false)
            log(when {
                storage.isFailure -> "exclusivo · estado de almacenamiento no disponible"
                !safe -> "exclusivo · hay almacenamiento extraíble activo o no se pudo verificar su estado"
                else -> "exclusivo · sin volúmenes extraíbles activos informados por Android"
            })
            supportsExclusive(device) && safe
        }, link = {
            val live = if (active()) liveDevice(manager, device) else null
            when {
                live == null -> RomLink.DETACHED
                !manager.hasPermission(live) -> RomLink.NO_PERMISSION
                else -> RomLink.READY
            }
        }, open = {
            manager.openDevice(device)?.let { AndroidRomConnection(it, iface, input, output) }
        }, log = log)
    }

    fun supportsExclusive(device: UsbDevice): Boolean {
        if (device.interfaceCount != 1) return false
        return UsbPolicy.canOfferExclusive(describe(device), device.configurationCount, pipes(device.getInterface(0)))
    }

    private fun pipes(iface: UsbInterface): List<UsbPipe> =
        (0 until iface.endpointCount).map { pipe(iface.getEndpoint(it)) }

    private fun pipe(endpoint: UsbEndpoint): UsbPipe = UsbPipe(
        endpoint.address, endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK, endpoint.maxPacketSize
    )
}

private class AndroidRomConnection(
    private val connection: UsbDeviceConnection,
    private val iface: UsbInterface,
    private val input: UsbEndpoint,
    private val output: UsbEndpoint
) : RomConnection {
    override fun claim(force: Boolean): Boolean = connection.claimInterface(iface, force)
    override fun release(): Boolean = connection.releaseInterface(iface)
    override fun bulkOut(data: ByteArray, timeoutMs: Int): Int =
        connection.bulkTransfer(output, data, data.size, timeoutMs)
    override fun bulkIn(data: ByteArray, timeoutMs: Int): Int =
        connection.bulkTransfer(input, data, data.size, timeoutMs)
    override fun close() = connection.close()
}

private fun liveDevice(manager: UsbManager, expected: UsbDevice): UsbDevice? =
    manager.deviceList[expected.deviceName]?.takeIf { sameAttachment(it, expected) }

private fun sameAttachment(first: UsbDevice, second: UsbDevice): Boolean =
    first.deviceName == second.deviceName && describe(first) == describe(second)

private fun classify(iface: UsbInterface): InterfaceClass =
    InterfaceClass(iface.interfaceClass, iface.interfaceSubclass, iface.interfaceProtocol, iface.alternateSetting)

private fun describe(device: UsbDevice): DeviceDescriptor =
    DeviceDescriptor(device.deviceId, device.vendorId, device.productId,
        (0 until device.interfaceCount).map { classify(device.getInterface(it)) })

@Suppress("DEPRECATION")
private fun deviceExtra(intent: Intent): UsbDevice? = if (Build.VERSION.SDK_INT >= 33)
    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
else intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
