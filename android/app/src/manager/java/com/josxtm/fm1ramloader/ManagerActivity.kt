package com.josxtm.fm1ramloader

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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
    private val chooseManifest = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::loadManifest)
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
    "DUPLICATE" to "Paquete duplicado", "NO_DIRECT" to "Sin descarga directa"
)
private val genreLabels = mapOf("synth" to "Síntesis", "groove" to "Groovebox", "drums" to "Batería / sampler",
    "tracker" to "Tracker", "chords" to "Acordes", "other" to "Experimental", "dev" to "MIDI / desarrollo")

@Composable
private fun ManagerScreen(model: ManagerModel, choose: () -> Unit, import: () -> Unit,
                          refresh: () -> Unit, open: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf("Catálogo") }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var available by rememberSaveable { mutableStateOf(false) }
    var inspected by rememberSaveable { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var copySelection by remember { mutableStateOf(setOf<String>()) }
    val entries = model.discovery?.entries.orEmpty()
    val staged = model.cached
    val library = model.card?.records.orEmpty()
    val onCard = remember(library) { library.map { it.sha }.toSet() }
    val filtered = remember(entries, query, category, available) {
        val term = query.trim()
        entries.filter { row ->
            (term.isEmpty() || listOf(row.name, row.author, row.repo, row.description).any { it.contains(term, true) }) &&
                (category.isEmpty() || category in row.genres) && (!available || row.packages.isNotEmpty())
        }
    }
    val selected = remember(entries, selection) { entries.filter { it.id in selection } }
    val selectedRow = filtered.find { it.id == inspected } ?: filtered.firstOrNull()

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FM1 Manager To USB", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(model.cardStatus, color = mint, style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(onClick = choose, enabled = !model.busy) { Text("Elegir carpeta FM1") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Catálogo", "Descargas", "MicroSD").forEach { name ->
                FilterChip(selected = tab == name, onClick = { tab = name }, label = { Text(name) })
            }
            TextButton(onClick = import, enabled = !model.busy) { Text("Importar originales") }
            TextButton(onClick = refresh, enabled = !model.busy) { Text("Cargar manifiesto") }
        }
        Surface(color = disk, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Text(model.status, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                color = pale, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }

        when (tab) {
            "Catálogo" -> {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Buscar por firmware, autor o función") })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = available, onClick = { available = !available }, label = { Text("Descarga directa") })
                    for ((tag, name) in genreLabels) {
                        FilterChip(selected = category == tag, onClick = { category = if (category == tag) "" else tag },
                            label = { Text(name) })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { model.enqueue(selected); tab = "Descargas" }, enabled = selected.any { it.packages.isNotEmpty() }) {
                        Text("Descargar selección")
                    }
                    OutlinedButton(onClick = { model.enqueue(filtered); tab = "Descargas" }, enabled = filtered.any { it.packages.isNotEmpty() }) {
                        Text("Descargar nuevos del filtro")
                    }
                    OutlinedButton(onClick = { selection = entries.filter { it.packages.isNotEmpty() }.map { it.id }.toSet() }) {
                        Text("Todos los descargables")
                    }
                    TextButton(onClick = { selection = emptySet() }) { Text("Limpiar selección") }
                    Text("${filtered.size} fichas · ${selection.size} seleccionadas", color = pale)
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    if (maxWidth >= 750.dp) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            CatalogList(filtered, selection, selectedRow?.id, staged, onCard, Modifier.weight(1f), {
                                inspected = it.id
                            }, { selection = toggle(selection, it.id) })
                            DetailPanel(selectedRow, Modifier.weight(0.9f), open)
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            CatalogList(filtered, selection, selectedRow?.id, staged, onCard, Modifier.weight(1.2f), {
                                inspected = it.id
                            }, { selection = toggle(selection, it.id) })
                            DetailPanel(selectedRow, Modifier.weight(1f), open)
                        }
                    }
                }
                TextButton(onClick = { open("https://thefirmwarehub.com/") }) {
                    Text("Firmware list: The Firmware Hub · CC BY 4.0 · catálogo normalizado")
                }
            }

            "Descargas" -> {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = model::changePause) { Text(if (model.paused) "Reanudar" else "Pausar") }
                    OutlinedButton(onClick = model::retry) { Text("Reintentar fallos") }
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
                            if (job.state == "Descargando") job.progress.toFloat() / job.link.size else null) {
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

            else -> {
                Text("Android SAF no confirma el tipo de sistema de archivos. Comprueba FAT32 en la tarjeta; la app no la formatea.",
                    color = pale, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = model::reloadCard, enabled = !model.busy) { Text("Recargar índice") }
                    OutlinedButton(onClick = { model.exportV1(library.filter { it.sha in copySelection }) },
                        enabled = !model.busy && library.any { it.sha in copySelection }) { Text("Exportar selección FM1CAT v1") }
                    TextButton(onClick = model::recoverV1, enabled = !model.busy) { Text("Recuperar índice v1") }
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

private fun toggle(keys: Set<String>, key: String) = if (key in keys) keys - key else keys + key

@Composable
private fun CatalogList(rows: List<Listing>, selected: Set<String>, current: String?, cached: Set<String>,
                        onCard: Set<String>, modifier: Modifier, open: (Listing) -> Unit, check: (Listing) -> Unit) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items(rows, key = Listing::id) { row ->
            val link = row.packages.firstOrNull()
            val state = when {
                row.packages.any { it.sha256 in onCard } -> "En índice microSD"
                row.packages.any { it.sha256 in cached } -> "En caché"
                else -> statusLabels[row.status].orEmpty()
            }
            Surface(color = if (current == row.id) Color(0xFF214036) else disk, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().clickable { open(row) }.padding(end = 12.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(row.id in selected, { check(row) }, enabled = link != null)
                    Column(Modifier.weight(1f)) {
                        Text(row.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(row.author, row.version).filter(String::isNotBlank).joinToString(" · "),
                            color = pale, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    Text(state, color = if (link != null) mint else pale,
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 6.dp), maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun DetailPanel(row: Listing?, modifier: Modifier, open: (String) -> Unit) {
    Surface(modifier.fillMaxSize(), color = disk, shape = MaterialTheme.shapes.large) {
        if (row == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay fichas para este filtro") }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(row.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("${row.author} · ${row.license}", color = mint, style = MaterialTheme.typography.labelLarge)
                row.image?.let { CardPhoto(it, row.name) }
                if (row.description.isNotBlank()) Text(row.description, style = MaterialTheme.typography.bodyMedium)
                val genres = row.genres.map { genreLabels[it] ?: it }
                if (genres.isNotEmpty()) Text(genres.joinToString(" · "), color = pale, style = MaterialTheme.typography.labelMedium)
                row.variantOf?.let { Text("Variante de $it", color = pale, style = MaterialTheme.typography.labelSmall) }
                HorizontalDivider()
                Text(statusLabels[row.status].orEmpty(), color = mint)
                if (row.packages.isEmpty()) Text("Sin descarga directa. Puedes importar un original obtenido por tu cuenta.", color = pale)
                Text("Perfil publicado: ${row.compatibility} · arranque sin verificar", style = MaterialTheme.typography.bodySmall)
                if (row.identity.isNotBlank()) Text(row.identity, style = MaterialTheme.typography.labelSmall)
                for (link in row.packages) {
                    Text("${link.version} · ${link.format} · ${link.size} B", fontWeight = FontWeight.Medium)
                    Text(link.sha256, color = pale, style = MaterialTheme.typography.labelSmall)
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

@Composable
private fun CardPhoto(url: String, title: String) {
    var picture by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        picture = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 7000
                c.readTimeout = 7000
                c.instanceFollowRedirects = false
                try {
                    if (c.responseCode != 200) return@runCatching null
                    val data = c.inputStream.use { it.bounded(360 * 1024) }
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(data, 0, data.size, opts)
                    if (opts.outHeight !in 1..1024 || opts.outWidth !in 1..1024) return@runCatching null
                    BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
                } finally { c.disconnect() }
            }.getOrNull()
        }
    }
    picture?.let { Image(it, "Captura de $title", Modifier.size(144.dp), contentScale = ContentScale.Fit) }
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
