// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.ui

import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.josxtm.fm1screen.BuildConfig
import com.josxtm.fm1screen.MainActivity
import com.josxtm.fm1screen.data.ColorRole
import com.josxtm.fm1screen.performance.PresentationMode
import com.josxtm.fm1screen.protocol.SIDE
import com.josxtm.fm1screen.protocol.Wire
import com.josxtm.fm1screen.transport.LinkKind
import com.josxtm.fm1screen.transport.MidiCategory
import com.josxtm.fm1screen.transport.MirrorScope
import com.josxtm.fm1screen.ui.faceplate.FaceplateUi
import com.josxtm.fm1screen.ui.faceplate.Scaling
import com.josxtm.fm1screen.ui.theme.Palette
import com.josxtm.fm1screen.ui.theme.RfmFonts
import com.josxtm.fm1screen.ui.theme.Rgb
import com.josxtm.fm1screen.ui.theme.Theme
import com.josxtm.fm1screen.ui.theme.color
import java.util.Locale
import kotlin.math.roundToInt

private enum class Tab(val title: String) { LINK("Conexión"), DISPLAY("Vista"), COLOR("Colores"), LOG("Diagnóstico") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Settings(app: MainActivity) {
    var tab by remember { mutableStateOf(Tab.LINK) }
    fun close() {
        app.faceplate.sheetOpen = false
        app.clearMessage()
    }
    BackHandler { close() }
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(Modifier.fillMaxSize().clickable { close() })
        val sheetWidth = if (maxWidth > 760.dp) 450.dp else if (maxWidth > 480.dp) 420.dp else maxWidth
        Column(Modifier.align(Alignment.CenterEnd).width(sheetWidth).fillMaxHeight()
            .background(Palette.sheetBackground).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)
            .testTag("settings")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("FM1 SCREEN", color = Palette.sheetText, fontFamily = RfmFonts.title, fontSize = 17.sp,
                    modifier = Modifier.weight(1f))
                Action("Cerrar", onClick = ::close)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Tab.entries.forEach { page -> Action(page.title, tab == page) { tab = page } }
            }
            HorizontalDivider(color = Palette.sheetLine)
            key(tab) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.height(4.dp))
                    when (tab) {
                        Tab.LINK -> LinkSettings(app)
                        Tab.DISPLAY -> DisplaySettings(app, ::close)
                        Tab.COLOR -> ThemeSettings(app)
                        Tab.LOG -> Diagnostics(app)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            app.message?.let { Label(it, Palette.sheetAccent) }
            Label("FM1 SCREEN v1.0 · comp. %03d".format(BuildConfig.COMPILATION), Palette.sheetMuted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkSettings(app: MainActivity) {
    Label(app.connectionText, Palette.sheetAccent)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LinkKind.entries.forEach { kind -> Action(kind.title, app.link == kind) { app.selectLink(kind) } }
        if (app.link == LinkKind.BLE) Action("Buscar BLE MIDI") { app.scanBluetooth() }
    }
    for (target in app.deviceList) {
        TextButton(onClick = { app.selectTarget(target) }) {
            Column {
                Label(target.name)
                Label(target.details, Palette.sheetMuted)
                if (!target.recognized) Label("Escucha pasiva", Palette.sheetMuted)
            }
        }
    }
    Toggle("Solicitar duplicación", app.streaming, app.streaming || app.requestBlocked == null, app::setWatching)
    app.requestBlocked?.let { Label(it, Palette.sheetMuted) }
    val ports = app.midiPorts
    if (ports.rxOpen && ports.txAvailable && !ports.txOpen && ports.scope != MirrorScope.NONE) {
        Action("Reabrir puertos MIDI") { app.reopenPorts() }
    }
    if (app.streaming && app.requestBlocked == null) Action("Resincronizar pantalla") { app.refreshPicture() }
    Toggle("Solo visualización", app.viewOnly, !app.viewOnly || app.canAuthorizeControl, app::changeViewOnly)
    Label(app.controlBlocked ?: "Panel fm1-base disponible · SAVE reservado al FM-1", Palette.sheetMuted)
    Section("Protocolo")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Wire.entries.forEach { wire -> Action(wire.title, app.protocol == wire) { app.selectProtocol(wire) } }
    }
    Section("Firmware")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Action("Auto", app.autoProfile) { app.selectProfile(null) }
        app.profiles.forEach { p -> Action(p.name, !app.autoProfile && app.profile?.id == p.id) { app.selectProfile(p) } }
    }
    app.profile?.let {
        Label("${it.name} · ${it.version}")
        Label(it.limits, Palette.sheetMuted)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Action("Importar perfil") { app.importProfile() }
        if (app.profile != null) Action("Exportar perfil") { app.exportProfile() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplaySettings(app: MainActivity, close: () -> Unit) {
    Section("Vista")
    Toggle("Modo demostración", app.demo, change = app::changeDemo)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("FULL CONTROL", "SCREEN + CONTROL", "BIG SCREEN").forEachIndexed { i, name ->
            val page = FaceplateUi.Page.entries[i]
            Action(name, app.faceplate.page == page) { app.faceplate.page = page; close() }
        }
    }
    Action("Pantalla inmersiva") { app.faceplate.displayAlone = true; close() }
    Section("Escalado")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Scaling.entries.forEach { scaling ->
            Action(scaling.title, app.scaling == scaling) {
                app.selectScaling(scaling)
                if (scaling == Scaling.ZOOM) {
                    app.faceplate.displayAlone = true
                    close()
                }
            }
        }
    }
    Section("Rendimiento gráfico")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PresentationMode.entries.forEach { mode ->
            Action(mode.title, app.presentation == mode) { app.selectPresentation(mode) }
        }
    }
    Toggle("Mantener pantalla encendida", app.stayAwake, change = app::keepScreen)
    Section("Captura de pantalla")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Action("Guardar PNG", enabled = app.hasImage) { app.png() }
        Action(if (app.recordingActive) "Detener MP4" else "Grabar MP4",
            enabled = app.recordingActive || (app.hasImage && !app.demo)) { app.record() }
        if (app.videoReady) Action("Guardar MP4") { app.exportVideo() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSettings(app: MainActivity) {
    var role by remember { mutableStateOf(ColorRole.BODY) }
    var sourceName by remember { mutableStateOf(app.theme?.name.orEmpty()) }
    var name by remember { mutableStateOf(app.theme?.name?.takeIf(app::isCustomTheme) ?: "") }
    val theme = app.theme ?: app.originalTheme
    var hex by remember { mutableStateOf(role.get(theme).hex) }
    fun apply(c: Rgb) { app.previewTheme(role.set(theme, c)) }
    fun select(next: Theme) {
        app.selectTheme(next)
        sourceName = next.name
        name = next.name.takeIf(app::isCustomTheme).orEmpty()
        hex = role.get(next).hex
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        app.themes.forEach { t -> Action(t.name, theme.name == t.name) { select(t) } }
    }
    Section("Editar un color")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ColorRole.entries.forEach { next ->
            Action(next.label, role == next) { role = next; hex = role.get(theme).hex }
        }
    }
    val selected = role.get(theme)
    key(role, sourceName) { ColorPicker(selected) { color -> hex = color.hex; apply(color) } }
    val swatches = listOf(app.originalTheme.body, app.originalTheme.key, app.originalTheme.knob,
        app.originalTheme.mark) + app.themes.take(9).map { it.body }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        swatches.distinct().forEach { color ->
            Box(Modifier.size(29.dp).background(color.color, RoundedCornerShape(6.dp))
                .border(1.dp, Palette.sheetMuted, RoundedCornerShape(6.dp))
                .semantics { contentDescription = color.hex }
                .clickable { hex = color.hex; apply(color) })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(hex, {
            if (it.length <= 7) {
                hex = it
                Rgb.hex(it)?.let(::apply)
            }
        }, label = { Text("HEX") }, singleLine = true, modifier = Modifier.weight(1f)
            .testTag("color-hex"))
        Action("Restaurar color") {
            val original = role.reset(theme, app.originalTheme)
            hex = role.get(original).hex
            app.previewTheme(original)
        }
    }
    if (minOf(theme.body.contrast(theme.label), theme.key.contrast(theme.padText), theme.padActive.contrast(theme.padText), theme.button.contrast(theme.keyText)) < 4.5) {
        Label("Contraste bajo en alguna etiqueta", Palette.sheetAccent)
    }
    OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nombre del tema") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Action("Guardar tema", enabled = name.isNotBlank()) {
            app.saveTheme(theme.copy(name = name))
            sourceName = app.theme?.name.orEmpty()
        }
        if (app.isCustomTheme(sourceName)) {
            Action("Renombrar", enabled = name.isNotBlank() && name != sourceName) {
                app.saveTheme(theme.copy(name = name), sourceName)
                sourceName = app.theme?.name.orEmpty()
            }
            Action("Duplicar") {
                val stem = "${sourceName.take(28)} copia"
                val names = app.themes.map(Theme::name).toSet()
                val copy = generateSequence(1) { it + 1 }.map { i ->
                    if (i == 1) stem else "$stem $i"
                }.first { it !in names }
                app.saveTheme(theme.copy(name = copy))
                sourceName = app.theme?.name.orEmpty()
                name = sourceName
            }
            Action("Eliminar") { app.themes.firstOrNull { it.name == sourceName }?.let(app::removeTheme); select(app.originalTheme) }
        }
        Action("Importar JSON") { app.importTheme() }
        Action("Exportar JSON") { app.exportTheme(theme) }
        Action("Restaurar ORIGINAL MINT") { select(app.originalTheme) }
    }
}

@Composable
private fun ColorPicker(color: Rgb, onColor: (Rgb) -> Unit) {
    val hsv = remember(color) { FloatArray(3).apply { AndroidColor.colorToHSV(color.argb, this) } }
    var hue by remember { mutableStateOf(hsv[0]) }
    val latestHue by rememberUpdatedState(hue)
    val latestColor by rememberUpdatedState(onColor)
    fun pick(point: Offset, width: Int, height: Int) {
        val s = (point.x / width).coerceIn(0f, 1f)
        val v = (1 - point.y / height).coerceIn(0f, 1f)
        latestColor(Rgb.argb(AndroidColor.HSVToColor(floatArrayOf(latestHue, s, v))))
    }
    Canvas(Modifier.fillMaxWidth().height(112.dp).testTag("color-picker")
        .pointerInput(Unit) { detectTapGestures { pick(it, size.width, size.height) } }
        .pointerInput(Unit) { detectDragGestures { change, _ -> pick(change.position, size.width, size.height) } }) {
        val pure = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val center = Offset(size.width * hsv[1], size.height * (1 - hsv[2]))
        drawCircle(Color.Black, 6.dp.toPx(), center)
        drawCircle(color.color, 4.dp.toPx(), center)
    }
    Slider(value = hue, onValueChange = {
        hue = it
        onColor(Rgb.argb(AndroidColor.HSVToColor(floatArrayOf(hue, hsv[1], hsv[2]))))
    }, valueRange = 0f..359.9f, modifier = Modifier.semantics { contentDescription = "Tono" })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Diagnostics(app: MainActivity) {
    val stats = app.mirrorStats
    val coverage = (stats.coverage.toDouble() * 100 / (SIDE * SIDE)).roundToInt()
    var filter by remember { mutableStateOf<MidiCategory?>(null) }
    Section("Señal")
    Label("${stats.state.title} · ${stats.wire?.title ?: "Sin protocolo"} · $coverage % de cobertura")
    Label("${stats.patchesPerSecond} bloques/s · ${stats.displaysPerSecond} publicaciones/s · ${app.rendersPerSecond} dibujadas/s")
    if (stats.wire == Wire.BIG2) Label("${stats.syncsPerSecond} fotogramas SYNC/s")
    Label("${stats.blocks} bloques · ${stats.fullFrames} pantallas completas")
    if (stats.wire == Wire.BASE) Label("${stats.gaps} bloques ausentes según secuencia")
    if (stats.wire == Wire.BIG1) Label("Big Screen V1 no incluye secuencia ni límite de fotograma", Palette.sheetMuted)
    Label(stats.sinceGraphicsMs?.let { "Último bloque gráfico hace ${it / 1000.0} s" } ?: "Sin bloques gráficos")
    stats.sinceSessionMs?.let { Label("Última señal de sesión hace ${it / 1000.0} s", Palette.sheetMuted) }
    val firmware = stats.identity
    val identity = when {
        firmware.atlatl -> "SKIFF Atlatl ${firmware.version ?: ""}".trim()
        firmware.reportedName != null -> firmware.reportedName.orEmpty()
        app.profile != null -> app.profile?.name.orEmpty() + if (app.autoProfile) " · descriptor" else " · perfil manual"
        else -> "Firmware sin identificar"
    }
    Label("${app.link.title} · $identity${firmware.packageId?.let { " · $it" }.orEmpty()}")
    val ports = app.midiPorts
    Label("RX ${if (ports.rxOpen) "abierto" else "cerrado"} · TX ${if (ports.txOpen) "abierto" else "cerrado"} · ${ports.reconnections} reconexiones")
    Label("RX ${stats.midiMessages} mensajes · ${stats.bytesPerSecond} B/s · ${stats.clockMessages} Clock F8")
    Label("TX ${stats.txMessages} mensajes · ${stats.txBytesPerSecond} B/s · ${stats.requests} solicitudes · ${stats.replies} respuestas")
    Label("${stats.retries} reintentos de pantalla · ${stats.bad} paquetes inválidos · ${stats.discardedPackets} descartados · ${stats.lostBytes} B perdidos",
        Palette.sheetMuted)
    stats.lastSysExTx?.let { Label("Último SysEx TX · " + it.take(160), Palette.sheetMuted) }
    stats.lastSysExRx?.let { Label("Último SysEx RX · " + it.take(160), Palette.sheetMuted) }
    app.controlBlocked?.let { Label("Control bloqueado · $it", Palette.sheetMuted) }
    Section("Rendimiento gráfico")
    Label("Modo gráfico · ${app.presentation.title}")
    val depth = app.rxDepth
    Label("Cola RX ${depth.chunks} fragmentos / ${depth.bytes} B · pico ${depth.peakChunks} / ${depth.peakBytes} B")
    Label("${app.uiDiscardedFrames} presentaciones omitidas · ${app.transferBytes} B transferidos por regiones",
        Palette.sheetMuted)
    val load = app.androidLoad
    Label("CPU Android ${load.cpuPercent?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—"} % · heap JVM ${load.javaHeapBytes / 1024} KiB · nativo ${load.nativeHeapBytes / 1024} KiB")
    Label("Asignación ${load.allocatedBytesPerSecond?.let { "${it / 1024} KiB/s" } ?: "—"} · GC ${load.gcCount ?: "—"} ciclos / ${load.gcMillis ?: "—"} ms")
    if (app.performanceStages.isEmpty()) Label("Latencia interna sin muestras de MIDI o LCD", Palette.sheetMuted)
    app.performanceStages.forEach { (stage, stats) ->
        Label(String.format(Locale.ROOT, "%s · P50 %.3f · P95 %.3f · P99 %.3f ms · n=%d",
            stage.title, stats.p50Ms, stats.p95Ms, stats.p99Ms, stats.count), Palette.sheetMuted)
    }
    Label(stats.decodingMs?.let { "Último mensaje decodificado en ${String.format(Locale.ROOT, "%.3f", it)} ms" }
        ?: "Decodificación sin muestra", Palette.sheetMuted)
    Label(stats.processingMs?.let { "Recepción → framebuffer ${String.format(Locale.ROOT, "%.2f", it)} ms" }
        ?: "Procesamiento local sin muestra", Palette.sheetMuted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Action("Exportar registros") { app.exportLogs() }
        Action("Compartir diagnóstico") { app.shareLogs() }
    }
    Section("Consola MIDI / SysEx")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Action("Todo", filter == null) { filter = null }
        MidiCategory.entries.forEach { category ->
            Action(category.title, filter == category) { filter = category }
        }
    }
    app.logs.asReversed().filter { filter == null || it.category == filter }.forEach {
        Text(it.line, color = Palette.sheetMuted, fontFamily = RfmFonts.mono, fontSize = 10.sp)
    }
    Section("Créditos")
    Label("Panel adaptado de RemoteFM1Android. FM1 SCREEN se distribuye bajo GNU GPL v3, sin garantía.")
    Label("Código y licencias en josxtm/FM1-USB-MULTIBOOT-, rama feat/fm1-screen-android.")
}

@Composable
private fun Toggle(text: String, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f), color = Palette.sheetText, fontFamily = RfmFonts.ui, fontSize = 14.sp)
        Switch(checked, change, modifier = Modifier.semantics { contentDescription = text }, enabled = enabled)
    }
}

@Composable
private fun Action(text: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    if (active) Button(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { selected = true },
        colors = ButtonDefaults.buttonColors(
        containerColor = Palette.sheetAccent, contentColor = Palette.sheetBackground
    )) { Text(text, fontFamily = RfmFonts.ui, fontSize = 12.sp) }
    else TextButton(onClick = onClick, enabled = enabled, colors = ButtonDefaults.textButtonColors(
        contentColor = Palette.sheetText, disabledContentColor = Palette.sheetMuted.copy(alpha = 0.55f)
    )) { Text(text, fontFamily = RfmFonts.ui, fontSize = 12.sp) }
}

@Composable
private fun Section(text: String) = Text(text, color = Palette.sheetAccent, fontFamily = RfmFonts.hardware, fontSize = 13.sp)

@Composable
private fun Label(text: String, color: Color = Palette.sheetText) = Text(text, color = color, fontFamily = RfmFonts.ui, fontSize = 13.sp)
