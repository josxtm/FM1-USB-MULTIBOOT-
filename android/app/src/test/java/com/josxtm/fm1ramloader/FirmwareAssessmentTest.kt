package com.josxtm.fm1ramloader

import org.junit.Assert.*
import org.junit.Test

class FirmwareAssessmentTest {
    private val hash = "a".repeat(64)

    @Test fun rawBinRemainsUnknownExecutionEvenWithValidHash() {
        val assessment = FirmwareEntry("DARK1", "DARK-1", "1", "DARK1.BIN", 1024, hash).assess()
        assertEquals(FirmwareFormat.RAW_BIN, assessment.format)
        assertFalse(assessment.integrity)
        assertTrue(assessment.metadataValid)
        assertTrue(assessment.registeredHashValid)
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

    @Test fun realContentHashAndSizeAreRequired() {
        val content = "firmware".toByteArray()
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(content)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val entry = FirmwareEntry("TEST", "Test", "1", "TEST.BIN", content.size.toLong(), hash)
        val verified = FirmwareAssetVerifier.verify(entry, content.inputStream())
        assertTrue(verified.integrityVerified)
        assertEquals(hash, verified.calculatedSha256)
        val corrupt = FirmwareAssetVerifier.verify(entry, "corrupt".byteInputStream())
        assertFalse(corrupt.integrityVerified)
        assertTrue(corrupt.warnings.any { it.contains("SHA-256 real") })
    }

    @Test fun absentAssetCannotBeClaimedVerified() {
        val entry = FirmwareEntry("MISSING", "Missing", "1", "MISSING.BIN", 1, "a".repeat(64))
        assertFalse(entry.assess().integrityVerified)
    }
}
