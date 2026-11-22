package com.josxtm.fm1ramloader

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reads the same FM1CAT fixtures used by the host-core conformance cases. */
class FirmwareCatalogConformanceTest {
    private fun fixture(name: String): String {
        var directory: File? = File(System.getProperty("user.dir").orEmpty())
        var file: File? = null
        while (directory != null) {
            val candidate = directory.resolve("tests/fixtures/fm1cat/$name")
            if (candidate.isFile) { file = candidate; break }
            directory = directory.parentFile
        }
        assertTrue("Missing shared fixture: $name", file?.isFile == true)
        return requireNotNull(file).readText(Charsets.US_ASCII)
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
