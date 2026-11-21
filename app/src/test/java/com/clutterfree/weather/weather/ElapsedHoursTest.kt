package com.clutterfree.weather.weather

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class ElapsedHoursTest {
    private val zone: ZoneId = ZoneId.of("America/Chicago")

    @Test
    fun keepsHoursFromEarlierTodayThatTheForecastDropped() {
        val now = instant("2026-11-20T16:30:00-06:00")
        val merged = mergeElapsedToday(
            previous = listOf(hour("2026-11-20T08:00:00-06:00"), hour("2026-11-20T15:00:00-06:00")),
            fresh = listOf(hour("2026-11-20T16:00:00-06:00")),
            zone = zone,
            now = now,
        )

        assertEquals(listOf(8, 15, 16), merged.map { it.start.atZone(zone).hour })
    }

    @Test
    fun dropsThePreviousDayAtMidnight() {
        val merged = mergeElapsedToday(
            previous = listOf(hour("2026-11-20T22:00:00-06:00")),
            fresh = listOf(hour("2026-11-21T00:00:00-06:00")),
            zone = zone,
            now = instant("2026-11-21T00:10:00-06:00"),
        )

        assertEquals(listOf(instant("2026-11-21T00:00:00-06:00")), merged.map { it.start })
    }

    @Test
    fun freshHourReplacesTheStoredOne() {
        val merged = mergeElapsedToday(
            previous = listOf(hour("2026-11-20T16:00:00-06:00", celsius = 1.0)),
            fresh = listOf(hour("2026-11-20T16:00:00-06:00", celsius = 9.0)),
            zone = zone,
            now = instant("2026-11-20T16:30:00-06:00"),
        )

        assertEquals(9.0, merged.single().temperatureCelsius!!, 0.01)
    }

    @Test
    fun doesNotKeepAFutureHourTheNewForecastOmitted() {
        val merged = mergeElapsedToday(
            previous = listOf(hour("2026-11-20T18:00:00-06:00")),
            fresh = listOf(hour("2026-11-20T10:00:00-06:00")),
            zone = zone,
            now = instant("2026-11-20T10:00:00-06:00"),
        )

        assertEquals(listOf(10), merged.map { it.start.atZone(zone).hour })
    }

    private fun hour(start: String, celsius: Double = 10.0) = ForecastPeriod(
        start = Instant.parse(start),
        end = Instant.parse(start).plus(Duration.ofHours(1)),
        isDaytime = true,
        temperatureCelsius = celsius,
        windSpeedMetersPerSecond = null,
        windSpeedHighMetersPerSecond = null,
        windDirection = null,
        shortForecast = null,
        precipitationProbabilityPercent = null,
        iconUrl = null,
    )

    private fun instant(text: String): Instant = Instant.parse(text)
}
