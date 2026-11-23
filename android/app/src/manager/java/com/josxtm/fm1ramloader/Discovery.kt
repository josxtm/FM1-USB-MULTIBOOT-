package com.josxtm.fm1ramloader

import org.json.JSONObject
import java.net.URI

internal const val MAX_ASSET = 16 * 1024 * 1024
internal const val MAX_MANIFEST = 6 * 1024 * 1024
internal val HASH64 = Regex("[a-f0-9]{64}")

internal data class PackageLink(
    val version: String, val file: String, val size: Long, val sha256: String,
    val url: String, val format: String, val provenance: String, val release: String
)

internal data class Listing(
    val id: String, val name: String, val author: String, val repo: String, val source: String,
    val license: String, val description: String, val genres: List<String>, val status: String,
    val version: String, val compatibility: String, val identity: String,
    val image: String?, val demo: String?, val variantOf: String?, val packages: List<PackageLink>
)

internal data class Discovery(val updated: String, val entries: List<Listing>) {
    companion object {
        private val states = setOf("AUTHOR", "COMMUNITY", "SOURCE", "RESTRICTED", "INCOMPATIBLE",
            "UNVERIFIED", "TOOL", "FORK", "DUPLICATE", "NO_DIRECT")
        private val idPattern = Regex("[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+){1,2}")
        private val formats = setOf("FWSC", "UFW", "BIN")
        fun decode(bytes: ByteArray): Discovery {
            require(bytes.size <= MAX_MANIFEST) { "Manifiesto demasiado grande" }
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            require(root.getInt("schema") == 1)
            val array = root.getJSONArray("entries")
            require(array.length() in 1..4096)
            val rows = List(array.length()) { i ->
                val row = array.getJSONObject(i)
                val status = row.field("status", 24)
                require(status in states)
                val packages = row.getJSONArray("packages")
                require(packages.length() <= 20)
                val versions = List(packages.length()) { n ->
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
                    require(status == provenance)
                    PackageLink(link.field("version", 80), original, size, hash, url, format, provenance, release)
                }
                require((status == "AUTHOR" || status == "COMMUNITY") == versions.isNotEmpty())
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
            return Discovery(root.field("updated", 100), rows)
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
