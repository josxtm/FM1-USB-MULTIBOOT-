package com.josxtm.fm1compatibility

import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.util.UUID
import java.time.Instant
import android.util.Base64

/** Blocking transport: invoke only on a dedicated IO executor. Never on LCD/MIDI threads. */
class CompatibilitySync(private val token: () -> String?) {
    private val endpoint = "https://api.github.com/repos/josxtm/FM1-USB-MULTIBOOT-/contents/shared/compatibility/firmware-compatibility.json"
    private val branch = "fm1-compatibility-data"
    data class Snapshot(val data: JSONObject, val sha: String)
    private class HttpFailure(val status: Int) : Exception("GitHub HTTP $status")
    private fun request(method: String, url: String, body: JSONObject? = null): JSONObject {
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            token()?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw HttpFailure(status)
            return JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally { connection.disconnect() }
    }
    fun read(): Snapshot {
        val response = request("GET", "$endpoint?ref=$branch")
        require(response.getString("encoding") == "base64")
        val data = JSONObject(String(Base64.decode(response.getString("content"), Base64.DEFAULT), Charsets.UTF_8))
        validate(data)
        return Snapshot(data, response.getString("sha"))
    }
    fun publish(local: JSONObject): Snapshot {
        require(!token().isNullOrBlank()) { "GitHub authorization required" }
        validate(local)
        repeat(4) { attempt ->
            val remote = read()
            val merged = merge(remote.data, local)
            if (canonical(merged) == canonical(remote.data)) return remote
            val payload = JSONObject().put("message", "Append FM1 compatibility evidence")
                .put("branch", branch).put("sha", remote.sha)
                .put("content", Base64.encodeToString(merged.toString(2).toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            try {
                request("PUT", endpoint, payload)
                val confirmed = read()
                require(canonical(merge(confirmed.data, local)) == canonical(confirmed.data)) { "Publication not confirmed" }
                return confirmed
            } catch (e: HttpFailure) {
                if (e.status !in listOf(409, 422) || attempt == 3) throw e
                Thread.sleep(250L shl attempt)
            }
        }
        error("Sync retry exhausted")
    }
    companion object {
        private fun objects(array: JSONArray) = (0 until array.length()).map { array.getJSONObject(it) }
        private fun canonical(value: Any?): String = when (value) {
            is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.get(it)) }
            is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
            is String -> JSONObject.quote(value)
            else -> value.toString()
        }
        fun validate(data: JSONObject) {
            require(data.getInt("schemaVersion") == 1)
            val firmware = objects(data.getJSONArray("firmwares"))
            val ids = firmware.map { it.getString("id") }
            require(ids.distinct().size == ids.size)
            firmware.forEach { require(it.getString("id").matches(Regex("[a-z0-9-]+"))); require(it.getString("name").isNotBlank()); it.getJSONArray("versions") }
            val events = objects(data.getJSONArray("events"))
            val byId = events.associateBy { it.getString("id") }
            require(byId.size == events.size)
            events.forEach { e ->
                UUID.fromString(e.getString("id"))
                Instant.parse(e.getString("recordedAt"))
                require(e.getString("firmwareId") in ids && e.has("version") && e.getInt("build") > 0)
                val result = e.getJSONObject("result")
                when (e.getString("source")) {
                    "screen" -> { require(result.getBoolean("physicalTest")); require(result.getString("lcd") in listOf("pending","pass","partial","fail","not_applicable")); require(result.getString("transport") in listOf("usb","ble","unknown")) }
                    "analyzer" -> require(result.getString("researchStatus") in listOf("pending","investigating","verified","inconclusive"))
                    else -> error("Unknown source")
                }
                if (e.has("supersedes")) {
                    val prior = byId.getValue(e.getString("supersedes"))
                    require(prior !== e && listOf("source","firmwareId","version").all { canonical(prior.get(it)) == canonical(e.get(it)) })
                    require(Instant.parse(prior.getString("recordedAt")).isBefore(Instant.parse(e.getString("recordedAt"))))
                }
            }
        }
        fun merge(remote: JSONObject, local: JSONObject): JSONObject {
            validate(remote); validate(local)
            val catalog = linkedMapOf<String, JSONObject>()
            (objects(remote.getJSONArray("firmwares")) + objects(local.getJSONArray("firmwares"))).forEach { item ->
                val id = item.getString("id")
                val existing = catalog[id]
                if (existing == null) catalog[id] = JSONObject(item.toString())
                else {
                    require(existing.getString("name") == item.getString("name"))
                    val versions = linkedSetOf<String>()
                    listOf(existing, item).forEach { f -> val a = f.getJSONArray("versions"); (0 until a.length()).forEach { versions.add(a.getString(it)) } }
                    existing.put("versions", JSONArray(versions.toList()))
                }
            }
            val events = linkedMapOf<String, JSONObject>()
            (objects(remote.getJSONArray("events")) + objects(local.getJSONArray("events"))).forEach { event ->
                val id = event.getString("id")
                val previous = events[id]
                require(previous == null || canonical(previous) == canonical(event)) { "Conflicting event UUID" }
                events[id] = event
            }
            return JSONObject().put("schemaVersion", 1).put("firmwares", JSONArray(catalog.values.toList())).put("events", JSONArray(events.values.toList())).also(::validate)
        }
    }
}
