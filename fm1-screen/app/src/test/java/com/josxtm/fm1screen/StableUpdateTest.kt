// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen

import com.josxtm.fm1screen.update.StableChannel
import com.josxtm.fm1screen.update.receiveApk
import com.josxtm.fm1screen.update.sha256Hex
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException

class StableUpdateTest {
    @get:Rule val files = TemporaryFolder()
    private val tag = "fm1-screen-v1.0-comp.029"
    private val root = "https://github.com/${StableChannel.repository}/releases/download/$tag"
    private val data = "apk test bytes".toByteArray()
    private val sign = "a".repeat(64)

    private fun manifest() = JSONObject()
        .put("schemaVersion", 1).put("applicationId", StableChannel.appId)
        .put("versionName", "1.0").put("versionCode", 29).put("build", "029").put("channel", "stable")
        .put("minSdk", 29).put("apkUrl", "$root/FM1-SCREEN-v1.0-comp.029.apk").put("sha256", data.sha256Hex())
        .put("signingCertificateSha256", sign).put("sizeBytes", data.size)
        .put("publishedAt", "2026-11-28T12:00:00Z").put("releaseNotes", JSONArray(listOf("Actualización")))

    private fun release(name: String = tag, draft: Boolean = false, prerelease: Boolean = false) =
        JSONObject().put("tag_name", name).put("draft", draft).put("prerelease", prerelease).put(
            "assets", JSONArray().put(JSONObject().put("state", "uploaded")
                .put("browser_download_url", "$root/${StableChannel.manifest}")),
        )

    private fun find(installed: Int = 28, sdk: Int = 36, certificate: String = sign,
                     listing: JSONArray = JSONArray().put(release()), body: String = manifest().toString()) =
        StableChannel.find(installed, sdk, certificate) { url, api ->
            if (api) listing.toString() else {
                assertEquals("$root/${StableChannel.manifest}", url)
                body
            }
        }

    @Test fun findsNewerReleaseWithMatchingCertificate() {
        assertEquals(29, find()?.code)
    }

    @Test fun rejectsCurrentAndOlderBuilds() {
        assertNull(find(installed = 29))
        assertNull(find(installed = 30))
        assertNull(find(listing = JSONArray()))
    }

    @Test fun ignoresOtherProductsDraftsAndPrereleases() {
        assertNull(find(listing = JSONArray().put(release("fm1-multiboot-v1.0-comp.029"))
            .put(release(draft = true)).put(release(prerelease = true))))
    }

    @Test fun rejectsInvalidJson() {
        assertThrows(IOException::class.java) { find(body = "{") }
    }

    @Test fun rejectsIncompatibleCertificateAndAndroid() {
        assertNull(find(certificate = "b".repeat(64)))
        assertNull(find(sdk = 28))
    }

    @Test fun rejectsWrongAppOrSource() {
        for ((field, value) in mapOf("applicationId" to "com.example.wrong", "apkUrl" to "https://example.com/fm1.apk",
            "channel" to "experimental", "versionCode" to 10, "sizeBytes" to 0)) {
            assertThrows(IOException::class.java) { find(body = manifest().put(field, value).toString()) }
        }
        assertThrows(IllegalArgumentException::class.java) { StableChannel.connect("http://github.com/") }
        assertThrows(IllegalArgumentException::class.java) { StableChannel.connect("https://github.com.example.com/") }
    }

    @Test fun reportsOfflineAndTimeout() {
        assertThrows(UnknownHostException::class.java) {
            StableChannel.find(28, 36, sign) { _, _ -> throw UnknownHostException() }
        }
        assertThrows(SocketTimeoutException::class.java) {
            StableChannel.find(28, 36, sign) { _, _ -> throw SocketTimeoutException() }
        }
    }

    @Test fun downloadsIntactApkAndReportsBytes() {
        val file = files.newFile()
        val progress = mutableListOf<Long>()
        receiveApk(data.inputStream(), file, requireNotNull(find()), { false }) { progress += it }
        assertArrayEquals(data, file.readBytes())
        assertEquals(listOf(data.size.toLong()), progress)
    }

    @Test fun discardsCancelledIncompleteExcessAndCorruptFiles() {
        val release = requireNotNull(find())
        val file = files.newFile()
        assertThrows(CancellationException::class.java) {
            receiveApk(data.inputStream(), file, release, { true }) {}
        }
        assertFalse(file.exists())
        for (bad in listOf(data.copyOf(1), data + 1.toByte(), ByteArray(data.size))) {
            assertThrows(IOException::class.java) { receiveApk(bad.inputStream(), file, release, { false }) {} }
            assertFalse(file.exists())
        }
    }
}
