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
}
