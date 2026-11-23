package com.josxtm.fm1ramloader

/** Redacted, exportable session report. Deliberately excludes serials, UIDs and keys. */
internal data class DiagnosticEvent(val time: String, val message: String)

internal object DiagnosticReport {
    fun render(appVersion: String, connection: String, identity: MbootIdentityProtocol.Identity?,
               events: List<DiagnosticEvent>): String = buildString {
        appendLine("FM1 MANAGER · INFORME TÉCNICO")
        appendLine("APP_VERSION=$appVersion")
        appendLine("CONNECTION=$connection")
        appendLine("IDENTITY=${identity?.let { "m-bootloader ${it.version}" } ?: "NO VERIFICADA"}")
        identity?.let {
            appendLine("PROTOCOL_KIND=${it.kind}")
            appendLine("FLASH_CAPACITY_BYTES=${it.capacity}")
            appendLine("FLASH_JEDEC=0x${it.jedec.toString(16).uppercase()}")
            appendLine("PROTOCOL_COMPATIBLE=${it.compatible}")
            appendLine("RAM_ANNOUNCED=${it.ramApps}")
        }
        appendLine("PHYSICAL_WRITE=BLOCKED")
        appendLine("RAM_EXECUTION=BLOCKED")
        appendLine("EVENTS")
        events.forEach { appendLine("${it.time} ${it.message}") }
    }
}
