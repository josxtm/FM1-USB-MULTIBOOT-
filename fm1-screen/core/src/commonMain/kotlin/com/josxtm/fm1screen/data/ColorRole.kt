// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.data

import com.josxtm.fm1screen.ui.theme.Rgb
import com.josxtm.fm1screen.ui.theme.Theme

enum class ColorRole(val label: String) {
    BODY("Fondo principal"), PANEL("Paneles"), PAD("Pads"), BUTTON("Botones"),
    KNOB("Potenciómetros"), MARK("Indicadores de potenciómetro"), TEXT("Texto de controles"),
    LABEL("Etiquetas exteriores"), ACCENT("Acento"), NAVIGATION("Navegación"),
    BEZEL("Marco de pantalla"), ACTIVE("Pulsación local"),
    PAD_ACTIVE("Pads activos"), PAD_TEXT("Texto de pads"), BUTTON_ACTIVE("Botones activos");

    fun get(t: Theme) = when (this) {
        BODY -> t.body
        PANEL -> t.bed
        PAD -> t.key
        BUTTON -> t.button
        KNOB -> t.knob
        MARK -> t.mark
        TEXT -> t.keyText
        LABEL -> t.label
        ACCENT -> t.accent
        NAVIGATION -> t.navigation
        BEZEL -> t.bezel
        ACTIVE -> t.pressed
        PAD_ACTIVE -> t.padActive
        PAD_TEXT -> t.padText
        BUTTON_ACTIVE -> t.buttonActive
    }

    fun set(t: Theme, c: Rgb): Theme = when (this) {
        BODY -> t.copy(body = c)
        PANEL -> t.copy(bed = c)
        PAD -> t.copy(key = c, hi = c.lightness(8.0), lo = c.lightness(-18.0))
        BUTTON -> t.copy(button = c, buttonHi = c.lightness(8.0), buttonLo = c.lightness(-18.0))
        KNOB -> t.copy(knob = c)
        MARK -> t.copy(mark = c)
        TEXT -> t.copy(keyText = c)
        LABEL -> t.copy(label = c)
        ACCENT -> t.copy(accent = c)
        NAVIGATION -> t.copy(navigation = c)
        BEZEL -> t.copy(bezel = c)
        ACTIVE -> t.copy(pressed = c)
        PAD_ACTIVE -> t.copy(padActive = c)
        PAD_TEXT -> t.copy(padText = c)
        BUTTON_ACTIVE -> t.copy(buttonActive = c)
    }

    fun reset(t: Theme, mint: Theme): Theme = when (this) {
        PAD -> t.copy(key = mint.key, hi = mint.hi, lo = mint.lo)
        BUTTON -> t.copy(button = mint.button, buttonHi = mint.buttonHi, buttonLo = mint.buttonLo)
        else -> set(t, get(mint))
    }
}
