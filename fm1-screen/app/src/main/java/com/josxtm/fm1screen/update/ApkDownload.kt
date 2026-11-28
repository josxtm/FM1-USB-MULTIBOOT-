// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.update

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException

internal fun ByteArray.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256").digest(this).hex()

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

internal fun receiveApk(
    input: InputStream,
    file: File,
    release: StableRelease,
    cancelled: () -> Boolean,
    progress: (Long) -> Unit,
) {
    val digest = MessageDigest.getInstance("SHA-256")
    try {
        file.outputStream().buffered().use { output ->
            val buffer = ByteArray(64 * 1024)
            var bytes = 0L
            var nextProgress = 0L
            while (true) {
                if (cancelled()) throw CancellationException()
                val count = input.read(buffer)
                if (count < 0) break
                bytes += count
                if (bytes > release.size) throw IOException("La descarga excede el tamaño anunciado")
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                if (bytes >= nextProgress || bytes == release.size) {
                    progress(bytes)
                    nextProgress = bytes + 256 * 1024
                }
            }
            if (cancelled()) throw CancellationException()
            if (bytes != release.size) throw IOException("La descarga está incompleta")
            if (digest.digest().hex() != release.sha256) throw IOException("El SHA-256 de la APK no coincide")
        }
    } catch (error: Exception) {
        file.delete()
        throw error
    }
}
