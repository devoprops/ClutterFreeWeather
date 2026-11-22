package com.clutterfree.weather.weather

import java.time.Instant

data class WeatherSnapshot(
    val fetchedAt: Instant,
    val observation: Observation?,
    val hourly: List<ForecastPeriod>,
    val dayNight: List<ForecastPeriod>,
    val alerts: List<WeatherAlert>,
)

data class Observation(
    val observedAt: Instant?,
    val stationName: String?,
    val stationId: String? = null,
    val temperatureCelsius: Double?,
    val dewpointCelsius: Double?,
    val windSpeedMetersPerSecond: Double?,
    val windDirectionDegrees: Double?,
    val windGustMetersPerSecond: Double?,
    val heatIndexCelsius: Double?,
    val windChillCelsius: Double?,
    val relativeHumidityPercent: Double?,
    val textDescription: String?,
    val iconUrl: String?,
)

data class ForecastPeriod(
    val start: Instant,
    val end: Instant,
    val isDaytime: Boolean,
    val temperatureCelsius: Double?,
    val windSpeedMetersPerSecond: Double?,
    val windSpeedHighMetersPerSecond: Double?,
    val windDirection: String?,
    val shortForecast: String?,
    val precipitationProbabilityPercent: Double?,
    val iconUrl: String?,
    val relativeHumidityPercent: Double? = null,
    val dewpointCelsius: Double? = null,
    val detailedForecast: String? = null,
)

data class WeatherAlert(
    val id: String,
    val event: String?,
    val severity: String?,
    val headline: String?,
    val onset: Instant?,
    val ends: Instant?,
    val instruction: String?,
    val description: String?,
)
