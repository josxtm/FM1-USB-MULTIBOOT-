package com.josxtm.fm1ramloader

import org.junit.Assert.*
import org.junit.Test

class CoverFlowModelTest {
    private fun entry(id: String) = FirmwareEntry(id, id, "1", "$id.BIN", 100, "a".repeat(64))

    @Test fun noCardEmptyAndBrowseStatesAreDistinct() {
        val model = CoverFlowModel()
        assertEquals(CoverFlowState.NO_CARD, model.state)
        model.attachCatalog(emptyList())
        assertEquals(CoverFlowState.EMPTY, model.state)
        model.attachCatalog(listOf(entry("ONE"), entry("TWO")))
        assertEquals(CoverFlowState.BROWSE, model.state)
    }

    @Test fun navigationWrapsAndInspectDoesNotLaunch() {
        val model = CoverFlowModel()
        model.attachCatalog(listOf(entry("ONE"), entry("TWO")))
        model.previous()
        assertEquals(1, model.selected)
        val assessment = model.inspect()
        assertEquals(CoverFlowState.INSPECT, model.state)
        assertEquals(FirmwareExecution.UNKNOWN, assessment?.execution)
        model.next()
        assertEquals(CoverFlowState.BROWSE, model.state)
        assertEquals(0, model.selected)
    }

    @Test fun inspectionUsesActualLibraryAssessment() {
        val content = "firmware".toByteArray()
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(content)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val item = FirmwareEntry("TEST", "Test", "1", "TEST.BIN", content.size.toLong(), hash)
        val assessment = FirmwareAssetVerifier.verify(item, content.inputStream())
        val model = CoverFlowModel()
        model.attachCatalog(listOf(item), listOf(assessment))
        assertTrue(model.inspect()!!.integrityVerified)
    }

    @Test fun readErrorIsVisibleAndBackReturnsToBrowse() {
        val model = CoverFlowModel()
        model.attachCatalog(listOf(entry("ONE")))
        model.fail("lectura FAT32")
        assertEquals(CoverFlowState.ERROR, model.state)
        model.back()
        assertEquals(CoverFlowState.BROWSE, model.state)
    }
}
