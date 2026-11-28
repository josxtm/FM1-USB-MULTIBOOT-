// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.ui


import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.josxtm.fm1screen.MainActivity
import com.josxtm.fm1screen.performance.Stage
import com.josxtm.fm1screen.protocol.SIDE
import com.josxtm.fm1screen.protocol.Wire
import com.josxtm.fm1screen.transport.MidiCategory
import com.josxtm.fm1screen.transport.MirrorScope
import com.josxtm.fm1screen.ui.theme.RfmFonts
import com.josxtm.fm1screen.ui.theme.color
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun DiagnosticsSettings(app: MainActivity) {
    val stats = app.mirrorStats
    val ports = app.midiPorts
    val latency = app.performanceStages[Stage.INTERNAL_LATEST]
    val rx = if (ports.rxOpen) "abierto" else "cerrado"
    val tx = if (ports.txOpen) "abierto" else "cerrado"
    Summary("Estado MIDI", if (ports.rxOpen || ports.txOpen) "Conectado" else "Sin conexión")
    Summary("RX / TX", "$rx / $tx")
    Summary("Actualización gráfica", "${stats.patchesPerSecond} bloques/s · ${app.rendersPerSecond} dibujos/s")
    Summary("Latencia interna", latency?.let { String.format(Locale.ROOT, "P95 %.2f ms", it.p95Ms) } ?: "Sin muestras")
    Summary("Estado de sesión", stats.state.title)
    Row(Modifier.fillMaxWidth()) {
        Action("Exportar registros", Glyph.EXPORT, Modifier.weight(1f), click = app::exportLogs)
        Action("Compartir diagnóstico", modifier = Modifier.weight(1f), click = app::shareLogs)
    }
    Disclosure("Diagnóstico avanzado") { AdvancedDiagnostics(app) }
    Disclosure("Consola MIDI / SysEx") { MidiConsole(app) }
}

@Composable
private fun Summary(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Note(label, modifier = Modifier.weight(0.42f))
        Note(value, DrawerColors.current.text.color, Modifier.weight(0.58f).padding(start = 8.dp))
    }
}

@Composable
private fun AdvancedDiagnostics(app: MainActivity) {
    val stats = app.mirrorStats
    val coverage = (stats.coverage.toDouble() * 100 / (SIDE * SIDE)).roundToInt()
    Caption("Señal")
    Note("${stats.state.title} · ${stats.wire?.title ?: "Sin protocolo"} · $coverage % de cobertura")
    Note("${stats.patchesPerSecond} bloques/s · ${stats.displaysPerSecond} publicaciones/s · ${app.rendersPerSecond} dibujadas/s")
    if (stats.wire == Wire.BIG2) Note("${stats.syncsPerSecond} fotogramas SYNC/s")
    Note("${stats.blocks} bloques · ${stats.fullFrames} pantallas completas")
    if (stats.wire == Wire.BASE) Note("${stats.gaps} bloques ausentes según secuencia")
    if (stats.wire == Wire.BIG1) Note("Big Screen V1 carece de marcas de secuencia y de fotograma")
    Note(stats.sinceGraphicsMs?.let { "Último bloque gráfico hace ${it / 1000.0} s" } ?: "Sin bloques gráficos")
    stats.sinceSessionMs?.let { Note("Última señal de sesión hace ${it / 1000.0} s") }

    Caption("Dispositivo y puertos")
    val firmware = stats.identity
    val identity = when {
        firmware.atlatl -> "SKIFF Atlatl ${firmware.version ?: ""}".trim()
        firmware.reportedName != null -> firmware.reportedName.orEmpty()
        app.profile != null -> app.profile?.name.orEmpty() + if (app.autoProfile) " · descriptor" else " · perfil manual"
        else -> "Firmware sin identificar"
    }
    Note("${app.link.title} · $identity${firmware.packageId?.let { " · $it" }.orEmpty()}")
    for (target in app.deviceList) {
        Note(listOfNotNull(target.name, target.details.takeIf(String::isNotBlank),
            if (target.recognized) null else "Escucha pasiva").joinToString(" · "))
    }
    val ports = app.midiPorts
    Note("RX ${if (ports.rxOpen) "abierto" else "cerrado"} · TX ${if (ports.txOpen) "abierto" else "cerrado"} · ${ports.reconnections} reconexiones")
    if (ports.rxOpen && ports.txAvailable && !ports.txOpen && ports.scope != MirrorScope.NONE) {
        Action("Reabrir puertos MIDI", Glyph.REFRESH) { app.reopenPorts() }
    }
    Note("RX ${stats.midiMessages} mensajes · ${stats.bytesPerSecond} B/s · ${stats.clockMessages} Clock F8")
    Note("TX ${stats.txMessages} mensajes · ${stats.txBytesPerSecond} B/s · ${stats.requests} solicitudes · ${stats.replies} respuestas")
    Note("${stats.retries} reintentos de pantalla · ${stats.bad} paquetes inválidos · ${stats.discardedPackets} descartados · ${stats.lostBytes} B perdidos")
    stats.lastSysExTx?.let { Note("Último SysEx TX · " + it.take(160)) }
    stats.lastSysExRx?.let { Note("Último SysEx RX · " + it.take(160)) }
    Note(app.controlBlocked?.let { "Control bloqueado · $it" } ?: "Panel fm1-base disponible · SAVE reservado al FM-1")

    Caption("Rendimiento gráfico")
    Note("Modo gráfico · ${app.presentation.title}")
    val depth = app.rxDepth
    Note("Cola RX ${depth.chunks} fragmentos / ${depth.bytes} B · pico ${depth.peakChunks} / ${depth.peakBytes} B")
    Note("${app.uiDiscardedFrames} presentaciones omitidas · ${app.transferBytes} B transferidos por regiones")
    val load = app.androidLoad
    Note("CPU Android ${load.cpuPercent?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—"} % · heap JVM ${load.javaHeapBytes / 1024} KiB · nativo ${load.nativeHeapBytes / 1024} KiB")
    Note("Asignación ${load.allocatedBytesPerSecond?.let { "${it / 1024} KiB/s" } ?: "—"} · GC ${load.gcCount ?: "—"} ciclos / ${load.gcMillis ?: "—"} ms")
    if (app.performanceStages.isEmpty()) Note("Latencia interna sin muestras de MIDI o LCD")
    app.performanceStages.forEach { (stage, sample) ->
        Note(String.format(Locale.ROOT, "%s · P50 %.3f · P95 %.3f · P99 %.3f ms · n=%d",
            stage.title, sample.p50Ms, sample.p95Ms, sample.p99Ms, sample.count))
    }
    Note(stats.decodingMs?.let { "Último mensaje decodificado en ${String.format(Locale.ROOT, "%.3f", it)} ms" }
        ?: "Decodificación sin muestra")
    Note(stats.processingMs?.let { "Recepción → framebuffer ${String.format(Locale.ROOT, "%.2f", it)} ms" }
        ?: "Procesamiento local sin muestra")
}

@Composable
private fun MidiConsole(app: MainActivity) {
    var filter by rememberSaveable { mutableStateOf<MidiCategory?>(null) }
    ChoiceMenu("Filtro MIDI", filter?.title ?: "Todo", listOf<MidiCategory?>(null) + MidiCategory.entries,
        { it?.title ?: "Todo" }) { filter = it }
    app.logs.asReversed().filter { filter == null || it.category == filter }.forEach {
        Text(it.line, color = DrawerColors.current.secondary.color, fontFamily = RfmFonts.mono, fontSize = 10.sp)
    }
}
