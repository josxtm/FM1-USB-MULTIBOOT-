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
    var hex by remember(role, theme.name) { mutableStateOf(role.get(theme).hex) }
    var managingName by remember(theme.name) { mutableStateOf(theme.name.takeIf(app::isCustomTheme).orEmpty()) }
    var remove by remember(theme.name) { mutableStateOf(false) }

    fun apply(color: Rgb) {
        app.previewTheme(role.set(theme, color))
    }

    ChoiceMenu("Tema", theme.name, app.themes, Theme::name, swatches = { Swatches(it, 12.dp) }) { app.selectTheme(it) }
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Note("Vista previa")
        Swatches(theme, 22.dp)
    }
    Disclosure("Colores personalizados") {
        ColorRole.entries.chunked(5).forEach { roles ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                roles.forEach { option ->
                    Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (role == option) colors.selection.color else colors.raised.color)
                        .selectable(role == option, role = Role.Button) { role = option }
                        .semantics { contentDescription = option.label },
                        contentAlignment = Alignment.Center) {
                        Sample(option.get(theme), 23.dp)
                    }
                }
            }
        }
        Caption(role.label)
        key(role, theme.name) { ColorPicker(role.get(theme)) { hex = it.hex; apply(it) } }
        Disclosure("Muestras") {
            val original = app.originalTheme
            val samples = (listOf(original.body, original.key, original.knob, original.mark) +
                app.themes.take(9).map(Theme::body)).distinct()
            samples.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { sample ->
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
                            .clickable(role = Role.Button) { hex = sample.hex; apply(sample) }
                            .semantics { contentDescription = sample.hex }, contentAlignment = Alignment.Center) {
                            Sample(sample, 24.dp)
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(hex, {
                if (it.length <= 7) {
                    hex = it
                    Rgb.hex(it)?.let(::apply)
                }
            }, label = { Text("HEX") }, singleLine = true, modifier = Modifier.weight(1f).testTag("color-hex"))
            Action("Restaurar color", Glyph.REFRESH) {
                val original = role.reset(theme, app.originalTheme)
                hex = role.get(original).hex
                app.previewTheme(original)
            }
        }
        if (minOf(theme.body.contrast(theme.label), theme.key.contrast(theme.padText),
                theme.padActive.contrast(theme.padText), theme.button.contrast(theme.keyText)) < 4.5) {
            Note("Contraste bajo en alguna etiqueta")
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Action("Importar tema", Glyph.IMPORT, Modifier.weight(1f), click = app::importTheme)
        Action("Exportar tema", Glyph.EXPORT, Modifier.weight(1f)) { app.exportTheme(theme) }
    }
    Action("Restaurar Original Mint", Glyph.REFRESH) { app.selectTheme(app.originalTheme) }
    Disclosure("Gestionar temas") {
        OutlinedTextField(managingName, { managingName = it.take(40) }, label = { Text("Nombre del tema") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Action("Guardar tema", enabled = managingName.isNotBlank()) { app.saveTheme(theme.copy(name = managingName)) }
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
