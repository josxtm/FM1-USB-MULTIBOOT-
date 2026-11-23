package com.josxtm.fm1ramloader

import org.junit.Assert.*
import org.junit.Test

class DiagnosticReportTest {
    @Test fun reportContainsVersionUtcEventsAndNoSecrets() {
        val report = DiagnosticReport.render("1.0-comp12", "USB-MIDI", null,
            listOf(DiagnosticEvent("2026-11-21T23:00:00Z", "Consulta cancelada")))
        assertTrue(report.contains("APP_VERSION=1.0-comp12"))
        assertTrue(report.contains("2026-11-21T23:00:00Z"))
        assertTrue(report.contains("RAM_EXECUTION=BLOCKED"))
        assertFalse(report.contains("UID"))
        assertFalse(report.contains("chip key"))
        assertFalse(report.contains("credential"))
    }
}
