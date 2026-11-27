// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.data

import android.content.Context
import com.josxtm.fm1screen.protocol.Wire
import org.json.JSONArray
import org.json.JSONObject

data class FirmwareProfile(
    val id: String,
    val name: String,
    val version: String,
    val wire: Wire,
    val deviceNames: List<String>,
    val pads: List<String?>,
    val buttons: List<String?>,
    val knobs: List<String?>,
    val evidence: String,
    val source: String,
    val limits: String,
) {
    fun encode(): JSONObject = JSONObject().apply {
        put("schema", 1)
        put("id", id)
        put("name", name)
        put("version", version)
        put("protocol", wire.name)
        put("resolution", JSONArray(listOf(240, 240)))
        put("pixels", "RGB565")
        put("deviceNames", JSONArray(deviceNames))
        put("pads", JSONArray(pads.map { it ?: JSONObject.NULL }))
        put("buttons", JSONArray(buttons.map { it ?: JSONObject.NULL }))
        put("knobs", JSONArray(knobs.map { it ?: JSONObject.NULL }))
        put("evidence", evidence)
        put("source", source)
        put("limits", limits)
    }

    companion object {
        private val idPattern = Regex("[a-z0-9][a-z0-9_-]{0,47}")

        fun decode(json: JSONObject): FirmwareProfile {
            require(json.getInt("schema") == 1) { "Versión de perfil no admitida" }
            val id = json.getString("id")
            require(idPattern.matches(id)) { "Identificador de perfil inválido" }
            val resolution = json.getJSONArray("resolution")
            require(resolution.length() == 2 && resolution.getInt(0) == 240 && resolution.getInt(1) == 240)
            require(json.getString("pixels") == "RGB565")
            fun text(key: String, max: Int) = json.getString(key).trim().also {
                require(it.isNotEmpty() && it.length <= max && it.none(Char::isISOControl)) { "Campo $key inválido" }
            }
            fun labels(key: String, length: Int): List<String?> {
                val arr = json.getJSONArray(key)
                require(arr.length() == length) { "Longitud incorrecta en $key" }
                return List(length) { i ->
                    if (arr.isNull(i)) null else arr.getString(i).trim().also {
                        require(it.length in 1..18 && it.none(Char::isISOControl)) { "Etiqueta inválida en $key" }
                    }
                }
            }
            val devices = json.optJSONArray("deviceNames") ?: JSONArray()
            require(devices.length() <= 32)
            return FirmwareProfile(id, text("name", 40), text("version", 64),
                Wire.valueOf(json.getString("protocol")), List(devices.length()) { i ->
                    devices.getString(i).also { require(it.length in 1..80 && it.none(Char::isISOControl)) }
                }, labels("pads", 27), labels("buttons", 14), labels("knobs", 8),
                text("evidence", 100), text("source", 300), text("limits", 500))
        }
    }
}

class ProfileLibrary(context: Context) {
    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    private val builtIn = context.assets.list("profiles").orEmpty().filter { it.endsWith(".json") }.sorted().map { name ->
        context.assets.open("profiles/$name").bufferedReader().use {
            FirmwareProfile.decode(JSONObject(it.readText()))
        }
    }

    fun list(): List<FirmwareProfile> {
        val custom = prefs.all.values.mapNotNull { raw ->
            runCatching { FirmwareProfile.decode(JSONObject(raw as String)) }.getOrNull()
        }
        return (builtIn.associateBy { it.id } + custom.associateBy { it.id }).values.sortedBy { it.name }
    }

    fun save(profile: FirmwareProfile) {
        require(profile.id in prefs.all || prefs.all.size < 128) { "Biblioteca local completa" }
        prefs.edit().putString(profile.id, profile.encode().toString()).apply()
    }
}
