// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 RemoteFM1 contributors
// Ported from RemoteFM1IOS edf2039 RFMKit/Sources/RFMKit/Faceplate/Theme.swift
// The faceplate's colours (design/README.md section 6, design/tokens.json): the five presets read from the bundled
// tokens.json (assets/tokens.json), the fixed colours, and the rules that derive a custom theme from its five chosen
// colours. Pure Kotlin (org.json only): tested on the JVM. ThemeStore keeps the choice.
package com.josxtm.fm1screen.ui.theme

import org.json.JSONObject

/** tokens.json: the presets in the design's order, the default, the fixed colours */
class ThemeTokens(json: String) {
    val presets: List<Theme>
    val defaultName: String
    val fixed: Map<String, Rgb>             // indicatorLed, playGlyph, recGlyph, linkedDot
    val sheet: Map<String, Rgb>             // fixedColors.sheet

    init {
        val root = JSONObject(json)
        val themes = root.getJSONObject("themes")
        val keys = themes.keys().asSequence().toList()
        val names = ORDER.filter { it in keys } + keys.filter { it !in ORDER }.sorted()
        presets = names.mapNotNull { n -> themes.optJSONObject(n)?.let { o -> Theme.of(n) { o.optString(it, "").ifEmpty { null } } } }
        require(presets.isNotEmpty()) { "no themes" }
        defaultName = root.optString("defaultTheme", presets[0].name)
        val f = mutableMapOf<String, Rgb>()
        val s = mutableMapOf<String, Rgb>()
        root.optJSONObject("fixedColors")?.let { fc ->
            for (k in fc.keys()) {
                val v = fc.get(k)
                if (v is String) Rgb.hex(v)?.let { f[k] = it }
                if (k == "sheet" && v is JSONObject) for (sk in v.keys()) Rgb.hex(v.optString(sk))?.let { s[sk] = it }
            }
        }
        fixed = f
        sheet = s
    }

    companion object {
        /** the design's order of the presets (JSON objects have none) */
        val ORDER = listOf("Tangerine", "Midnight", "Mint", "Lilac", "Cream")

        /** (only if the asset were missing: Tangerine alone) */
        const val FALLBACK = """{"defaultTheme":"Tangerine","themes":{"Tangerine":{"body":"#EE7A3C","label":"#3A1606","bed":"#874332","key":"#924E3A","hi":"#A9614B","lo":"#64301F","keyText":"#FBEDE3","knob":"#1B1715","mark":"#F4EDE6","bezel":"#141210","lcd":"#C9D6EA","ink":"#13203A","led":"#FFE6A8","ledOff":"#6F6668"}}}"""
    }
}
