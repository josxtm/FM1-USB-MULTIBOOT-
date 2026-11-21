package com.josxtm.fm1ramloader

/** Conservative metadata assessment; integrity never implies executability. */
enum class FirmwareFormat { RAW_BIN, FWSC_CONTAINER, UNKNOWN }
enum class FirmwareExecution { RAM, XIP, FLASH, UNKNOWN }
enum class FirmwareCompatibility { VERIFIED, UNVERIFIED, INCOMPATIBLE }

data class FirmwareAssessment(
    val format: FirmwareFormat,
    val integrity: Boolean,
    val compatibility: FirmwareCompatibility,
    val execution: FirmwareExecution,
    val warnings: List<String>
)

internal fun FirmwareEntry.assess(): FirmwareAssessment {
    val format = when {
        fileName.endsWith(".BIN") -> FirmwareFormat.RAW_BIN
        fileName.endsWith(".FWS") -> FirmwareFormat.FWSC_CONTAINER
        else -> FirmwareFormat.UNKNOWN
    }
    val integrity = size > 0 && sha256.matches(Regex("[0-9a-f]{64}"))
    val warnings = buildList {
        if (!integrity) add("SHA-256 o tamaño no verificable")
        add("La integridad del archivo no demuestra compatibilidad ni ejecutabilidad")
        if (format == FirmwareFormat.RAW_BIN) add("BIN: formato de ejecución y ABI desconocidos")
        if (format == FirmwareFormat.FWSC_CONTAINER) add("FWS: contenedor opaco; no se desempaqueta")
        add("No hay evidencia de mapa RAM, ABI, XIP o método de arranque")
    }
    return FirmwareAssessment(format, integrity, FirmwareCompatibility.UNVERIFIED,
        FirmwareExecution.UNKNOWN, warnings)
}
