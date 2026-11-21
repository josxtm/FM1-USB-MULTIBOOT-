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
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.io.Closeable
import java.util.concurrent.Executors

internal data class UsbPanel(
    val devices: List<DeviceDescriptor> = emptyList(),
    val selection: Int? = null,
    val permission: String = "Pendiente",
    val state: String = "Sin dispositivo",
    val identity: String? = null,
    val busy: Boolean = false
) {
    val selected: DeviceDescriptor? get() = devices.firstOrNull { it.id == selection }
}

internal class AndroidUsb(private val context: Context, private val log: (String) -> Unit) : Closeable {
    private val manager = context.getSystemService(UsbManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val permissionAction = "${context.packageName}.USB_PERMISSION"
    private var permissionRequest: Int? = null
    @Volatile private var generation = 0
    var panel by mutableStateOf(UsbPanel())
        private set

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                permissionAction -> {
                    val device = deviceExtra(intent) ?: return
                    if (permissionRequest == null || device.deviceId != permissionRequest ||
                        !UsbPolicy.requestMatches(generation, intent.getIntExtra("request", -1))) return
                    val accepted = UsbPolicy.permissionMatches(
                        permissionRequest, device.deviceId, intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false),
                        manager?.hasPermission(device) == true
                    )
                    permissionRequest = null
                    if (accepted) identify(device)
                    else {
                        panel = panel.copy(permission = "Denegado", state = "Android no concedió el permiso", busy = false)
                        log("USB · permiso denegado")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED -> refresh()
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

    fun refresh() {
        val found = devices().map(::describe).filter { it.mode != null }.sortedBy { it.id }
        val before = panel.selected
        val selected = found.firstOrNull { it.id == before?.id } ?: found.singleOrNull()
        if (selected != before) {
            if (before != null) log("USB · dispositivo retirado o reenumerado")
            resetSession()
            panel = UsbPanel(devices = found, selection = selected?.id, state = initial(found, selected))
        } else {
            panel = panel.copy(devices = found, state = if (selected == null) initial(found, selected) else panel.state)
        }
    }

    fun select(id: Int) {
        if (panel.selection == id || panel.busy) return
        val selected = panel.devices.firstOrNull { it.id == id } ?: return
        resetSession()
        panel = UsbPanel(devices = panel.devices, selection = id, state = initial(panel.devices, selected))
    }

    fun connect() {
        val manager = manager ?: return
        val descriptor = panel.selected ?: return
        if (panel.busy) return
        val device = devices().firstOrNull { it.deviceId == descriptor.id } ?: return refresh()
        if (descriptor.mode == UsbMode.MIDI) {
            panel = panel.copy(
                permission = if (manager.hasPermission(device)) "Otorgado" else "Sin solicitar",
                state = "Interfaz MIDI detectada · ROM por separado"
            )
            log("USB · MIDI ${descriptor.ids} · sin comandos MIDI")
            return
        }
        if (manager.hasPermission(device)) {
            identify(device)
            return
        }
        permissionRequest = device.deviceId
        val request = ++generation
        panel = panel.copy(state = "Esperando permiso de Android", permission = "Solicitado", busy = true)
        val result = PendingIntent.getBroadcast(
            context, request, Intent(permissionAction).setPackage(context.packageName).putExtra("request", request),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
        )
        try {
            manager.requestPermission(device, result)
        } catch (_: SecurityException) {
            resetSession()
            panel = panel.copy(state = "Android no permite solicitar acceso", busy = false, permission = "Denegado")
        }
        main.postDelayed({
            if (request == generation && permissionRequest == device.deviceId) {
                resetSession()
                panel = panel.copy(state = "Solicitud de permiso sin respuesta", busy = false, permission = "Pendiente")
                log("USB · tiempo de espera del permiso agotado")
            }
        }, 30_000)
    }

    fun disconnect() {
        resetSession()
        val selected = panel.selected
        panel = panel.copy(busy = false, identity = null, state = initial(panel.devices, selected),
            permission = if (selected != null && devices().any {
                it.deviceId == selected.id && manager?.hasPermission(it) == true
            }) "Otorgado por Android" else "Pendiente")
        log("USB · sesión local cerrada")
    }

    private fun identify(device: UsbDevice) {
        val manager = manager ?: return
        val token = ++generation
        panel = panel.copy(busy = true, state = "Consultando identidad ROM", permission = "Otorgado", identity = null)
        log("USB · INQUIRY 0x12 · ${describe(device).ids}")
        worker.execute {
            val result = runCatching { AndroidRomInquiry.identify(manager, device) { token == generation } }
            main.post {
                if (token != generation) return@post
                result.fold(
                    onSuccess = { identity ->
                        panel = panel.copy(state = "Firma USB de ROM · consulta cerrada", identity = identity, busy = false)
                        log("USB · INQUIRY + CSW válidos · $identity")
                    },
                    onFailure = { error ->
                        val detail = when (error) {
                            is UsbProblem -> error.reason.label
                            is SecurityException -> "Android retiró el permiso USB"
                            else -> "Interfaz USB ocupada o no disponible"
                        }
                        panel = panel.copy(state = detail, busy = false)
                        log("USB · $detail")
                        refresh()
                    }
                )
            }
        }
    }

    private fun initial(devices: List<DeviceDescriptor>, selected: DeviceDescriptor?): String = when {
        manager == null -> "Servicio USB Host no disponible"
        devices.isEmpty() -> "Sin dispositivo compatible"
        selected == null -> "Selecciona un dispositivo"
        else -> "${selected.mode?.label} detectado"
    }

    private fun resetSession() {
        generation++
        permissionRequest = null
    }

    override fun close() {
        resetSession()
        main.removeCallbacksAndMessages(null)
        context.unregisterReceiver(receiver)
        worker.shutdownNow()
    }
}

/** Android has access to a single standard SCSI INQUIRY. The RAM simulator has no access here. */
private object AndroidRomInquiry {
    fun identify(manager: UsbManager, device: UsbDevice, active: () -> Boolean): String {
        if (!active()) throw UsbProblem(UsbFailure.DISCONNECTED)
        if (UsbPolicy.mode(describe(device)) != UsbMode.ROM) throw UsbProblem(UsbFailure.POLICY)
        if (!manager.hasPermission(device)) throw SecurityException()
        val iface = (0 until device.interfaceCount).map(device::getInterface)
            .single { UsbPolicy.isRomInterface(classify(it)) }
        val endpoints = (0 until iface.endpointCount).map(iface::getEndpoint).filter {
            it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
        }
        val input = endpoints.single { it.direction == UsbConstants.USB_DIR_IN }
        val output = endpoints.single { it.direction == UsbConstants.USB_DIR_OUT }
        if (input.maxPacketSize !in 1..1024 || output.maxPacketSize !in 1..1024)
            throw UsbProblem(UsbFailure.POLICY)
        val connection = manager.openDevice(device) ?: throw UsbProblem(UsbFailure.DISCONNECTED)
        try {
            check(connection.claimInterface(iface, false))
            try {
                return BotSession(InquiryWire(manager, device, connection, input, output, active)).inquiry()
            } finally {
                connection.releaseInterface(iface)
            }
        } finally {
            connection.close()
        }
    }
}

private class InquiryWire(
    private val manager: UsbManager,
    private val device: UsbDevice,
    private val connection: UsbDeviceConnection,
    private val input: UsbEndpoint,
    private val output: UsbEndpoint,
    private val active: () -> Boolean
) : BulkWire {
    override val inPacketSize = input.maxPacketSize
    private var inquirySent = false

    override fun write(bytes: ByteArray, offset: Int, length: Int): Int {
        if (inquirySent || offset != 0 || length != bytes.size || !RomProtocol.inquiryOnly(bytes))
            throw UsbProblem(UsbFailure.POLICY)
        if (!active()) throw UsbProblem(UsbFailure.DISCONNECTED)
        inquirySent = true
        return checked(connection.bulkTransfer(output, bytes, length, 1200))
    }

    override fun read(buffer: ByteArray): Int {
        if (!inquirySent) throw UsbProblem(UsbFailure.POLICY)
        if (!active()) throw UsbProblem(UsbFailure.DISCONNECTED)
        return checked(connection.bulkTransfer(input, buffer, buffer.size, 1200))
    }

    private fun checked(value: Int): Int {
        if (value >= 0) return value
        val present = manager.deviceList.values.any { it.deviceId == device.deviceId }
        throw UsbProblem(if (present) UsbFailure.TIMEOUT else UsbFailure.DISCONNECTED)
    }
}

private fun classify(iface: UsbInterface): InterfaceClass =
    InterfaceClass(iface.interfaceClass, iface.interfaceSubclass, iface.interfaceProtocol, iface.alternateSetting)

private fun describe(device: UsbDevice): DeviceDescriptor =
    DeviceDescriptor(device.deviceId, device.vendorId, device.productId,
        (0 until device.interfaceCount).map { classify(device.getInterface(it)) })

@Suppress("DEPRECATION")
private fun deviceExtra(intent: Intent): UsbDevice? = if (Build.VERSION.SDK_INT >= 33)
    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
else intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
