package com.clutterfree.weather.data

import com.clutterfree.weather.weather.ForecastPeriod
import com.clutterfree.weather.weather.HomeCard
import com.clutterfree.weather.weather.HomeSettings
import com.clutterfree.weather.weather.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Instant

class WeatherStoreTest {
    @Test
    fun snapshotKeepsHumidityDewpointAndHomeSettings() {
        val directory = Files.createTempDirectory("weather-store").toFile()
        val store = WeatherStore(directory)
        val period = ForecastPeriod(
            start = Instant.parse("2026-11-20T18:00:00Z"),
            end = Instant.parse("2026-11-20T19:00:00Z"),
            isDaytime = true,
            temperatureCelsius = 20.0,
            windSpeedMetersPerSecond = 3.0,
            windSpeedHighMetersPerSecond = null,
            windDirection = "NW",
            shortForecast = "Partly cloudy",
            precipitationProbabilityPercent = 10.0,
            iconUrl = "https://api.weather.gov/icons/land/day/sct?size=medium",
            relativeHumidityPercent = 55.0,
            dewpointCelsius = 10.0,
            detailedForecast = "Partly cloudy, with a high near 68.",
        )
        store.writeSnapshot(
            WeatherSnapshot(
                fetchedAt = Instant.parse("2026-11-20T18:05:00Z"),
                observation = null,
                hourly = listOf(period),
                dayNight = emptyList(),
                alerts = emptyList(),
            ),
        )
        store.writeHome(
            HomeSettings(
                showHumidity = true,
                extendedVisibleDays = 4,
                radarMiles = 40,
            ),
        )

        val stored = store.readSnapshot()!!.hourly.single()
        assertEquals(55.0, stored.relativeHumidityPercent!!, 0.01)
        assertEquals(10.0, stored.dewpointCelsius!!, 0.01)
        assertEquals("Partly cloudy, with a high near 68.", stored.detailedForecast)
        val home = store.readHome()
        assertTrue(home.showHumidity)
        assertEquals(4, home.extendedVisibleDays)
        assertEquals(40, home.radarMiles)
        assertEquals(HomeCard.TODAY, home.cardOrder.first())
    }

    @Test
    fun olderSnapshotWithoutHumidityStillLoads() {
        val directory = Files.createTempDirectory("weather-store-old").toFile()
        val store = WeatherStore(directory)
        java.io.File(directory, "snapshot.json").writeText(
            """
            {
              "fetchedAt": "2026-11-20T00:00:00Z",
              "observation": null,
              "hourly": [{
                "start": "2026-11-20T00:00:00Z",
                "end": "2026-11-20T01:00:00Z",
                "isDaytime": false,
                "temperatureCelsius": 1,
                "windSpeedMetersPerSecond": null,
                "windSpeedHighMetersPerSecond": null,
                "windDirection": null,
                "shortForecast": "Clear",
                "precipitationProbabilityPercent": null,
                "iconUrl": null
              }],
              "dayNight": [],
              "alerts": []
            }
            """.trimIndent(),
        )

        val period = store.readSnapshot()!!.hourly.single()
        assertNull(period.relativeHumidityPercent)
        assertNull(period.dewpointCelsius)
        assertNull(period.detailedForecast)
        assertEquals(3, store.readHome().extendedVisibleDays)
    }
}
