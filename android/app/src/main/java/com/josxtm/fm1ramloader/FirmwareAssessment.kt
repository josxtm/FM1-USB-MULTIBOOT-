package com.josxtm.fm1ramloader

import java.io.InputStream
import java.security.MessageDigest

enum class FirmwareFormat { RAW_BIN, FWSC_CONTAINER, UNKNOWN }
enum class FirmwareExecution { RAM, XIP, FLASH, UNKNOWN }
enum class FirmwareCompatibility { VERIFIED, UNVERIFIED, INCOMPATIBLE }

data class FirmwareAssessment(
    val format: FirmwareFormat,
    val metadataValid: Boolean,
    val registeredHashValid: Boolean,
    val calculatedSha256: String?,
    val actualSize: Long?,
    val integrityVerified: Boolean,
    val compatibility: FirmwareCompatibility,
    val execution: FirmwareExecution,
    val warnings: List<String>
) {
    /** Compatibility alias retained for callers; use integrityVerified for UI. */
    val integrity: Boolean get() = integrityVerified
}

internal object FirmwareAssetVerifier {
    fun verify(entry: FirmwareEntry, input: InputStream): FirmwareAssessment {
        val metadata = entry.assess()
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        var size = 0L
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(count > 0) { "Lectura sin progreso" }
                size += count
                require(size <= FirmwareCatalog.MAX_IMAGE_BYTES) { "Firmware demasiado grande" }
                digest.update(buffer, 0, count)
            }
        } finally {
            input.close()
        }
        val calculated = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        val verified = metadata.metadataValid && metadata.registeredHashValid &&
            size == entry.size && calculated == entry.sha256
        val warnings = metadata.warnings.toMutableList()
        if (size != entry.size) warnings += "Tamaño real distinto del catálogo"
        if (calculated != entry.sha256) warnings += "SHA-256 real distinto del registrado"
        return metadata.copy(calculatedSha256 = calculated, actualSize = size,
            integrityVerified = verified, warnings = warnings)
    }
}

internal fun FirmwareEntry.assess(): FirmwareAssessment {
    val format = when {
        fileName.endsWith(".BIN") -> FirmwareFormat.RAW_BIN
        fileName.endsWith(".FWS") -> FirmwareFormat.FWSC_CONTAINER
        else -> FirmwareFormat.UNKNOWN
    }
    val metadataValid = id.matches(Regex("[A-Z0-9_]{1,8}")) &&
        (fileName == "$id.BIN" || fileName == "$id.FWS")
    val registeredHashValid = sha256.matches(Regex("[0-9a-f]{64}"))
    val warnings = buildList {
        if (!metadataValid) add("Metadatos o nombre no válidos")
        if (!registeredHashValid) add("SHA-256 registrado no válido")
        add("La integridad no demuestra compatibilidad ni ejecutabilidad")
        if (format == FirmwareFormat.RAW_BIN) add("BIN: formato de ejecución y ABI desconocidos")
        if (format == FirmwareFormat.FWSC_CONTAINER) add("FWS: contenedor opaco; no se desempaqueta")
        add("No hay evidencia de mapa RAM, ABI, XIP o método de arranque")
    }
    return FirmwareAssessment(format, metadataValid, registeredHashValid, null, null, false,
        FirmwareCompatibility.UNVERIFIED, FirmwareExecution.UNKNOWN, warnings)
}
