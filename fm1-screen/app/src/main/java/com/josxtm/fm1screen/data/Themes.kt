// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.data

import android.content.Context
import com.josxtm.fm1screen.ui.theme.Palette
import com.josxtm.fm1screen.ui.theme.Rgb
import com.josxtm.fm1screen.ui.theme.Theme
import com.josxtm.fm1screen.ui.theme.ThemeTokens
import org.json.JSONObject

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
        fun encode(theme: Theme): JSONObject = JSONObject().apply {
            put("name", theme.name)
            Theme.ROLES.forEach { put(it, checkNotNull(theme[it]).hex) }
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
            val base = requireNotNull(Theme.of(name) { json.optString(it, "").ifEmpty { null } })
            var result = base
            val buttonHi = requireNotNull(Rgb.hex(json.getString("buttonHi")))
            val buttonLo = requireNotNull(Rgb.hex(json.getString("buttonLo")))
            ColorRole.entries.forEach { role ->
                val fallback = when (role) {
                    ColorRole.PAD_ACTIVE -> base.hi
                    ColorRole.PAD_TEXT -> result.keyText
                    ColorRole.BUTTON_ACTIVE -> buttonHi
                    else -> null
                }
                val value = if (fallback != null && !colors.has(role.name)) fallback
                    else requireNotNull(Rgb.hex(colors.getString(role.name)))
                result = role.set(result, value)
            }
            return result.copy(name = name, hi = base.hi, lo = base.lo,
                buttonHi = buttonHi, buttonLo = buttonLo)
        }
    }
}
