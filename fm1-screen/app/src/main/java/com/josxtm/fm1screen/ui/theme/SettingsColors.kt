// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.ui.theme

/** Drawer colors are derived for the active theme, including arbitrary colors imported from JSON. */
internal class SettingsColors(theme: Theme) {
    val gear = readable(theme.label, theme.body)
    val panel = blend(theme.body, gear, 0.04)
    val text = readable(theme.label, panel)
    val raised = blend(panel, text, 0.075)
    val secondary = readable(blend(panel, text, 0.73), panel)
    val accent = readable(theme.accent, panel)
    val selection = blend(panel, theme.accent, 0.18)
    val onSelection = readable(theme.accent, selection)
    val onAccent = readable(panel, accent)
    val divider = blend(panel, text, 0.22)
    val outline = readable(blend(panel, text, 0.58), panel, 3.0)

    private fun readable(preferred: Rgb, surface: Rgb, ratio: Double = 4.55): Rgb {
        if (preferred.contrast(surface) >= ratio) return preferred
        val fallback = if (surface.luminance >= 0.179) Rgb(0.0, 0.0, 0.0) else Rgb(1.0, 1.0, 1.0)
        var low = 0.0
        var high = 1.0
        repeat(10) {
            val amount = (low + high) / 2.0
            if (blend(preferred, fallback, amount).contrast(surface) >= ratio) high = amount else low = amount
        }
        return blend(preferred, fallback, high)
    }

    private fun blend(base: Rgb, accent: Rgb, amount: Double) = Rgb.of(
        base.r + (accent.r - base.r) * amount,
        base.g + (accent.g - base.g) * amount,
        base.b + (accent.b - base.b) * amount,
    )
}
