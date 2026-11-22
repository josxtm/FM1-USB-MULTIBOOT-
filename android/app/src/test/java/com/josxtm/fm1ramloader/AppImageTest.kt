package com.josxtm.fm1ramloader

import java.io.File
import org.junit.Assert.*
import org.junit.Test

internal fun diagnostic(): ByteArray = File(checkNotNull(System.getProperty("fm1.reference"))).readBytes()

class AppImageTest {
    @Test fun approvesThePhaseOneImage() {
        val result = ApprovedApp.inspect(diagnostic())
        assertEquals(9796, result.size)
        assertEquals(1, result.board)
        assertEquals(AppSlot.SHA256, result.sha256)
        assertTrue(result.checks.all { it.second })
        assertNotNull(result.approved)
        assertEquals(0x828d, crc16(diagnostic(), result.size - 8))
    }

    @Test fun rejectsBadTrailerBoardAndVersion() {
        val original = diagnostic()
        for (index in listOf(original.size - 8, original.size - 2, original.size - 1)) {
            val bad = original.copyOf().apply { this[index] = 0 }
            assertNull(ApprovedApp.inspect(bad).approved)
        }
        assertNull(ApprovedApp.inspect(byteArrayOf(1)).approved)
        assertNull(ApprovedApp.inspect(original + 0.toByte()).approved)
        assertNull(ApprovedApp.inspect(ByteArray(AppSlot.MAX_BYTES + 2)).approved)
    }

    @Test fun rejectsBadCrcAndRejectsUnknownShaEvenWithRepairedCrc() {
        val bad = diagnostic().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        assertFalse(ApprovedApp.inspect(bad).checks.toMap().getValue("CRC16-XMODEM"))
        val crc = crc16(bad, bad.size - 8)
        bad[bad.size - 4] = crc.toByte()
        bad[bad.size - 3] = (crc ushr 8).toByte()
        val repaired = ApprovedApp.inspect(bad)
        assertTrue(repaired.checks.toMap().getValue("CRC16-XMODEM"))
        assertFalse(repaired.checks.toMap().getValue("SHA-256 aprobado"))
        assertNull(repaired.approved)
    }

    @Test fun copiesApprovedBytesAndValidatesExclusiveEnd() {
        val original = diagnostic()
        val approved = checkNotNull(ApprovedApp.inspect(original).approved)
        original[0] = 0
        approved.chunk(0)[1] = 1
        assertArrayEquals(diagnostic().copyOf(512), approved.chunk(0))
        assertTrue(AppSlot.contains(AppSlot.START, AppSlot.MAX_BYTES))
        assertFalse(AppSlot.contains(AppSlot.START, AppSlot.MAX_BYTES + 1))
        assertFalse(AppSlot.contains(AppSlot.START - 1, 1))
        assertFalse(AppSlot.contains(AppSlot.END, 1))
        assertFalse(AppSlot.contains(Long.MAX_VALUE, Int.MAX_VALUE))
        assertFalse(AppSlot.contains(AppSlot.START, 0))
    }
}
