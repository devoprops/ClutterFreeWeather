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
        val observation = NwsParser.parseObservation(fixture("nws-observation.json"), station)

        assertEquals("KORD", station.id)
        assertEquals("https://api.weather.gov/stations/KORD/observations/latest", station.observationUrl)
        assertEquals("Chicago O'Hare", observation.stationName)
        assertEquals("KORD", observation.stationId)
        assertEquals(41.96019, observation.stationLatitude!!, 0.00001)
        assertEquals(-87.93389, observation.stationLongitude!!, 0.00001)
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
    fun parseObservationTreatsJsonNullIconAsMissing() {
        val json = """{"properties":{"timestamp":"2026-11-22T15:15:00+00:00","textDescription":"","icon":null}}"""
        val observation = NwsParser.parseObservation(json)

        assertNull(observation.iconUrl)
        assertNull(observation.textDescription)
    }

    @Test
    fun parseObservationFallsBackToTheRawReport() {
        val json = """{"properties":{"timestamp":"2026-11-22T15:15:00+00:00","icon":null,""" +
            """"rawMessage":"KMDQ 221515Z AUTO 00000KT 3 1/2SM -RA BR BKN011 OVC024 14/12 A3005 RMK A02"}}"""
        val day = StationLink("KMDQ", "Madison County", "https://example.test", latitude = 34.86, longitude = -86.56)

        assertEquals("https://api.weather.gov/icons/land/day/rain", NwsParser.parseObservation(json, day).iconUrl)

        val evening = json.replace("2026-11-22T15:15:00", "2026-11-23T01:15:00")
        assertEquals("https://api.weather.gov/icons/land/night/rain", NwsParser.parseObservation(evening, day).iconUrl)
    }

    @Test
    fun rawReportIsWindyAboveEighteenMph() {
        fun observed(kilometersPerHour: Double) = NwsParser.parseObservation(
            """{"properties":{"timestamp":"2026-11-22T15:15:00+00:00","icon":null,""" +
                """"windSpeed":{"unitCode":"wmoUnit:km_h-1","value":$kilometersPerHour},""" +
                """"rawMessage":"KMDQ 221515Z 25025KT 10SM BKN030 14/08 A3005"}}""",
        ).iconUrl

        assertEquals("https://api.weather.gov/icons/land/day/wind_bkn", observed(30.0))
        assertEquals("https://api.weather.gov/icons/land/day/bkn", observed(28.0))
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
