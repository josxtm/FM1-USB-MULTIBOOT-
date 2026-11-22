package com.josxtm.fm1ramloader

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reads the same FM1CAT fixtures used by the host-core conformance cases. */
class FirmwareCatalogConformanceTest {
    private fun fixture(name: String): String {
        val file = File(System.getProperty("user.dir")).parentFile
            .resolve("tests/fixtures/fm1cat/$name")
        assertTrue("Missing shared fixture: $file", file.isFile)
        return file.readText(Charsets.US_ASCII)
    }

    @Test fun validEmptyAndCrlfCatalogsMatchSpec() {
        assertEquals(0, FirmwareCatalog.decode(fixture("valid-empty.csv")).size)
        assertEquals(1, FirmwareCatalog.decode(fixture("valid-one.csv")).size)
        assertEquals(1, FirmwareCatalog.decode(fixture("valid-crlf.csv")).size)
    }

    @Test fun invalidSharedCatalogsAreRejected() {
        listOf("invalid-path.csv", "invalid-duplicate.csv").forEach { name ->
            try {
                FirmwareCatalog.decode(fixture(name))
                throw AssertionError("Expected rejection: $name")
            } catch (_: IllegalArgumentException) {
                // Expected FM1CAT validation failure.
            }
        }
    }
}
