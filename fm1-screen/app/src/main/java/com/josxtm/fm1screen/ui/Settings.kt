// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.josxtm.fm1screen.MainActivity
import com.josxtm.fm1screen.ui.theme.RfmFonts
import com.josxtm.fm1screen.ui.theme.SettingsColors
import com.josxtm.fm1screen.ui.theme.Theme
import com.josxtm.fm1screen.ui.theme.color

private enum class Category(val title: String, val glyph: Glyph) {
    CONNECTION("Conexión", Glyph.USB),
    VIEW("Vista", Glyph.SCREEN),
    COLORS("Colores", Glyph.PALETTE),
    ADVANCED("Avanzado", Glyph.ACTIVITY),
}

internal val DrawerColors = staticCompositionLocalOf<SettingsColors> { error("SettingsColors missing") }
private val tileShape = RoundedCornerShape(10.dp)

@Composable
internal fun SettingsGear(theme: Theme, modifier: Modifier = Modifier, open: () -> Unit) {
    val colors = remember(theme) { SettingsColors(theme) }
    IconAction("Abrir configuración", Glyph.GEAR, colors.gear.color, modifier.testTag("settings-gear"), open)
}

@Composable
internal fun Settings(app: MainActivity, theme: Theme, visible: Boolean, close: () -> Unit) {
    BackHandler(visible, close)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(150)),
    ) {
        val colors = remember(theme) { SettingsColors(theme) }
        CompositionLocalProvider(DrawerColors provides colors) {
            val inherited = MaterialTheme.colorScheme
            val scheme = remember(colors, inherited) {
                inherited.copy(
                    primary = colors.accent.color, onPrimary = colors.onAccent.color,
                    secondary = colors.accent.color, onSecondary = colors.onAccent.color,
                    background = colors.panel.color, onBackground = colors.text.color,
                    surfaceTint = colors.accent.color,
                    primaryContainer = colors.selection.color, onPrimaryContainer = colors.onSelection.color,
                    secondaryContainer = colors.selection.color, onSecondaryContainer = colors.onSelection.color,
                    surface = colors.panel.color, onSurface = colors.text.color,
                    onSurfaceVariant = colors.secondary.color,
                    surfaceVariant = colors.raised.color, surfaceContainer = colors.raised.color,
                    surfaceContainerHighest = colors.raised.color, outline = colors.outline.color,
                    outlineVariant = colors.divider.color, inverseSurface = colors.text.color,
                    inverseOnSurface = colors.panel.color, inversePrimary = colors.onAccent.color,
                )
            }
            MaterialTheme(colorScheme = scheme) {
                BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                    Box(Modifier.fillMaxSize().background(colors.text.color.copy(alpha = 0.07f))
                        .semantics { hideFromAccessibility() }
                        .pointerInput(app.faceplate) {
                            var distance = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { distance = 0f },
                                onDragEnd = { if (kotlin.math.abs(distance) > 60.dp.toPx()) app.faceplate.move(if (distance < 0) 1 else -1) },
                            ) { _, dx -> distance += dx }
                        }
                        .clickable(interactionSource = null, indication = null, onClick = close))
                    val width = when {
                        maxWidth >= 900.dp -> (maxWidth * 0.30f).coerceIn(280.dp, 336.dp)
                        maxWidth >= 536.dp -> (maxWidth * 0.53f).coerceIn(280.dp, 332.dp)
                        else -> (maxWidth - 16.dp).coerceAtMost(332.dp)
                    }.coerceIn(0.dp, maxWidth)
                    Column(
                        Modifier.align(Alignment.CenterEnd).width(width).fillMaxHeight()
                            .animateEnterExit(
                                enter = slideInHorizontally(tween(150)) { it },
                                exit = slideOutHorizontally(tween(150)) { it },
                            )
                            .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                            .background(colors.panel.color)
                            // Own the entire drawer hit area so that blank space never reaches the backdrop or faceplate.
                            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                            .testTag("settings").padding(horizontal = 12.dp),
                    ) {
                        var page by rememberSaveable { mutableStateOf(Category.CONNECTION) }
                        var seenUpdateRequest by rememberSaveable { mutableStateOf(0) }
                        val updateRequest = app.updateSettingsRequest
                        val revealUpdates = updateRequest != seenUpdateRequest
                        LaunchedEffect(updateRequest) {
                            if (revealUpdates) {
                                page = Category.ADVANCED
                                seenUpdateRequest = updateRequest
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Configuración", color = colors.text.color, fontFamily = RfmFonts.hardware,
                                fontSize = 17.sp, modifier = Modifier.weight(1f))
                            IconAction("Cerrar configuración", Glyph.CLOSE, colors.text.color, click = close)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Category.entries.forEach { tab ->
                                Tile(tab.title, tab.glyph, page == tab, Modifier.weight(1f)) { page = tab }
                            }
                        }
                        HorizontalDivider(Modifier.padding(top = 5.dp), color = colors.divider.color)
                        AnimatedContent(page, modifier = Modifier.weight(1f).fillMaxWidth(), transitionSpec = {
                            fadeIn(tween(100, delayMillis = 50)) togetherWith fadeOut(tween(50))
                        }, label = "categoría") { category ->
                            Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                when (category) {
                                    Category.CONNECTION -> ConnectionSettings(app)
                                    Category.VIEW -> ScreenSettings(app, close)
                                    Category.COLORS -> AppearanceSettings(app)
                                    Category.ADVANCED -> {
                                        Disclosure("Actualizaciones", initial = updateRequest > 0) { UpdateSettings(app) }
                                        Disclosure("Diagnóstico") { DiagnosticsSettings(app) }
                                        Disclosure("Información y licencias") { AboutSettings() }
                                    }
                                }
                            }
                        }
                        app.message?.let { Note(it, colors.accent.color, Modifier.padding(vertical = 5.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun IconAction(description: String, glyph: Glyph, color: Color, modifier: Modifier = Modifier,
                        click: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(modifier.size(48.dp).clip(tileShape)
        .background(if (pressed) color.copy(alpha = 0.15f) else Color.Transparent)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = click)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        SettingsIcon(glyph, color)
    }
}

@Composable
internal fun Tile(label: String, glyph: Glyph, selected: Boolean, modifier: Modifier = Modifier,
                  description: String = label, click: () -> Unit) {
    val colors = DrawerColors.current
    val color = if (selected) colors.onSelection.color else colors.text.color
    Column(
        modifier.heightIn(min = 48.dp).clip(tileShape)
            .background(if (selected) colors.selection.color else Color.Transparent)
            .selectable(selected, role = Role.Tab, onClick = click)
            .semantics { contentDescription = description }.padding(horizontal = 1.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        SettingsIcon(glyph, color, 19.dp)
        Text(label, fontFamily = RfmFonts.ui, fontSize = 10.sp, lineHeight = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = color, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun Note(text: String, color: Color = DrawerColors.current.secondary.color, modifier: Modifier = Modifier) {
    Text(text, modifier, color = color, fontFamily = RfmFonts.ui, fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
internal fun Caption(text: String) = Text(text, color = DrawerColors.current.accent.color,
    fontFamily = RfmFonts.hardware, fontSize = 13.sp, lineHeight = 18.sp)

@Composable
internal fun Action(label: String, glyph: Glyph? = null, modifier: Modifier = Modifier, enabled: Boolean = true,
                    click: () -> Unit) {
    val colors = DrawerColors.current
    Row(modifier.heightIn(min = 48.dp).clip(tileShape)
        .clickable(role = Role.Button, enabled = enabled, onClick = click).padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (glyph != null) SettingsIcon(glyph, if (enabled) colors.accent.color else colors.secondary.color, 18.dp)
        Note(label, if (enabled) colors.text.color else colors.secondary.color)
    }
}

@Composable
internal fun Toggle(label: String, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    val colors = DrawerColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(tileShape)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = change)
        .semantics { contentDescription = label }.padding(start = 7.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Note(label, if (enabled) colors.text.color else colors.secondary.color, Modifier.weight(1f))
        Switch(checked, onCheckedChange = null, modifier = Modifier.scale(0.8f), enabled = enabled)
    }
}

@Composable
internal fun Disclosure(label: String, initial: Boolean = false, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(initial) }
    val colors = DrawerColors.current
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(tileShape).background(colors.selection.color)
            .clickable(role = Role.Button) { expanded = !expanded }
            .semantics { stateDescription = if (expanded) "Expandido" else "Plegado" }.padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Note(label, colors.onSelection.color, Modifier.weight(1f))
            SettingsIcon(if (expanded) Glyph.UP else Glyph.CARET, colors.onSelection.color, 18.dp)
        }
        AnimatedVisibility(
            expanded,
            enter = fadeIn(tween(120)) + expandVertically(tween(150), expandFrom = Alignment.Top),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(150), shrinkTowards = Alignment.Top),
        ) {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { content() }
        }
    }
}

@Composable
internal fun <T> ChoiceMenu(
    label: String,
    selected: String,
    choices: List<T>,
    title: (T) -> String,
    modifier: Modifier = Modifier,
    swatches: (@Composable (T) -> Unit)? = null,
    choose: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val colors = DrawerColors.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxWidth
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(tileShape)
            .border(1.dp, colors.outline.color, tileShape)
            .clickable(role = Role.Button, enabled = choices.isNotEmpty()) { open = true }
            .semantics { contentDescription = label; stateDescription = selected }.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = colors.secondary.color, fontFamily = RfmFonts.ui, fontSize = 10.sp, lineHeight = 12.sp)
                Text(selected, color = colors.text.color, fontFamily = RfmFonts.ui, fontSize = 13.sp, lineHeight = 17.sp)
            }
            SettingsIcon(Glyph.CARET, colors.accent.color, 19.dp)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.width(width).heightIn(max = 300.dp),
            containerColor = colors.panel.color, tonalElevation = 0.dp, shadowElevation = 3.dp) {
            choices.forEach { option ->
                DropdownMenuItem(
                    text = { Note(title(option), colors.text.color) },
                    leadingIcon = swatches?.let { sample -> { sample(option) } },
                    trailingIcon = if (title(option) == selected) {
                        { SettingsIcon(Glyph.CHECK, colors.accent.color, 17.dp) }
                    } else null,
                    onClick = { open = false; choose(option) },
                )
            }
        }
    }
}
