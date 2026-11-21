package com.josxtm.fm1ramloader

/** Portable, backend-free selector state; PLAY can only inspect, never launch. */
internal enum class CoverFlowState { NO_CARD, EMPTY, BROWSE, INSPECT, ERROR }

internal class CoverFlowModel {
    var state: CoverFlowState = CoverFlowState.NO_CARD
        private set
    var entries: List<FirmwareEntry> = emptyList()
        private set
    var selected: Int = 0
        private set
    var error: String? = null
        private set

    fun attachCatalog(catalog: List<FirmwareEntry>) {
        entries = catalog
        selected = 0
        error = null
        state = if (catalog.isEmpty()) CoverFlowState.EMPTY else CoverFlowState.BROWSE
    }

    fun fail(message: String) {
        error = message
        state = CoverFlowState.ERROR
    }

    fun next() {
        if (entries.isNotEmpty()) selected = (selected + 1) % entries.size
        if (state == CoverFlowState.INSPECT) state = CoverFlowState.BROWSE
    }

    fun previous() {
        if (entries.isNotEmpty()) selected = (selected - 1 + entries.size) % entries.size
        if (state == CoverFlowState.INSPECT) state = CoverFlowState.BROWSE
    }

    /** PLAY is deliberately an inspection transition, not an execution request. */
    fun inspect(): FirmwareAssessment? {
        if (state != CoverFlowState.BROWSE || entries.isEmpty()) return null
        state = CoverFlowState.INSPECT
        return entries[selected].assess()
    }

    fun back() {
        state = if (entries.isEmpty()) CoverFlowState.EMPTY else CoverFlowState.BROWSE
    }
}
