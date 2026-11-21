package com.josxtm.fm1ramloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmwareCatalogTest {
    private val original = FirmwareEntry(
        "DARK1", "DARK-1", "1.2", "DARK1.BIN", 1024,
        "a".repeat(64)
    )

    @Test fun roundTripPreservesIntegrityMetadata() {
        val decoded = FirmwareCatalog.decode(FirmwareCatalog.encode(listOf(original)))
        assertEquals(listOf(original), decoded)
        assertTrue(FirmwareCatalog.encode(decoded).startsWith("FM1CAT|1\n"))
    }

    @Test fun supportsOpaqueFirmwareContainersWithoutMarkingCompatible() {
        val entry = original.copy(id = "FACTORY", label = "ORIGINAL", fileName = "FACTORY.FWS")
        assertEquals("UNVERIFIED", FirmwareCatalog.decode(
            FirmwareCatalog.encode(listOf(entry))).single().compatibility)
    }

    @Test fun rejectsPathTraversal() {
        expectFailure { FirmwareCatalog.encode(listOf(original.copy(fileName = "../X.BIN"))) }
    }

    @Test fun rejectsDuplicateIds() {
        expectFailure { FirmwareCatalog.encode(listOf(original, original)) }
    }

    @Test fun rejectsInvalidShaAndUnverifiedClaims() {
        expectFailure { FirmwareCatalog.encode(listOf(original.copy(sha256 = "INVALID"))) }
        expectFailure { FirmwareCatalog.encode(listOf(original.copy(compatibility = "VERIFIED"))) }
    }

    @Test fun rejectsOversizedAndTooManyEntries() {
        expectFailure { FirmwareCatalog.encode(listOf(original.copy(size = FirmwareCatalog.MAX_IMAGE_BYTES + 1))) }
        expectFailure { FirmwareCatalog.decode("FM1CAT|1\nID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\n" +
            "A|Bad|1|A.BIN|1|invalid|UNVERIFIED\n") }
    }

    @Test fun cleansNamesToShortAscii() {
        assertEquals("DARK_1", FirmwareCatalog.cleanId("Dark-1"))
        assertEquals("FIRMWARE", FirmwareCatalog.cleanId("firmware"))
    }

    private fun expectFailure(action: () -> Unit) {
        var failed = false
        try { action() } catch (_: IllegalArgumentException) { failed = true }
        assertTrue("Expected invalid metadata to be rejected", failed)
    }
}