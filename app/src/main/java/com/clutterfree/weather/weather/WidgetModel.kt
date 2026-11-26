package com.clutterfree.weather.weather

import java.time.Instant
import java.time.ZoneId

data class WidgetHour(
    val start: Instant,
    val iconUrl: String?,
    val temperatureCelsius: Double?,
)

data class WidgetModel(
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
    val zone: ZoneId,
    val observedAt: Instant?,
    val iconUrl: String?,
    val temperatureCelsius: Double?,
    val highCelsius: Double?,
    val lowCelsius: Double?,
    val windMetersPerSecond: Double?,
    val windDirection: String?,
    val hours: List<WidgetHour>,
)

/** Current conditions, then the next [AppConfig.WIDGET_HOUR_COUNT] hours that have not started. */
fun widgetModel(place: Place, snapshot: WeatherSnapshot, now: Instant): WidgetModel {
    val zone = runCatching { ZoneId.of(place.timeZone) }.getOrElse { ZoneId.systemDefault() }
    val today = now.atZone(zone).toLocalDate()
    val day = civilDays(snapshot.dayNight, snapshot.hourly, zone).firstOrNull { it.date == today }
    val current = snapshot.hourly.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
    val observation = snapshot.observation
    val hours = snapshot.hourly
        .filter { it.start.isAfter(now) }
        .take(AppConfig.WIDGET_HOUR_COUNT)
        .map { period ->
            WidgetHour(
                start = period.start,
                iconUrl = period.iconUrl,
                temperatureCelsius = period.temperatureCelsius,
            )
        }
    return WidgetModel(
        placeName = place.displayName,
        latitude = place.latitude,
        longitude = place.longitude,
        zone = zone,
        observedAt = observation?.observedAt,
        iconUrl = observation?.iconUrl ?: current?.iconUrl,
        temperatureCelsius = observation?.temperatureCelsius ?: current?.temperatureCelsius,
        highCelsius = day?.highCelsius,
        lowCelsius = day?.lowCelsius,
        windMetersPerSecond = observation?.windSpeedMetersPerSecond ?: current?.windSpeedMetersPerSecond,
        windDirection = observation?.windDirectionDegrees?.let(::compassPoint) ?: current?.windDirection,
        hours = hours,
    )
}
