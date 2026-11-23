package com.josxtm.fm1ramloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile

internal class SafCardStore(private val context: Context, uri: Uri) : CardStore {
    private val treeUri = uri
    private var flatFiles: MutableMap<String, DocumentFile>? = null
    private val provider = context.contentResolver
    private val root = requireNotNull(DocumentFile.fromTreeUri(context, uri)) { "Tarjeta inaccesible" }

    init {
        require(root.isDirectory && root.name?.equals("FM1", ignoreCase = true) == true &&
            root.canRead() && root.canWrite()) { "Selecciona una carpeta FM1 accesible de la microSD" }
    }

    private fun parent(path: String, create: Boolean): Pair<DocumentFile, String>? {
        val parts = path.split('/')
        require(!create || parts.first() != "FW" || parts.size == 2) { "FW sólo admite archivos directos" }
        require(parts.all { it.matches(Regex("[A-Z0-9]{1,8}(\\.[A-Z0-9]{1,3})?")) })
        var dir = root
        for (part in parts.dropLast(1)) {
            dir = dir.findFile(part) ?: if (create) {
                requireNotNull(dir.createDirectory(part)) { "No se pudo crear $part" }
            } else return null
            require(dir.isDirectory && dir.name == part) { "Ruta en conflicto: $part" }
        }
        return dir to parts.last()
    }

    // Enumerate a large flat directory once per operation, avoiding one listing per asset.
    private fun fileAt(dir: DocumentFile, leaf: String, flat: Boolean): DocumentFile? {
        if (!flat) return dir.findFile(leaf)
        val files = flatFiles ?: run {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getDocumentId(dir.uri))
            val indexed = mutableMapOf<String, DocumentFile>()
            requireNotNull(provider.query(children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null)).use { cursor ->
                while (cursor.moveToNext()) {
                    val key = cursor.getString(1).uppercase()
                    val document = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
                    require(indexed.put(key, requireNotNull(DocumentFile.fromSingleUri(context, document))) == null) {
                        "Nombres ambiguos en FW"
                    }
                }
            }
            indexed.also { flatFiles = it }
        }
        return files[leaf]
    }

    override fun read(path: String, max: Int): ByteArray? {
        require(root.canRead()) { "Tarjeta retirada o permiso perdido" }
        val (dir, leaf) = parent(path, false) ?: return null
        val file = fileAt(dir, leaf, path.count { it == '/' } == 1 && path.startsWith("FW/")) ?: return null
        require(file.isFile && file.name == leaf) { "Archivo ausente o nombre modificado: $path" }
        return requireNotNull(provider.openInputStream(file.uri)).use { it.bounded(max) }
    }

    override fun write(path: String, data: ByteArray, replace: Boolean) {
        val (dir, leaf) = requireNotNull(parent(path, true))
        val flat = path.startsWith("FW/")
        val old = fileAt(dir, leaf, flat)
        require(old == null || replace && old.isFile) { "El archivo ya existe: $path" }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(leaf.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        val file = old ?: requireNotNull(dir.createFile(mime, leaf)) {
            "No se pudo crear $path; comprueba espacio, tarjeta y permiso"
        }
        try {
            require(file.name == leaf) { "Nombre modificado por Android: $leaf" }
            requireNotNull(provider.openOutputStream(file.uri, "wt")).use { stream ->
                stream.write(data)
                stream.flush()
            }
            if (flat) flatFiles?.set(leaf, file)
        } catch (error: Exception) {
            if (old == null) runCatching { file.delete() }
            throw error
        }
    }

    override fun deleteStagingLegacy() {
        val file = root.findFile("INDEX.NEW") ?: error("Falta INDEX.NEW")
        require(file.isFile && file.delete()) { "Índice publicado; no se pudo retirar INDEX.NEW" }
    }

    companion object {
        fun persist(context: Context, uri: Uri) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
    }
}
