package com.josxtm.fm1ramloader

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val mint = Color(0xFF76E3C2)
private val pale = Color(0xFFB3CBC3)
private val disk = Color(0xFF101C1A)

class ManagerActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[ManagerModel::class.java] }
    private val chooseFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(model::chooseCard)
    }
    private val choosePackages = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) model.importFiles(uris)
    }
    private val chooseManifest = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { files ->
        if (files.isNotEmpty()) model.loadManifest(files)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = mint, secondary = Color(0xFFF4CE82),
                background = Color(0xFF091210), surface = disk, onSurface = Color(0xFFE4F3ED))) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ManagerScreen(model,
                        choose = { chooseFolder.launch(null) },
                        import = { choosePackages.launch(arrayOf("*/*")) },
                        refresh = { chooseManifest.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        systemStorage = {
                            runCatching { startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
                                .recoverCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                        },
                        open = { link ->
                            if (https(link)) runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                        })
                }
            }
        }
    }
}

private val statusLabels = mapOf(
    "AUTHOR" to "Publicación del autor", "COMMUNITY" to "Compilación comunitaria · hardware sin probar",
    "SOURCE" to "Código con receta de compilación", "RESTRICTED" to "Distribución sin autorización verificada",
    "INCOMPATIBLE" to "Otro dispositivo", "UNVERIFIED" to "Proyecto por verificar",
    "TOOL" to "Herramienta", "FORK" to "Fork sin paquete independiente",
    "DUPLICATE" to "Paquete duplicado", "NO_DIRECT" to "Sin descarga directa",
    "LICENSE_UNKNOWN" to "Licencia o autorización sin acreditar", "REMOVED" to "Enlace retirado",
    "BLOCKED" to "Distribución bloqueada", "ACCESS" to "Acceso pendiente de verificar",
    "TEMPORARY" to "Error temporal del servidor"
)
private fun availabilityLabel(row: Listing, blocked: Set<String>): String = when {
    row.downloads(blocked).isNotEmpty() -> statusLabels[row.status].orEmpty()
    row.packages.any { it.url in blocked } -> "Enlace retirado o bloqueado"
    row.status in setOf("AUTHOR", "COMMUNITY") -> "Descarga sin verificar"
    else -> statusLabels[row.status].orEmpty()
}

private val genreLabels = mapOf("synth" to "Síntesis", "groove" to "Groovebox", "drums" to "Batería / sampler",
    "tracker" to "Tracker", "chords" to "Acordes", "other" to "Experimental", "dev" to "MIDI / desarrollo")

@Composable
private fun ManagerScreen(model: ManagerModel, choose: () -> Unit, import: () -> Unit,
                          refresh: () -> Unit, systemStorage: () -> Unit, open: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf("Catálogo") }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var showGenres by rememberSaveable { mutableStateOf(false) }
    var filterName by rememberSaveable { mutableStateOf(DownloadFilter.AVAILABLE.name) }
    val filter = DownloadFilter.valueOf(filterName)
    val blocked = model.blockedUrls
    var inspected by rememberSaveable { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var copySelection by remember { mutableStateOf(setOf<String>()) }
    val entries = model.discovery?.entries.orEmpty()
    val staged = model.cached
    val library = model.card?.records.orEmpty()
    val onCard = remember(library) { library.map { it.sha }.toSet() }
    val updates = remember(model.discovery, library, blocked, model.omitted) {
        firmwareUpdates(model.discovery, library, blocked, model.omitted)
    }
    val updateReady = remember(updates) { updates.filter { it.candidate != null } }
    val updateProjects = remember(updateReady) { updateReady.mapTo(hashSetOf()) { it.row.id } }
    val downloadedCount = remember(entries, blocked) { entries.count { it.downloads(blocked).isNotEmpty() } }
    var updateSelection by remember { mutableStateOf(setOf<String>()) }
    var toolsOpen by remember { mutableStateOf(false) }
    val copy by model.copyProgress.collectAsState()
    val filtered = remember(entries, query, category, filter, blocked) {
        val term = query.trim()
        entries.filter { row ->
            (term.isEmpty() || listOf(row.name, row.author, row.repo, row.description).any { it.contains(term, true) }) &&
                (category.isEmpty() || category in row.genres) && filter.matches(row, blocked)
        }
    }
    val selected = remember(entries, selection) { entries.filter { it.id in selection } }
    val selectedForDownload = filtered.filter { it.id in selection && it.downloads(blocked).isNotEmpty() }
    val selectedRow = filtered.find { it.id == inspected } ?: filtered.firstOrNull()

    AdaptiveScreen {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FM1 Manager To USB", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(model.cardStatus, color = mint, style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(onClick = choose, enabled = !model.busy) { Text("Elegir carpeta FM1") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            listOf("Catálogo", "Descargas", "MicroSD", "Actualizaciones").forEach { name ->
                val count = when (name) {
                    "Descargas" -> model.jobs.values.count { it.state in setOf("En cola", "Pausado", "Descargando", "Descargando actualización") }
                    "Actualizaciones" -> updateReady.size
                    else -> 0
                }
                FilterChip(selected = tab == name, onClick = { tab = name },
                    label = { Text(if (count > 0) "$name ($count)" else name) })
            }
            TextButton(onClick = { tab = "Actualizaciones"; model.checkUpdates() }, enabled = !model.refreshing) {
                Text("Buscar actualizaciones")
            }
            Box {
                TextButton(onClick = { toolsOpen = true }) { Text("Más") }
                DropdownMenu(expanded = toolsOpen, onDismissRequest = { toolsOpen = false }) {
                    DropdownMenuItem(text = { Text("Importar originales") }, enabled = !model.busy,
                        onClick = { toolsOpen = false; import() })
                    DropdownMenuItem(text = { Text("Cargar manifiesto") }, enabled = !model.busy,
                        onClick = { toolsOpen = false; refresh() })
                    DropdownMenuItem(text = { Text("Manifiestos en GitHub") },
                        onClick = { toolsOpen = false; open(ManifestRefresh.PORTAL) })
                }
            }
        }
        Text("${library.size} firmwares en microSD · $downloadedCount fichas descargables · ${model.status}",
            color = pale, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        copy?.let {
            if (model.busy || tab in setOf("Descargas", "MicroSD", "Actualizaciones")) {
                CopyBanner(it, model.busy, model::cancelCopy, systemStorage)
            }
        }

        when (tab) {
            "Catálogo" -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true,
                        label = { Text("Buscar firmware, autor o función") })
                    FilterChip(selected = showGenres || category.isNotEmpty(),
                        onClick = { showGenres = !showGenres },
                        label = { Text(if (category.isEmpty()) "Géneros" else genreLabels[category] ?: category) })
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DownloadFilter.entries.forEach { option ->
                        FilterChip(selected = filter == option, onClick = { filterName = option.name },
                            label = { Text(option.label) })
                    }
                }
                if (showGenres) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((tag, name) in genreLabels) {
                            FilterChip(selected = category == tag, onClick = { category = if (category == tag) "" else tag },
                                label = { Text(name) })
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { model.enqueue(selectedForDownload); tab = "Descargas" },
                        enabled = selectedForDownload.isNotEmpty() && !model.busy) { Text("Descargar selección") }
                    OutlinedButton(onClick = { model.enqueue(filtered); tab = "Descargas" },
                        enabled = !model.busy && filtered.any { it.downloads(blocked).isNotEmpty() }) { Text("Nuevos del filtro") }
                    TextButton(onClick = { selection = filtered.filter { it.downloads(blocked).isNotEmpty() }.mapTo(hashSetOf()) { it.id } }) {
                        Text("Todos los descargables")
                    }
                    if (selection.isNotEmpty()) TextButton(onClick = { selection = emptySet() }) { Text("Limpiar") }
                    Text("${filtered.size} fichas · ${selection.size} marcadas", color = pale,
                        style = MaterialTheme.typography.labelMedium)
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    if (maxWidth >= 760.dp) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            CatalogList(filtered, selection, selectedRow?.id, staged, onCard, blocked, updateProjects, Modifier.weight(0.35f), {
                                inspected = it.id
                            }, { selection = toggle(selection, it.id) })
                            DetailPanel(selectedRow, blocked, updates.firstOrNull { it.row.id == selectedRow?.id }, Modifier.weight(0.65f), open)
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            CatalogList(filtered, selection, selectedRow?.id, staged, onCard, blocked, updateProjects, Modifier.weight(1f), {
                                inspected = it.id
                            }, { selection = toggle(selection, it.id) })
                            DetailPanel(selectedRow, blocked, updates.firstOrNull { it.row.id == selectedRow?.id }, Modifier.weight(1.15f), open)
                        }
                    }
                }
                Text("Catálogo: The Firmware Hub · CC BY 4.0", color = pale,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable { open("https://thefirmwarehub.com/") }.padding(vertical = 2.dp))
            }

            "Descargas" -> {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = model::changePause, enabled = !model.busy) { Text(if (model.paused) "Reanudar" else "Pausar") }
                    OutlinedButton(onClick = model::retry, enabled = !model.busy) { Text("Reintentar fallos") }
                    Button(onClick = {
                        model.export(selected, model.imports.values.filter { it.record.sha in copySelection })
                    }, enabled = !model.busy && model.card != null) { Text("Copiar selección a microSD") }
                    TextButton(onClick = {
                        selection = entries.filter { row -> row.packages.any { it.sha256 in staged } }.map { it.id }.toSet()
                        copySelection = model.imports.keys.toSet()
                    }) { Text("Seleccionar archivos en caché") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(model.imports.values.toList(), key = { "import/" + it.record.sha }) { imported ->
                        QueueRow(imported.record.name, "Importación manual · ${imported.record.crc}",
                            imported.record.sha in copySelection, null) {
                            copySelection = toggle(copySelection, imported.record.sha)
                        }
                    }
                    items(model.jobs.values.sortedBy { it.listing.name.lowercase() }, key = { it.link.sha256 }) { job ->
                        QueueRow("${job.listing.name} · ${job.link.version}", job.state,
                            job.listing.id in selection || job.link.sha256 in onCard,
                            if (job.state.startsWith("Descargando")) job.progress.toFloat() / job.link.size else null) {
                            selection = toggle(selection, job.listing.id)
                        }
                    }
                    val jobs = model.jobs.keys.toSet()
                    items(entries.filter { row -> row.packages.any { it.sha256 in staged && it.sha256 !in jobs } },
                        key = Listing::id) { row ->
                        QueueRow(row.name, "Caché existente · se releerá antes de exportar",
                            row.id in selection, null) { selection = toggle(selection, row.id) }
                    }
                }
            }

            "Actualizaciones" -> {
                val active = updateReady.filter { it.key in updateSelection }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { model.update(active) }, enabled = !model.busy && active.isNotEmpty()) {
                        Text("Actualizar selección")
                    }
                    OutlinedButton(onClick = { model.update(updateReady) }, enabled = !model.busy && updateReady.isNotEmpty()) {
                        Text("Actualizar todas (${updateReady.size})")
                    }
                    if (model.omitted.isNotEmpty()) TextButton(onClick = model::restoreOmitted) { Text("Recuperar omitidas") }
                }
                if (updates.isEmpty()) Text(if (model.card == null) "Elige la carpeta FM1 para comparar sus versiones."
                    else "No hay versiones nuevas verificables en el catálogo activo.", color = pale)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(updates, key = { it.row.id }) { item ->
                        UpdateCard(item, updateSelection, model.cached, model.jobs, model.busy,
                            select = { updateSelection = toggle(updateSelection, item.key) },
                            update = { model.update(listOf(item)) }, omit = { model.omit(item) })
                    }
                }
            }

            else -> {
                Text("Android SAF no confirma el tipo de sistema de archivos. Comprueba FAT32 en la tarjeta; la app no la formatea.",
                    color = pale, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = model::reorganizeCard,
                        enabled = !model.busy && library.any { it.nested }) { Text("Reorganizar en FW") }
                    OutlinedButton(onClick = model::reloadCard, enabled = !model.busy) { Text("Recargar índice") }
                    OutlinedButton(onClick = { model.exportV1(library.filter { it.sha in copySelection }) },
                        enabled = !model.busy && library.any { it.sha in copySelection }) { Text("Exportar selección FM1CAT v1") }
                    TextButton(onClick = model::recoverV1, enabled = !model.busy) { Text("Recuperar índice v1") }
                    TextButton(onClick = systemStorage, enabled = !model.busy) { Text("Ajustes de almacenamiento") }
                }
                if (model.busy && copy?.phase !in setOf(CopyPhase.PREPARING, CopyPhase.SPACE, CopyPhase.COPYING,
                        CopyPhase.VERIFYING, CopyPhase.INDEX, CopyPhase.INDEX_VERIFY)) {
                    Text("No desconectes la microSD: operación en curso.", color = mint)
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(library, key = CardRecord::sha) { row ->
                        Surface(color = disk, shape = MaterialTheme.shapes.medium) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(row.sha in copySelection, { copySelection = toggle(copySelection, row.sha) })
                                Column(Modifier.weight(1f)) {
                                    Text(row.name, fontWeight = FontWeight.SemiBold)
                                    Text("${row.version} · ${row.format} · ${row.crc}", color = pale)
                                    Text("${row.path} · ${row.size} B · ${row.sha.take(12)}", color = pale,
                                        style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick = { model.verifyCard(row) }, enabled = !model.busy) { Text("Verificar original") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdaptiveScreen(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        // A bounded viewport keeps the lazy lists usable when the header takes most of a short window.
        val layout = if (maxHeight < 555.dp) {
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).height(555.dp)
        } else Modifier.fillMaxSize()
        Column(layout.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
    }
}

private fun toggle(keys: Set<String>, key: String) = if (key in keys) keys - key else keys + key

@Composable
private fun CatalogList(rows: List<Listing>, selected: Set<String>, current: String?, cached: Set<String>,
                        onCard: Set<String>, blocked: Set<String>, updates: Set<String>, modifier: Modifier, open: (Listing) -> Unit, check: (Listing) -> Unit) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items(rows, key = Listing::id) { row ->
            val link = row.downloads(blocked).firstOrNull()
            val state = when {
                row.id in updates -> "Actualización disponible"
                row.packages.any { it.sha256 in onCard } -> "En índice microSD"
                row.packages.any { it.sha256 in cached } -> "En caché"
                else -> availabilityLabel(row, blocked)
            }
            Surface(color = if (current == row.id) Color(0xFF214036) else disk, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().clickable { open(row) }.padding(end = 12.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(row.id in selected, { check(row) }, enabled = link != null)
                    Column(Modifier.weight(1f)) {
                        Text(row.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(row.author, row.version).filter(String::isNotBlank).joinToString(" · "),
                            color = pale, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Text(state, color = if (link != null) mint else pale,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailPanel(row: Listing?, blocked: Set<String>, update: FirmwareUpdate?, modifier: Modifier, open: (String) -> Unit) {
    Surface(modifier.fillMaxSize(), color = disk, shape = MaterialTheme.shapes.large) {
        if (row == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay fichas para este filtro") }
        } else key(row.id) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(row.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(listOf(row.author, row.version, row.license).filter(String::isNotBlank).joinToString(" · "),
                    color = mint, style = MaterialTheme.typography.bodyLarge)
                Text(update?.candidate?.let { "Actualización disponible · ${it.version}" } ?:
                    availabilityLabel(row, blocked), color = pale)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val showSideBySide = maxWidth >= 540.dp && row.image != null
                    val imageSize = minOf(maxWidth * .48f, 320.dp)
                    val summary = row.description.ifBlank { "El autor no ha publicado una descripción." }
                    if (showSideBySide) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top) {
                            CardPhoto(row.image, row.name, Modifier.size(imageSize))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("DESCRIPCIÓN", style = MaterialTheme.typography.labelMedium, color = mint)
                                Text(summary, style = MaterialTheme.typography.bodyLarge)
                                val genres = row.genres.map { genreLabels[it] ?: it }
                                if (genres.isNotEmpty()) Text(genres.joinToString(" · "), color = pale)
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (row.image != null) CardPhoto(row.image, row.name, Modifier.fillMaxWidth().height(240.dp))
                            else CardPhoto(null, row.name, Modifier.fillMaxWidth().height(64.dp))
                            Text("DESCRIPCIÓN", style = MaterialTheme.typography.labelMedium, color = mint)
                            Text(summary, style = MaterialTheme.typography.bodyLarge)
                            val genres = row.genres.map { genreLabels[it] ?: it }
                            if (genres.isNotEmpty()) Text(genres.joinToString(" · "), color = pale)
                        }
                    }
                }
                row.variantOf?.let { Text("Variante de $it", color = pale, style = MaterialTheme.typography.labelSmall) }
                HorizontalDivider()
                if (row.downloads(blocked).isEmpty()) Text("Sin descarga directa. Puedes importar un original obtenido por tu cuenta.", color = pale)
                Text("Perfil publicado: ${row.compatibility} · arranque sin verificar", style = MaterialTheme.typography.bodySmall)
                if (row.identity.isNotBlank()) Text(row.identity, style = MaterialTheme.typography.labelSmall)
                for (link in row.packages) {
                    Text("${link.version} · ${link.format} · ${link.size} B" +
                        when {
                            link in row.downloads(blocked) -> ""
                            !link.current && link.availability == "AVAILABLE" -> " · versión histórica"
                            else -> " · no descargable"
                        }, fontWeight = FontWeight.Medium)
                    Text(link.sha256, color = pale, style = MaterialTheme.typography.labelSmall)
                    link.publishedAt?.let { Text(it.toString().take(10), color = pale) }
                    if (link.notes.isNotBlank()) Text(link.notes, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { open(link.release) }) { Text("Versión publicada") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { open(row.source) }) { Text("Fuente del autor") }
                    if (row.repo.isNotBlank()) TextButton(onClick = { open("https://github.com/${row.repo}/releases") }) {
                        Text("Historial")
                    }
                    row.demo?.let { link -> TextButton(onClick = { open(link) }) { Text("Demostración") } }
                }
            }
        }
    }
}

private val photoCache = object : LruCache<String, ImageBitmap>(24 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = maxOf(1, value.width * value.height / 256)
}

@Composable
internal fun CardPhoto(url: String?, title: String, modifier: Modifier) {
    var picture by remember(url) { mutableStateOf(url?.let(photoCache::get)) }
    var loading by remember(url) { mutableStateOf(url != null && picture == null) }
    LaunchedEffect(url) {
        if (url != null && picture == null) {
            picture = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 7000
                    connection.readTimeout = 7000
                    connection.instanceFollowRedirects = false
                    try {
                        if (connection.responseCode != 200) return@runCatching null
                        val data = connection.inputStream.use { it.bounded(2 * 1024 * 1024) }
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(data, 0, data.size, opts)
                        if (opts.outHeight !in 1..8192 || opts.outWidth !in 1..8192) return@runCatching null
                        opts.inJustDecodeBounds = false
                        opts.inSampleSize = 1
                        while (maxOf(opts.outWidth, opts.outHeight) / opts.inSampleSize > 1280) opts.inSampleSize *= 2
                        BitmapFactory.decodeByteArray(data, 0, data.size, opts)?.asImageBitmap()?.also {
                            photoCache.put(url, it)
                        }
                    } finally { connection.disconnect() }
                }.getOrNull()
            }
        }
        loading = false
    }
    Surface(modifier, color = Color(0xFF071311), shape = MaterialTheme.shapes.medium) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val image = picture
            if (image == null) Text(if (loading) "Cargando imagen…" else "Imagen no disponible",
                color = pale, style = MaterialTheme.typography.labelSmall)
            else Image(image, "Captura de $title", Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun QueueRow(name: String, state: String, selected: Boolean, progress: Float?, check: () -> Unit) {
    Surface(color = disk, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(selected, { check() })
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Medium)
                Text(state, color = pale, style = MaterialTheme.typography.bodySmall)
                if (progress != null) {
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
