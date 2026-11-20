package com.clutterfree.weather.weather

import java.time.Instant

data class Place(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val office: String,
    val gridX: Int,
    val gridY: Int,
    val timeZone: String,
    val forecastUrl: String,
    val hourlyForecastUrl: String,
    val observationStationsUrl: String,
    val gridResolvedAt: Instant,
)
