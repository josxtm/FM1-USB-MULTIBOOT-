package com.josxtm.fm1ramloader

import org.junit.Assert.*
import org.junit.Test

class FirmwareAssessmentTest {
    private val hash = "a".repeat(64)

    @Test fun rawBinRemainsUnknownExecutionEvenWithValidHash() {
        val assessment = FirmwareEntry("DARK1", "DARK-1", "1", "DARK1.BIN", 1024, hash).assess()
        assertEquals(FirmwareFormat.RAW_BIN, assessment.format)
        assertTrue(assessment.integrity)
        assertEquals(FirmwareCompatibility.UNVERIFIED, assessment.compatibility)
        assertEquals(FirmwareExecution.UNKNOWN, assessment.execution)
    }

    @Test fun opaqueFwsIsNotUnpackedOrMarkedCompatible() {
        val assessment = FirmwareEntry("PACKED", "Packed", "1", "PACKED.FWS", 1024, hash).assess()
        assertEquals(FirmwareFormat.FWSC_CONTAINER, assessment.format)
        assertEquals(FirmwareCompatibility.UNVERIFIED, assessment.compatibility)
        assertTrue(assessment.warnings.any { it.contains("opaco") })
    }

    @Test fun malformedMetadataIsNotIntegrityVerified() {
        val assessment = FirmwareEntry("BAD", "Bad", "1", "BAD.BIN", 0, "bad").assess()
        assertFalse(assessment.integrity)
        assertTrue(assessment.warnings.isNotEmpty())
    }
}
