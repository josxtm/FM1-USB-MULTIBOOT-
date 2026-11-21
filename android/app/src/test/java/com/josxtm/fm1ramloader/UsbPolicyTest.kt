package com.josxtm.fm1ramloader

import org.junit.Assert.*
import org.junit.Test

class UsbPolicyTest {
    private val rom = InterfaceClass(8, 6, 0x50, 0)
    private val midi = InterfaceClass(1, 3, 0, 0)

    @Test fun detectsTheExactRomInterfaceAndKnownMidiIds() {
        assertEquals(UsbMode.ROM, DeviceDescriptor(1, 0x4c4a, 0x8057, listOf(rom)).mode)
        assertEquals(UsbMode.MIDI, DeviceDescriptor(1, 0x1209, 1, listOf(midi)).mode)
        assertEquals(UsbMode.MIDI, DeviceDescriptor(1, 0x4c4a, 0xc755, listOf(midi)).mode)
    }

    @Test fun rejectsUnrelatedIdsWrongInterfacesAndAmbiguity() {
        assertNull(DeviceDescriptor(1, 0, 0x8057, listOf(rom)).mode)
        assertNull(DeviceDescriptor(1, 0x4c4a, 0x8057, listOf(midi)).mode)
        assertNull(DeviceDescriptor(1, 0x4c4a, 0x8057, listOf(rom.copy(alternate = 1))).mode)
        assertNull(DeviceDescriptor(1, 0x4c4a, 0x8057, listOf(rom, rom)).mode)
        assertNull(DeviceDescriptor(1, 0x1209, 1, listOf(rom)).mode)
    }

    @Test fun requiresCurrentRequestAndPermissionForTheSameDevice() {
        assertTrue(UsbPolicy.requestMatches(3, 3))
        assertFalse(UsbPolicy.requestMatches(4, 3))
        assertFalse(UsbPolicy.requestMatches(4, -1))
        assertTrue(UsbPolicy.permissionMatches(11, 11, true, true))
        assertFalse(UsbPolicy.permissionMatches(null, null, true, true))
        assertFalse(UsbPolicy.permissionMatches(11, 12, true, true))
        assertFalse(UsbPolicy.permissionMatches(11, 11, false, true))
        assertFalse(UsbPolicy.permissionMatches(11, 11, true, false))
    }

    @Test fun selectsExactlyOneValidBulkInAndOut() {
        val input = UsbPipe(0x81, true, 64)
        val output = UsbPipe(0x02, true, 64)
        val interrupt = UsbPipe(0x83, false, 16)
        assertEquals(BulkPair(input, output), UsbPolicy.bulkPair(listOf(output, interrupt, input)))
        assertNull(UsbPolicy.bulkPair(listOf(input, interrupt)))
        assertNull(UsbPolicy.bulkPair(listOf(input, input.copy(address = 0x82), output)))
        assertNull(UsbPolicy.bulkPair(listOf(input, output.copy(address = 0))))
        assertNull(UsbPolicy.bulkPair(listOf(input.copy(packetSize = 0), output)))
    }

    @Test fun exclusiveProfileRequiresOneConfigurationOneInterfaceAndOnlyTwoBulkPipes() {
        val device = DeviceDescriptor(1, 0x4c4a, 0x8057, listOf(rom))
        val pipes = listOf(UsbPipe(0x81, true, 64), UsbPipe(0x02, true, 64))
        assertTrue(UsbPolicy.canOfferExclusive(device, 1, pipes))
        assertFalse(UsbPolicy.canOfferExclusive(device, 2, pipes))
        assertFalse(UsbPolicy.canOfferExclusive(device.copy(interfaces = listOf(rom, midi)), 1, pipes))
        assertFalse(UsbPolicy.canOfferExclusive(device, 1, pipes + UsbPipe(0x83, false, 16)))
        assertFalse(UsbPolicy.canOfferExclusive(device.copy(pid = 1), 1, pipes))
    }
}
