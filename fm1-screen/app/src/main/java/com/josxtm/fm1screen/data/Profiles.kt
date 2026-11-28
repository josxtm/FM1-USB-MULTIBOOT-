// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.data

import android.content.Context
import com.josxtm.fm1screen.protocol.Wire
import org.json.JSONArray
import org.json.JSONObject

object ProfileJson {
    fun encode(profile: FirmwareProfile): JSONObject = JSONObject().apply {
        put("schema", 1)
        put("id", profile.id)
        put("name", profile.name)
        put("version", profile.version)
        put("protocol", profile.wire.name)
        put("resolution", JSONArray(listOf(240, 240)))
        put("pixels", "RGB565")
        put("deviceNames", JSONArray(profile.deviceNames))
        put("pads", JSONArray(profile.pads.map { it ?: JSONObject.NULL }))
        put("buttons", JSONArray(profile.buttons.map { it ?: JSONObject.NULL }))
        put("knobs", JSONArray(profile.knobs.map { it ?: JSONObject.NULL }))
        put("evidence", profile.evidence)
        put("source", profile.source)
        put("limits", profile.limits)
    }

    fun decode(json: JSONObject): FirmwareProfile {
        require(json.getInt("schema") == 1) { "Versión de perfil no admitida" }
        val resolution = json.getJSONArray("resolution")
        require(resolution.length() == 2 && resolution.getInt(0) == 240 && resolution.getInt(1) == 240)
        require(json.getString("pixels") == "RGB565")
        fun strings(key: String): List<String?> = json.getJSONArray(key).let { array ->
            List(array.length()) { if (array.isNull(it)) null else array.getString(it).trim() }
        }
        val devices = json.optJSONArray("deviceNames") ?: JSONArray()
        require(devices.length() <= 32)
        return FirmwareProfile(
            json.getString("id"), json.getString("name").trim(), json.getString("version").trim(),
            Wire.valueOf(json.getString("protocol")), List(devices.length()) { devices.getString(it) },
            strings("pads"), strings("buttons"), strings("knobs"), json.getString("evidence").trim(),
            json.getString("source").trim(), json.getString("limits").trim(),
        )
    }
}

class ProfileLibrary(context: Context) {
    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    private val builtIn = context.assets.list("profiles").orEmpty().filter { it.endsWith(".json") }.sorted().map { name ->
        context.assets.open("profiles/$name").bufferedReader().use {
            ProfileJson.decode(JSONObject(it.readText()))
        }
    }

    fun list(): List<FirmwareProfile> {
        val custom = prefs.all.values.mapNotNull { raw ->
            runCatching { ProfileJson.decode(JSONObject(raw as String)) }.getOrNull()
        }
        return (builtIn.associateBy { it.id } + custom.associateBy { it.id }).values.sortedBy { it.name }
    }

    fun save(profile: FirmwareProfile) {
        require(profile.id in prefs.all || prefs.all.size < 128) { "Biblioteca local completa" }
        prefs.edit().putString(profile.id, ProfileJson.encode(profile).toString()).apply()
    }
}
