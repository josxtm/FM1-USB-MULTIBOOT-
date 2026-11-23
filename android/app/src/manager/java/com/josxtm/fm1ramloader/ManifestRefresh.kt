package com.josxtm.fm1ramloader

import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant

internal object ManifestRefresh {
    const val PORTAL = "https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/workflows/fm1-discovery-refresh.yml"
    private const val ROOT = "https://raw.githubusercontent.com/josxtm/FM1-USB-MULTIBOOT-/feat/fm1-manager-v1-1-updates-progress-ui/android/app/src/manager/assets/"

    fun check(active: Discovery?, now: Instant = Instant.now(), get: (String, Int) -> ByteArray = ::read): Pair<ByteArray, Discovery> {
        val expected = get(ROOT + "discovery.sha256", 128).decodeToString().trim()
        require(HASH64.matches(expected)) { "SHA-256 del catálogo inválido" }
        val bytes = get(ROOT + "discovery.json", MAX_MANIFEST)
        require(sha256(bytes) == expected) { "SHA-256 del catálogo no coincide; vuelve a comprobar más tarde" }
        val parsed = Discovery.decode(bytes)
        requireFresh(parsed, active, now)
        return bytes to parsed
    }

    fun requireFresh(parsed: Discovery, active: Discovery?, now: Instant = Instant.now()) {
        val generated = requireNotNull(parsed.generated) { "Falta la fecha de generación del manifiesto" }
        require(generated <= now.plusSeconds(600) && generated >= now.minus(Duration.ofDays(14))) {
            "El manifiesto publicado está caducado o tiene fecha futura"
        }
        require(active?.generated?.let { generated >= it } != false) { "El manifiesto es anterior al catálogo activo" }
    }

    private fun read(url: String, limit: Int): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 8000
        connection.readTimeout = 12000
        connection.setRequestProperty("Cache-Control", "no-cache")
        return try {
            val code = connection.responseCode
            if (code != 200) throw DownloadHttpFailure(code)
            connection.inputStream.use { it.bounded(limit) }
        } finally {
            connection.disconnect()
        }
    }
}
