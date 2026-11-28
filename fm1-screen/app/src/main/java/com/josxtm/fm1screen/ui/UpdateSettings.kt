// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.ui

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.josxtm.fm1screen.BuildConfig
import com.josxtm.fm1screen.MainActivity
import com.josxtm.fm1screen.ui.theme.SettingsColors
import com.josxtm.fm1screen.ui.theme.Theme
import com.josxtm.fm1screen.ui.theme.color
import com.josxtm.fm1screen.update.StableChannel
import com.josxtm.fm1screen.update.StableRelease
import java.text.DateFormat
import java.util.Date

@Composable
internal fun UpdateSettings(app: MainActivity) {
    val updates = app.updates
    val release = updates.release
    var details by remember { mutableStateOf(false) }
    Note("Versión instalada ${BuildConfig.VERSION_NAME} · Comp. ${BuildConfig.VERSION_CODE.toString().padStart(3, '0')}")
    Note("Canal estable")
    val lastCheck = updates.lastCheck.takeIf { it > 0 }?.let { localDate(it) } ?: "Nunca"
    Note("Última comprobación · $lastCheck")
    Note(updates.status)
    Toggle("Comprobar automáticamente", updates.automatic, change = updates::changeAutomatic)
    Action("Buscar actualizaciones", enabled = !updates.busy) { updates.check() }
    if (release != null) {
        Action(if (updates.ready) "Instalar APK verificada" else "Descargar e instalar",
            enabled = !updates.busy) { app.installUpdate() }
        Action("Ver novedades de la comp. ${release.build}") { details = true }
        if (updates.downloading) {
            val fraction = (updates.received.toFloat() / release.size).coerceIn(0f, 1f)
            LinearProgressIndicator(progress = { fraction })
            Note("${(fraction * 100).toInt()} % · ${Formatter.formatFileSize(app, updates.received)} / ${Formatter.formatFileSize(app, release.size)}")
            Action("Cancelar descarga", click = updates::cancelDownload)
        }
        if (details) UpdateDetails(app, release) { details = false }
    }
    Action("Historial de novedades") {
        val url = "https://github.com/${StableChannel.repository}/releases?q=fm1-screen-v"
        runCatching { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { app.updates.browserUnavailable() }
    }
}

@Composable
internal fun UpdateNotice(app: MainActivity, theme: Theme, modifier: Modifier = Modifier) {
    val release = app.updates.release ?: return
    if (app.updates.dismissed) return
    var details by remember(release.code) { mutableStateOf(false) }
    val colors = remember(theme) { SettingsColors(theme) }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        primary = colors.accent.color, onSurface = colors.text.color,
        surfaceContainerHigh = colors.panel.color, onSurfaceVariant = colors.secondary.color,
    )) {
        Surface(modifier = modifier, color = colors.panel.color,
            contentColor = colors.text.color, shape = MaterialTheme.shapes.medium) {
            Row {
                TextButton(onClick = { details = true }) { Text("Actualización · ${release.build}") }
                TextButton(onClick = app.updates::dismiss) { Text("Más tarde") }
            }
        }
        if (details) UpdateDetails(app, release) { details = false }
    }
}

@Composable
private fun UpdateDetails(app: MainActivity, release: StableRelease, close: () -> Unit) {
    val icon = remember { app.applicationInfo.loadIcon(app.packageManager).toBitmap(64, 64).asImageBitmap() }
    var notes by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = close,
        icon = { Image(icon, contentDescription = "FM1 SCREEN", modifier = Modifier.size(48.dp)) },
        title = { Text(if (notes) "Novedades · Comp. ${release.build}" else "Actualización disponible") },
        text = {
            Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                Text("Instalada ${BuildConfig.VERSION_NAME} · Comp. ${BuildConfig.VERSION_CODE.toString().padStart(3, '0')}")
                Text("Nueva ${release.version} · Comp. ${release.build}")
                Text("${Formatter.formatFileSize(app, release.size)} · ${localDate(release.publishedAt.toEpochMilli())}")
                val lines = if (notes) release.notes else release.notes.take(2)
                lines.forEach { Text("• $it", Modifier.padding(top = 6.dp)) }
                if (!notes) TextButton(onClick = { notes = true }) { Text("VER NOVEDADES") }
            }
        },
        confirmButton = {
            TextButton(enabled = !app.updates.busy, onClick = {
                close()
                app.openUpdateSettings()
                app.installUpdate()
            }) { Text("DESCARGAR E INSTALAR") }
        },
        dismissButton = {
            TextButton(onClick = {
                app.updates.dismiss()
                close()
            }) { Text("MÁS TARDE") }
        },
    )
}

private fun localDate(millis: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    .format(Date(millis))
