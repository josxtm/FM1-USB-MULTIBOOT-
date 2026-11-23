package com.josxtm.fm1ramloader

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.zip.CRC32

class ManagerContractsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun manifestUsesRealDiscoveryAndScales() {
        val data = File(System.getProperty("fm1.discovery")!!).readBytes()
        val bundled = Discovery.decode(data)
        assertTrue(bundled.entries.size >= 250)
        assertTrue(bundled.entries.flatMap { it.packages }.map { it.sha256 }.toSet().size > 250)
        val json = JSONObject(data.toString(Charsets.UTF_8))
        val sample = json.getJSONArray("entries").getJSONObject(0).toString()
        val enlarged = JSONArray()
        for (i in 0 until 1150) enlarged.put(JSONObject(sample).put("id", "test/item$i"))
        json.put("entries", enlarged)
        assertEquals(1150, Discovery.decode(json.toString().encodeToByteArray()).entries.size)
        enlarged.put(enlarged.getJSONObject(0))
        expectFailure { Discovery.decode(json.toString().encodeToByteArray()) }
        enlarged.remove(enlarged.length() - 1)
        enlarged.getJSONObject(0).put("status", "TRUST_ME")
        expectFailure { Discovery.decode(json.toString().encodeToByteArray()) }
    }

    @Test fun packageReadOnlyFormatsAndCorruption() {
        val ufw = ufw(0)
        assertEquals("UFW", PackageInspector.inspect(ufw, "firmware.ufw").container)
        val fwsc = wrapFwsc(ufw)
        assertEquals("FM-1_900", PackageInspector.inspect(fwsc, "firmware.fwsc").identity)
        val corrupt = ufw.copyOf().also { it[1050] = (it[1050].toInt() xor 1).toByte() }
        expectFailure { PackageInspector.inspect(corrupt, "bad.ufw") }
        expectFailure { PackageInspector.inspect(wrapFwsc(ufw, "SMK-37_100"), "wrong.fwsc") }
        assertEquals("SLOT v1", PackageInspector.inspect(slot(2), "firmware.bin").container)
        expectFailure { PackageInspector.inspect(slot(2).also { it[12] = 0 }, "wrong.bin") }
        expectFailure { PackageInspector.inspect(slot(2).also { it[it.lastIndex] = 14 }, "bad.bin") }
    }

    @Test fun interruptedNetworkResumesOnlyAgainstSameETag() {
        val bytes = wrapFwsc(ufw(3))
        val expected = link(bytes)
        val requests = mutableListOf<Long>()
        val cache = DownloadCache(temporary.newFolder(), DownloadHttp { _, offset, etag ->
            requests += offset
            if (offset == 0L) body(200, bytes.copyOf(1234))
            else {
                assertEquals("\"x\"", etag)
                body(206, bytes.copyOfRange(offset.toInt(), bytes.size), "bytes $offset-${bytes.lastIndex}/${bytes.size}")
            }
        })
        expectFailure { cache.obtain(expected) }
        assertEquals(expected.sha256, cache.obtain(expected).digest)
        assertEquals(listOf(0L, 1234L), requests)
        assertEquals(expected.sha256, cache.obtain(expected).digest)
        assertEquals(2, requests.size)
    }

    @Test fun abandonedRangeAndExpiredLinkDoNotPublish() {
        val bytes = wrapFwsc(ufw(11))
        val expected = link(bytes)
        var calls = 0
        val cache = DownloadCache(temporary.newFolder(), DownloadHttp { _, offset, _ ->
            calls++
            when (calls) {
                1 -> body(200, bytes.copyOf(100))
                2 -> body(206, bytes, "bytes 99-${bytes.lastIndex}/${bytes.size}")
                3 -> { assertEquals(0, offset); body(410, byteArrayOf()) }
                else -> body(200, bytes)
            }
        })
        repeat(3) { expectFailure { cache.obtain(expected) }; assertTrue(cache.complete().isEmpty()) }
        assertEquals(expected.sha256, cache.obtain(expected).digest)
    }

    @Test fun checksumMismatchPauseAndConcurrentDeduplication() {
        val first = wrapFwsc(ufw(17))
        val second = wrapFwsc(ufw(23))
        val a = link(first)
        val b = link(second, "v2")
        val hits = java.util.concurrent.atomic.AtomicInteger()
        val cache = DownloadCache(temporary.newFolder(), DownloadHttp { url, _, _ ->
            hits.incrementAndGet()
            body(200, if (url == a.url) first else second)
        })
        assertEquals(a.sha256, cache.obtain(a).digest)
        assertEquals(1, hits.get())
        expectFailure { cache.obtain(b, paused = { true }) }
        assertEquals(1, hits.get())
        val executor = Executors.newFixedThreadPool(3)
        try {
            val outcomes = executor.invokeAll(listOf(a, b, b).map { packageLink ->
                Callable { cache.obtain(packageLink).digest }
            }).map { it.get() }
            assertEquals(listOf(a.sha256, b.sha256, b.sha256), outcomes)
            assertEquals(2, hits.get())
        } finally { executor.shutdownNow() }
        val other = DownloadCache(temporary.newFolder(), DownloadHttp { _, _, _ -> body(200, second) })
        expectFailure { other.obtain(a) }
        assertTrue(other.complete().isEmpty())
    }

    @Test fun manualImportPreservesOriginalAndDeduplicates() {
        val data = wrapFwsc(ufw(23))
        val cache = DownloadCache(temporary.newFolder())
        val found = cache.importOriginal("original.fwsc", ByteArrayInputStream(data))
        assertArrayEquals(data, found.file.readBytes())
        assertEquals(found.file, cache.importOriginal("third.fwsc", ByteArrayInputStream(data)).file)
        expectFailure { cache.importOriginal("truncated.fwsc", ByteArrayInputStream(data.copyOf(12))) }
    }

    @Test fun pagedIndexSupports1152RecordsAndRecoversAfterLoss() {
        val store = MemoryCard()
        val library = CardLibrary(store)
        val items = List(1152) { index -> entry("v$index".encodeToByteArray(), index) }
        val snapshot = library.publish(items)
        assertEquals(1152, snapshot.records.size)
        assertEquals(36, store.files.keys.count { it.endsWith(".JSN") })
        assertEquals(snapshot.records, library.scan().records)
        val new = entry("version 1153".encodeToByteArray(), 1153)
        store.fail = "CATALOG/INDEXB.V2"
        expectFailure { library.publish(listOf(new)) }
        val recovered = library.scan()
        assertTrue(recovered.recovered)
        assertEquals(snapshot.records, recovered.records)
        store.fail = null
        val updated = library.publish(listOf(new, new))
        assertEquals(1153, updated.records.size)
        assertTrue(library.verify(new.record))
        store.files.remove(new.record.path)
        assertFalse(library.verify(new.record))
        store.files.remove(store.files.keys.first { it.endsWith(".JSN") })
        assertTrue(library.scan().records.isNotEmpty())
    }

    @Test fun missingMediaAndFullStorageLeaveOldIndex() {
        val store = MemoryCard()
        val library = CardLibrary(store)
        val initial = library.publish(listOf(entry(byteArrayOf(1), 1)))
        val added = entry(byteArrayOf(2), 2)
        store.offline = true
        expectFailure { library.publish(listOf(added)) }
        store.offline = false
        store.fail = added.record.path
        expectFailure { library.publish(listOf(added)) }
        assertEquals(initial.records, library.scan().records)
        assertEquals(1, store.files.keys.count { it.endsWith(".V2") })
    }

    @Test fun firstPublicationCannotOverwriteAnUnexpectedIndex() {
        val backing = MemoryCard()
        val path = "CATALOG/INDEXA.V2"
        val previous = "existing index".encodeToByteArray()
        val raced = object : CardStore by backing {
            override fun write(path: String, data: ByteArray, replace: Boolean) {
                if (path == "CATALOG/INDEXA.V2") backing.files[path] = previous
                backing.write(path, data, replace)
            }
        }
        expectFailure { CardLibrary(raced).publish(listOf(entry(byteArrayOf(9), 9))) }
        assertArrayEquals(previous, backing.files[path])
    }

    @Test fun kotlinExportFeedsEmbeddedV1AndRecoversBackup() {
        val store = MemoryCard()
        val library = CardLibrary(store)
        val a = entry(slot(6, true), 1)
        val b = entry(slot(9, true), 2)
        val snapshot = library.publish(listOf(a, b))
        assertEquals(1, library.exportLegacy(listOf(a.record)))
        assertEquals(1, library.exportLegacy(listOf(b.record)))
        assertEquals(2, FirmwareCatalog.decode(store.files.getValue("INDEX.CSV").decodeToString()).size)
        val out = File(System.getProperty("fm1.card.fixture")!!)
        for ((path, data) in store.files) {
            val file = File(out, path)
            requireNotNull(file.parentFile).mkdirs()
            file.writeBytes(data)
        }
        store.files["INDEX.CSV"] = byteArrayOf(1)
        library.recoverLegacy()
        assertEquals(1, FirmwareCatalog.decode(store.files.getValue("INDEX.CSV").decodeToString()).size)
        assertEquals(snapshot.records, library.scan().records)
    }

    @Test fun approvedSnapshotAndFiltersNeverSelectUnqualifiedPackages() {
        val snapshot = Discovery.decode(File(System.getProperty("fm1.discovery")!!).readBytes())
        val available = snapshot.entries.filter { DownloadFilter.AVAILABLE.matches(it, emptySet()) }
        assertEquals(913, snapshot.entries.size)
        assertEquals(410, available.flatMap { it.downloads() }.map { it.sha256 }.toSet().size)
        assertEquals(280, snapshot.entries.count { DownloadFilter.AUTHOR.matches(it, emptySet()) })
        assertEquals(129, snapshot.entries.count { DownloadFilter.COMMUNITY.matches(it, emptySet()) })
        assertEquals(504, snapshot.entries.count { DownloadFilter.UNAVAILABLE.matches(it, emptySet()) })
        val author = available.first { it.status == "AUTHOR" }
        val p = author.downloads().first()
        for (broken in listOf(p.copy(url = "https://example.org/file"), p.copy(sha256 = ""),
            p.copy(distribution = ""), p.copy(availability = "REMOVED"), p.copy(availability = "TEMPORARY"))) {
            assertTrue(author.copy(packages = listOf(broken)).downloads().isEmpty())
        }
        assertTrue(author.copy(license = "NOASSERTION").downloads().isEmpty())
        assertTrue(author.downloads(setOf(p.url)).isEmpty())
        assertTrue(author.copy(repo = "unrelated/project").downloads().isEmpty())
        assertTrue(DownloadFilter.ALL.matches(author.copy(license = "NOASSERTION"), emptySet()))
    }

    @Test fun flatLibraryReusesSameFileForV1AndRejectsConflicts() {
        val store = MemoryCard()
        val lib = CardLibrary(store)
        val a = entry(byteArrayOf(1, 2, 3), 1)
        val b = entry(byteArrayOf(9, 8, 7), 2)
        assertEquals(1, lib.publish(listOf(a, a)).records.size)
        assertEquals(1, lib.exportLegacy(listOf(a.record)))
        assertEquals(listOf(a.record.path), store.files.keys.filter { it.startsWith("FW/") })
        store.files[b.record.path] = byteArrayOf(0)
        expectFailure { lib.publish(listOf(b)) }
        assertArrayEquals(byteArrayOf(0), store.files[b.record.path])
        assertEquals(1, lib.scan().records.size)
    }

    @Test fun legacyMigrationRetainsOriginalThroughInterruptedIndexWrite() {
        val store = MemoryCard()
        val lib = CardLibrary(store)
        val asset = entry(byteArrayOf(1, 7, 8), 8)
        val old = seedLegacy(store, listOf(asset)).single()
        assertTrue(lib.scan().records.single().nested)
        assertTrue(lib.verify(old))
        store.fail = "CATALOG/INDEXB.V2"
        expectFailure { lib.reorganize() }
        assertTrue(lib.scan().recovered)
        assertTrue(lib.scan().records.single().nested)
        store.fail = null
        val final = lib.reorganize()
        assertFalse(final.records.single().nested)
        assertEquals(asset.record.path, lib.scan().records.single().path)
        assertArrayEquals(asset.load(), store.files[old.path])
        assertArrayEquals(asset.load(), store.files[asset.record.path])
        assertEquals(final, lib.reorganize())
    }

    @Test fun scaleMatrixKeepsEveryOriginalFlatAcrossReopen() {
        val output = JSONArray()
        for (size in listOf(250, 415, 1024, 2048, 4096)) {
            val store = MemoryCard()
            val assets = List(size) { i -> entry("synthetic-library:$i".encodeToByteArray(), i) }
            val first = CardLibrary(store).publish(assets)
            assertEquals(size, first.records.size)
            val reopened = CardLibrary(store)
            assertEquals(first, reopened.publish(assets))
            val files = store.files.keys.filter { it.startsWith("FW/") }
            assertEquals(size, files.size)
            assertTrue(files.all { it.matches(Regex("FW/[A-F0-9]{8}\\.BIN")) })
            assertEquals(files.toSet(), reopened.scan().records.map { it.path }.toSet())
            assertTrue(first.records.all(reopened::verify))
            output.put(JSONObject().put("source", "synthetic").put("entries", size)
                .put("flat_files", files.size).put("nested_files", 0).put("sha256_verified", size))
        }
        val dir = requireNotNull(File(System.getProperty("fm1.card.fixture")!!).parentFile)
        dir.mkdirs()
        File(dir, "MATRIX.json").writeText(output.toString(2) + "\n")
    }

    @Test fun migrates415IndexedOriginalsKeepsBackupAndDoesNotRecopy() {
        val store = MemoryCard()
        val originals = List(415) { i -> entry("synthetic-migration:$i".encodeToByteArray(), i) }
        val old = seedLegacy(store, originals)
        val before = store.files.getValue("CATALOG/INDEXA.V2").copyOf()
        val migrated = CardLibrary(store).reorganize()
        assertEquals(415, migrated.records.size)
        assertEquals(2L, migrated.sequence)
        assertTrue(migrated.records.none { it.nested })
        assertArrayEquals(before, store.files.getValue("CATALOG/INDEXA.V2"))
        assertTrue(old.all(CardLibrary(store)::verify))
        assertTrue(migrated.records.all(CardLibrary(store)::verify))
        assertEquals(415, store.files.keys.count { it.matches(Regex("FW/[A-F0-9]{8}\\.BIN")) })
        assertEquals(migrated, CardLibrary(store).reorganize())
    }

    @Test fun payloadCollisionUsesTwoActualDigestsWithSamePrefix() {
        val store = MemoryCard()
        val a = entry("collision-test:34786".encodeToByteArray(), 1)
        val b = entry("collision-test:90803".encodeToByteArray(), 2)
        assertEquals(a.record.path, b.record.path)
        assertNotEquals(a.record.sha, b.record.sha)
        val first = CardLibrary(store).publish(listOf(a))
        expectFailure { CardLibrary(store).publish(listOf(b)) }
        assertArrayEquals(a.load(), store.files[a.record.path])
        assertEquals(first, CardLibrary(store).scan())
    }

    @Test fun revokedPermissionAndProviderRenameLeavePreviousIndex() {
        val backing = MemoryCard()
        val lib = CardLibrary(backing)
        val first = lib.publish(listOf(entry(byteArrayOf(88), 1)))
        val next = entry(byteArrayOf(99), 2)
        val denied = object : CardStore by backing {
            override fun write(path: String, data: ByteArray, replace: Boolean) {
                throw SecurityException("permission expired")
            }
        }
        val renamed = object : CardStore by backing {
            override fun write(path: String, data: ByteArray, replace: Boolean) {
                backing.write(path + " (1)", data, replace)
            }
        }
        for (store in listOf(denied, renamed)) {
            expectFailure { CardLibrary(store).publish(listOf(next)) }
            assertEquals(first, lib.scan())
            assertTrue(lib.verify(first.records.single()))
        }
    }

    @Test fun httpAccessRejectionIsDistinctFromRateLimitingAndRemoval() {
        val data = wrapFwsc(ufw(2))
        for (code in listOf(403, 404, 410, 429, 451)) {
            val folder = temporary.newFolder()
            val cache = DownloadCache(folder, DownloadHttp { _, _, _ -> body(code, ByteArray(0)) })
            val error = runCatching { cache.obtain(link(data)) }.exceptionOrNull() as DownloadHttpFailure
            assertEquals(code, error.code)
            assertEquals(code in listOf(404, 410, 451), error.permanent)
            assertTrue(cache.complete().isEmpty())
            if (!error.permanent) {
                val reopened = DownloadCache(folder, DownloadHttp { _, _, _ -> body(200, data) })
                assertEquals(link(data).sha256, reopened.obtain(link(data)).digest)
            }
        }
    }

    private fun seedLegacy(store: MemoryCard, assets: List<CardAsset>): List<CardRecord> {
        val records = assets.map { item ->
            item.record.copy(nested = true).also { store.files[it.path] = item.load() }
        }.sortedBy { it.sha }
        val pages = records.chunked(PAGE_SIZE).mapIndexed { i, group ->
            ("FM1CAT|2|PAGE|${i + 1}\n" + group.joinToString("\n", postfix = "\n") {
                it.json().apply { remove("author") }.toString()
            }).encodeToByteArray()
        }
        val hashes = pages.map(::sha256)
        val generation = sha256(hashes.joinToString("").encodeToByteArray()).take(16).uppercase()
        pages.forEachIndexed { i, page ->
            store.files["CATALOG/${generation.take(8)}/${generation.drop(8)}/P${(i + 1).toString().padStart(7, '0')}.JSN"] = page
        }
        val body = JSONObject().put("generation", generation).put("sequence", 1)
            .put("count", records.size).put("pages", JSONArray(hashes)).toString().encodeToByteArray()
        store.files["CATALOG/INDEXA.V2"] = "FM1CAT|2\n${sha256(body)}\n".encodeToByteArray() + body
        return records
    }

    private fun entry(bytes: ByteArray, version: Int) = CardAsset(
        CardRecord("test/device", "Fixture", "$version", "original.bin", bytes.size.toLong(), sha256(bytes),
            "SLOT v1", "FM-1", "Test CRC", "", "Synthetic test"), { bytes })

    private class MemoryCard : CardStore {
        val files = linkedMapOf<String, ByteArray>()
        var fail: String? = null
        var offline = false
        override fun read(path: String, max: Int): ByteArray? {
            if (offline) throw IOException("no volume")
            return files[path]?.also { require(it.size <= max) }
        }
        override fun write(path: String, data: ByteArray, replace: Boolean) {
            require(replace || path !in files)
            if (path == fail) {
                files[path] = data.copyOf(1)
                throw IOException("full or disconnected")
            }
            files[path] = data.copyOf()
        }
        override fun deleteStagingLegacy() { files.remove("INDEX.NEW") }
    }

    private fun link(bytes: ByteArray, version: String = "v1") = PackageLink(version, "original.fwsc", bytes.size.toLong(),
        sha256(bytes), "https://github.com/author/repo/releases/download/$version/original.fwsc",
        "FWSC", "AUTHOR", "https://github.com/author/repo/releases/tag/$version")

    private fun body(code: Int, bytes: ByteArray, contentRange: String? = null) = object : HttpBody {
        override val status = code
        override val etag = "\"x\""
        override val range = contentRange
        override fun input() = ByteArrayInputStream(bytes)
        override fun close() = Unit
    }

    private fun expectFailure(action: () -> Unit) {
        try { action() } catch (_: Exception) { return }
        fail("Expected rejection")
    }

    private fun le(bytes: ByteArray, offset: Int, value: Long, length: Int = 4) {
        repeat(length) { bytes[offset + it] = (value shr (it * 8)).toByte() }
    }

    private fun ufw(seed: Int): ByteArray {
        val raw = ByteArray(8192)
        for (i in 1024 until 5120) raw[i] = (seed + i % 33).toByte()
        val row = ByteArray(80)
        le(row, 4, PackageInspector.crc16(raw, 1024, 5120).toLong(), 2)
        le(row, 8, 1024)
        le(row, 12, 4096)
        "flash.bin".encodeToByteArray().copyInto(row, 64)
        PackageInspector.enc(row, 0, 80).copyInto(raw, 64)
        val header = ByteArray(64)
        le(header, 2, PackageInspector.crc16(raw, 64, 144).toLong(), 2)
        le(header, 8, 1, 2)
        le(header, 0, PackageInspector.crc16(header, 2).toLong(), 2)
        PackageInspector.enc(header, 0, 64).copyInto(raw)
        return raw
    }

    private fun wrapFwsc(raw: ByteArray, id: String = "FM-1_900"): ByteArray = ByteArray(raw.size + 20).also { out ->
        for (i in 0 until 20) {
            raw.copyInto(out, i * 48, i * 47, i * 47 + 47)
            out[i * 48 + 47] = if (i < id.length) (id[i].code + i + 1).toByte() else 0x7d
        }
        raw.copyInto(out, 20 * 48, 20 * 47)
    }

    private fun slot(seed: Int, synthetic: Boolean = false): ByteArray = ByteArray(512 + 4096).also { out ->
        "SLOT".encodeToByteArray().copyInto(out)
        out[4] = 1; out[5] = 1; out[16] = (1 shl 4).toByte()
        le(out, 6, 512, 2); le(out, 8, 1); le(out, 12, 0x856014)
        out.fill(seed.toByte(), 512)
        if (synthetic) "FM1-VIRTUAL-5Y\u0000".encodeToByteArray().copyInto(out, 512)
        le(out, 112, CRC32().apply { update(out, 512, out.size - 512) }.value)
        le(out, 508, CRC32().apply { update(out, 0, 508) }.value)
    }
}
