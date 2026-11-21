package com.josxtm.fm1ramloader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

internal data class LibraryItem(val entry: FirmwareEntry, val assessment: FirmwareAssessment)

/**
 * Android Storage Access Framework only: the tablet manages a user-selected FM1 folder
 * on the microSD. No permission to access raw block devices or to program synthesizer Flash.
 */
class SdLibraryActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var selectedFirmware: Uri? = null
    private var selectedFolder: Uri? = null
    private var firmwareName by mutableStateOf("Sin archivo")
    private var folderName by mutableStateOf("Sin carpeta")
    private var idText by mutableStateOf("")
    private var labelText by mutableStateOf("")
    private var versionText by mutableStateOf("1.0")
    private var status by mutableStateOf("Importa un archivo .bin o .fwsc y selecciona la carpeta FM1 de tu microSD.")
    private var busy by mutableStateOf(false)
    private val coverFlow = CoverFlowModel()
    private var libraryItems by mutableStateOf(listOf<LibraryItem>())
    private var libraryState by mutableStateOf(CoverFlowState.NO_CARD)
    private var librarySelection by mutableStateOf(0)
    private var selectedAssessment by mutableStateOf<FirmwareAssessment?>(null)

    private val pickFirmware = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedFirmware = uri
            val name = displayName(uri)
            firmwareName = name
            idText = FirmwareCatalog.cleanId(name.substringBeforeLast('.', name))
            labelText = name.substringBeforeLast('.', name)
                .replace(Regex("[^A-Za-z0-9 _.-]"), "_").take(24).ifBlank { "Firmware" }
            status = "Seleccionado: " + name + " · no se ha copiado todavía"
        }
    }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val tree = DocumentFile.fromTreeUri(this, uri)
            if (tree == null || tree.name?.uppercase() != "FM1" || !tree.isDirectory) {
                status = "Selecciona la carpeta FM1 creada previamente en tu microSD. No selecciones la raíz."
                return@registerForActivityResult
            }
            selectedFolder = uri
            folderName = tree.name ?: "FM1"
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            status = "Destino FM1 seleccionado. Leyendo FM1/INDEX.CSV…"
            loadCatalog(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF65E8CD), background = Color(0xFF0A1117),
                surface = Color(0xFF18232B))) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("FM1 MANAGER · MICROSD", style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFF65E8CD))
                        Text("Solo biblioteca; no instala ni ejecuta firmware en el FM1.")
                        Text(status)
                        OutlinedButton(
                            onClick = { pickFirmware.launch(arrayOf("*/*")) },
                            enabled = !busy, modifier = Modifier.fillMaxWidth()
                        ) { Text("1. Seleccionar firmware (.bin / .fwsc)") }
                        Text("Archivo: " + firmwareName)
                        OutlinedTextField(
                            value = idText, onValueChange = { idText = it },
                            label = { Text("ID corto (máx. 8 ASCII)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy
                        )
                        OutlinedTextField(
                            value = labelText, onValueChange = { labelText = it },
                            label = { Text("Nombre para Cover Flow") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy
                        )
                        OutlinedTextField(
                            value = versionText, onValueChange = { versionText = it },
                            label = { Text("Versión") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy
                        )
                        OutlinedButton(
                            onClick = { pickFolder.launch(null) },
                            enabled = !busy, modifier = Modifier.fillMaxWidth()
                        ) { Text("2. Seleccionar carpeta FM1 de la microSD") }
                        Text("Destino: " + folderName)
                        OutlinedButton(onClick = { selectedFolder?.let(::loadCatalog) },
                            enabled = !busy && selectedFolder != null, modifier = Modifier.fillMaxWidth()) {
                            Text("Abrir / recargar FM1/INDEX.CSV")
                        }
                        Text("Cover Flow Android · estado: $libraryState")
                        if (libraryItems.isNotEmpty()) {
                            val item = libraryItems[librarySelection]
                            AndroidCoverFlow(
                                items = libraryItems, selected = librarySelection,
                                onPrevious = ::previousLibrary, onNext = ::nextLibrary,
                                onInspect = ::inspectLibrary
                            )
                            Text("Formato: ${item.assessment.format} · ${item.entry.size} bytes")
                            Text("Integridad: " + if (item.assessment.integrityVerified) "VERIFICADA" else "NO VERIFICADA")
                            Text("Compatibilidad: ${item.assessment.compatibility} · Ejecución: ${item.assessment.execution}")
                            selectedAssessment?.warnings?.take(4)?.forEach { Text("Aviso: $it") }
                        }
                        Button(
                            onClick = ::exportFirmware,
                            enabled = !busy && selectedFirmware != null && selectedFolder != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (busy) "Exportando..." else "3. Exportar y verificar en microSD") }
                        Text(
                            "Se copiarán los bytes sin modificar a FM1/FW/ y se actualizará INDEX.CSV. " +
                                "Las imágenes se registran como UNVERIFIED: aparecer en Cover Flow NO significa que puedan arrancar.",
                            color = Color(0xFFB0C5C9)
                        )
                        OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Volver al diagnóstico USB")
                        }
                    }
                }
            }
        }
    }

    private fun loadCatalog(treeUri: Uri) {
        if (busy) return
        busy = true
        worker.execute {
            val result = runCatching {
                val root = requireNotNull(DocumentFile.fromTreeUri(this, treeUri)) { "FM1 inaccesible" }
                val index = requireNotNull(root.findFile(FirmwareCatalog.INDEX)) { "Falta FM1/INDEX.CSV" }
                val text = requireNotNull(contentResolver.openInputStream(index.uri)).use {
                    readLimited(it, FirmwareCatalog.MAX_BYTES).toString(Charsets.UTF_8)
                }
                val entries = FirmwareCatalog.decode(text)
                val fw = root.findFile("FW")
                entries.map { entry ->
                    val file = fw?.findFile(entry.fileName)
                    val assessment = if (file == null) {
                        entry.assess().let { base ->
                            base.copy(warnings = base.warnings + "Archivo ausente en FM1/FW")
                        }
                    } else FirmwareAssetVerifier.verify(entry,
                        requireNotNull(contentResolver.openInputStream(file.uri)))
                    LibraryItem(entry, assessment)
                }
            }
            runOnUiThread {
                busy = false
                result.fold(
                    onSuccess = { items ->
                        libraryItems = items
                        coverFlow.attachCatalog(items.map { it.entry }, items.map { it.assessment })
                        librarySelection = 0
                        selectedAssessment = null
                        libraryState = coverFlow.state
                        status = "Catálogo leído: ${items.size} firmware(s). PLAY solo inspecciona."
                    },
                    onFailure = {
                        libraryItems = emptyList()
                        libraryState = CoverFlowState.ERROR
                        coverFlow.fail(it.message ?: "Error de lectura")
                        status = "Error leyendo FM1/INDEX.CSV: ${it.message ?: it.javaClass.simpleName}"
                    }
                )
            }
        }
    }

    private fun nextLibrary() {
        coverFlow.next()
        librarySelection = coverFlow.selected
        libraryState = coverFlow.state
        selectedAssessment = null
    }

    private fun previousLibrary() {
        coverFlow.previous()
        librarySelection = coverFlow.selected
        libraryState = coverFlow.state
        selectedAssessment = null
    }

    private fun inspectLibrary() {
        selectedAssessment = coverFlow.inspect()
        libraryState = coverFlow.state
        status = "Inspección de ${libraryItems.getOrNull(librarySelection)?.entry?.id ?: "firmware"}; no se ejecuta."
    }

    private fun displayName(uri: Uri): String {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
    }

    private fun exportFirmware() {
        val source = selectedFirmware ?: return
        val folder = selectedFolder ?: return
        if (busy) return
        val id = idText.trim().uppercase()
        val label = labelText.trim()
        val version = versionText.trim()
        val extension = when (firmwareName.substringAfterLast('.', "").lowercase()) {
            "bin" -> ".BIN"
            "fwsc" -> ".FWS"
            else -> {
                status = "Formato no admitido aún. Solo .bin o .fwsc; no renombrar ZIP a BIN."
                return
            }
        }
        busy = true
        status = "Exportando: primero copia, después verificación y actualización del catálogo"
        worker.execute {
            val result = runCatching { exportToFolder(source, folder, id, label, version, extension) }
            runOnUiThread {
                busy = false
                status = result.fold(
                    onSuccess = {
                        val message = "Exportación verificada: ${it.id} · ${it.size} bytes · " +
                            "SHA-256 ${it.sha256.take(16)}... · COMPATIBILIDAD NO VERIFICADA"
                        // INDEX.CSV and the SAF-backed assessments are reloaded after commit.
                        loadCatalog(folder)
                        message + " · Cover Flow actualizado"
                    },
                    onFailure = { "Exportación detenida: " + (it.message ?: it.javaClass.simpleName) +
                        ". Revisa FW, INDEX.NEW e INDEX.BAK en FM1 antes de repetir." }
                )
            }
        }
    }

    private fun exportToFolder(
        source: Uri, treeUri: Uri, id: String, label: String,
        version: String, extension: String
    ): FirmwareEntry {
        val root = requireNotNull(DocumentFile.fromTreeUri(this, treeUri)) { "Destino inaccesible" }
        require(root.isDirectory && root.name?.uppercase() == "FM1") { "Destino no es la carpeta FM1" }
        val firmwareDir = root.findFile("FW") ?: requireNotNull(root.createDirectory("FW"))
        require(firmwareDir.isDirectory) { "FW no es un directorio" }
        val name = id + extension
        require(firmwareDir.findFile(name) == null) { "Firmware ya existe, no se sobrescribe" }
        val index = root.findFile(FirmwareCatalog.INDEX)
        val previousBackup = root.findFile("INDEX.BAK")
        require(root.findFile("INDEX.NEW") == null) { "Hay un INDEX.NEW pendiente de revisión" }
        require(index != null || previousBackup == null) {
            "Existe INDEX.BAK sin INDEX.CSV: recuperar manualmente antes de exportar"
        }
        if (previousBackup != null) {
            require(previousBackup.isFile) { "INDEX.BAK no es un archivo" }
            val oldCatalog = requireNotNull(contentResolver.openInputStream(previousBackup.uri)).use {
                readLimited(it, FirmwareCatalog.MAX_BYTES)
            }
            // A valid old snapshot can be rotated after the replacement has been staged.
            FirmwareCatalog.decode(oldCatalog.toString(Charsets.US_ASCII))
        }
        val prior = if (index != null) {
            require(index.isFile) { "INDEX.CSV no es un archivo" }
            val content = requireNotNull(contentResolver.openInputStream(index.uri)).use {
                readLimited(it, FirmwareCatalog.MAX_BYTES)
            }
            FirmwareCatalog.decode(content.toString(Charsets.UTF_8))
        } else emptyList()
        require(prior.none { it.id == id }) { "ID ya registrado, elige otro" }
        require(prior.size < FirmwareCatalog.MAX_ENTRIES) { "Catálogo lleno" }

        // Validate name/version/ID before creating even one file.
        val placeholder = FirmwareEntry(id, label, version, name, 1L, "0".repeat(64))
        FirmwareCatalog.encode(prior + placeholder)

        val firmwareFile = requireNotNull(firmwareDir.createFile("application/octet-stream", name)) {
            "No se pudo crear el archivo de destino"
        }
        var catalogCommitted = false
        try {
            require(firmwareFile.name?.equals(name, ignoreCase = true) == true) {
                "El proveedor cambió el nombre del fichero"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            requireNotNull(contentResolver.openInputStream(source)).use { input ->
                requireNotNull(contentResolver.openOutputStream(firmwareFile.uri, "wt")).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        require(n > 0) { "Lectura sin progreso" }
                        size += n
                        require(size <= FirmwareCatalog.MAX_IMAGE_BYTES) { "Imagen demasiado grande" }
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                    output.flush()
                }
            }
            require(size > 0) { "Archivo vacío" }
            val hash = digest.digest().hex()
            val verified = MessageDigest.getInstance("SHA-256")
            var readBack = 0L
            requireNotNull(contentResolver.openInputStream(firmwareFile.uri)).use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(n > 0) { "Lectura sin progreso" }
                    readBack += n
                    require(readBack <= FirmwareCatalog.MAX_IMAGE_BYTES) { "Lectura fuera de límites" }
                    verified.update(buffer, 0, n)
                }
            }
            require(readBack == size && verified.digest().hex() == hash) {
                "La copia no coincide con el archivo original"
            }
            val entry = FirmwareEntry(id, label, version, name, size, hash)
            val text = FirmwareCatalog.encode(prior + entry)
            val temp = requireNotNull(root.createFile("application/octet-stream", "INDEX.NEW")) {
                "No se pudo preparar el catálogo"
            }
            require(temp.name?.equals("INDEX.NEW", ignoreCase = true) == true) {
                "El proveedor cambió el nombre del catálogo"
            }
            requireNotNull(contentResolver.openOutputStream(temp.uri, "wt")).use {
                it.write(text.toByteArray(Charsets.US_ASCII))
                it.flush()
            }
            val check = requireNotNull(contentResolver.openInputStream(temp.uri)).use {
                readLimited(it, FirmwareCatalog.MAX_BYTES).toString(Charsets.US_ASCII)
            }
            require(check == text && FirmwareCatalog.decode(check).last() == entry) {
                "No se pudo verificar INDEX.NEW"
            }
            if (index != null) {
                // The new INDEX.NEW is already verified. Rotate the previous backup only now,
                // with the current verified INDEX.CSV still intact if deletion fails.
                if (previousBackup != null) {
                    require(previousBackup.delete()) { "No se pudo rotar INDEX.BAK antiguo" }
                }
                require(index.renameTo("INDEX.BAK")) { "No se pudo conservar INDEX.BAK" }
            }
            if (!temp.renameTo(FirmwareCatalog.INDEX)) {
                // Best-effort rollback: preserve old data if the provider refused rename.
                root.findFile("INDEX.BAK")?.renameTo(FirmwareCatalog.INDEX)
                error("El proveedor rechazó el cambio de nombre de INDEX.NEW")
            }
            catalogCommitted = true
            return entry
        } finally {
            // Never delete an image that has been successfully catalogued.
            if (!catalogCommitted) firmwareFile.delete()
        }
    }

    private fun readLimited(input: InputStream, max: Int): ByteArray {
        val buffer = ByteArray(4096)
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(n > 0 && out.size() + n <= max) { "Catálogo demasiado grande" }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}