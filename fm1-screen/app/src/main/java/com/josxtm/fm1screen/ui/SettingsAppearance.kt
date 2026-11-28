// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.josxtm.fm1screen.MainActivity
import com.josxtm.fm1screen.data.ColorRole
import com.josxtm.fm1screen.ui.theme.Rgb
import com.josxtm.fm1screen.ui.theme.Theme
import com.josxtm.fm1screen.ui.theme.color

@Composable
internal fun AppearanceSettings(app: MainActivity) {
    val theme = app.theme ?: app.originalTheme
    val colors = DrawerColors.current
    var role by remember { mutableStateOf(ColorRole.BODY) }
    var original by remember { mutableStateOf<Theme?>(null) }
    var hex by remember { mutableStateOf("") }
    var managingName by remember(theme.name) { mutableStateOf(theme.name.takeIf(app::isCustomTheme).orEmpty()) }
    var remove by remember(theme.name) { mutableStateOf(false) }
    fun cancel() {
        val previous = original
        original = null
        if (previous != null && app.theme?.name == "CUSTOM") app.previewTheme(previous)
    }
    DisposableEffect(app) { onDispose { cancel() } }
    fun choose(value: Theme) {
        cancel()
        app.selectTheme(value)
    }
    fun preview(color: Rgb) {
        hex = color.hex
        app.previewTheme(role.set(app.theme ?: theme, color))
    }

    val presets = app.themes.filterNot { app.isCustomTheme(it.name) }
    presets.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { preset ->
                ThemeTile(preset, theme.name == preset.name, Modifier.weight(1f)) { choose(preset) }
            }
            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
        }
    }
    if (app.themes.any { app.isCustomTheme(it.name) }) Disclosure("Temas guardados") {
        app.themes.filter { app.isCustomTheme(it.name) }.forEach { saved ->
            ThemeTile(saved, theme.name == saved.name, Modifier.fillMaxWidth(), showName = true) { choose(saved) }
        }
    }

    if (theme.name == "CUSTOM") {
        Caption("Colores personalizados")
        ColorRole.entries.chunked(5).forEach { roles ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                roles.forEach { option ->
                    val selected = original != null && role == option
                    Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) colors.selection.color else colors.raised.color)
                        .selectable(selected, role = Role.Button) {
                            cancel()
                            val current = app.theme ?: theme
                            role = option
                            original = current
                            hex = option.get(current).hex
                        }
                        .semantics { contentDescription = option.label }, contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            SettingsIcon(option.glyph(), if (selected) colors.onSelection.color else colors.text.color, 21.dp)
                            Sample(option.get(theme), 10.dp)
                        }
                    }
                }
            }
        }
        val before = original
        if (before != null) {
            Caption(role.label)
            key(role) { ColorPicker(role.get(theme), ::preview) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(hex, {
                    if (it.length <= 7) {
                        hex = it
                        Rgb.hex(it)?.let { color -> app.previewTheme(role.set(app.theme ?: theme, color)) }
                    }
                }, label = { Text("HEX") }, singleLine = true, modifier = Modifier.weight(1f).testTag("color-hex"))
                Action("Restaurar color", Glyph.REFRESH) {
                    val restored = role.reset(app.theme ?: theme, app.originalTheme)
                    hex = role.get(restored).hex
                    app.previewTheme(restored)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Action("Aceptar", Glyph.CHECK, Modifier.weight(1f), enabled = Rgb.hex(hex) != null) {
                    original = null
                    app.applyCustomTheme(before, app.theme ?: theme)
                }
                Action("Cancelar", Glyph.CLOSE, Modifier.weight(1f), click = ::cancel)
            }
            Disclosure("Muestras") {
                val mint = app.originalTheme
                val samples = (listOf(mint.body, mint.key, mint.knob, mint.mark) + presets.take(9).map(Theme::body)).distinct()
                samples.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEach { sample ->
                            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(6.dp))
                                .clickable(role = Role.Button) { preview(sample) }
                                .semantics { contentDescription = sample.hex }, contentAlignment = Alignment.Center) {
                                Sample(sample, 24.dp)
                            }
                        }
                        repeat(5 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
            if (minOf(theme.body.contrast(theme.label), theme.key.contrast(theme.padText),
                    theme.padActive.contrast(theme.padText), theme.button.contrast(theme.keyText)) < 4.5) {
                Note("Contraste bajo en alguna etiqueta")
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Action("Importar tema", Glyph.IMPORT, Modifier.weight(1f)) { cancel(); app.importTheme() }
        Action("Exportar tema", Glyph.EXPORT, Modifier.weight(1f)) { app.exportTheme(theme) }
    }
    Row(Modifier.fillMaxWidth()) {
        Action("Restaurar tema", Glyph.REFRESH, Modifier.weight(1f)) { cancel(); app.restoreTheme() }
        Action("Deshacer", Glyph.UNDO, Modifier.weight(1f), enabled = app.canUndoTheme) { cancel(); app.undoTheme() }
    }
    Disclosure("Gestionar temas") {
        OutlinedTextField(managingName, { managingName = it.take(40) }, label = { Text("Nombre del tema") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Action("Guardar tema", enabled = managingName.isNotBlank()) {
            original = null
            app.saveTheme(theme.copy(name = managingName))
        }
        if (app.isCustomTheme(theme.name)) {
            Row(Modifier.fillMaxWidth()) {
                Action("Renombrar", enabled = managingName.isNotBlank() && managingName != theme.name) {
                    app.saveTheme(theme.copy(name = managingName), theme.name)
                }
                Action("Duplicar") {
                    val stem = "${theme.name.take(28)} copia"
                    val names = app.themes.map(Theme::name).toSet()
                    val copy = generateSequence(1) { it + 1 }.map { if (it == 1) stem else "$stem $it" }
                        .first { it !in names }
                    app.saveTheme(theme.copy(name = copy))
                }
                Action("Eliminar") { remove = true }
            }
        }
    }
    if (remove) {
        AlertDialog(onDismissRequest = { remove = false }, title = { Text("Eliminar tema") },
            text = { Note(theme.name) },
            confirmButton = { Action("Eliminar tema") { remove = false; app.removeTheme(theme) } },
            dismissButton = { Action("Cancelar") { remove = false } })
    }
}

@Composable
private fun ThemeTile(theme: Theme, selected: Boolean, modifier: Modifier, showName: Boolean = false,
                      choose: () -> Unit) {
    val colors = DrawerColors.current
    Column(modifier.height(48.dp).clip(RoundedCornerShape(8.dp))
        .background(if (selected) colors.selection.color else colors.raised.color)
        .selectable(selected, role = Role.Button, onClick = choose)
        .semantics { contentDescription = theme.name },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Swatches(theme, 10.dp)
        if (showName || theme.name == "CUSTOM") Note(theme.name)
        else if (selected) SettingsIcon(Glyph.CHECK, colors.onSelection.color, 15.dp)
    }
}

private fun ColorRole.glyph(): Glyph = when (this) {
    ColorRole.BODY -> Glyph.BODY
    ColorRole.PANEL -> Glyph.PANEL
    ColorRole.PAD -> Glyph.PAD
    ColorRole.BUTTON -> Glyph.BUTTON
    ColorRole.KNOB -> Glyph.KNOB
    ColorRole.MARK -> Glyph.MARK
    ColorRole.TEXT -> Glyph.TEXT
    ColorRole.LABEL -> Glyph.LABEL
    ColorRole.ACCENT -> Glyph.PALETTE
    ColorRole.NAVIGATION -> Glyph.NAVIGATION
    ColorRole.BEZEL -> Glyph.BEZEL
    ColorRole.ACTIVE -> Glyph.ACTIVE
    ColorRole.PAD_ACTIVE -> Glyph.PAD_ACTIVE
    ColorRole.PAD_TEXT -> Glyph.PAD_TEXT
    ColorRole.BUTTON_ACTIVE -> Glyph.BUTTON_ACTIVE
}

@Composable
private fun Swatches(theme: Theme, size: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(theme.body, theme.bed, theme.key, theme.accent).forEach { Sample(it, size) }
    }
}

@Composable
private fun Sample(value: Rgb, size: Dp) {
    val shape = RoundedCornerShape(4.dp)
    Box(Modifier.size(size).background(value.color, shape).border(1.dp, DrawerColors.current.outline.color, shape))
}

@Composable
private fun ColorPicker(color: Rgb, onColor: (Rgb) -> Unit) {
    val hsv = remember(color) { FloatArray(3).apply { AndroidColor.colorToHSV(color.argb, this) } }
    val hue = hsv[0]
    val latestHue by rememberUpdatedState(hue)
    val latestColor by rememberUpdatedState(onColor)
    fun pick(point: Offset, width: Int, height: Int) {
        val s = (point.x / width).coerceIn(0f, 1f)
        val v = (1 - point.y / height).coerceIn(0f, 1f)
        latestColor(Rgb.argb(AndroidColor.HSVToColor(floatArrayOf(latestHue, s, v))))
    }
    Column {
        Canvas(Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(6.dp)).testTag("color-picker")
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
            onColor(Rgb.argb(AndroidColor.HSVToColor(floatArrayOf(it, hsv[1].coerceAtLeast(0.1f), hsv[2].coerceAtLeast(0.1f)))))
        }, valueRange = 0f..359.9f, modifier = Modifier.semantics { contentDescription = "Tono" })
    }
}
