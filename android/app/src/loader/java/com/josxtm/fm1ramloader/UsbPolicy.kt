package com.josxtm.fm1ramloader

internal enum class UsbMode(val label: String) { MIDI("MIDI"), ROM("ROM") }
internal data class InterfaceClass(val kind: Int, val subclass: Int, val protocol: Int, val alternate: Int)
internal data class UsbPipe(val address: Int, val bulk: Boolean, val packetSize: Int) {
    val input: Boolean get() = address and 0x80 != 0
    val label: String get() = "0x${hex(address.toLong(), 2)}"
}

internal data class BulkPair(val input: UsbPipe, val output: UsbPipe)

internal data class DeviceDescriptor(val id: Int, val vid: Int, val pid: Int, val interfaces: List<InterfaceClass>) {
    val ids: String get() = "${hex(vid.toLong(), 4)}:${hex(pid.toLong(), 4)}"
    val mode: UsbMode? get() = UsbPolicy.mode(this)
}

internal object UsbPolicy {
    fun mode(device: DeviceDescriptor): UsbMode? = when {
        hasRomIds(device) && device.interfaces.count(::isRomInterface) == 1 -> UsbMode.ROM
        ((device.vid == 0x1209 && device.pid == 1) || (device.vid == 0x4c4a && device.pid == 0xc755)) &&
            device.interfaces.any { it.kind == 1 && it.subclass == 3 } -> UsbMode.MIDI
        else -> null
    }

    fun isRomInterface(descriptor: InterfaceClass): Boolean =
        descriptor == InterfaceClass(8, 6, 0x50, 0)

    fun hasRomIds(device: DeviceDescriptor): Boolean = device.vid == 0x4c4a && device.pid == 0x8057

    fun bulkPair(endpoints: List<UsbPipe>): BulkPair? {
        val bulk = endpoints.filter { it.bulk }
        val input = bulk.singleOrNull { it.input } ?: return null
        val output = bulk.singleOrNull { !it.input } ?: return null
        if (bulk.any { it.address and 0x70 != 0 || it.address and 0x0f == 0 || it.packetSize !in 8..1024 })
            return null
        return BulkPair(input, output)
    }

    fun canOfferExclusive(device: DeviceDescriptor, configurations: Int, endpoints: List<UsbPipe>): Boolean =
        configurations == 1 && device.interfaces.size == 1 && mode(device) == UsbMode.ROM &&
            endpoints.size == 2 && bulkPair(endpoints) != null

    fun requestMatches(generation: Int, returned: Int): Boolean =
        generation > 0 && generation == returned

    fun permissionMatches(expected: Int?, returned: Int?, granted: Boolean, managerGranted: Boolean): Boolean =
        expected != null && expected == returned && granted && managerGranted
}
