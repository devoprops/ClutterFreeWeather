package com.clutterfree.weather.data

import com.clutterfree.weather.weather.DisplayUnits
import com.clutterfree.weather.weather.PrecipitationUnit
import com.clutterfree.weather.weather.TemperatureUnit
import com.clutterfree.weather.weather.UnitFormatter
import com.clutterfree.weather.weather.WindUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NwsParserTest {
    @Test
    fun parsePlaceKeepsLinksFromThePointsDocument() {
        val place = NwsParser.parsePlace(
            json = fixture("nws-points.json"),
            displayName = "Chicago, IL",
            latitude = 41.8843,
            longitude = -87.6324,
            resolvedAt = Instant.parse("2026-11-19T23:00:00Z"),
        )

        assertEquals("LOT", place.office)
        assertEquals(72, place.gridX)
        assertEquals(69, place.gridY)
        assertEquals("America/Chicago", place.timeZone)
        assertEquals("https://api.weather.gov/gridpoints/LOT/72,69/forecast", place.forecastUrl)
        assertEquals("https://api.weather.gov/gridpoints/LOT/72,69/forecast/hourly", place.hourlyForecastUrl)
        assertEquals("https://api.weather.gov/gridpoints/LOT/72,69/stations", place.observationStationsUrl)
    }

    @Test
    fun parsePeriodsConvertsFahrenheitAndWindText() {
        val period = NwsParser.parsePeriods(fixture("nws-periods.json")).single()

        assertEquals(20.0, period.temperatureCelsius!!, 0.01)
        assertEquals(false, period.isDaytime)
        assertEquals(5 * 0.44704, period.windSpeedMetersPerSecond!!, 0.001)
        assertEquals(10 * 0.44704, period.windSpeedHighMetersPerSecond!!, 0.001)
        assertEquals("NW", period.windDirection)
        assertEquals(10.0, period.precipitationProbabilityPercent!!, 0.01)
        assertEquals(55.0, period.relativeHumidityPercent!!, 0.01)
        assertEquals(10.0, period.dewpointCelsius!!, 0.01)
        assertEquals("Mostly Clear", period.shortForecast)
        assertEquals("Mostly clear, with a low around 68.", period.detailedForecast)
    }

    @Test
    fun parseObservationConvertsQuantitativeValues() {
        val station = NwsParser.parseFirstStation(fixture("nws-stations.json"))!!
        val observation = NwsParser.parseObservation(fixture("nws-observation.json"), station.name, station.id)

        assertEquals("KORD", station.id)
        assertEquals("https://api.weather.gov/stations/KORD/observations/latest", station.observationUrl)
        assertEquals("Chicago O'Hare", observation.stationName)
        assertEquals("KORD", observation.stationId)
        assertEquals(10.0, observation.temperatureCelsius!!, 0.01)
        assertEquals(10.0, observation.windSpeedMetersPerSecond!!, 0.01)
        assertEquals(270.0, observation.windDirectionDegrees!!, 0.01)
        assertNull(observation.windGustMetersPerSecond)
        assertNull(observation.heatIndexCelsius)
        assertEquals(8.0, observation.windChillCelsius!!, 0.01)
        assertEquals(55.0, observation.relativeHumidityPercent!!, 0.01)
        assertEquals("Clear", observation.textDescription)
    }

    @Test
    fun parseAlertsKeepsOfficialText() {
        val alert = NwsParser.parseAlerts(fixture("nws-alerts.json")).single()

        assertEquals("Wind Advisory", alert.event)
        assertEquals("Secure loose objects.", alert.instruction)
        assertEquals("West winds 25 to 35 mph.", alert.description)
        assertTrue(alert.id.contains("urn:oid"))
    }

    @Test
    fun formatterUsesUsDefaults() {
        val formatter = UnitFormatter(DisplayUnits())

        assertEquals("68°F", formatter.temperature(20.0))
        assertEquals("68°", formatter.temperatureCompact(20.0))
        assertEquals("10%", formatter.percent(10.2))
        assertEquals("10 mph", formatter.wind(4.4704))
        assertEquals("5 to 10 mph", formatter.wind(5 * 0.44704, 10 * 0.44704))
        assertEquals("0.1 in", formatter.precipitation(2.54))
    }

    @Test
    fun formatterUsesMetricPreferences() {
        val formatter = UnitFormatter(
            DisplayUnits(
                temperature = TemperatureUnit.CELSIUS,
                wind = WindUnit.KILOMETERS_PER_HOUR,
                precipitation = PrecipitationUnit.MILLIMETERS,
            ),
        )

        assertEquals("20°C", formatter.temperature(20.0))
        assertEquals("36 km/h", formatter.wind(10.0))
        assertEquals("2.5 mm", formatter.precipitation(2.54))
    }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().use { it.readText() }
}
