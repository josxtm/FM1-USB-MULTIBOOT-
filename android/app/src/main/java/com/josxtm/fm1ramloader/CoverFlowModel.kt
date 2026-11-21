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
    private var assessments: List<FirmwareAssessment> = emptyList()

    /** Attach catalog metadata and, when available, assessments of the actual SAF files. */
    fun attachCatalog(catalog: List<FirmwareEntry>, verifiedAssessments: List<FirmwareAssessment> = emptyList(), preferredId: String? = null) {
        require(verifiedAssessments.isEmpty() || verifiedAssessments.size == catalog.size) {
            "El número de evaluaciones no coincide con el catálogo"
        }
        val previousId = preferredId ?: entries.getOrNull(selected)?.id
        entries = catalog
        assessments = verifiedAssessments
        selected = previousId?.let { id -> catalog.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
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
        return assessments.getOrNull(selected) ?: entries[selected].assess()
    }

    fun back() {
        state = if (entries.isEmpty()) CoverFlowState.EMPTY else CoverFlowState.BROWSE
    }
}
