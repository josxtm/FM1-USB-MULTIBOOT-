package com.josxtm.fm1ramloader

import org.json.JSONArray
import org.json.JSONObject

internal interface CardStore {
    fun read(path: String, max: Int): ByteArray?
    fun write(path: String, data: ByteArray, replace: Boolean)
    fun writePackage(path: String, data: ByteArray, check: () -> Unit, written: (Int) -> Unit) {
        check()
        write(path, data, replace = false)
        written(data.size)
    }
    fun availableBytes(): Long? = null
    fun deleteStagingLegacy()
}

internal const val PAGE_SIZE = 32
internal const val MAX_RECORDS = 4096
internal const val MAX_PAGE = 128 * 1024
private const val MAX_INDEX = 24 * 1024
private val slots = listOf("CATALOG/INDEXA.V2", "CATALOG/INDEXB.V2")

internal data class CardRecord(
    val project: String, val name: String, val version: String, val original: String,
    val size: Long, val sha: String, val format: String, val identity: String,
    val crc: String, val layout: String, val source: String,
    val author: String = "", val nested: Boolean = false
) {
    val extension get() = when (original.substringAfterLast('.').lowercase()) {
        "fwsc" -> "FWS"
        "ufw" -> "UFW"
        "bin" -> "BIN"
        else -> error("Extensión original no admitida")
    }
    val flatPath get() = "FW/${sha.take(8).uppercase()}.$extension"
    private val oldPath get() = sha.uppercase().let { "FW/${it.take(2)}/${it.substring(2, 10)}/${it.substring(10, 18)}.$extension" }
    val path get() = if (nested) oldPath else flatPath

    fun json(): JSONObject = JSONObject()
        .put("project", project).put("name", name).put("version", version).put("original", original)
        .put("size", size).put("sha256", sha).put("format", format).put("identity", identity)
        .put("author", author).put("crc", crc).put("layout", layout).put("source", source).put("path", path).put("compat", "UNVERIFIED")

    companion object {
        fun decode(row: JSONObject): CardRecord {
            val record = CardRecord(row.field("project", 180), row.field("name", 160),
                row.field("version", 80), row.field("original", 160), row.getLong("size"),
                row.field("sha256", 64), row.field("format", 24), row.field("identity", 100),
                row.field("crc", 120), row.field("layout", 900), row.field("source", 512),
                if (row.has("author")) row.field("author", 160) else "")
            require(record.size in 1..MAX_ASSET.toLong() && HASH64.matches(record.sha) &&
                validBaseName(record.original) &&
                row.getString("path") in listOf(record.flatPath, record.oldPath) &&
                row.getString("compat") == "UNVERIFIED")
            return record.copy(nested = row.getString("path") == record.oldPath)
        }
    }
}

internal data class CardAsset(val record: CardRecord, val load: () -> ByteArray)
internal data class CardSnapshot(val sequence: Long, val slot: Int, val records: List<CardRecord>, val recovered: Boolean = false)

internal class CardLibrary(private val store: CardStore) {
    fun scan(): CardSnapshot {
        val candidates = slots.mapIndexed { index, path ->
            runCatching { store.read(path, MAX_INDEX)?.let { decodeIndex(it, index) } }
        }
        val good = candidates.mapNotNull { it.getOrNull() }
        if (good.isEmpty()) {
            require(candidates.all { it.isSuccess }) { "Ambos índices FM1CAT v2 son ilegibles; conserva la tarjeta para revisión" }
            return CardSnapshot(0, -1, emptyList())
        }
        require(good.size < 2 || good[0].sequence != good[1].sequence) { "Versiones de índice ambiguas" }
        return good.maxBy { it.sequence }.copy(recovered = candidates.any { it.isFailure })
    }

    fun publish(assets: List<CardAsset>, copy: CardCopy = CardCopy()): CardSnapshot {
        val result = publishChecked(assets, copy)
        copy.phase(CopyPhase.DONE)
        return result
    }

    private fun publishChecked(assets: List<CardAsset>, copy: CardCopy): CardSnapshot {
        copy.check()
        val current = scan()
        val have = current.records.associateBy { it.sha }
        val selected = assets.distinctBy { it.record.sha }
        copy.plan(selected.size, selected.sumOf { it.record.size })
        val added = selected.filter { it.record.sha !in have }
        require(added.size + have.size <= MAX_RECORDS) { "Biblioteca llena" }
        val additions = added.associate { it.record.sha to CardRecord.decode(it.record.copy(nested = false).json()) }
        val records = (current.records + additions.values).sortedBy { it.sha }
        rejectCollisions(records)
        for (item in selected) {
            copy.check()
            val record = additions[item.record.sha] ?: have.getValue(item.record.sha)
            copy.phase(CopyPhase.SPACE, record)
            val prior = store.read(record.path, MAX_ASSET)
            if (prior == null) {
                require(record.sha !in have) { "Copia registrada ausente: ${record.path}" }
                require((store.availableBytes() ?: Long.MAX_VALUE) >= record.size) { "Espacio insuficiente para ${record.original}" }
                val bytes = item.load()
                assertPayload(record, bytes)
                copy.check()
                copy.phase(CopyPhase.COPYING)
                store.writePackage(record.path, bytes, copy::check, copy::written)
            }
            copy.check()
            copy.phase(CopyPhase.VERIFYING)
            val output = prior ?: requireNotNull(store.read(record.path, MAX_ASSET)) { "No se pudo releer ${record.path}" }
            assertPayload(record, output)
            copy.verified(if (prior == null) 0 else record.size)
        }
        copy.check()
        if (added.isNotEmpty()) return commit(current, records, copy)
        copy.phase(CopyPhase.INDEX_VERIFY)
        return scan().also { require(it == current) { "El catálogo cambió durante la comprobación" } }
    }

    fun reorganize(): CardSnapshot {
        val current = scan()
        if (current.records.none { it.nested }) return current
        val records = current.records.map { it.copy(nested = false) }
        rejectCollisions(records)
        current.records.zip(records).forEach { (old, flat) ->
            val bytes = requireNotNull(store.read(old.path, MAX_ASSET)) { "Archivo ausente: ${old.path}" }
            assertPayload(old, bytes)
            if (old.nested) immutable(flat.path, bytes, MAX_ASSET)
        }
        require(records.all(::verify)) { "La biblioteca reorganizada no supera la verificación" }
        return commit(current, records)
    }

    private fun rejectCollisions(records: List<CardRecord>) {
        val collision = records.groupBy { it.flatPath }.entries.firstOrNull { it.value.size > 1 }
        require(collision == null) { "Colisión 8.3: ${collision?.key}; se conservan ambos originales" }
    }

    private fun commit(current: CardSnapshot, records: List<CardRecord>, copy: CardCopy = CardCopy()): CardSnapshot {
        copy.phase(CopyPhase.INDEX)
        val pages = records.chunked(PAGE_SIZE).mapIndexed { page, group ->
            ("FM1CAT|2|PAGE|${page + 1}\n" + group.joinToString("\n", postfix = "\n") { it.json().toString() })
                .encodeToByteArray().also { require(it.size <= MAX_PAGE) }
        }
        val hashes = pages.map(::sha256)
        val generation = sha256(hashes.joinToString("").encodeToByteArray()).take(16).uppercase()
        pages.forEachIndexed { i, bytes -> immutable(pagePath(generation, i), bytes, MAX_PAGE) }
        require(current.sequence < Long.MAX_VALUE)
        val target = if (current.slot == 0) 1 else 0
        val body = JSONObject().put("generation", generation).put("sequence", current.sequence + 1)
            .put("count", records.size).put("pages", JSONArray(hashes)).toString().encodeToByteArray()
        val wire = "FM1CAT|2\n${sha256(body)}\n".encodeToByteArray() + body
        val path = slots[target]
        require(scan() == current) { "El catálogo cambió durante la copia; vuelve a cargarlo" }
        store.write(path, wire, replace = current.slot >= 0)
        copy.phase(CopyPhase.INDEX_VERIFY)
        val reloaded = decodeIndex(requireNotNull(store.read(path, MAX_INDEX)), target)
        require(reloaded.records == records) { "La relectura del índice no coincide" }
        return reloaded
    }

    fun verify(record: CardRecord): Boolean {
        val bytes = store.read(record.path, MAX_ASSET) ?: return false
        return bytes.size.toLong() == record.size && sha256(bytes) == record.sha
    }

    /** The original implementation's 8.3 catalog, staging, re-read and backup contract. */
    fun exportLegacy(records: List<CardRecord>): Int {
        val old = store.read("INDEX.CSV", FirmwareCatalog.MAX_BYTES)
        val prior = old?.let { FirmwareCatalog.decode(it.toString(Charsets.UTF_8)) } ?: run {
            require(store.read("INDEX.BAK", FirmwareCatalog.MAX_BYTES) == null) { "Recupera INDEX.BAK antes de exportar" }
            emptyList()
        }
        require(store.read("INDEX.NEW", FirmwareCatalog.MAX_BYTES) == null) { "Existe INDEX.NEW; revisa la copia anterior" }
        val known = prior.associateBy { it.id }
        val knownHashes = prior.map { it.sha256 }.toHashSet()
        val additions = records.distinctBy { it.sha }.filter { it.sha !in knownHashes }.map { item ->
            require(item.extension != "UFW") { "FM1CAT v1 sólo admite BIN y FWS" }
            val id = item.sha.take(8).uppercase()
            require(known[id]?.sha256.let { it == null || it == item.sha }) { "Colisión de ID corto $id" }
            val label = item.name.replace(Regex("[^A-Za-z0-9 _.-]"), "_").take(24).ifBlank { id }
            val version = item.version.replace(Regex("[^A-Za-z0-9._-]"), "_").take(16).ifBlank { "1" }
            item to FirmwareEntry(id, label, version, "$id.${item.extension}", item.size, item.sha)
        }
        val newRows = additions.filter { (_, e) -> e.id !in known }
        val encoded = FirmwareCatalog.encode(prior + newRows.map { it.second }).encodeToByteArray()
        if (newRows.isEmpty()) return 0
        newRows.forEach { (item, entry) ->
            val bytes = requireNotNull(store.read(item.path, MAX_ASSET)) { "Archivo ausente: ${item.original}" }
            assertPayload(item, bytes)
            immutable("FW/${entry.fileName}", bytes, FirmwareCatalog.MAX_IMAGE_BYTES.toInt())
        }
        immutable("INDEX.NEW", encoded, FirmwareCatalog.MAX_BYTES)
        val current = store.read("INDEX.CSV", FirmwareCatalog.MAX_BYTES)
        require(if (old == null) current == null else current?.contentEquals(old) == true) {
            "INDEX.CSV cambió durante la exportación"
        }
        if (old != null) {
            store.write("INDEX.BAK", old, store.read("INDEX.BAK", FirmwareCatalog.MAX_BYTES) != null)
            require(store.read("INDEX.BAK", FirmwareCatalog.MAX_BYTES)?.contentEquals(old) == true)
        }
        store.write("INDEX.CSV", encoded, old != null)
        require(store.read("INDEX.CSV", FirmwareCatalog.MAX_BYTES)?.contentEquals(encoded) == true)
        store.deleteStagingLegacy()
        return newRows.size
    }

    fun recoverLegacy() {
        val current = store.read("INDEX.CSV", FirmwareCatalog.MAX_BYTES)
        if (current != null && runCatching { FirmwareCatalog.decode(current.toString(Charsets.UTF_8)) }.isSuccess) {
            if (store.read("INDEX.NEW", FirmwareCatalog.MAX_BYTES)?.contentEquals(current) == true)
                store.deleteStagingLegacy()
            return
        }
        val backup = requireNotNull(store.read("INDEX.BAK", FirmwareCatalog.MAX_BYTES)) { "No hay INDEX.BAK" }
        FirmwareCatalog.decode(backup.toString(Charsets.UTF_8))
        store.write("INDEX.CSV", backup, current != null)
        require(store.read("INDEX.CSV", FirmwareCatalog.MAX_BYTES)?.contentEquals(backup) == true)
    }

    private fun assertPayload(record: CardRecord, bytes: ByteArray) {
        require(bytes.size.toLong() == record.size && sha256(bytes) == record.sha) { "Paquete ausente o modificado" }
    }

    private fun immutable(path: String, data: ByteArray, max: Int) {
        val prior = store.read(path, max)
        if (prior == null) store.write(path, data, replace = false)
        val output = prior ?: store.read(path, max)
        require(output?.contentEquals(data) == true) { "Relectura o nombre en conflicto: $path" }
    }

    private fun decodeIndex(data: ByteArray, slot: Int): CardSnapshot {
        val fields = data.toString(Charsets.UTF_8).split('\n', limit = 3)
        require(fields.size == 3 && fields[0] == "FM1CAT|2" &&
            sha256(fields[2].encodeToByteArray()) == fields[1]) { "Hash de índice v2 incorrecto" }
        val body = JSONObject(fields[2])
        val generation = body.getString("generation")
        val count = body.getInt("count")
        val seq = body.getLong("sequence")
        require(Regex("[A-F0-9]{16}").matches(generation) && seq > 0 && count in 1..MAX_RECORDS)
        val pages = body.getJSONArray("pages")
        require(pages.length() == (count + PAGE_SIZE - 1) / PAGE_SIZE)
        val hashes = List(pages.length()) { pages.getString(it).also { hash -> require(HASH64.matches(hash)) } }
        require(sha256(hashes.joinToString("").encodeToByteArray()).take(16).uppercase() == generation)
        val records = hashes.flatMapIndexed { i, hash ->
            val bytes = requireNotNull(store.read(pagePath(generation, i), MAX_PAGE))
            require(sha256(bytes) == hash) { "Página de catálogo dañada" }
            val lines = bytes.toString(Charsets.UTF_8).split('\n')
            val expected = minOf(PAGE_SIZE, count - i * PAGE_SIZE)
            require(lines.first() == "FM1CAT|2|PAGE|${i + 1}" && lines.last().isEmpty() && lines.size == expected + 2)
            lines.drop(1).dropLast(1).map { CardRecord.decode(JSONObject(it)) }
        }
        require(records.zipWithNext().all { (left, right) -> left.sha < right.sha }) { "Índice fuera de orden" }
        require(records.map { it.path }.toSet().size == records.size) { "Rutas físicas repetidas" }
        return CardSnapshot(seq, slot, records)
    }

    private fun pagePath(generation: String, page: Int) =
        "CATALOG/${generation.take(8)}/${generation.drop(8)}/P${(page + 1).toString().padStart(7, '0')}.JSN"
}
