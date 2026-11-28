// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.capture

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException
import java.io.OutputStream

internal enum class CaptureKind(val title: String, val suffix: String) {
    NATIVE_LCD("LCD 240 × 240", "lcd"),
    ENLARGED_LCD("LCD ampliado", "lcd-x4"),
    INTERFACE("Interfaz completa", "interfaz"),
}

internal fun Bitmap.writePng(output: OutputStream, scale: Int) {
    val image = if (scale == 1) this else Bitmap.createScaledBitmap(this, width * scale, height * scale, false)
    try {
        check(image.compress(Bitmap.CompressFormat.PNG, 100, output)) { "No se pudo codificar el PNG" }
    } finally {
        if (image !== this) image.recycle()
    }
}

internal fun saveMedia(resolver: ContentResolver, name: String, mime: String, write: (OutputStream) -> Unit) {
    val image = mime.startsWith("image/")
    val collection = if (image) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val directory = if (image) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
    val item = resolver.insert(collection, ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/FM1 SCREEN")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }) ?: throw IOException("Android no pudo crear el archivo")
    try {
        (resolver.openOutputStream(item) ?: throw IOException("Android no pudo abrir el archivo")).use(write)
        resolver.update(item, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    } catch (failure: Exception) {
        resolver.delete(item, null, null)
        throw failure
    }
}
