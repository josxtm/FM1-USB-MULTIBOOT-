package com.josxtm.fm1ramloader

/**
 * FM1CAT v1 records file presence and integrity, NEVER execution safety.
 * Short ASCII filenames suit the initial FAT32 reader.
 */
internal data class FirmwareEntry(
    val id: String,
    val label: String,
    val version: String,
    val fileName: String,
    val size: Long,
    val sha256: String,
    val compatibility: String = "UNVERIFIED"
)

internal object FirmwareCatalog {
    const val INDEX = "INDEX.CSV"
    const val MAX_ENTRIES = 64
    const val MAX_BYTES = 24576
    const val MAX_IMAGE_BYTES = 4L * 1024 * 1024

    private val idRegex = Regex("[A-Z0-9_]{1,8}")
    private val textRegex = Regex("[A-Za-z0-9 _.-]{1,24}")
    private val versionRegex = Regex("[A-Za-z0-9._-]{1,16}")
    private val hashRegex = Regex("[0-9a-f]{64}")

    fun cleanId(raw: String): String =
        raw.trim().uppercase().replace(Regex("[^A-Z0-9_]"), "_").take(8)

    private fun validate(entry: FirmwareEntry) {
        require(idRegex.matches(entry.id)) { "ID 8.3 no válido" }
        require(textRegex.matches(entry.label)) { "Nombre fuera del formato ASCII" }
        require(versionRegex.matches(entry.version)) { "Versión no válida" }
        require(entry.fileName == entry.id + ".BIN" || entry.fileName == entry.id + ".FWS") { "Ruta de firmware no válida" }
        require(entry.size in 1..MAX_IMAGE_BYTES) { "Tamaño no permitido" }
        require(hashRegex.matches(entry.sha256)) { "SHA-256 incorrecto" }
        require(entry.compatibility == "UNVERIFIED") { "Compatibilidad no certificada" }
    }

    fun encode(entries: List<FirmwareEntry>): String {
        require(entries.size <= MAX_ENTRIES) { "Catálogo completo" }
        require(entries.map { it.id }.distinct().size == entries.size) { "IDs duplicados" }
        entries.forEach(::validate)
        val text = buildString {
            append("FM1CAT|1\n")
            append("ID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\n")
            for (entry in entries) {
                append(
                    listOf(entry.id, entry.label, entry.version, entry.fileName,
                        entry.size.toString(), entry.sha256, entry.compatibility).joinToString("|")
                )
                append('\n')
            }
        }
        require(text.toByteArray(Charsets.US_ASCII).size <= MAX_BYTES) { "Catálogo demasiado grande" }
        return text
    }

    fun decode(text: String): List<FirmwareEntry> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Catálogo demasiado grande" }
        require(text.endsWith('\n')) { "Catálogo truncado" }
        require(!text.replace("\r\n", "").contains('\r')) { "CR aislado" }
        val normalized = text.replace("\r\n", "\n")
        val lines = normalized.dropLast(1).split('\n')
        require(lines.none { it.isEmpty() }) { "Línea vacía" }
        require(lines.size >= 2 && lines[0] == "FM1CAT|1" &&
            lines[1] == "ID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT") { "Formato FM1CAT incompatible" }
        val entries = lines.drop(2).map { line ->
            val parts = line.split('|')
            require(parts.size == 7) { "Fila corrupta" }
            val size = parts[4].toLongOrNull() ?: error("Tamaño inválido")
            FirmwareEntry(parts[0], parts[1], parts[2], parts[3],
                size, parts[5], parts[6]).also(::validate)
        }
        require(entries.size <= MAX_ENTRIES && entries.map { it.id }.distinct().size == entries.size) {
            "Catálogo lleno o duplicado"
        }
        return entries
    }
}