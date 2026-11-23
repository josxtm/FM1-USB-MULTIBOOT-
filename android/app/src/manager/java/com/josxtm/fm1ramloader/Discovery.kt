package com.josxtm.fm1ramloader

import org.json.JSONObject
import java.net.URI

internal const val MAX_ASSET = 16 * 1024 * 1024
internal const val MAX_MANIFEST = 6 * 1024 * 1024
internal val HASH64 = Regex("[a-f0-9]{64}")

internal data class PackageLink(
    val version: String, val file: String, val size: Long, val sha256: String,
    val url: String, val format: String, val provenance: String, val release: String,
    val availability: String = "UNVERIFIED", val distribution: String = "",
    val publishedAt: java.time.Instant? = null, val notes: String = "", val revision: String = "",
    val prerelease: Boolean = false, val current: Boolean = true
)

internal data class Listing(
    val id: String, val name: String, val author: String, val repo: String, val source: String,
    val license: String, val description: String, val genres: List<String>, val status: String,
    val version: String, val compatibility: String, val identity: String,
    val image: String?, val demo: String?, val variantOf: String?, val packages: List<PackageLink>
)

internal enum class DownloadFilter(val label: String) {
    AVAILABLE("Descargables"), AUTHOR("Originales del autor"), COMMUNITY("Compilaciones comunitarias"),
    ALL("Mostrar todos"), UNAVAILABLE("No descargables");

    fun matches(row: Listing, blocked: Set<String>): Boolean {
        val packages = row.downloads(blocked)
        return when (this) {
            ALL -> true
            AVAILABLE -> packages.isNotEmpty()
            UNAVAILABLE -> packages.isEmpty()
            AUTHOR, COMMUNITY -> packages.any { it.provenance == name }
        }
    }
}

private val openLicenses = Regex("(?:GPL-[23]\\.0(?:-only|-or-later)?|MPL-2\\.0|MIT|BSD-[23]-Clause|Apache-2\\.0|CC0-1\\.0)")

internal fun Listing.downloads(blocked: Set<String> = emptySet()): List<PackageLink> =
    packages.filter { link ->
        link.current && status in setOf("AUTHOR", "COMMUNITY") && status == link.provenance &&
            link.availability == "AVAILABLE" && link.url !in blocked && openLicenses.matches(license) &&
            HASH64.matches(link.sha256) && link.size in 1..MAX_ASSET.toLong() && validBaseName(link.file) &&
            link.format in setOf("FWSC", "UFW", "BIN") && link.file.substringAfterLast('.').uppercase() == link.format &&
            releaseUrl(link.url) && https(link.release) && publicationMatches(this, link)
    }

private fun publicationMatches(row: Listing, link: PackageLink): Boolean {
    val publisher = when (link.distribution) {
        "AUTHOR_OPEN_RELEASE" -> row.repo.takeIf { link.provenance == "AUTHOR" }
        "COMMUNITY_OPEN_BUILD" -> "2bbb/fm1-firmware-collection".takeIf { link.provenance == "COMMUNITY" }
        else -> null
    } ?: return false
    val url = URI(link.url)
    val segments = url.path.trim('/').split('/')
    if (segments.size != 6 || !segments.take(2).joinToString("/").equals(publisher, true) ||
        segments[5] != link.file) return false
    val published = URI(link.release)
    val release = published.path.trim('/').split('/')
    return published.host == "github.com" && published.rawQuery == null && published.rawFragment == null &&
        release.size == 5 && release.take(2).joinToString("/").equals(publisher, true) &&
        release.subList(2, 4) == listOf("releases", "tag") && release[4] == segments[4]
}

internal data class Discovery(val updated: String, val entries: List<Listing>,
                              val generated: java.time.Instant? = null) {
    companion object {
        private val states = setOf("AUTHOR", "COMMUNITY", "SOURCE", "RESTRICTED", "INCOMPATIBLE",
            "UNVERIFIED", "TOOL", "FORK", "DUPLICATE", "NO_DIRECT", "LICENSE_UNKNOWN",
            "REMOVED", "BLOCKED", "ACCESS", "TEMPORARY")
        private val idPattern = Regex("[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+){1,2}")
        private val formats = setOf("FWSC", "UFW", "BIN")
        fun decode(bytes: ByteArray): Discovery {
            require(bytes.size <= MAX_MANIFEST) { "Manifiesto demasiado grande" }
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            require(root.getInt("schema") == 2) { "Usa un manifiesto alpha02 con verificación de disponibilidad" }
            val array = root.getJSONArray("entries")
            require(array.length() in 1..4096)
            val rows = List(array.length()) { i ->
                val row = array.getJSONObject(i)
                val status = row.field("status", 24)
                require(status in states)
                val packages = row.getJSONArray("packages")
                require(packages.length() <= 20)
                val versions = (0 until packages.length()).mapNotNull { n ->
                    runCatching {
                    val link = packages.getJSONObject(n)
                    val size = link.getLong("size")
                    val hash = link.field("sha256", 64)
                    val original = link.field("file", 160)
                    val format = link.field("format", 12)
                    val url = link.field("url", 512)
                    val release = link.field("release", 512)
                    require(size in 1..MAX_ASSET.toLong() && HASH64.matches(hash) && format in formats &&
                        validBaseName(original) && original.substringAfterLast('.').uppercase() == format &&
                        releaseUrl(url) && https(release)) { "Paquete publicado inválido" }
                    val provenance = link.field("provenance", 20)
                    require(provenance in setOf("AUTHOR", "COMMUNITY"))
                    PackageLink(link.field("version", 80), original, size, hash, url, format, provenance, release,
                        link.optString("availability", "UNVERIFIED"), link.optString("distribution", ""),
                        publishedInstant(link.optString("publishedAt")), link.optString("notes").take(300),
                        link.optString("revision").take(40), link.optBoolean("prerelease"), link.optBoolean("current", true))
                    }.getOrNull()
                }
                val id = row.field("id", 180)
                val source = row.field("source", 512)
                require(idPattern.matches(id) && https(source))
                val tags = row.getJSONArray("genres")
                require(tags.length() <= 12)
                val image = row.optString("image", "").takeIf {
                    https(it) && URI(it).host == "thefirmwarehub.com" && URI(it).path.startsWith("/fw/cards/")
                }
                Listing(id, row.field("name", 160), row.field("author", 160), row.field("repo", 180), source,
                    row.field("license", 140), row.field("description", 700),
                    List(tags.length()) { tags.getString(it).also { tag -> require(tag.length in 1..40) } },
                    status, row.field("version", 80), row.field("compatibility", 200), row.field("identity", 100),
                    image, row.optString("demo", "").takeIf(::https),
                    row.optString("variantOf", "").takeIf { it != "null" && it.length in 1..180 }, versions)
            }
            require(rows.map { it.id }.toSet().size == rows.size) { "IDs duplicados" }
            return Discovery(root.field("updated", 100), rows, publishedInstant(root.optString("generatedAt")))
        }
    }
}

internal fun JSONObject.field(key: String, max: Int): String =
    getString(key).also { require(it.length <= max && it.none { char -> char.code < 32 && char !in "\r\n\t" }) }

internal fun https(value: String): Boolean = value.length in 10..4096 && runCatching {
    val p = URI(value)
    p.scheme == "https" && p.host != null && p.userInfo == null && p.port in listOf(-1, 443)
}.getOrDefault(false)

internal fun validBaseName(name: String): Boolean =
    name.length in 1..160 && name != "." && name != ".." && name.none { it in "/\\:" || it.code < 32 }

internal fun releaseUrl(value: String): Boolean = https(value) && URI(value).let {
    it.host == "github.com" && it.path.split('/').let { parts ->
        parts.size >= 7 && parts[3] == "releases" && parts[4] == "download"
    } && it.rawQuery == null && it.rawFragment == null
}
