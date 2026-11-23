package com.josxtm.fm1ramloader

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal data class QueueItem(val listing: Listing, val link: PackageLink, val state: String = "En cola",
                              val progress: Long = 0, val attempts: Int = 0)
internal data class ImportItem(val record: CardRecord, val file: File)

internal class ManagerModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("manager", 0)
    private val manifest = AtomicFile(File(app.filesDir, "discovery.json"))
    private val importIndex = AtomicFile(File(app.filesDir, "imports.json"))
    private val cacheDir = File(app.filesDir, "packages")
    private val cache = DownloadCache(cacheDir)
    private val network = Executors.newFixedThreadPool(3).asCoroutineDispatcher()
    private val storage = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val pauseFlag = AtomicBoolean(false)
    private var active = 0
    private var cardUri: Uri? = prefs.getString("card", null)?.let(Uri::parse)
    val jobs = mutableStateMapOf<String, QueueItem>()
    val imports = mutableStateMapOf<String, ImportItem>()
    var discovery by mutableStateOf<Discovery?>(null)
        private set
    var cached by mutableStateOf(cache.complete())
        private set
    var card by mutableStateOf<CardSnapshot?>(null)
        private set
    var cardStatus by mutableStateOf("Sin carpeta FM1")
        private set
    var status by mutableStateOf("Cargando catálogo…")
        private set
    var busy by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set

    init {
        task {
            val found = runCatching {
                manifest.openRead().use { Discovery.decode(it.bounded(MAX_MANIFEST)) }
            }.getOrElse {
                app.assets.open("discovery.json").use { Discovery.decode(it.bounded(MAX_MANIFEST)) }
            }
            val restoredImports = runCatching { loadImports() }
            withContext(Dispatchers.Main) {
                discovery = found
                imports.putAll(restoredImports.getOrDefault(emptyMap()))
                status = "${found.entries.size} fichas · ${found.updated}" +
                    if (restoredImports.isFailure) " · Índice local de importaciones ilegible" else ""
            }
            cardUri?.let { uri ->
                runCatching { CardLibrary(SafCardStore(app, uri)).scan() }
                    .onSuccess { result -> withContext(Dispatchers.Main) { showCard(result) } }
                    .onFailure { withContext(Dispatchers.Main) { cardStatus = "Carpeta no disponible; vuelve a seleccionarla" } }
            }
        }
    }

    fun loadManifest(uri: Uri) = task {
        val bytes = requireNotNull(app.contentResolver.openInputStream(uri)).use { it.bounded(MAX_MANIFEST) }
        val parsed = Discovery.decode(bytes)
        save(manifest, bytes)
        withContext(Dispatchers.Main) {
            discovery = parsed
            status = "Manifiesto cargado · ${parsed.entries.size} fichas"
        }
    }

    fun chooseCard(uri: Uri) = task {
        val store = SafCardStore(app, uri)
        SafCardStore.persist(app, uri)
        val result = CardLibrary(store).scan()
        withContext(Dispatchers.Main) {
            cardUri = uri
            prefs.edit().putString("card", uri.toString()).apply()
            showCard(result)
        }
    }

    fun reloadCard() {
        val uri = cardUri ?: return
        task {
            try {
                val result = CardLibrary(SafCardStore(app, uri)).scan()
                withContext(Dispatchers.Main) { showCard(result) }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    card = null
                    cardStatus = "Tarjeta no disponible o índice ilegible"
                }
                throw error
            }
        }
    }

    private fun showCard(result: CardSnapshot) {
        card = result
        cardStatus = "${result.records.size} en el índice microSD" +
            if (result.recovered) " · recuperado desde la copia" else ""
    }

    fun enqueue(listings: List<Listing>) {
        val stored = card?.records?.map { it.sha }?.toHashSet().orEmpty()
        var added = 0
        for ((listing, link) in listings.flatMap { row -> row.packages.map { row to it } }) {
            if (link.sha256 in stored || link.sha256 in cached) continue
            val prior = jobs[link.sha256]
            if (prior == null) {
                jobs[link.sha256] = QueueItem(listing, link)
                added++
            } else if (prior.state.startsWith("Error") && prior.link.url != link.url) {
                jobs[link.sha256] = QueueItem(listing, link)
                added++
            }
        }
        status = if (added == 0) "Ningún paquete nuevo en la selección" else "$added paquetes nuevos en cola"
        pump()
    }

    fun changePause() {
        paused = !paused
        pauseFlag.set(paused)
        if (!paused) {
            jobs.toMap().forEach { (key, item) -> if (item.state == "Pausado") jobs[key] = item.copy(state = "En cola") }
            pump()
        }
    }

    fun retry() {
        jobs.toMap().forEach { (key, item) ->
            if (item.state.startsWith("Error") && item.attempts < 4) jobs[key] = item.copy(state = "En cola")
        }
        pump()
    }

    private fun pump() {
        if (paused) return
        while (active < 3) {
            val entry = jobs.entries.firstOrNull { it.value.state == "En cola" } ?: return
            val key = entry.key
            val item = entry.value
            active++
            jobs[key] = item.copy(state = "Descargando", attempts = item.attempts + 1)
            viewModelScope.launch {
                try {
                    withContext(network) {
                        var nextReport = 0L
                        cache.obtain(item.link, pauseFlag::get) { n ->
                            if (n >= nextReport) {
                                nextReport = n + 256 * 1024
                                viewModelScope.launch { jobs[key]?.takeIf { it.state == "Descargando" }?.let {
                                    jobs[key] = it.copy(progress = n)
                                } }
                            }
                        }
                    }
                    cached = cached + key
                    jobs[key] = item.copy(state = "Verificado", progress = item.link.size)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: DownloadPaused) {
                    jobs[key] = item.copy(state = if (paused) "Pausado" else "En cola")
                } catch (error: Exception) {
                    jobs[key] = item.copy(state = "Error · ${error.message ?: "transferencia"}", attempts = item.attempts + 1)
                } finally {
                    active--
                    pump()
                }
            }
        }
    }

    fun importFiles(uris: List<Uri>) = task {
        var accepted = 0
        val failures = mutableListOf<String>()
        val index = loadImports().toMutableMap()
        for (uri in uris) {
            val name = displayName(uri)
            try {
                val item = requireNotNull(app.contentResolver.openInputStream(uri)).use { cache.importOriginal(name, it) }
                val record = record("import/" + item.digest.take(16), name.take(160), "Importado",
                    name, item, "Importación manual")
                require(index.size < MAX_RECORDS || item.digest in index) { "Límite de importaciones alcanzado" }
                index[item.digest] = ImportItem(record, item.file)
                val entries = JSONArray(index.values.map { it.record.json() })
                save(importIndex, entries.toString().encodeToByteArray())
                withContext(Dispatchers.Main) {
                    imports[item.digest] = ImportItem(record, item.file)
                    cached = cached + item.digest
                }
                accepted++
            } catch (error: Exception) {
                failures += "$name · ${error.message}"
            }
        }
        withContext(Dispatchers.Main) {
            status = "$accepted importados" + if (failures.isEmpty()) "" else " · " + failures.take(3).joinToString(" · ")
        }
    }

    fun export(listings: List<Listing>, manual: List<ImportItem>) {
        val uri = cardUri ?: return
        val selected = listings.flatMap { row -> row.packages.filter { it.sha256 in cached }.map { row to it } }
        task {
            val library = CardLibrary(SafCardStore(app, uri))
            val items = manual.map { CardAsset(it.record) { it.file.inputStream().use { input -> input.bounded(MAX_ASSET) } } } +
                selected.map { (listing, link) ->
                    val item = cache.existing(link)
                    CardAsset(record(listing.id, listing.name, link.version, link.file, item, link.url)) {
                        item.file.inputStream().use { it.bounded(MAX_ASSET) }
                    }
                }
            require(items.isNotEmpty()) { "Selecciona paquetes verificados o importados" }
            val result = library.publish(items)
            withContext(Dispatchers.Main) {
                showCard(result)
                status = "Copia e índice comprobados · ${result.records.size} registros"
            }
        }
    }

    fun verifyCard(item: CardRecord) {
        val uri = cardUri ?: return
        task {
            val valid = CardLibrary(SafCardStore(app, uri)).verify(item)
            withContext(Dispatchers.Main) { status = if (valid) "Copia SD verificada · ${item.original}" else "Archivo SD ausente o modificado" }
        }
    }

    fun exportV1(records: List<CardRecord>) {
        val uri = cardUri ?: return
        task {
            val count = CardLibrary(SafCardStore(app, uri)).exportLegacy(records)
            withContext(Dispatchers.Main) { status = "FM1CAT v1 comprobado · $count nuevas entradas" }
        }
    }

    fun recoverV1() {
        val uri = cardUri ?: return
        task {
            CardLibrary(SafCardStore(app, uri)).recoverLegacy()
            withContext(Dispatchers.Main) { status = "INDEX.CSV validado" }
        }
    }

    private fun record(project: String, name: String, version: String, original: String,
                       asset: CachedPackage, source: String) = CardRecord(project, name, version,
        original, asset.size, asset.digest, asset.info.container, asset.info.identity, asset.info.crc, asset.info.layout, source)

    private fun displayName(uri: Uri): String =
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "original"

    private fun loadImports(): Map<String, ImportItem> {
        if (!importIndex.baseFile.exists() && !File(importIndex.baseFile.path + ".bak").exists()) return emptyMap()
        val array = importIndex.openRead().use { JSONArray(it.bounded(12 * 1024 * 1024).toString(Charsets.UTF_8)) }
        require(array.length() <= MAX_RECORDS)
        return List(array.length()) { i ->
            val row = CardRecord.decode(array.getJSONObject(i))
            row.sha to ImportItem(row, File(cacheDir, "${row.sha}.blob"))
        }.toMap()
    }

    private fun save(destination: AtomicFile, bytes: ByteArray) {
        val stream = destination.startWrite()
        try {
            stream.write(bytes)
            destination.finishWrite(stream)
        } catch (error: Exception) {
            destination.failWrite(stream)
            throw error
        }
    }

    private fun task(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                withContext(storage) { action() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                status = error.message ?: "Operación interrumpida"
            } finally {
                busy = false
            }
        }
    }

    override fun onCleared() {
        pauseFlag.set(true)
        network.close()
        storage.close()
    }
}
