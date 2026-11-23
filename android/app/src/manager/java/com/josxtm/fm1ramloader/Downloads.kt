package com.josxtm.fm1ramloader

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal fun InputStream.bounded(max: Int): ByteArray {
    val out = ByteArrayOutputStream()
    transferBounded(this, max.toLong()) { data, n -> out.write(data, 0, n) }
    return out.toByteArray()
}

private fun transferBounded(input: InputStream, maximum: Long, write: (ByteArray, Int) -> Unit): Long {
    var total = 0L
    val buffer = ByteArray(32 * 1024)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) return total
        require(n > 0 && n <= maximum - total) { "La ubicación publicó más bytes de los previstos" }
        write(buffer, n)
        total += n
    }
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 255) }

internal interface HttpBody : Closeable {
    val status: Int
    val etag: String?
    val range: String?
    fun input(): InputStream
}

internal fun interface DownloadHttp {
    fun get(url: String, offset: Long, etag: String?): HttpBody
}

internal object GithubDownloadHttp : DownloadHttp {
    private val hosts = setOf("github.com", "release-assets.githubusercontent.com",
        "objects.githubusercontent.com", "github-releases.githubusercontent.com")

    override fun get(url: String, offset: Long, etag: String?): HttpBody {
        require(releaseUrl(url)) { "Se esperaba un GitHub Release público" }
        var current = url
        repeat(6) {
            require(https(current) && URI(current).host in hosts) { "Redirección fuera de GitHub" }
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12_000
            connection.readTimeout = 18_000
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("User-Agent", "FM1-Manager-To-USB")
            if (offset > 0 && etag != null) {
                connection.setRequestProperty("Range", "bytes=$offset-")
                connection.setRequestProperty("If-Range", etag)
            }
            val code = try {
                connection.responseCode
            } catch (error: IOException) {
                connection.disconnect()
                throw error
            }
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                current = URI(current).resolve(requireNotNull(location)).toString()
            } else {
                return object : HttpBody {
                    override val status = code
                    override val etag: String? = connection.getHeaderField("ETag")
                    override val range: String? = connection.getHeaderField("Content-Range")
                    override fun input() = connection.inputStream
                    override fun close() = connection.disconnect()
                }
            }
        }
        error("Demasiadas redirecciones")
    }
}

internal class DownloadHttpFailure(val code: Int) : IOException(when (code) {
    404, 410 -> "Enlace retirado (HTTP $code); actualiza el manifiesto"
    451 -> "Bloqueo de disponibilidad (HTTP 451)"
    401, 403 -> "Acceso denegado (HTTP $code); vuelve a comprobar el enlace"
    429 -> "Límite temporal del servidor (HTTP 429)"
    else -> "Error HTTP $code"
}) {
    val permanent get() = code in setOf(404, 410, 451)
}

internal class DownloadPaused : IOException("En pausa")

internal data class CachedPackage(val file: File, val size: Long, val digest: String, val info: PackageInfo)

internal class DownloadCache(private val root: File, private val http: DownloadHttp = GithubDownloadHttp) {
    private val locks = ConcurrentHashMap<String, Any>()
    init { require(root.isDirectory || root.mkdirs()) { "Caché inaccesible" } }

    fun complete(): Set<String> = root.list().orEmpty().filter { it.endsWith(".blob") }.map { it.removeSuffix(".blob") }
        .filter(HASH64::matches).toSet()

    fun obtain(item: PackageLink, paused: () -> Boolean = { false }, update: (Long) -> Unit = {}): CachedPackage {
        require(HASH64.matches(item.sha256) && item.size in 1..MAX_ASSET.toLong())
        synchronized(locks.getOrPut(item.sha256) { Any() }) {
            val dest = File(root, "${item.sha256}.blob")
            if (dest.exists()) return verify(dest, item.file, item.size, item.sha256)
            val part = File(root, "${item.sha256}.part")
            val note = File(root, "${item.sha256}.etag")
            var tag = if (note.length() in 2..512) note.readText().takeIf(::strong) else null
            var offset = part.length()
            if (tag == null || offset >= item.size) {
                part.delete()
                note.delete()
                offset = 0
                tag = null
            }
            if (paused()) throw DownloadPaused()
            http.get(item.url, offset, tag).use { response ->
                when (response.status) {
                    206 -> {
                        val expected = "bytes $offset-${item.size - 1}/${item.size}"
                        if (offset <= 0 || response.etag != tag || response.range != expected) {
                            part.delete()
                            note.delete()
                            error("Rango o versión remota incoherente; reintenta desde el principio")
                        }
                    }
                    200 -> {
                        offset = 0
                        note.delete()
                        part.delete()
                    }
                    else -> throw DownloadHttpFailure(response.status)
                }
                val freshTag = response.etag?.takeIf(::strong)
                if (freshTag != null) note.writeText(freshTag)
                var progress = offset
                response.input().use { input ->
                    FileOutputStream(part, offset > 0).use { output ->
                        transferBounded(input, item.size - offset) { buffer, n ->
                            if (paused() || Thread.currentThread().isInterrupted) throw DownloadPaused()
                            output.write(buffer, 0, n)
                            progress += n
                            update(progress)
                        }
                        output.fd.sync()
                    }
                }
                if (progress != item.size) throw IOException("Transferencia incompleta")
            }
            val checked = try {
                verify(part, item.file, item.size, item.sha256)
            } catch (error: Exception) {
                part.delete()
                note.delete()
                throw error
            }
            require(part.renameTo(dest)) { "No se pudo publicar el archivo temporal" }
            note.delete()
            return checked.copy(file = dest)
        }
    }

    fun importOriginal(name: String, input: InputStream): CachedPackage {
        require(validBaseName(name) && name.substringAfterLast('.').lowercase() in setOf("fwsc", "bin", "ufw")) {
            "Selecciona un original .fwsc, .ufw o .bin"
        }
        val temporary = File.createTempFile("import-", ".part", root)
        try {
            input.use { source ->
                FileOutputStream(temporary).use { dest ->
                    transferBounded(source, MAX_ASSET.toLong()) { buffer, n -> dest.write(buffer, 0, n) }
                    dest.fd.sync()
                }
            }
            val data = temporary.inputStream().use { it.bounded(MAX_ASSET) }
            val hash = sha256(data)
            val info = PackageInspector.inspect(data, name)
            synchronized(locks.getOrPut(hash) { Any() }) {
                val target = File(root, "$hash.blob")
                if (target.exists()) return verify(target, name, data.size.toLong(), hash)
                require(temporary.renameTo(target)) { "No se pudo conservar el original" }
                return CachedPackage(target, data.size.toLong(), hash, info)
            }
        } finally {
            temporary.delete()
        }
    }

    fun existing(item: PackageLink) = verify(File(root, "${item.sha256}.blob"), item.file, item.size, item.sha256)

    private fun verify(file: File, name: String, size: Long, digest: String): CachedPackage {
        val bytes = file.inputStream().use { it.bounded(MAX_ASSET) }
        require(bytes.size.toLong() == size && sha256(bytes) == digest) { "Tamaño o SHA-256 incorrectos" }
        return CachedPackage(file, size, digest, PackageInspector.inspect(bytes, name))
    }

    private fun strong(value: String): Boolean =
        value.length in 2..512 && value.first() == '"' && value.last() == '"' && value.none { it.code < 32 }
}
