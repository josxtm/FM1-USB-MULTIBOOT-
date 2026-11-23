package com.josxtm.fm1ramloader

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlin.system.measureTimeMillis

class ManagerPhaseDTest {
    private val project = "hub/test"
    private val repo = "musician/project"
    private val now = Instant.parse("2026-11-23T10:00:00Z")

    @Test fun matchesByProjectAndOrdersVersionsSafely() {
        val old = published("v1.9.4", "a")
        val new = published("v1.10.0", "b")
        val installed = record(old)
        assertEquals(new, check(listOf(new, old), installed).single().candidate)
        assertEquals(new, check(listOf(new), installed, record(published("1.8.0", "c"))).single().candidate)
        assertTrue(check(listOf(old), installed).isEmpty())
        assertTrue(check(listOf(published("v1.9.0", "c")), installed).isEmpty())
        assertTrue(check(listOf(new), installed.copy(project = "hub/unrelated")).isEmpty())
        assertTrue(check(listOf(new.copy(availability = "REMOVED")), installed).isEmpty())
        assertTrue(check(listOf(new.copy(prerelease = true)), installed).isEmpty())
        for (unknown in listOf(published("latest", "c"), published("v1.9.4", "c"), published("1.9.4+rev", "c"))) {
            assertNull(check(listOf(unknown), installed).single().candidate)
        }
        assertNull(check(listOf(new), installed.copy(author = "Alguien distinto")).single().candidate)
        assertNull(check(listOf(new), installed.copy(source = old.url.replace(repo, "other/owner"))).single().candidate)
        assertEquals(1, compareFirmwareVersion(published("v2.0.0-rc.11", "c"), record(published("2.0.0-rc.2", "a")), null))
        assertTrue(compareFirmwareVersion(published("20261123", "c"), record(published("2026-10-03", "a")), null)!! > 0)
    }

    @Test fun communityUpdatesRequireExactRevisionHistoryAndPublisher() {
        val communityRepo = "2bbb/fm1-firmware-collection"
        val old = published("deadbee", "a", communityRepo, "collection-1").copy(provenance = "COMMUNITY",
            distribution = "COMMUNITY_OPEN_BUILD", revision = "deadbeef1234", publishedAt = now)
        val recent = published("1234567", "b", communityRepo, "collection-2").copy(provenance = "COMMUNITY",
            distribution = "COMMUNITY_OPEN_BUILD", revision = "123456789abc", publishedAt = now.plusSeconds(86400))
        val row = listing(listOf(recent, old.copy(current = false))).copy(
            id = "github/$repo", status = "COMMUNITY")
        val installed = record(old).copy(project = row.id)
        fun suggestion(value: Listing) = firmwareUpdates(Discovery("fixture", listOf(value)), listOf(installed),
            emptySet(), emptySet()).single()
        assertEquals(recent, suggestion(row).candidate)
        assertNull(suggestion(row.copy(packages = listOf(recent))).candidate)
        assertNull(suggestion(row.copy(packages = listOf(recent.copy(revision = "different"), old.copy(current = false)))).candidate)
        assertTrue(firmwareUpdates(Discovery("fixture", listOf(row)), listOf(installed), setOf(recent.url), emptySet()).isEmpty())
    }

    @Test fun omittedVersionsDoNotHideFutureCandidates() {
        val old = record(published("1.0", "a"))
        val next = published("1.1", "b")
        val update = check(listOf(next), old).single()
        val info = Discovery("fixture", listOf(listing(listOf(next))))
        assertTrue(firmwareUpdates(info, listOf(old), emptySet(), setOf(update.key)).isEmpty())
        val later = Discovery("fixture", listOf(listing(listOf(published("1.2", "c")))))
        assertEquals("1.2", firmwareUpdates(later, listOf(old), emptySet(), setOf(update.key)).single().candidate?.version)
    }

    @Test fun rejectsBrokenOrOlderRefreshWhileOfflineKeepsCurrent() {
        val file = File(System.getProperty("fm1.discovery")!!).readText()
        fun document(time: Instant) = JSONObject(file).put("generatedAt", time.toString()).toString().encodeToByteArray()
        val recent = document(now)
        val active = Discovery.decode(recent)
        fun refresh(snapshot: ByteArray, digest: String = sha256(snapshot)) = ManifestRefresh.check(active, now) { url, _ ->
            if (url.endsWith(".sha256")) digest.encodeToByteArray() else snapshot
        }
        assertEquals(now, refresh(recent).second.generated)
        assertNotNull(runCatching { refresh(recent, "0".repeat(64)) }.exceptionOrNull())
        assertNotNull(runCatching { refresh(document(now.minusSeconds(1))) }.exceptionOrNull())
        assertNotNull(runCatching { refresh(document(now.plusSeconds(601))) }.exceptionOrNull())
        assertNotNull(runCatching { ManifestRefresh.requireFresh(Discovery.decode(document(now.minusSeconds(15 * 86400))), null, now) }.exceptionOrNull())
        assertNotNull(runCatching { refresh("invalid".encodeToByteArray()) }.exceptionOrNull())
        assertNotNull(runCatching { ManifestRefresh.check(active, now) { _, _ -> throw IOException("no network") } }.exceptionOrNull())
        assertEquals(now, active.generated)
    }

    @Test fun progressMatchesEveryWriteAndRereadAtScale() {
        val matrix = JSONArray()
        for (size in listOf(1, 33, 250, 410, 1024, 4096)) {
            val store = MemoryCard()
            val assets = List(size) { asset(it) }
            val expected = assets.sumOf { it.record.size }
            val trace = mutableListOf<CopyProgress>()
            val library = CardLibrary(store)
            val ms = measureTimeMillis { library.publish(assets, CardCopy(trace::add)) }
            val last = trace.last()
            assertEquals(CopyPhase.DONE, last.phase)
            assertEquals(size, last.completed)
            assertEquals(expected, last.written)
            assertEquals(expected, last.bytes)
            assertEquals(1f, last.fraction)
            assertTrue(trace.zipWithNext().all { (a, b) -> a.written <= b.written && a.fraction <= b.fraction })
            assertTrue(trace.indexOfFirst { it.phase == CopyPhase.INDEX } < trace.indexOfFirst { it.phase == CopyPhase.INDEX_VERIFY })
            assertEquals(size, trace.count { it.phase == CopyPhase.VERIFYING && it.fileWritten == it.fileBytes } / 2)
            assertTrue(store.files.keys.filter { it.startsWith("FW/") }.all { it.count { ch -> ch == '/' } == 1 })
            val reopened = CardLibrary(store)
            assertTrue(reopened.scan().records.all(reopened::verify))
            val duplicate = mutableListOf<CopyProgress>()
            reopened.publish(listOf(assets.first(), assets.first()), CardCopy(duplicate::add))
            assertEquals(1, duplicate.last().completed)
            assertEquals(assets.first().record.size, duplicate.last().reused)
            assertEquals(0L, duplicate.last().written)
            matrix.put(JSONObject().put("synthetic_files", size).put("bytes_written", expected)
                .put("verified_files", last.completed).put("nested_files", 0).put("host_ms", ms))
        }
        val output = requireNotNull(File(System.getProperty("fm1.card.fixture")!!).parentFile)
        output.mkdirs()
        File(output, "COPY-MATRIX.json").writeText(matrix.toString(2) + "\n")
    }

    @Test fun failureDuringUpdateNeverReplacesTheExistingVersionAndReopenCanResume() {
        val original = asset(0)
        val next = asset(1)
        for (failure in listOf("space", "cancel", "removed", "corrupt", "index")) {
            val store = MemoryCard()
            val old = CardLibrary(store).publish(listOf(original))
            val trace = mutableListOf<CopyProgress>()
            var stop = false
            val copy = CardCopy({ progress ->
                trace += progress
                if (failure == "cancel" && progress.written > 0) stop = true
            }, { stop })
            store.failure = failure
            val error = runCatching { CardLibrary(store).publish(listOf(next), copy) }.exceptionOrNull() as Exception
            copy.failed(error)
            assertEquals(if (failure == "cancel") CopyPhase.STOPPED else CopyPhase.ERROR, trace.last().phase)
            assertFalse(trace.any { it.phase == CopyPhase.DONE })
            store.failure = ""
            assertEquals(old.records, CardLibrary(store).scan().records)
            assertTrue(CardLibrary(store).verify(original.record))
            if (failure == "corrupt") store.files.remove(next.record.path)
            val after = CardLibrary(store).publish(listOf(next))
            assertEquals(setOf(original.record.sha, next.record.sha), after.records.map { it.sha }.toSet())
            assertTrue(after.records.all(CardLibrary(store)::verify))
        }
    }

    private fun check(links: List<PackageLink>, vararg installed: CardRecord) =
        firmwareUpdates(Discovery("fixture", listOf(listing(links))), installed.toList(), emptySet(), emptySet())

    private fun listing(links: List<PackageLink>) = Listing(project, "Fixture", "Musician", repo,
        "https://github.com/$repo", "MIT", "", emptyList(), "AUTHOR", "", "FM-1", "", null, null, null, links)

    private fun published(version: String, hash: String, publisher: String = repo, tag: String = version) =
        PackageLink(version, "original.fwsc", 20, hash.repeat(64),
            "https://github.com/$publisher/releases/download/$tag/original.fwsc", "FWSC", "AUTHOR",
            "https://github.com/$publisher/releases/tag/$tag", "AVAILABLE", "AUTHOR_OPEN_RELEASE")

    private fun record(link: PackageLink) = CardRecord(project, "Fixture", link.version, link.file, link.size, link.sha256,
        "FWSC", "FM-1", "", "", link.url, "Musician")

    private fun asset(index: Int): CardAsset {
        val bytes = ByteArray(101 + index % 7) { (it * 17 + index).toByte() }
        index.toString().encodeToByteArray().copyInto(bytes)
        return CardAsset(record(published(index.toString(), "a")).copy(size = bytes.size.toLong(), sha = sha256(bytes))) { bytes }
    }

    private class MemoryCard : CardStore {
        val files = mutableMapOf<String, ByteArray>()
        var failure = ""
        override fun read(path: String, max: Int) = files[path]?.also { require(it.size <= max) }
        override fun availableBytes(): Long? = if (failure == "space") 0 else null
        override fun write(path: String, data: ByteArray, replace: Boolean) {
            require(replace || path !in files)
            if (failure == "index" && path.endsWith(".V2")) throw IOException("index write")
            files[path] = data.copyOf()
        }
        override fun writePackage(path: String, data: ByteArray, check: () -> Unit, written: (Int) -> Unit) {
            check()
            written(10)
            check()
            if (failure == "removed") throw IOException("device removed")
            written(data.size - 10)
            write(path, if (failure == "corrupt") data.clone().also { it[0] = 0xff.toByte() } else data, false)
        }
        override fun deleteStagingLegacy() = Unit
    }
}
