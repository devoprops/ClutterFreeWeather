package com.clutterfree.weather.data

import com.clutterfree.weather.weather.AppConfig
import com.clutterfree.weather.weather.ExtendedDay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One national file for the days after the National Weather Service forecast.
 * A fresh copy is reused. After its cache window, a conditional request downloads
 * a new copy only when Cloudflare says the file changed. A failed request keeps
 * the copy already on disk.
 */
class ExtendedForecast(
    private val directory: File,
    private val http: TextSource = TextHttp(),
    private val now: () -> Instant = Instant::now,
) {
    fun ensure() {
        val current = now()
        val saved = readMeta()
        val body = bodyFile()
        if (saved != null && body.exists() && saved.freshUntil?.isAfter(current) == true) return
        val response = try {
            http.get(AppConfig.EXTENDED_FORECAST_URL, conditionalHeaders(saved))
        } catch (_: IOException) {
            return
        }
        if (response.code == NOT_MODIFIED && saved != null && body.exists()) {
            writeMeta(saved.copy(freshUntil = freshUntil(response.headers, current)))
            return
        }
        if (response.code !in 200..299 || !response.body.startsWith(DOCUMENT_PREFIX)) return
        val temporary = File(directory, "$BODY_NAME.tmp")
        temporary.writeText(response.body, Charsets.UTF_8)
        if (!temporary.renameTo(body)) {
            temporary.copyTo(body, overwrite = true)
            temporary.delete()
        }
        writeMeta(
            Meta(
                etag = response.headers.header("etag"),
                lastModified = response.headers.header("last-modified"),
                freshUntil = freshUntil(response.headers, current),
            ),
        )
    }

    fun daysFor(timeZone: String, latitude: Double, longitude: Double): List<ExtendedDay> {
        val file = bodyFile()
        if (!file.exists()) return emptyList()
        val root = try {
            JSONObject(file.readText())
        } catch (_: Exception) {
            return emptyList()
        }
        if (root.optInt("v") != 1) return emptyList()
        val zones = root.optJSONObject("zones") ?: return emptyList()
        val grid = root.optDouble("grid", AppConfig.EXTENDED_GRID_DEGREES)
        val row = selectExtendedPoint(zones, timeZone, latitude, longitude, grid) ?: return emptyList()
        return parseExtendedDays(row)
    }

    private fun conditionalHeaders(saved: Meta?): Map<String, String> = buildMap {
        put("User-Agent", NwsClient.USER_AGENT)
        put("Accept", "application/json")
        if (saved == null || !bodyFile().exists()) return@buildMap
        saved.etag?.let { put("If-None-Match", it) }
        saved.lastModified?.let { put("If-Modified-Since", it) }
    }

    private fun bodyFile(): File = File(directory, BODY_NAME)

    private fun readMeta(): Meta? {
        val file = File(directory, META_NAME)
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            Meta(
                etag = json.optString("etag").ifBlank { null },
                lastModified = json.optString("lastModified").ifBlank { null },
                freshUntil = json.optString("freshUntil").ifBlank { null }?.let(Instant::parse),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun writeMeta(meta: Meta) {
        directory.mkdirs()
        val json = JSONObject()
            .put("etag", meta.etag ?: "")
            .put("lastModified", meta.lastModified ?: "")
            .put("freshUntil", meta.freshUntil?.toString() ?: "")
        File(directory, META_NAME).writeText(json.toString())
    }

    private data class Meta(
        val etag: String?,
        val lastModified: String?,
        val freshUntil: Instant?,
    )

    private companion object {
        const val BODY_NAME = "extended.json"
        const val META_NAME = "extended-meta.json"
        const val NOT_MODIFIED = 304
        const val DOCUMENT_PREFIX = "{\"v\":1"
    }
}

internal fun selectExtendedPoint(
    zones: JSONObject,
    timeZone: String,
    latitude: Double,
    longitude: Double,
    grid: Double,
): JSONArray? {
    val key = extendedGridKey(latitude, longitude, grid)
    zones.optJSONObject(timeZone)?.optJSONArray(key)?.let { return it }
    val names = zones.keys()
    while (names.hasNext()) {
        zones.optJSONObject(names.next())?.optJSONArray(key)?.let { return it }
    }
    val limit = AppConfig.EXTENDED_MATCH_DEGREES
    var best: JSONArray? = null
    var bestDistance = limit * limit
    val zoneNames = zones.keys()
    while (zoneNames.hasNext()) {
        val points = zones.optJSONObject(zoneNames.next()) ?: continue
        val keys = points.keys()
        while (keys.hasNext()) {
            val point = keys.next()
            val parts = point.split(',')
            if (parts.size != 2) continue
            val pointLatitude = parts[0].toDoubleOrNull() ?: continue
            val pointLongitude = parts[1].toDoubleOrNull() ?: continue
            val latitudeGap = pointLatitude - latitude
            val longitudeGap = pointLongitude - longitude
            val distance = latitudeGap * latitudeGap + longitudeGap * longitudeGap
            if (distance < bestDistance) {
                bestDistance = distance
                best = points.optJSONArray(point)
            }
        }
    }
    return best
}

internal fun extendedGridKey(latitude: Double, longitude: Double, grid: Double): String {
    fun snap(value: Double): Double = (value / grid).roundToInt() * grid
    return String.format(Locale.US, "%.2f,%.2f", snap(latitude), snap(longitude))
}

internal fun parseExtendedDays(rows: JSONArray): List<ExtendedDay> = buildList {
    for (index in 0 until rows.length()) {
        val row = rows.optJSONArray(index) ?: continue
        if (row.length() < 9) continue
        val date = extendedDate(row.optInt(0)) ?: continue
        val condition = row.optString(8)
        if (condition.isBlank()) continue
        add(
            ExtendedDay(
                date = date,
                highFahrenheit = row.optInt(1),
                lowFahrenheit = row.optInt(2),
                precipHundredths = row.optInt(3),
                humidityPercent = row.optInt(5),
                windMph = row.optInt(6),
                windFromDegrees = row.optInt(7),
                condition = condition,
            ),
        )
    }
}

private fun extendedDate(stamp: Int): LocalDate? {
    val text = stamp.toString()
    if (text.length != 8) return null
    return try {
        LocalDate.of(text.substring(0, 4).toInt(), text.substring(4, 6).toInt(), text.substring(6, 8).toInt())
    } catch (_: Exception) {
        null
    }
}

private fun Map<String, String>.header(name: String): String? =
    entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.ifBlank { null }
