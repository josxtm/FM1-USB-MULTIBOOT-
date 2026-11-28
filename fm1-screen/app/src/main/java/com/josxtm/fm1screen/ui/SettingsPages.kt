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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.josxtm.fm1screen.BuildConfig
import com.josxtm.fm1screen.MainActivity
import com.josxtm.fm1screen.data.FirmwareProfile
import com.josxtm.fm1screen.performance.PresentationMode
import com.josxtm.fm1screen.protocol.Wire
import com.josxtm.fm1screen.transport.LinkKind
import com.josxtm.fm1screen.ui.faceplate.FaceplateUi
import com.josxtm.fm1screen.ui.faceplate.Scaling
import com.josxtm.fm1screen.ui.theme.color

@Composable
internal fun ConnectionSettings(app: MainActivity) {
    val colors = DrawerColors.current
    Row(Modifier.padding(start = 7.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape)
            .background(if (app.connected) colors.accent.color else colors.secondary.color))
        Note(app.connectionText, colors.text.color)
    }
    ChoiceMenu("Transporte", app.link.title, LinkKind.entries, LinkKind::title, choose = app::selectLink)
    if (app.deviceList.isNotEmpty()) {
        val selected = app.deviceList.firstOrNull { app.connected && app.connectionText.substringBefore(" · ") == it.name }
        ChoiceMenu("Dispositivo MIDI", selected?.name ?: "Seleccionar dispositivo", app.deviceList, { it.name },
            choose = app::selectTarget)
    }
    if (app.link == LinkKind.BLE) Action("Buscar BLE MIDI", Glyph.SEARCH, click = app::scanBluetooth)
    val blocked = app.requestBlocked
    Note(app.profile?.let { "Perfil: ${it.name} · ${it.version}" } ?: "Firmware sin identificar")
    Note(if (app.streaming) "Duplicación solicitada" else "Duplicación desactivada")
    Disclosure("Conexión avanzada") {
        Toggle("Solicitar duplicación", app.streaming, app.streaming || blocked == null, app::setWatching)
        Toggle("Solo visualización", app.viewOnly, !app.viewOnly || app.canAuthorizeControl, app::changeViewOnly)
    }
    Action("Resincronizar pantalla", Glyph.REFRESH, enabled = app.streaming && blocked == null, click = app::refreshPicture)
    if (blocked != null && (app.connected || app.demo)) Note(blocked)
}

@Composable
internal fun FirmwareSettings(app: MainActivity) {
    val profile = app.profile
    Disclosure("Selección manual y protocolo") {
    ChoiceMenu("Selección de firmware", if (app.autoProfile) "Automática" else profile?.name ?: "Sin perfil",
        listOf<FirmwareProfile?>(null) + app.profiles, { it?.name ?: "Automática" }, choose = app::selectProfile)
    if (profile != null) Note("${profile.name} · ${profile.version}")
    ChoiceMenu("Protocolo", app.protocol.title, Wire.entries, Wire::title, choose = app::selectProtocol)
    Row(Modifier.fillMaxWidth()) {
        Action("Importar perfil", Glyph.IMPORT, Modifier.weight(1f), click = app::importProfile)
        Action("Exportar perfil", Glyph.EXPORT, Modifier.weight(1f), enabled = profile != null, click = app::exportProfile)
    }
    }
    if (profile != null) Disclosure("Detalles del perfil") {
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
    Toggle("Panel dinámico", app.dynamicPanel, change = app::selectDynamicPanel)
    Note(if (app.dynamicPanel) "Estados reales cuando el firmware los transmite" else "Estático · prioridad LCD")
    Toggle("Modo demostración", app.demo, change = app::changeDemo)
    ChoiceMenu("Escalado", app.scaling.title, Scaling.entries, Scaling::title) { scaling ->
        app.selectScaling(scaling)
        if (scaling == Scaling.ZOOM) {
            app.faceplate.displayAlone = true
            close()
        }
    }
    Action("Pantalla completa", Glyph.MAXIMIZE) {
        app.faceplate.displayAlone = true
        close()
    }
    Disclosure("Captura y energía") {
        Toggle("Mantener pantalla encendida", app.stayAwake, change = app::keepScreen)
        Row(Modifier.fillMaxWidth()) {
            Action("Guardar PNG", Glyph.EXPORT, Modifier.weight(1f), enabled = app.hasImage, click = app::png)
            Action(if (app.recordingActive) "Detener MP4" else "Grabar MP4", Glyph.SCREEN, Modifier.weight(1f),
                enabled = app.recordingActive || (app.hasImage && !app.demo), click = app::record)
        }
        if (app.videoReady) Action("Guardar MP4", Glyph.EXPORT, click = app::exportVideo)
    }
}

@Composable
internal fun PerformanceSettings(app: MainActivity) {
    val glyphs = listOf(Glyph.BOLT, Glyph.BALANCE, Glyph.LEAF)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        PresentationMode.entries.forEachIndexed { index, mode ->
            Tile(mode.title, glyphs[index], app.presentation == mode, Modifier.weight(1f)) {
                app.selectPresentation(mode)
            }
        }
    }
    val behavior = when (app.presentation) {
        PresentationMode.ULTRA -> "Respuesta inmediata · sin suavizado"
        PresentationMode.BALANCED -> "Fluidez y consumo equilibrados"
        PresentationMode.ECO -> "Menos dibujos · menor consumo"
    }
    Note(behavior, modifier = Modifier.padding(7.dp))
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
