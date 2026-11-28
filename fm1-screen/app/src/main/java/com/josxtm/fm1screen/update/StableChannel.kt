// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.update

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.time.Instant
import javax.net.ssl.HttpsURLConnection

internal data class StableRelease(
    val tag: String, val version: String, val code: Int, val build: String, val minSdk: Int,
    val apkUrl: String, val sha256: String, val certificate: String, val size: Long,
    val publishedAt: Instant, val notes: List<String>,
)

internal object StableChannel {
    const val repository = "josxtm/FM1-USB-MULTIBOOT-"
    const val appId = "com.josxtm.fm1screen"
    const val manifest = "FM1-SCREEN-update.json"
    const val maxApkSize = 128L * 1024 * 1024
    private const val downloads = "https://github.com/$repository/releases/download"
    private val tagPattern = Regex("^fm1-screen-v([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)-comp\\.([0-9]{3,10})$")
    private val shaPattern = Regex("^[0-9a-f]{64}$")
    private val assetHosts = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")

    fun buildOf(tag: String): Int? {
        val build = tagPattern.matchEntire(tag)?.groupValues?.get(2) ?: return null
        val code = build.toIntOrNull() ?: return null
        return code.takeIf { it > 0 && build == it.toString().padStart(3, '0') }
    }

    fun candidates(page: JSONArray, installed: Int): List<String> =
        (0 until page.length()).map { page.getJSONObject(it) }.mapNotNull { release ->
            val tag = release.getString("tag_name")
            val build = buildOf(tag) ?: return@mapNotNull null
            if (build <= installed || release.getBoolean("draft") || release.getBoolean("prerelease")) return@mapNotNull null
            val assets = release.getJSONArray("assets")
            tag.takeIf { (0 until assets.length()).any { index ->
                val asset = assets.getJSONObject(index)
                asset.optString("state") == "uploaded" &&
                    asset.optString("browser_download_url") == "$downloads/$tag/$manifest"
            } }
        }

    fun parse(tag: String, json: JSONObject): StableRelease {
        val match = requireNotNull(tagPattern.matchEntire(tag))
        val (version, build) = match.destructured
        val code = requireNotNull(buildOf(tag))
        val url = "$downloads/$tag/FM1-SCREEN-v$version-comp.$build.apk"
        val minSdk = json.get("minSdk") as? Int ?: error("minSdk inválido")
        val size = when (val value = json.get("sizeBytes")) {
            is Int -> value.toLong()
            is Long -> value
            else -> error("Tamaño inválido")
        }
        require(json.get("schemaVersion") == 1 && json.get("applicationId") == appId &&
            json.get("channel") == "stable" && json.get("versionName") == version &&
            json.get("versionCode") == code && json.get("build") == build && json.get("apkUrl") == url)
        require(minSdk >= 29 && size in 1..maxApkSize)
        val sha = json.getString("sha256")
        val certificate = json.getString("signingCertificateSha256")
        require(shaPattern.matches(sha) && shaPattern.matches(certificate))
        val notes = json.getJSONArray("releaseNotes")
        require(notes.length() in 1..30)
        val lines = (0 until notes.length()).map { notes.getString(it).also { line ->
            require(line.isNotBlank() && line.length <= 500)
        } }
        return StableRelease(tag, version, code, build, minSdk, url, sha, certificate, size,
            Instant.parse(json.getString("publishedAt")), lines)
    }

    fun find(installed: Int, sdk: Int, certificate: String, read: (String, Boolean) -> String = ::readJson): StableRelease? {
        val tags = mutableSetOf<String>()
        for (page in 1..20) {
            val releases = JSONArray(read("https://api.github.com/repos/$repository/releases?per_page=100&page=$page", true))
            tags += candidates(releases, installed)
            if (releases.length() < 100) break
            if (page == 20) throw IOException("El listado de publicaciones excede el límite")
        }
        val ordered = tags.sortedByDescending { buildOf(it) }
        var invalid = false
        for (tag in ordered) {
            val body = read("$downloads/$tag/$manifest", false)
            val candidate = try {
                parse(tag, JSONObject(body))
            } catch (_: Exception) {
                invalid = true
                continue
            }
            if (candidate.minSdk <= sdk && candidate.certificate == certificate) return candidate
        }
        if (invalid) throw IOException("Manifiesto de actualización inválido")
        return null
    }

    fun connect(url: String, api: Boolean = false): HttpsURLConnection {
        var uri = URI(url)
        repeat(5) {
            require(uri.scheme == "https" && uri.userInfo == null && uri.fragment == null &&
                uri.port == -1 && if (api) uri.host == "api.github.com" else uri.host in assetHosts)
            val connection = uri.toURL().openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            if (api) connection.setRequestProperty("Accept", "application/vnd.github+json")
            var keep = false
            val location = try {
                when (val code = connection.responseCode) {
                    200 -> {
                        keep = true
                        return connection
                    }
                    301, 302, 303, 307, 308 -> connection.getHeaderField("Location")
                    else -> throw IOException("GitHub respondió HTTP $code")
                }
            } finally {
                // Only a successful response transfers ownership to the caller.
                if (!keep) connection.disconnect()
            }
            uri = uri.resolve(location ?: throw IOException("Redirección sin destino"))
        }
        throw IOException("Demasiadas redirecciones")
    }

    private fun readJson(url: String, api: Boolean): String {
        val connection = connect(url, api)
        try {
            return connection.inputStream.use {
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = it.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 4 * 1024 * 1024) throw IOException("Respuesta demasiado grande")
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }
}
