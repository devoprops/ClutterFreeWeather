package com.clutterfree.weather.data

import com.clutterfree.weather.weather.DisplayUnits
import com.clutterfree.weather.weather.ForecastPeriod
import com.clutterfree.weather.weather.HomeCard
import com.clutterfree.weather.weather.HomeSettings
import com.clutterfree.weather.weather.Observation
import com.clutterfree.weather.weather.Place
import com.clutterfree.weather.weather.PrecipitationUnit
import com.clutterfree.weather.weather.TemperatureUnit
import com.clutterfree.weather.weather.WeatherAlert
import com.clutterfree.weather.weather.WeatherSnapshot
import com.clutterfree.weather.weather.WindUnit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

class WeatherStore(private val directory: File) {
    private val lock = Any()

    fun readUnits(): DisplayUnits = synchronized(lock) { readPlaceFile().units }

    fun writeUnits(units: DisplayUnits) = synchronized(lock) {
        val current = readPlaceFile()
        writePlaceFile(current.copy(units = units))
    }

    fun readHome(): HomeSettings = synchronized(lock) { readPlaceFile().home }

    fun writeHome(home: HomeSettings) = synchronized(lock) {
        val current = readPlaceFile()
        writePlaceFile(current.copy(home = home))
    }

    fun readPlace(): Place? = synchronized(lock) { readPlaceFile().place }

    fun writePlace(place: Place) = synchronized(lock) {
        val current = readPlaceFile()
        writePlaceFile(current.copy(place = place))
    }

    fun readSnapshot(): WeatherSnapshot? = synchronized(lock) { readSnapshotFile() }

    fun writeSnapshot(snapshot: WeatherSnapshot) = synchronized(lock) {
        write(File(directory, SNAPSHOT_FILE), snapshotJson(snapshot))
    }

    fun clearSnapshot() = synchronized(lock) {
        File(directory, SNAPSHOT_FILE).delete()
    }

    private fun readSnapshotFile(): WeatherSnapshot? {
        val file = File(directory, SNAPSHOT_FILE)
        if (!file.exists()) return null
        return try {
            snapshot(JSONObject(file.readText()))
        } catch (_: Exception) {
            null
        }
    }

    private fun readPlaceFile(): StoredPlace {
        val file = File(directory, PLACE_FILE)
        if (!file.exists()) return StoredPlace(DisplayUnits(), HomeSettings(), null)
        return try {
            val json = JSONObject(file.readText())
            StoredPlace(
                units = units(json.optJSONObject("units")),
                home = home(json.optJSONObject("home")),
                place = json.optJSONObject("place")?.let(::place),
            )
        } catch (_: Exception) {
            StoredPlace(DisplayUnits(), HomeSettings(), null)
        }
    }

    private fun writePlaceFile(stored: StoredPlace) {
        val json = JSONObject()
            .put("units", unitsJson(stored.units))
            .put("home", homeJson(stored.home))
            .put("place", stored.place?.let(::placeJson) ?: JSONObject.NULL)
        write(File(directory, PLACE_FILE), json)
    }

    private fun write(file: File, json: JSONObject) {
        directory.mkdirs()
        val temporary = File(directory, "${file.name}.tmp")
        temporary.writeText(json.toString())
        if (file.exists() && !file.delete()) {
            temporary.delete()
            error("Could not replace ${file.name}")
        }
        if (!temporary.renameTo(file)) {
            file.writeText(temporary.readText())
            temporary.delete()
        }
    }

    private fun units(json: JSONObject?): DisplayUnits {
        if (json == null) return DisplayUnits()
        return DisplayUnits(
            temperature = enumValue(json.optString("temperature"), TemperatureUnit.FAHRENHEIT),
            wind = enumValue(json.optString("wind"), WindUnit.MILES_PER_HOUR),
            precipitation = enumValue(json.optString("precipitation"), PrecipitationUnit.INCHES),
        )
    }

    private fun unitsJson(units: DisplayUnits): JSONObject = JSONObject()
        .put("temperature", units.temperature.name)
        .put("wind", units.wind.name)
        .put("precipitation", units.precipitation.name)

    private fun home(json: JSONObject?): HomeSettings {
        if (json == null) return HomeSettings()
        val storedOrder = json.optJSONArray("cardOrder")
        val cards = if (storedOrder == null) {
            HomeSettings().cardOrder
        } else {
            List(storedOrder.length()) { index ->
                runCatching { HomeCard.valueOf(storedOrder.getString(index)) }.getOrNull()
            }.filterNotNull().ifEmpty { HomeSettings().cardOrder }
        }
        val order = cards + HomeCard.entries.filter { it !in cards }
        return HomeSettings(
            cardOrder = order,
            showTemperature = json.optBoolean("showTemperature", true),
            showRainChance = json.optBoolean("showRainChance", true),
            showHumidity = json.optBoolean("showHumidity", false),
            showWind = json.optBoolean("showWind", false),
            showDewpoint = json.optBoolean("showDewpoint", false),
            extendedVisibleDays = json.optInt("extendedVisibleDays", 3).coerceIn(1, 7),
            radarMiles = json.optInt("radarMiles", 50).coerceAtLeast(1),
        )
    }

    private fun homeJson(home: HomeSettings): JSONObject = JSONObject()
        .put("cardOrder", JSONArray().apply { home.cardOrder.forEach { put(it.name) } })
        .put("showTemperature", home.showTemperature)
        .put("showRainChance", home.showRainChance)
        .put("showHumidity", home.showHumidity)
        .put("showWind", home.showWind)
        .put("showDewpoint", home.showDewpoint)
        .put("extendedVisibleDays", home.extendedVisibleDays)
        .put("radarMiles", home.radarMiles)

    private fun place(json: JSONObject): Place = Place(
        displayName = json.getString("displayName"),
        latitude = json.getDouble("latitude"),
        longitude = json.getDouble("longitude"),
        office = json.getString("office"),
        gridX = json.getInt("gridX"),
        gridY = json.getInt("gridY"),
        timeZone = json.getString("timeZone"),
        forecastUrl = json.getString("forecastUrl"),
        hourlyForecastUrl = json.getString("hourlyForecastUrl"),
        observationStationsUrl = json.getString("observationStationsUrl"),
        gridResolvedAt = Instant.parse(json.getString("gridResolvedAt")),
    )

    private fun placeJson(place: Place): JSONObject = JSONObject()
        .put("displayName", place.displayName)
        .put("latitude", place.latitude)
        .put("longitude", place.longitude)
        .put("office", place.office)
        .put("gridX", place.gridX)
        .put("gridY", place.gridY)
        .put("timeZone", place.timeZone)
        .put("forecastUrl", place.forecastUrl)
        .put("hourlyForecastUrl", place.hourlyForecastUrl)
        .put("observationStationsUrl", place.observationStationsUrl)
        .put("gridResolvedAt", place.gridResolvedAt.toString())

    private fun snapshot(json: JSONObject): WeatherSnapshot = WeatherSnapshot(
        fetchedAt = Instant.parse(json.getString("fetchedAt")),
        observation = json.optJSONObject("observation")?.let(::observation),
        hourly = periods(json.optJSONArray("hourly")),
        dayNight = periods(json.optJSONArray("dayNight")),
        alerts = alerts(json.optJSONArray("alerts")),
    )

    private fun snapshotJson(snapshot: WeatherSnapshot): JSONObject = JSONObject()
        .put("fetchedAt", snapshot.fetchedAt.toString())
        .put("observation", snapshot.observation?.let(::observationJson) ?: JSONObject.NULL)
        .put("hourly", JSONArray().apply { snapshot.hourly.forEach { put(periodJson(it)) } })
        .put("dayNight", JSONArray().apply { snapshot.dayNight.forEach { put(periodJson(it)) } })
        .put("alerts", JSONArray().apply { snapshot.alerts.forEach { put(alertJson(it)) } })

    private fun periods(array: JSONArray?): List<ForecastPeriod> {
        if (array == null) return emptyList()
        return List(array.length()) { index -> period(array.getJSONObject(index)) }
    }

    private fun period(json: JSONObject): ForecastPeriod = ForecastPeriod(
        start = Instant.parse(json.getString("start")),
        end = Instant.parse(json.getString("end")),
        isDaytime = json.getBoolean("isDaytime"),
        temperatureCelsius = optionalDouble(json, "temperatureCelsius"),
        windSpeedMetersPerSecond = optionalDouble(json, "windSpeedMetersPerSecond"),
        windSpeedHighMetersPerSecond = optionalDouble(json, "windSpeedHighMetersPerSecond"),
        windDirection = optionalString(json, "windDirection"),
        shortForecast = optionalString(json, "shortForecast"),
        precipitationProbabilityPercent = optionalDouble(json, "precipitationProbabilityPercent"),
        iconUrl = optionalString(json, "iconUrl"),
        relativeHumidityPercent = optionalDouble(json, "relativeHumidityPercent"),
        dewpointCelsius = optionalDouble(json, "dewpointCelsius"),
        detailedForecast = optionalString(json, "detailedForecast"),
    )

    private fun periodJson(period: ForecastPeriod): JSONObject = JSONObject()
        .put("start", period.start.toString())
        .put("end", period.end.toString())
        .put("isDaytime", period.isDaytime)
        .putNullable("temperatureCelsius", period.temperatureCelsius)
        .putNullable("windSpeedMetersPerSecond", period.windSpeedMetersPerSecond)
        .putNullable("windSpeedHighMetersPerSecond", period.windSpeedHighMetersPerSecond)
        .putNullable("windDirection", period.windDirection)
        .putNullable("shortForecast", period.shortForecast)
        .putNullable("precipitationProbabilityPercent", period.precipitationProbabilityPercent)
        .putNullable("iconUrl", period.iconUrl)
        .putNullable("relativeHumidityPercent", period.relativeHumidityPercent)
        .putNullable("dewpointCelsius", period.dewpointCelsius)
        .putNullable("detailedForecast", period.detailedForecast)

    private fun observation(json: JSONObject): Observation = Observation(
        observedAt = optionalString(json, "observedAt")?.let(Instant::parse),
        stationName = optionalString(json, "stationName"),
        temperatureCelsius = optionalDouble(json, "temperatureCelsius"),
        dewpointCelsius = optionalDouble(json, "dewpointCelsius"),
        windSpeedMetersPerSecond = optionalDouble(json, "windSpeedMetersPerSecond"),
        windDirectionDegrees = optionalDouble(json, "windDirectionDegrees"),
        windGustMetersPerSecond = optionalDouble(json, "windGustMetersPerSecond"),
        heatIndexCelsius = optionalDouble(json, "heatIndexCelsius"),
        windChillCelsius = optionalDouble(json, "windChillCelsius"),
        relativeHumidityPercent = optionalDouble(json, "relativeHumidityPercent"),
        textDescription = optionalString(json, "textDescription"),
        iconUrl = optionalString(json, "iconUrl"),
    )

    private fun observationJson(observation: Observation): JSONObject = JSONObject()
        .putNullable("observedAt", observation.observedAt?.toString())
        .putNullable("stationName", observation.stationName)
        .putNullable("temperatureCelsius", observation.temperatureCelsius)
        .putNullable("dewpointCelsius", observation.dewpointCelsius)
        .putNullable("windSpeedMetersPerSecond", observation.windSpeedMetersPerSecond)
        .putNullable("windDirectionDegrees", observation.windDirectionDegrees)
        .putNullable("windGustMetersPerSecond", observation.windGustMetersPerSecond)
        .putNullable("heatIndexCelsius", observation.heatIndexCelsius)
        .putNullable("windChillCelsius", observation.windChillCelsius)
        .putNullable("relativeHumidityPercent", observation.relativeHumidityPercent)
        .putNullable("textDescription", observation.textDescription)
        .putNullable("iconUrl", observation.iconUrl)

    private fun alerts(array: JSONArray?): List<WeatherAlert> {
        if (array == null) return emptyList()
        return List(array.length()) { index -> alert(array.getJSONObject(index)) }
    }

    private fun alert(json: JSONObject): WeatherAlert = WeatherAlert(
        id = json.getString("id"),
        event = optionalString(json, "event"),
        severity = optionalString(json, "severity"),
        headline = optionalString(json, "headline"),
        onset = optionalString(json, "onset")?.let(Instant::parse),
        ends = optionalString(json, "ends")?.let(Instant::parse),
        instruction = optionalString(json, "instruction"),
        description = optionalString(json, "description"),
    )

    private fun alertJson(alert: WeatherAlert): JSONObject = JSONObject()
        .put("id", alert.id)
        .putNullable("event", alert.event)
        .putNullable("severity", alert.severity)
        .putNullable("headline", alert.headline)
        .putNullable("onset", alert.onset?.toString())
        .putNullable("ends", alert.ends?.toString())
        .putNullable("instruction", alert.instruction)
        .putNullable("description", alert.description)

    private fun optionalString(json: JSONObject, name: String): String? {
        if (!json.has(name) || json.isNull(name)) return null
        return json.getString(name)
    }

    private fun optionalDouble(json: JSONObject, name: String): Double? {
        if (!json.has(name) || json.isNull(name)) return null
        return json.getDouble(name)
    }

    private fun <T : Enum<T>> enumValue(raw: String, fallback: T): T {
        if (raw.isBlank()) return fallback
        return try {
            java.lang.Enum.valueOf(fallback.declaringJavaClass, raw)
        } catch (_: IllegalArgumentException) {
            fallback
        }
    }

    private data class StoredPlace(
        val units: DisplayUnits,
        val home: HomeSettings,
        val place: Place?,
    )

    private companion object {
        const val PLACE_FILE = "place.json"
        const val SNAPSHOT_FILE = "snapshot.json"
    }
}

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject {
    if (value == null) put(name, JSONObject.NULL) else put(name, value)
    return this
}
