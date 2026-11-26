package com.clutterfree.weather.weather

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WidgetModelTest {
    @Test
    fun nextHoursSkipThePeriodAlreadyUnderway() {
        val now = Instant.parse("2026-11-26T16:30:00Z")
        val hourly = (0..4).map { offset ->
            val start = Instant.parse("2026-11-26T16:00:00Z").plus(Duration.ofHours(offset.toLong()))
            period(start, temperature = 10.0 + offset)
        }
        val model = widgetModel(place(), snapshot(hourly), now)

        assertEquals(
            listOf("2026-11-26T17:00:00Z", "2026-11-26T18:00:00Z"),
            model.hours.map { it.start.toString() },
        )
        assertEquals(2, model.hours.size)
    }

    @Test
    fun currentTemperaturePrefersTheObservation() {
        val now = Instant.parse("2026-11-26T16:30:00Z")
        val hourly = listOf(period(Instant.parse("2026-11-26T16:00:00Z"), temperature = 8.0))
        val stored = snapshot(hourly).copy(
            observation = Observation(
                observedAt = now,
                stationName = "Huntsville",
                temperatureCelsius = 12.0,
                dewpointCelsius = null,
                windSpeedMetersPerSecond = 1.0,
                windDirectionDegrees = 0.0,
                windGustMetersPerSecond = null,
                heatIndexCelsius = null,
                windChillCelsius = null,
                relativeHumidityPercent = null,
                textDescription = null,
                iconUrl = "https://api.weather.gov/icons/land/day/few",
            ),
        )
        val model = widgetModel(place(), stored, now)

        assertEquals(12.0, model.temperatureCelsius!!, 0.01)
        assertEquals("N", model.windDirection)
        assertEquals(emptyList<WidgetHour>(), model.hours)
    }

    private fun place() = Place(
        displayName = "Huntsville, AL",
        latitude = 34.73,
        longitude = -86.59,
        office = "HUN",
        gridX = 1,
        gridY = 1,
        timeZone = "UTC",
        forecastUrl = "https://api.weather.gov/forecast",
        hourlyForecastUrl = "https://api.weather.gov/forecast/hourly",
        observationStationsUrl = "https://api.weather.gov/stations",
        gridResolvedAt = Instant.parse("2026-11-26T00:00:00Z"),
    )

    private fun snapshot(hourly: List<ForecastPeriod>) = WeatherSnapshot(
        fetchedAt = Instant.parse("2026-11-26T16:00:00Z"),
        observation = null,
        hourly = hourly,
        dayNight = emptyList(),
        alerts = emptyList(),
    )

    private fun period(start: Instant, temperature: Double) = ForecastPeriod(
        start = start,
        end = start.plus(Duration.ofHours(1)),
        isDaytime = true,
        temperatureCelsius = temperature,
        windSpeedMetersPerSecond = null,
        windSpeedHighMetersPerSecond = null,
        windDirection = null,
        shortForecast = "Clear",
        precipitationProbabilityPercent = null,
        iconUrl = "https://api.weather.gov/icons/land/day/skc",
    )
}
