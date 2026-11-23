package com.josxtm.fm1ramloader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

private fun bytes(n: Long): String = when {
    n >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MiB", n / 1048576.0)
    n >= 1024 -> String.format(Locale.getDefault(), "%.1f KiB", n / 1024.0)
    else -> "$n B"
}

@Composable
internal fun CopyBanner(progress: CopyProgress, busy: Boolean, cancel: () -> Unit, settings: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        progress.phase == CopyPhase.DONE && !busy -> "Copia y verificación finalizadas. Puedes expulsar la unidad desde Android."
                        progress.phase in setOf(CopyPhase.ERROR, CopyPhase.STOPPED) -> "${progress.phase.title} · ${progress.detail}"
                        else -> "${progress.phase.title} · No desconectes la microSD: operación en curso."
                    },
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium
                )
                if (!busy && progress.phase == CopyPhase.DONE) {
                    TextButton(onClick = settings) { Text("Almacenamiento") }
                } else if (busy && progress.phase in setOf(CopyPhase.PREPARING, CopyPhase.SPACE, CopyPhase.COPYING, CopyPhase.VERIFYING)) {
                    TextButton(onClick = cancel) { Text("Cancelar") }
                }
            }
            if (progress.bytes > 0 && progress.phase != CopyPhase.DONE) {
                Text("${progress.completed} / ${progress.total} archivos verificados · " +
                    "${bytes(progress.written)} / ${bytes(progress.bytes)} escritos · ${(progress.fraction * 100).toInt()} %" +
                    if (progress.reused > 0) " · ${bytes(progress.reused)} reutilizados" else "",
                    style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                if (progress.file.isNotEmpty() && progress.phase in setOf(CopyPhase.SPACE, CopyPhase.COPYING, CopyPhase.VERIFYING)) {
                    Text("${progress.file} · ${bytes(progress.fileWritten)} / ${bytes(progress.fileBytes)} escritos",
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!busy && progress.phase == CopyPhase.DONE) {
                Text("En Android, abre Almacenamiento o la notificación de la unidad USB, toca Expulsar y espera la confirmación.",
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun UpdateCard(item: FirmwareUpdate, selected: Set<String>, cached: Set<String>, jobs: Map<String, QueueItem>,
                        busy: Boolean, select: () -> Unit, update: () -> Unit, omit: () -> Unit) {
    val link = item.candidate
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (link != null) Checkbox(item.key in selected, { select() })
                Column(Modifier.weight(1f)) {
                    Text(item.row.name, style = MaterialTheme.typography.titleMedium)
                    Text("MicroSD · ${item.installed.version}  •  " +
                        (link?.let { "Disponible · ${it.version}" } ?: "Versión no comparable"))
                }
            }
            if (link != null) {
                Text(listOfNotNull(link.publishedAt?.toString()?.take(10),
                    jobs[link.sha256]?.state ?: if (link.sha256 in cached) "En caché" else "Pendiente de descarga",
                    "Pendiente en microSD").joinToString(" · "), style = MaterialTheme.typography.labelMedium)
                if (link.notes.isNotBlank()) Text(link.notes, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = update, enabled = !busy) { Text("Actualizar") }
                    TextButton(onClick = omit, enabled = !busy) { Text("Omitir") }
                }
            }
        }
    }
}
