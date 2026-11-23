package com.josxtm.fm1ramloader

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal data class FirmwareUpdate(val row: Listing, val installed: CardRecord, val candidate: PackageLink?) {
    val key get() = "${row.id}:${candidate?.sha256.orEmpty()}"
}

private val semverPattern = Regex(
    "^[vV]?([0-9]+(?:\\.[0-9]+){0,2})(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z.-]+)?$"
)
private val datePattern = Regex("^\\d{4}-?\\d{2}-?\\d{2}$")
private val revisionPattern = Regex("^[a-f0-9]{7,40}$")
private val publisherRepo = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

private fun date(version: String): LocalDate? = if (datePattern.matches(version)) {
    runCatching { LocalDate.parse(version, if ('-' in version) DateTimeFormatter.ISO_DATE else DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
} else null

private data class Version(val core: List<Long>, val prerelease: List<String>) : Comparable<Version> {
    override fun compareTo(other: Version): Int {
        for ((a, b) in core.zip(other.core)) if (a != b) return a.compareTo(b)
        if (prerelease.isEmpty() != other.prerelease.isEmpty()) return if (prerelease.isEmpty()) 1 else -1
        for ((a, b) in prerelease.zip(other.prerelease)) {
            if (a == b) continue
            val an = a.all(Char::isDigit)
            val bn = b.all(Char::isDigit)
            return when {
                an && bn -> a.length.compareTo(b.length).takeIf { it != 0 } ?: a.compareTo(b)
                an -> -1
                bn -> 1
                else -> a.compareTo(b)
            }
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }
}

private fun semantic(label: String): Version? {
    if (date(label) != null || revisionPattern.matches(label)) return null
    val match = semverPattern.matchEntire(label) ?: return null
    val core = match.groupValues[1].split('.').map {
        if (it.length > 1 && it.startsWith('0')) return null
        it.toLongOrNull() ?: return null
    }
    val prerelease = match.groupValues[2].takeIf(String::isNotEmpty)?.split('.').orEmpty()
    if (prerelease.any { it.length > 1 && it.startsWith('0') && it.all(Char::isDigit) }) return null
    return Version(core + List(3 - core.size) { 0L }, prerelease)
}

internal fun compareFirmwareVersion(candidate: PackageLink, installed: CardRecord, historical: PackageLink?): Int? {
    if (candidate.sha256 == installed.sha) return 0
    // A different package labelled as the same release requires a deliberate manual selection.
    if (candidate.version.equals(installed.version, true)) return null
    val aDate = date(candidate.version)
    val bDate = date(installed.version)
    if (aDate != null && bDate != null) return aDate.compareTo(bDate).takeUnless { it == 0 }
    val aVersion = semantic(candidate.version)
    val bVersion = semantic(installed.version)
    if (aVersion != null && bVersion != null) return aVersion.compareTo(bVersion).takeUnless { it == 0 }
    if (candidate.provenance == "COMMUNITY" && historical?.sha256 == installed.sha &&
        revisionPattern.matches(candidate.revision) && revisionPattern.matches(historical.revision) &&
        candidate.revision != historical.revision && historical.revision.startsWith(installed.version) &&
        candidate.revision.startsWith(candidate.version) &&
        (candidate.release != historical.release || candidate.publishedAt != historical.publishedAt)) {
        val a = candidate.publishedAt
        val b = historical.publishedAt
        if (a != null && b != null) return a.compareTo(b).takeUnless { it == 0 }
    }
    return null
}

internal fun firmwareUpdates(discovery: Discovery?, installed: List<CardRecord>, blocked: Set<String>,
                             omitted: Set<String>): List<FirmwareUpdate> {
    val byProject = installed.groupBy { it.project }
    val have = installed.mapTo(hashSetOf()) { it.sha }
    return discovery?.entries.orEmpty().mapNotNull { row ->
        if (!publisherRepo.matches(row.repo)) return@mapNotNull null
        val versions = byProject[row.id].orEmpty()
        if (versions.isEmpty()) return@mapNotNull null
        val history = row.packages.associateBy(PackageLink::sha256)
        val reference = versions.first()
        val downloads = row.downloads(blocked).filterNot(PackageLink::prerelease)
        if (downloads.isEmpty()) return@mapNotNull null
        val candidates = downloads.filter { it.sha256 !in have }
        if (candidates.isEmpty()) return@mapNotNull null
        val sameSource = versions.all { record ->
            (record.author.isBlank() || record.author.equals(row.author, true)) &&
                releaseUrl(record.source) && java.net.URI(record.source).path.trim('/').split('/').take(2).joinToString("/").equals(
                    if (row.status == "COMMUNITY") "2bbb/fm1-firmware-collection" else row.repo, true
                ) && (row.status != "COMMUNITY" || row.id.equals("github/${row.repo}", true))
        }
        val newer = if (sameSource) candidates.filter { link ->
            versions.all { (compareFirmwareVersion(link, it, history[it.sha]) ?: -1) > 0 }
        } else emptyList()
        val latest = newer.singleOrNull { link ->
            newer.all { other ->
                val otherRecord = reference.copy(version = other.version, sha = other.sha256)
                link == other || (compareFirmwareVersion(link, otherRecord, other) ?: -1) > 0
            } && downloads.all { other ->
                // A sibling build with the same or an unknown version makes automatic choice ambiguous.
                link == other || (compareFirmwareVersion(link,
                    reference.copy(version = other.version, sha = other.sha256), other) ?: -1) > 0
            }
        }
        val current = versions.singleOrNull { item ->
            val known = history[item.sha]
            versions.all { other ->
                item == other || (compareFirmwareVersion(known ?: downloads.first().copy(
                    version = item.version, sha256 = item.sha, publishedAt = null, revision = ""
                ), other, history[other.sha]) ?: -1) > 0
            }
        } ?: reference
        if (latest == null && sameSource && candidates.all { link ->
                versions.any { (compareFirmwareVersion(link, it, history[it.sha]) ?: 1) < 0 }
            }) return@mapNotNull null
        FirmwareUpdate(row, current, latest).takeUnless { it.key in omitted }
    }
}

internal fun publishedInstant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()
