package com.josxtm.fm1compatibility

import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.util.UUID

/** Blocking transport. Call exclusively on a dedicated IO worker. */
class SheetsTransport(private val key: () -> String?) {
    private val endpoint = "https://script.google.com/macros/s/AKfycbyzrsOneoJNl4GJukz1Oozj_PDB88Z7EYVybSkB9F2osZiOPhqy1LtXIAHn7rgN6J5VzA/exec"

    private fun request(payload: JSONObject? = null): JSONObject {
        var url = URL(endpoint)
        var body = payload
        repeat(5) {
            val c = url.openConnection() as HttpsURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 10000
                c.readTimeout = 20000
                c.requestMethod = if (body == null) "GET" else "POST"
                c.setRequestProperty("Accept", "application/json")
                body?.let {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    c.outputStream.use { out -> out.write(it.toString().toByteArray(Charsets.UTF_8)) }
                }
                val status = c.responseCode
                if (status in listOf(301, 302, 303)) {
                    val next = URL(url, c.getHeaderField("Location") ?: error("Missing redirect"))
                    require(next.protocol == "https" && next.host in listOf("script.google.com", "script.googleusercontent.com"))
                    url = next
                    body = null // Apps Script returns its result through a GET redirect; never resend the secret.
                } else {
                    require(status in 200..299) { "HTTP $status" }
                    val result = JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                    require(result.optBoolean("ok")) { "FM1 API rejected request" }
                    return result
                }
            } finally { c.disconnect() }
        }
        error("Too many redirects")
    }

    fun read(): JSONObject {
        val response = request()
        require(response.getInt("schemaVersion") == 1)
        val data = response.getJSONObject("datos")
        mapOf("CATALOGO" to 5, "SCREEN" to 13, "ANALIZER" to 10).forEach { (name, width) ->
            val rows = data.getJSONArray(name)
            require(rows.length() > 0)
            for (i in 0 until rows.length()) require(rows.getJSONArray(i).length() == width)
        }
        return data
    }

    /** Preserve the same UUID and exact row on every retry. */
    fun publish(source: String, row: List<String>): Boolean {
        require(source in listOf("SCREEN", "ANALIZER"))
        require(row.size == if (source == "SCREEN") 13 else 10)
        require(UUID.fromString(row[0]).toString() == row[0])
        require(row[2].isNotBlank() && row[3].isNotBlank())
        if (source == "SCREEN") require(row[5] in listOf("pending", "pass", "partial", "fail"))
        val secret = key()?.takeIf { it.isNotBlank() } ?: error("API key required")
        val response = request(JSONObject().put("key", secret).put("schemaVersion", 1)
            .put("source", source).put("row", JSONArray(row)))
        require(response.getString("id") == row[0])
        return response.optBoolean("duplicado")
    }
}
