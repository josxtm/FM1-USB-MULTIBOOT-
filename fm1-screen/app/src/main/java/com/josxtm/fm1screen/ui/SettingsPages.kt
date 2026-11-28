// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.josxtm.fm1screen.BuildConfig
import com.josxtm.fm1screen.MainActivity
import com.josxtm.fm1screen.capture.CaptureKind
import com.josxtm.fm1screen.data.FirmwareProfile
import com.josxtm.fm1screen.performance.HealthLevel
import com.josxtm.fm1screen.performance.PresentationMode
import com.josxtm.fm1screen.protocol.Wire
import com.josxtm.fm1screen.transport.LinkKind
import com.josxtm.fm1screen.transport.MirrorScope
import com.josxtm.fm1screen.transport.StreamState
import com.josxtm.fm1screen.ui.faceplate.FaceplateUi
import com.josxtm.fm1screen.ui.faceplate.Scaling
import com.josxtm.fm1screen.ui.theme.SettingsColors
import com.josxtm.fm1screen.ui.theme.Theme
import com.josxtm.fm1screen.ui.theme.color

@Composable
internal fun Status(level: HealthLevel, label: String) {
    val tint = when (level) {
        HealthLevel.OK -> Color(0xFF2EA66B)
        HealthLevel.WARNING -> Color(0xFFDAAB32)
        HealthLevel.ERROR -> Color(0xFFE36267)
        HealthLevel.UNKNOWN -> DrawerColors.current.secondary.color
    }
    Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
        Note(label, DrawerColors.current.text.color)
    }
}

@Composable
internal fun ConnectionSettings(app: MainActivity) {
    val stats = app.mirrorStats
    val level = when {
        stats.state == StreamState.NO_RESPONSE -> HealthLevel.ERROR
        app.connected && (stats.state == StreamState.STARTING || stats.state == StreamState.RECOVERING ||
            stats.state == StreamState.BLOCKED || !app.midiPorts.txOpen) -> HealthLevel.WARNING
        app.connected -> HealthLevel.OK
        else -> HealthLevel.UNKNOWN
    }
    Status(level, app.connectionText)
    ChoiceMenu("Transporte", app.link.title, LinkKind.entries, LinkKind::title, choose = app::selectLink)
    if (app.deviceList.isNotEmpty()) {
        val selected = app.deviceList.firstOrNull { app.connected && app.connectionText.substringBefore(" · ") == it.name }
        ChoiceMenu("Dispositivo MIDI", selected?.name ?: "Seleccionar dispositivo", app.deviceList, { it.name },
            choose = app::selectTarget)
    }
    if (app.link == LinkKind.BLE) Action("Buscar BLE MIDI", Glyph.SEARCH, click = app::scanBluetooth)
    val identity = stats.identity
    val firmware = when {
        identity.atlatl -> "SKIFF Atlatl ${identity.version.orEmpty()}".trim()
        identity.reportedName != null -> identity.reportedName.orEmpty()
        else -> null
    }
    Note(firmware?.let { "Identificación · $it" } ?: "Firmware sin identificar")
    Note(stats.wire?.let { "Protocolo recibido · ${it.title}" } ?: "Protocolo sin confirmar")
    Note(if (app.streaming) "Duplicación · ${stats.state.title}" else "Duplicación desactivada")
    val blocked = app.requestBlocked
    Action("Resincronizar pantalla", Glyph.REFRESH, enabled = app.streaming && blocked == null, click = app::refreshPicture)
    Disclosure("Conexión avanzada") {
        Toggle("Solicitar duplicación", app.streaming, app.streaming || blocked == null, app::setWatching)
        blocked?.let { Note(it) }
        Toggle("Solo visualización", app.viewOnly, !app.viewOnly || app.canAuthorizeControl, app::changeViewOnly)
        app.controlBlocked?.takeIf { it != blocked }?.let { Note(it) }
        val ports = app.midiPorts
        if (ports.rxOpen && ports.txAvailable && !ports.txOpen && ports.scope != MirrorScope.NONE) {
            Action("Reabrir puertos MIDI", Glyph.REFRESH, click = app::reopenPorts)
        }
        FirmwareSettings(app)
    }
}

@Composable
private fun FirmwareSettings(app: MainActivity) {
    val profile = app.profile
    ChoiceMenu("Perfil de firmware", if (app.autoProfile) "Automático" else profile?.name ?: "Sin perfil",
        listOf<FirmwareProfile?>(null) + app.profiles, { it?.name ?: "Automático" }, choose = app::selectProfile)
    if (profile != null) Note("Perfil aplicado · ${profile.name} · ${profile.version}")
    ChoiceMenu("Protocolo manual", app.protocol.title, Wire.entries, Wire::title, choose = app::selectProtocol)
    Row(Modifier.fillMaxWidth()) {
        Action("Importar perfil", Glyph.IMPORT, Modifier.weight(1f), click = app::importProfile)
        Action("Exportar perfil", Glyph.EXPORT, Modifier.weight(1f), enabled = profile != null, click = app::exportProfile)
    }
    if (profile != null) Disclosure("Compatibilidad del perfil") {
        Note(profile.evidence)
        Note(profile.limits)
        Note(profile.source)
    }
}

@Composable
internal fun ScreenSettings(app: MainActivity, close: () -> Unit) {
    val labels = listOf("FULL CONTROL", "SCREEN + CONTROL", "BIG SCREEN")
    val glyphs = listOf(Glyph.FULL_VIEW, Glyph.CONTROLS_VIEW, Glyph.BIG_VIEW)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FaceplateUi.Page.entries.forEachIndexed { index, page ->
            Tile(labels[index], glyphs[index], app.faceplate.page == page, Modifier.weight(1f)) {
                app.faceplate.page = page
            }
        }
    }
    Caption("FM1 LIVE")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Tile("ESTÁTICO", Glyph.PANEL, !app.dynamicPanel, Modifier.weight(1f)) { app.selectDynamicPanel(false) }
        Tile("DINÁMICO", Glyph.ACTIVITY, app.dynamicPanel, Modifier.weight(1f)) { app.selectDynamicPanel(true) }
    }
    if (app.dynamicPanel && !app.hasPanelTelemetry) Note("Telemetría física sin confirmar")
    Action("Pantalla completa", Glyph.MAXIMIZE) {
        app.faceplate.displayAlone = true
        close()
    }
    Disclosure("Capturas y grabación") {
        CaptureActions(app)
        Action(if (app.recordingActive) "Detener MP4" else "Grabar MP4", Glyph.SCREEN,
            enabled = app.recordingActive || app.hasImage, click = app::record)
        if (app.videoReady) Row(Modifier.fillMaxWidth()) {
            Action("Guardar MP4", Glyph.EXPORT, Modifier.weight(1f)) { app.exportVideo() }
            Action("Compartir MP4", Glyph.LINK, Modifier.weight(1f)) { app.exportVideo(true) }
        }
    }
    Disclosure("Vista avanzada") {
        ChoiceMenu("Escalado", app.scaling.title, Scaling.entries, Scaling::title) { scaling ->
            app.selectScaling(scaling)
            if (scaling == Scaling.ZOOM) {
                app.faceplate.displayAlone = true
                close()
            }
        }
        Toggle("Mantener pantalla encendida al duplicar", app.stayAwake, change = app::keepScreen)
        Caption("Rendimiento")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val performanceGlyphs = listOf(Glyph.BOLT, Glyph.BALANCE, Glyph.LEAF)
            PresentationMode.entries.forEachIndexed { index, mode ->
                Tile(mode.title, performanceGlyphs[index], app.presentation == mode, Modifier.weight(1f)) {
                    app.selectPresentation(mode)
                }
            }
        }
    }
}

@Composable
private fun CaptureActions(app: MainActivity) {
    CaptureKind.entries.forEach { kind ->
        val available = app.hasImage || kind == CaptureKind.INTERFACE
        Caption(kind.title)
        Row(Modifier.fillMaxWidth()) {
            Action("Guardar PNG", Glyph.EXPORT, Modifier.weight(1f), enabled = available) { app.png(kind) }
            Action("Compartir PNG", Glyph.LINK, Modifier.weight(1f), enabled = available) { app.png(kind, true) }
        }
    }
}

@Composable
internal fun QuickCapture(app: MainActivity, theme: Theme) {
    var open by remember { mutableStateOf(false) }
    val colors = remember(theme) { SettingsColors(theme) }
    Box {
        IconAction("Capturas", Glyph.CAMERA, colors.gear.color) { open = true }
        DropdownMenu(open, { open = false }, containerColor = colors.panel.color, tonalElevation = 0.dp) {
            CompositionLocalProvider(DrawerColors provides colors) {
                CaptureKind.entries.forEach { kind ->
                    DropdownMenuItem(text = { Note("Guardar ${kind.title}", colors.text.color) },
                        enabled = app.hasImage || kind == CaptureKind.INTERFACE,
                        onClick = { open = false; app.png(kind) })
                }
            }
        }
    }
}

@Composable
internal fun AboutSettings() {
    val context = LocalContext.current
    val repository = "https://github.com/josxtm/FM1-USB-MULTIBOOT-"
    val module = "$repository/blob/feat/fm1-screen-android/fm1-screen"
    fun open(uri: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
    }
    Caption("FM1 SCREEN v1.0 · comp. %03d".format(BuildConfig.COMPILATION))
    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = DrawerColors.current.divider.color)
    Action("Repositorio", Glyph.LINK) { open(repository) }
    Action("Licencias", Glyph.FILE) { open("$module/docs/LICENCIAS-Y-ATRIBUCIONES.md") }
    Disclosure("Créditos") {
        Note("Panel adaptado de RemoteFM1Android. FM1 SCREEN se distribuye bajo GNU GPL v3, sin garantía.")
    }
}
