package com.josxtm.fm1ramloader

internal enum class UsbMode(val label: String) { MIDI("MIDI"), ROM("ROM") }
internal data class InterfaceClass(val kind: Int, val subclass: Int, val protocol: Int, val alternate: Int)
internal data class DeviceDescriptor(val id: Int, val vid: Int, val pid: Int, val interfaces: List<InterfaceClass>) {
    val ids: String get() = "${hex(vid.toLong(), 4)}:${hex(pid.toLong(), 4)}"
    val mode: UsbMode? get() = UsbPolicy.mode(this)
}

internal object UsbPolicy {
    fun mode(device: DeviceDescriptor): UsbMode? = when {
        device.vid == 0x4c4a && device.pid == 0x8057 && device.interfaces.count(::isRomInterface) == 1 -> UsbMode.ROM
        ((device.vid == 0x1209 && device.pid == 1) || (device.vid == 0x4c4a && device.pid == 0xc755)) &&
            device.interfaces.any { it.kind == 1 && it.subclass == 3 } -> UsbMode.MIDI
        else -> null
    }

    fun isRomInterface(descriptor: InterfaceClass): Boolean =
        descriptor == InterfaceClass(8, 6, 0x50, 0)

    fun requestMatches(generation: Int, returned: Int): Boolean =
        generation > 0 && generation == returned

    fun permissionMatches(expected: Int?, returned: Int?, granted: Boolean, managerGranted: Boolean): Boolean =
        expected != null && expected == returned && granted && managerGranted
}
