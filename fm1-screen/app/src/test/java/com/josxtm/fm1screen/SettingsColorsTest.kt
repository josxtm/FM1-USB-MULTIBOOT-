// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen

import com.josxtm.fm1screen.ui.theme.Rgb
import com.josxtm.fm1screen.ui.theme.SettingsColors
import com.josxtm.fm1screen.ui.theme.ThemeTokens
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsColorsTest {
    private val themes = ThemeTokens(File("../assets/tokens.json").readText()).presets

    @Test
    fun controlsMeetContrastWithLightDarkAndLowContrastCustomColors() {
        val mint = themes.first { it.name == "Mint" }
        val custom = listOf("#0C121A", "#757575", "#C8BBF4", "#FFFFEE").map { hex ->
            val body = requireNotNull(Rgb.hex(hex))
            mint.copy(name = hex, body = body, label = body, accent = body)
        }
        for (theme in themes + custom) {
            val palette = SettingsColors(theme)
            val pairs = listOf(
                palette.gear to theme.body, palette.text to palette.panel, palette.secondary to palette.panel,
                palette.accent to palette.panel, palette.onSelection to palette.selection, palette.onAccent to palette.accent,
            )
            for ((foreground, background) in pairs) {
                val ratio = Rgb.argb(foreground.argb).contrast(Rgb.argb(background.argb))
                assertTrue("${theme.name}: $foreground / $background has contrast $ratio", ratio >= 4.5)
            }
            assertTrue(palette.outline.contrast(palette.panel) >= 3.0)
        }
    }

    @Test
    fun drawerSurfaceChangesImmediatelyWithTheme() {
        val mint = SettingsColors(themes.first { it.name == "Mint" })
        val dark = SettingsColors(themes.first { it.name == "Midnight" })
        assertNotEquals(mint.panel, dark.panel)
        assertNotEquals(mint.selection, dark.selection)
    }
}
