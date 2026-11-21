package com.josxtm.fm1ramloader

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class ResidentProbeTest {
    @Test fun matchesMFlasherEchoMagicVersionAndBigEndianAddress() {
        val reply = ByteBuffer.allocate(16).putShort(0xfc40.toShort())
            .put("MFLR234".toByteArray()).put(0).putInt(0x01c28000).array()
        assertEquals(ResidentLoader("234", 0x01c28000), parseResidentLoader(reply))
        assertTrue(checkNotNull(parseResidentLoader(reply)).knownSlot)
        assertNull(parseResidentLoader(reply.copyOf(15)))
        assertNull(parseResidentLoader(reply.copyOf().apply { this[1] = 0x41 }))
        assertNull(parseResidentLoader(reply.copyOf().apply { this[6] = 0x1b }))
        assertFalse(checkNotNull(parseResidentLoader(reply.copyOf().apply { fill(0, 10, 14) })).knownSlot)
        assertFalse(RomProtocol.inquiryOnly(RomProtocol.wrapper(RomProtocol.identifyResidentCdb(), 1, 16, true)))
    }

    @Test fun refusesUploadBeforeAnyWriteWhenResidentOrUnknown() {
        val app = checkNotNull(ApprovedApp.inspect(diagnostic()).approved)
        for (profile in SimulatedRom.Resident.entries.filter { it != SimulatedRom.Resident.CLEAN_ROM }) {
            val wire = SimulatedRom(resident = profile)
            val error = assertThrows(UsbProblem::class.java) { RamSimulation(wire).run(app, { _, _ -> }, {}) }
            assertEquals(UsbFailure.POLICY, error.reason)
            assertEquals(listOf(RomProtocol.INQUIRY, MFLR_IDENTIFY), wire.commands)
            assertEquals(0, wire.jumps)
        }
    }
}
