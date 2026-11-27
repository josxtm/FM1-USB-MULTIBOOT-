// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.data

import android.content.Context
import com.josxtm.fm1screen.ui.theme.Palette
import com.josxtm.fm1screen.ui.theme.Rgb
import com.josxtm.fm1screen.ui.theme.Theme
import com.josxtm.fm1screen.ui.theme.ThemeTokens
import org.json.JSONObject

enum class ColorRole(val label: String) {
    BODY("Fondo principal"), PANEL("Paneles"), PAD("Pads"), BUTTON("Botones"),
    KNOB("Potenciómetros"), MARK("Indicadores de potenciómetro"), TEXT("Texto de controles"),
    LABEL("Etiquetas exteriores"), ACCENT("Acento"), NAVIGATION("Navegación"),
    BEZEL("Marco de pantalla"), ACTIVE("Control pulsado");

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
    }

    fun reset(t: Theme, mint: Theme): Theme = when (this) {
        PAD -> t.copy(key = mint.key, hi = mint.hi, lo = mint.lo)
        BUTTON -> t.copy(button = mint.button, buttonHi = mint.buttonHi, buttonLo = mint.buttonLo)
        else -> set(t, get(mint))
    }
}

class ThemeLibrary(context: Context) {
    private val prefs = context.getSharedPreferences("themes", Context.MODE_PRIVATE)
    private val tokens = ThemeTokens(context.assets.open("tokens.json").bufferedReader().use { it.readText() })
    private fun upstream(name: String, display: String) = tokens.presets.first { it.name == name }.copy(name = display)
    val original = upstream("Mint", "ORIGINAL MINT")
    private val presets = listOf(
        original,
        generated("TURQUESA", "#4FCDBF", "#236C6D"),
        generated("SILVER", "#C7CED3", "#515A65"),
        upstream("Midnight", "DARK"),
        upstream("Cream", "CREAM"),
        upstream("Tangerine", "ORANGE"),
        generated("BLUE", "#83B6EA", "#284E78"),
        upstream("Lilac", "PURPLE"),
        generated("RED", "#DF838C", "#6D2635"),
        original.copy(name = "CUSTOM"),
    )
    init { Palette.load(tokens) }

    fun list() = presets + prefs.all.filterKeys { it.startsWith("custom-") }.values.mapNotNull {
        runCatching { decode(JSONObject(it as String)) }.getOrNull()
    }.sortedBy { it.name }

    fun isCustom(name: String) = prefs.contains("custom-$name")
    fun store(theme: Theme, replaceName: String? = null): Theme {
        val name = theme.name.trim()
        require(name.length in 1..40 && name.none(Char::isISOControl)) { "Nombre inválido" }
        require(presets.none { it.name.equals(name, true) }) { "Nombre reservado para un tema predefinido" }
        require(isCustom(name) || prefs.all.keys.count { it.startsWith("custom-") } < 64 || replaceName != null)
        val ready = theme.copy(name = name)
        val edit = prefs.edit()
        if (replaceName != null && replaceName != name) edit.remove("custom-$replaceName")
        edit.putString("custom-$name", encode(ready).toString()).apply()
        return ready
    }

    fun remove(theme: Theme) { prefs.edit().remove("custom-${theme.name}").apply() }

    private fun generated(name: String, body: String, controls: String): Theme {
        val color = Rgb.hex(controls)!!
        val background = Rgb.hex(body)!!
        return Theme.derived(Theme.Choice(background, color, original.knob, original.led, original.bezel), name, original)
    }

    companion object {
        fun encode(theme: Theme): JSONObject = theme.toJson().apply {
            put("schema", 1)
            put("colors", JSONObject().apply {
                ColorRole.entries.forEach { put(it.name, it.get(theme).hex) }
            })
            put("buttonHi", theme.buttonHi.hex)
            put("buttonLo", theme.buttonLo.hex)
        }

        fun decode(json: JSONObject): Theme {
            require(json.getInt("schema") == 1) { "Versión de tema no admitida" }
            val name = json.getString("name").trim()
            require(name.length in 1..40 && name.none(Char::isISOControl))
            val colors = json.getJSONObject("colors")
            val base = requireNotNull(Theme.fromJson(json))
            var result = base
            ColorRole.entries.forEach {
                result = it.set(result, requireNotNull(Rgb.hex(colors.getString(it.name))))
            }
            return result.copy(name = name, hi = base.hi, lo = base.lo,
                buttonHi = requireNotNull(Rgb.hex(json.getString("buttonHi"))),
                buttonLo = requireNotNull(Rgb.hex(json.getString("buttonLo"))))
        }
    }
}
