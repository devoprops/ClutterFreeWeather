package com.clutterfree.weather.data

import com.clutterfree.weather.weather.CardTheme
import com.clutterfree.weather.weather.DetailItem
import com.clutterfree.weather.weather.ForecastPeriod
import com.clutterfree.weather.weather.GraphSettings
import com.clutterfree.weather.weather.HomeCard
import com.clutterfree.weather.weather.HomeSettings
import com.clutterfree.weather.weather.Place
import com.clutterfree.weather.weather.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                hourlyGraph = GraphSettings(visible = false, humidity = true),
                details = setOf(DetailItem.SUNRISE),
                extendedVisibleDays = 4,
                radarMiles = 40,
                cardTheme = CardTheme.WEATHER,
            ),
        )

        val stored = store.readSnapshot()!!.hourly.single()
        assertEquals(55.0, stored.relativeHumidityPercent!!, 0.01)
        assertEquals(10.0, stored.dewpointCelsius!!, 0.01)
        assertEquals("Partly cloudy, with a high near 68.", stored.detailedForecast)
        val home = store.readHome()
        assertFalse(home.hourlyGraph.visible)
        assertTrue(home.hourlyGraph.humidity)
        assertTrue(home.todayGraph.visible)
        assertEquals(setOf(DetailItem.SUNRISE), home.details)
        assertEquals(4, home.extendedVisibleDays)
        assertEquals(40, home.radarMiles)
        assertEquals(CardTheme.WEATHER, home.cardTheme)
        assertEquals(HomeCard.TODAY, home.cardOrder.first())
    }

    @Test
    fun olderGraphFlagsApplyToEveryCard() {
        val directory = Files.createTempDirectory("weather-store-flags").toFile()
        java.io.File(directory, "place.json").writeText(
            """{"home": {"showHumidity": true, "showRainChance": false}, "place": null}""",
        )

        val home = WeatherStore(directory).readHome()
        listOf(home.todayGraph, home.hourlyGraph, home.extendedGraph).forEach { graph ->
            assertTrue(graph.humidity)
            assertFalse(graph.rainChance)
        }
        assertEquals(CardTheme.PLAIN, home.cardTheme)
    }

    @Test
    fun savedPlacesStayUntilRemovedAndTheCurrentOneCannotBeRemoved() {
        val store = WeatherStore(Files.createTempDirectory("weather-store-saved").toFile())
        store.writePlace(place("Chicago, IL"))
        store.writePlace(place("Huntsville, AL"))

        assertEquals(listOf("Chicago, IL", "Huntsville, AL"), store.readSaved().map { it.displayName })
        store.removeSaved("Huntsville, AL")
        assertEquals(2, store.readSaved().size)
        store.removeSaved("Chicago, IL")
        assertEquals(listOf("Huntsville, AL"), store.readSaved().map { it.displayName })
    }

    @Test
    fun savedPlacesKeepTheChosenOrder() {
        val store = WeatherStore(Files.createTempDirectory("weather-store-order").toFile())
        store.writePlace(place("Chicago, IL"))
        store.writePlace(place("Huntsville, AL"))
        store.writePlace(place("Denver, CO"))

        store.reorderSaved(listOf("Denver, CO", "Chicago, IL", "Huntsville, AL"))
        assertEquals(listOf("Denver, CO", "Chicago, IL", "Huntsville, AL"), store.readSaved().map { it.displayName })
    }

    @Test
    fun detailItemsAddedLaterAreOnForOlderFilesButStayOffOnceChosen() {
        val directory = Files.createTempDirectory("weather-store-details").toFile()
        java.io.File(directory, "place.json").writeText(
            """{"home": {"details": ["SUNRISE"]}, "place": null}""",
        )
        val store = WeatherStore(directory)
        val upgraded = store.readHome().details
        assertEquals(setOf(DetailItem.SUNRISE, DetailItem.CURRENT_TEXT, DetailItem.WIND_CHILL_HEAT_INDEX), upgraded)

        store.writeHome(store.readHome().copy(details = setOf(DetailItem.SUNRISE)))
        assertEquals(setOf(DetailItem.SUNRISE), store.readHome().details)
    }

    @Test
    fun olderPlaceFileListsTheCurrentPlaceAsSaved() {
        val directory = Files.createTempDirectory("weather-store-legacy").toFile()
        val store = WeatherStore(directory)
        store.writePlace(place("Chicago, IL"))
        val file = java.io.File(directory, "place.json")
        val json = org.json.JSONObject(file.readText())
        json.remove("saved")
        file.writeText(json.toString())

        assertEquals(listOf("Chicago, IL"), store.readSaved().map { it.displayName })
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

    private fun place(name: String) = Place(
        displayName = name,
        latitude = 41.0,
        longitude = -87.0,
        office = "LOT",
        gridX = 1,
        gridY = 1,
        timeZone = "America/Chicago",
        forecastUrl = "https://api.weather.gov/gridpoints/LOT/1,1/forecast",
        hourlyForecastUrl = "https://api.weather.gov/gridpoints/LOT/1,1/forecast/hourly",
        observationStationsUrl = "https://api.weather.gov/gridpoints/LOT/1,1/stations",
        gridResolvedAt = Instant.parse("2026-11-20T00:00:00Z"),
    )
}
