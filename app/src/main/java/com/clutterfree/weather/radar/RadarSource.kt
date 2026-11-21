package com.clutterfree.weather.radar

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.clutterfree.weather.data.NwsClient
import com.clutterfree.weather.data.TextHttp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** Radar images from the Iowa Environmental Mesonet NEXRAD composite. */
class RadarSource(private val http: TextHttp = TextHttp()) {
    fun latestValid(): Instant? = runCatching {
        val response = http.get(LATEST_URL, mapOf("User-Agent" to NwsClient.USER_AGENT))
        if (response.code !in 200..299) return null
        Instant.parse(JSONObject(response.body).getJSONObject("meta").getString("valid"))
    }.getOrNull()

    fun tile(key: TileKey): ImageBitmap? = runCatching {
        val connection = URL(radarTileUrl(key)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", NwsClient.USER_AGENT)
            if (connection.responseCode !in 200..299) return null
            val bytes = connection.inputStream.use { it.readBytes() }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private companion object {
        const val LATEST_URL = "https://mesonet.agron.iastate.edu/data/gis/images/4326/USCOMP/n0q_0.json"
        const val TIMEOUT_MS = 15_000
    }
}
