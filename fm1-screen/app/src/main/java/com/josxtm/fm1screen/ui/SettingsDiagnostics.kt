// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.ui

import android.os.PowerManager
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.josxtm.fm1screen.MainActivity
import com.josxtm.fm1screen.performance.HealthLevel
import com.josxtm.fm1screen.performance.Stage
import com.josxtm.fm1screen.performance.healthLevel
import com.josxtm.fm1screen.protocol.SIDE
import com.josxtm.fm1screen.protocol.Wire
import com.josxtm.fm1screen.transport.MidiCategory
import com.josxtm.fm1screen.transport.StreamState
import com.josxtm.fm1screen.ui.theme.RfmFonts
import com.josxtm.fm1screen.ui.theme.color
import java.util.Locale

@Composable
internal fun DiagnosticsSettings(app: MainActivity) {
    DisposableEffect(app) {
        app.observeDiagnostics(true)
        onDispose { app.observeDiagnostics(false) }
    }
    val stats = app.mirrorStats
    val ports = app.midiPorts
    val latency = app.performanceStages[Stage.INTERNAL_LATEST]
    val cpu = app.androidLoad.normalizedCpuPercent
    val queue = app.queueOccupancy.takeIf { ports.rxOpen }
    Status(when {
        stats.state == StreamState.NO_RESPONSE -> HealthLevel.ERROR
        stats.state in listOf(StreamState.STARTING, StreamState.RECOVERING, StreamState.BLOCKED) -> HealthLevel.WARNING
        ports.rxOpen -> HealthLevel.OK
        else -> HealthLevel.UNKNOWN
    }, stats.state.title)
    Note("RX ${if (ports.rxOpen) "abierto" else "cerrado"} · TX ${if (ports.txOpen) "abierto" else "cerrado"}")
    Status(healthLevel(latency?.p95Ms, 20.0, 50.0), "Latencia interna P95 · ${latency?.p95Ms?.let { "${decimal(it)} ms" } ?: "sin muestras"}")
    Status(healthLevel(cpu, 40.0, 70.0), "CPU normalizada · ${percent(cpu)}")
    Status(healthLevel(queue, 25.0, 75.0), "Ocupación RX · ${percent(queue)}")

    val remaining = app.analysisRemaining
    if (remaining == null) Action("Analizar 30 segundos", Glyph.ACTIVITY, enabled = app.canAnalyze, click = app::startAnalysis)
    else {
        Note("Observación en curso · $remaining s")
        Action("Cancelar análisis", Glyph.CLOSE, click = app::cancelAnalysis)
    }
    app.analysisFinding?.let {
        Caption(it.action)
        Note(it.reason)
    }

    Disclosure("Comunicaciones") {
        val identity = stats.identity
        val firmware = if (identity.atlatl) "SKIFF Atlatl ${identity.version.orEmpty()}" else identity.reportedName
        Note("${app.link.title} · ${stats.wire?.title ?: "Protocolo sin confirmar"}")
        Note("Identificación · ${firmware?.trim() ?: "desconocida"} · ${identity.packageId ?: "sin identificador de paquete"}")
        app.profile?.let { Note("Perfil aplicado · ${it.name} · ${it.version}") }
        app.deviceList.forEach { Note(listOf(it.name, it.details).filter(String::isNotBlank).joinToString(" · ")) }
        Note("RX ${stats.midiMessages} mensajes · ${stats.bytesPerSecond} B/s · ${stats.clockMessages} Clock F8")
        Note("TX ${stats.txMessages} mensajes · ${stats.txBytesPerSecond} B/s · ${stats.requests} solicitudes · ${stats.replies} respuestas")
        stats.lastSysExTx?.let { Note("Último SysEx TX · " + it.take(160)) }
        stats.lastSysExRx?.let { Note("Último SysEx RX · " + it.take(160)) }
        app.controlBlocked?.let { Note("Control bloqueado · $it") }
    }
    Disclosure("Motor gráfico") {
        Note("Modo · ${app.presentation.title}")
        Note("Recibidos ${stats.patchesPerSecond} bloques/s · publicados ${stats.displaysPerSecond}/s · dibujados ${app.rendersPerSecond}/s")
        if (stats.wire == Wire.BIG2) Note("SYNC recibidos · ${stats.syncsPerSecond} fotogramas/s")
        else Note("FPS recibidos · sin marca de fotograma de origen")
        Note("${stats.blocks} bloques · ${stats.fullFrames} coberturas completas · ${stats.coverage * 100 / (SIDE * SIDE)} % del LCD")
        Note("${app.uiDiscardedFrames} presentaciones omitidas · ${app.transferBytes} B transferidos por regiones")
        Note(stats.sinceGraphicsMs?.let { "Último bloque hace ${it / 1000.0} s" } ?: "Sin bloques gráficos")
    }
    Disclosure("Latencia") {
        Note("Tiempos internos · del callback MIDI al registro de dibujo Android")
        if (app.performanceStages.isEmpty()) Note("Latencia interna sin muestras de MIDI o LCD")
        app.performanceStages.forEach { (stage, sample) ->
            Note("${stage.title} · P50 ${decimal(sample.p50Ms)} · P95 ${decimal(sample.p95Ms)} · P99 ${decimal(sample.p99Ms)} ms · n=${sample.count}")
        }
        stats.decodingMs?.let { Note("Última decodificación · ${decimal(it)} ms") }
        stats.processingMs?.let { Note("Recepción al framebuffer · ${decimal(it)} ms") }
    }
    Disclosure("Android") {
        val load = app.androidLoad
        Note("CPU por núcleo · ${percent(load.cpuPercent)} · normalizada · ${percent(load.normalizedCpuPercent)}")
        Note("Heap JVM ${load.javaHeapBytes / 1024} KiB · nativo ${load.nativeHeapBytes / 1024} KiB")
        Note("Asignación ${load.allocatedBytesPerSecond?.let { "${it / 1024} KiB/s" } ?: "—"} · GC ${load.gcCount ?: "—"} ciclos / ${load.gcMillis ?: "—"} ms")
        val thermal = app.thermalStatus
        val level = when {
            thermal == null -> HealthLevel.UNKNOWN
            thermal >= PowerManager.THERMAL_STATUS_SEVERE -> HealthLevel.ERROR
            thermal > PowerManager.THERMAL_STATUS_NONE -> HealthLevel.WARNING
            else -> HealthLevel.OK
        }
        val thermalLabel = when (thermal) {
            0 -> "normal"; 1 -> "leve"; 2 -> "moderado"; 3 -> "severo"; 4 -> "crítico"
            5 -> "emergencia"; 6 -> "apagado"; else -> "no disponible"
        }
        Status(level, "Estado térmico · $thermalLabel")
    }
    Disclosure("Buffers") {
        val depth = app.rxDepth
        Note("RX ${depth.chunks} fragmentos / ${depth.bytes} B · pico ${depth.peakChunks} / ${depth.peakBytes} B")
        Note("${stats.lostBytes} B descartados en la cola · ${stats.discardedPackets} mensajes descartados")
        if (stats.wire == Wire.BASE) Note("${stats.gaps} bloques ausentes según secuencia")
        else Note("Pérdida de bloques · no medible sin secuencia")
    }
    Disclosure("Estabilidad") {
        Note("Duración · ${stats.sessionMillis?.let { "${it / 1_000} s" } ?: "sin sesión"}")
        Note("Último envío de inicio o renovación · ${stats.sinceRenewalMs?.let { "$it ms" } ?: "sin dato"}")
        Note("Última señal de sesión · ${stats.sinceSessionMs?.let { "$it ms" } ?: "sin dato"}")
        Note("${ports.reconnections} reconexiones · ${stats.manualResync} resincronizaciones manuales · ${stats.retries} reintentos")
        Note("${stats.bad} tramas gráficas SysEx inválidas")
    }
    Disclosure("Consola técnica") { MidiConsole(app) }
    Disclosure("Exportación de informe") {
        Row(Modifier.fillMaxWidth()) {
            Action("Exportar registros", Glyph.EXPORT, Modifier.weight(1f), click = app::exportLogs)
            Action("Compartir diagnóstico", Glyph.LINK, Modifier.weight(1f), click = app::shareLogs)
        }
    }
}

private fun percent(value: Double?) = value?.let { "${decimal(it)} %" } ?: "sin datos"
private fun decimal(value: Double) = String.format(Locale.ROOT, "%.2f", value)

@Composable
private fun MidiConsole(app: MainActivity) {
    var filter by rememberSaveable { mutableStateOf<MidiCategory?>(null) }
    var limit by rememberSaveable { mutableStateOf(30) }
    ChoiceMenu("Filtro MIDI", filter?.title ?: "Todo", listOf<MidiCategory?>(null) + MidiCategory.entries,
        { it?.title ?: "Todo" }) { filter = it; limit = 30 }
    val entries = app.logs.asReversed().filter { filter == null || it.category == filter }
    entries.take(limit).forEach {
        Text(it.line, color = DrawerColors.current.secondary.color, fontFamily = RfmFonts.mono, fontSize = 10.sp)
    }
    if (entries.size > limit) Action("Mostrar más") { limit += 30 }
}
