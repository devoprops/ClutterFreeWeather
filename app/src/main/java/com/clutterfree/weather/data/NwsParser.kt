package com.clutterfree.weather.data

import com.clutterfree.weather.weather.AppConfig
import com.clutterfree.weather.weather.ForecastPeriod
import com.clutterfree.weather.weather.Measures
import com.clutterfree.weather.weather.Observation
import com.clutterfree.weather.weather.Place
import com.clutterfree.weather.weather.WeatherAlert
import com.clutterfree.weather.weather.sunTimes
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

object NwsParser {
    fun parsePlace(
        json: String,
        displayName: String,
        latitude: Double,
        longitude: Double,
        resolvedAt: Instant,
    ): Place {
        val properties = JSONObject(json).getJSONObject("properties")
        return Place(
            displayName = displayName,
            latitude = latitude,
            longitude = longitude,
            office = properties.getString("gridId"),
            gridX = properties.getInt("gridX"),
            gridY = properties.getInt("gridY"),
            timeZone = properties.getString("timeZone"),
            forecastUrl = properties.getString("forecast"),
            hourlyForecastUrl = properties.getString("forecastHourly"),
            observationStationsUrl = properties.getString("observationStations"),
            gridResolvedAt = resolvedAt,
        )
    }

    fun parsePeriods(json: String): List<ForecastPeriod> {
        val periods = JSONObject(json).getJSONObject("properties").optJSONArray("periods") ?: JSONArray()
        return List(periods.length()) { index -> period(periods.getJSONObject(index)) }.filterNotNull()
    }

    fun parseFirstStation(json: String): StationLink? {
        val features = JSONObject(json).optJSONArray("features") ?: return null
        if (features.length() == 0) return null
        val feature = features.getJSONObject(0)
        val properties = feature.getJSONObject("properties")
        val id = properties.optString("@id").ifBlank { properties.optString("id") }
        if (id.isBlank()) return null
        val stationUrl = id.removeSuffix("/")
        val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
        val point = if (coordinates != null && coordinates.length() >= 2) coordinates else null
        return StationLink(
            id = stationUrl.substringAfterLast('/').ifBlank { null },
            name = properties.text("name"),
            observationUrl = "$stationUrl/observations/latest",
            latitude = point?.optDouble(1)?.takeUnless { it.isNaN() },
            longitude = point?.optDouble(0)?.takeUnless { it.isNaN() },
        )
    }

    fun parseObservation(json: String, station: StationLink? = null): Observation {
        val properties = JSONObject(json).getJSONObject("properties")
        val observedAt = instant(properties.text("timestamp"))
        val windSpeed = metersPerSecond(properties.optJSONObject("windSpeed"))
        val raw = properties.text("rawMessage")
        return Observation(
            observedAt = observedAt,
            stationName = station?.name,
            stationId = station?.id,
            stationLatitude = station?.latitude,
            stationLongitude = station?.longitude,
            temperatureCelsius = celsius(properties.optJSONObject("temperature")),
            dewpointCelsius = celsius(properties.optJSONObject("dewpoint")),
            windSpeedMetersPerSecond = windSpeed,
            windDirectionDegrees = degrees(properties.optJSONObject("windDirection")),
            windGustMetersPerSecond = metersPerSecond(properties.optJSONObject("windGust")),
            heatIndexCelsius = celsius(properties.optJSONObject("heatIndex")),
            windChillCelsius = celsius(properties.optJSONObject("windChill")),
            relativeHumidityPercent = percent(properties.optJSONObject("relativeHumidity")),
            textDescription = properties.text("textDescription"),
            iconUrl = properties.text("icon") ?: metarIconUrl(raw, observedAt, windSpeed, station),
            severeSpeci = raw?.let(::severeSpeci) == true,
        )
    }

    private fun metarIconUrl(
        raw: String?,
        observedAt: Instant?,
        windMetersPerSecond: Double?,
        station: StationLink?,
    ): String? {
        val sky = raw?.let(::metarCondition) ?: return null
        val windy = windMetersPerSecond != null &&
            windMetersPerSecond * Measures.MPH_PER_METER_PER_SECOND > AppConfig.WINDY_MPH
        val condition = if (windy) "wind_$sky" else sky
        val latitude = station?.latitude
        val longitude = station?.longitude
        val sun = if (observedAt != null && latitude != null && longitude != null) {
            sunTimes(observedAt.atZone(ZoneOffset.UTC).toLocalDate(), latitude, longitude, ZoneOffset.UTC)
        } else {
            null
        }
        val night = sun != null && (observedAt!!.isBefore(sun.sunrise) || !observedAt.isBefore(sun.sunset))
        return "$NWS_ICON_BASE/${if (night) "night" else "day"}/$condition"
    }

    fun parseAlerts(json: String): List<WeatherAlert> {
        val features = JSONObject(json).optJSONArray("features") ?: JSONArray()
        return List(features.length()) { index -> alert(features.getJSONObject(index)) }.filterNotNull()
    }

    private fun period(json: JSONObject): ForecastPeriod? {
        val start = instant(json.text("startTime")) ?: return null
        val end = instant(json.text("endTime")) ?: return null
        val wind = windText(json.optString("windSpeed"))
        val precipitation = json.optJSONObject("probabilityOfPrecipitation")
        return ForecastPeriod(
            start = start,
            end = end,
            isDaytime = json.optBoolean("isDaytime"),
            temperatureCelsius = periodTemperature(json),
            windSpeedMetersPerSecond = wind?.lowMetersPerSecond,
            windSpeedHighMetersPerSecond = wind?.highMetersPerSecond,
            windDirection = json.text("windDirection"),
            shortForecast = json.text("shortForecast"),
            precipitationProbabilityPercent = percent(precipitation),
            iconUrl = json.text("icon"),
            relativeHumidityPercent = percent(json.optJSONObject("relativeHumidity")),
            dewpointCelsius = celsius(json.optJSONObject("dewpoint")),
            detailedForecast = json.text("detailedForecast"),
        )
    }

    private fun alert(feature: JSONObject): WeatherAlert? {
        val properties = feature.optJSONObject("properties") ?: return null
        val id = feature.optString("id").ifBlank { properties.optString("id") }
        if (id.isBlank()) return null
        return WeatherAlert(
            id = id,
            event = properties.text("event"),
            severity = properties.text("severity"),
            headline = properties.text("headline"),
            onset = instant(properties.text("onset")),
            ends = instant(properties.text("ends")),
            instruction = properties.text("instruction"),
            description = properties.text("description"),
        )
    }

    private fun periodTemperature(json: JSONObject): Double? {
        if (!json.has("temperature") || json.isNull("temperature")) return null
        val value = json.getDouble("temperature")
        return when (json.optString("temperatureUnit")) {
            "F" -> (value - 32.0) * 5.0 / 9.0
            "C" -> value
            else -> null
        }
    }

    private fun celsius(quantity: JSONObject?): Double? {
        val value = quantityValue(quantity) ?: return null
        return when (quantity?.optString("unitCode")) {
            "wmoUnit:degC", "ucum:Cel" -> value
            "wmoUnit:degF", "ucum:[degF]" -> (value - 32.0) * 5.0 / 9.0
            else -> null
        }
    }

    private fun metersPerSecond(quantity: JSONObject?): Double? {
        val value = quantityValue(quantity) ?: return null
        return when (quantity?.optString("unitCode")) {
            "wmoUnit:m_s-1", "ucum:m/s" -> value
            "wmoUnit:km_h-1", "ucum:km/h" -> value / 3.6
            "wmoUnit:mph" -> value * MPH_TO_METERS_PER_SECOND
            "wmoUnit:knot", "wmoUnit:kt" -> value * KNOTS_TO_METERS_PER_SECOND
            else -> null
        }
    }

    private fun degrees(quantity: JSONObject?): Double? {
        val value = quantityValue(quantity) ?: return null
        return when (quantity?.optString("unitCode")) {
            "wmoUnit:degree_(angle)", "ucum:deg" -> value
            else -> null
        }
    }

    private fun percent(quantity: JSONObject?): Double? {
        val value = quantityValue(quantity) ?: return null
        return if (quantity?.optString("unitCode") == "wmoUnit:percent") value else null
    }

    private fun quantityValue(quantity: JSONObject?): Double? {
        if (quantity == null || !quantity.has("value") || quantity.isNull("value")) return null
        return quantity.getDouble("value")
    }

    private fun windText(text: String): WindText? {
        val match = WIND_TEXT.find(text) ?: return null
        val low = match.groupValues[1].toDouble()
        val high = match.groupValues[2].toDoubleOrNull()
        val unit = match.groupValues[3].lowercase()
        return WindText(
            lowMetersPerSecond = toMetersPerSecond(low, unit),
            highMetersPerSecond = high?.let { toMetersPerSecond(it, unit) },
        )
    }

    private fun toMetersPerSecond(value: Double, unit: String): Double = when (unit) {
        "mph" -> value * MPH_TO_METERS_PER_SECOND
        "km/h" -> value / 3.6
        "m/s" -> value
        "kt", "knots" -> value * KNOTS_TO_METERS_PER_SECOND
        else -> throw IllegalArgumentException("Unknown wind unit $unit")
    }

    /** Android's optString returns the word "null" for a JSON null. */
    private fun JSONObject.text(name: String): String? =
        if (isNull(name)) null else optString(name).ifBlank { null }

    private fun instant(text: String?): Instant? {
        if (text.isNullOrBlank()) return null
        return OffsetDateTime.parse(text).toInstant()
    }

    private data class WindText(
        val lowMetersPerSecond: Double,
        val highMetersPerSecond: Double?,
    )

    private val WIND_TEXT = Regex(
        """(\d+(?:\.\d+)?)(?:\s+to\s+(\d+(?:\.\d+)?))?\s*(mph|km/h|m/s|kt|knots)""",
        RegexOption.IGNORE_CASE,
    )

    private const val NWS_ICON_BASE = "https://api.weather.gov/icons/land"
    private const val MPH_TO_METERS_PER_SECOND = 0.44704
    private const val KNOTS_TO_METERS_PER_SECOND = 0.514444
}

data class StationLink(
    val id: String?,
    val name: String?,
    val observationUrl: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
