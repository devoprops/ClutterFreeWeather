package com.clutterfree.weather.data

import java.util.Locale

class NwsClient(private val http: TextSource = TextHttp()) {
    fun points(latitude: Double, longitude: Double): NwsDocument {
        val url = String.format(Locale.US, "https://api.weather.gov/points/%.4f,%.4f", latitude, longitude)
        return get(url)
    }

    fun alerts(latitude: Double, longitude: Double): NwsDocument {
        val url = String.format(
            Locale.US,
            "https://api.weather.gov/alerts/active?point=%.4f,%.4f",
            latitude,
            longitude,
        )
        return get(url)
    }

    fun get(url: String): NwsDocument {
        val response = http.get(url, NWS_HEADERS)
        return NwsDocument(response.code, response.body)
    }

    companion object {
        const val USER_AGENT = "(ClutterFreeWeather, noahmagnuson@gmail.com)"

        private val NWS_HEADERS = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/geo+json",
        )
    }
}

data class NwsDocument(val code: Int, val body: String) {
    val isSuccess: Boolean get() = code in 200..299
    val isMissing: Boolean get() = code == 404 || code == 410
}
