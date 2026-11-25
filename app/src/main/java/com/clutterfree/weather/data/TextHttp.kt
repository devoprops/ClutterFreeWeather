package com.clutterfree.weather.data

import java.net.HttpURLConnection
import java.net.URL

interface TextSource {
    fun get(url: String, headers: Map<String, String> = emptyMap()): TextHttp.Response
}

class TextHttp : TextSource {
    data class Response(val code: Int, val body: String, val headers: Map<String, String> = emptyMap())

    override fun get(url: String, headers: Map<String, String>): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val received = buildMap {
                connection.headerFields.forEach { (name, values) ->
                    if (name != null && values.isNotEmpty()) put(name.lowercase(), values.joinToString(","))
                }
            }
            Response(code, body, received)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
    }
}
