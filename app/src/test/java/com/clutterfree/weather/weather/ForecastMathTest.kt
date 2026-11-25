package com.clutterfree.weather.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class ForecastMathTest {
    @Test
    fun iconUrlUsesTheFirstConditionAndDropsTheRainChance() {
        val dual = parseIconUrl("https://api.weather.gov/icons/land/day/sct/rain?size=medium")
        val chance = parseIconUrl("https://api.weather.gov/icons/land/night/tsra,40?size=medium")
        val wind = parseIconUrl("https://api.weather.gov/icons/land/day/wind_bkn?size=medium")

        assertEquals(ParsedIcon(night = false, condition = "sct", windy = false), dual)
        assertEquals(ParsedIcon(night = true, condition = "tsra", windy = false), chance)
        assertEquals(ParsedIcon(night = false, condition = "bkn", windy = true), wind)
        assertEquals(ParsedIcon(night = false, condition = "skc", windy = false), parseIconUrl(null))
    }

    @Test
    fun nightCandidatesFallBackToTheDayFileThenClear() {
        assertEquals(
            listOf(
                "ic_wx_night_rain_snow",
                "ic_wx_night_rain",
                "ic_wx_day_rain_snow",
                "ic_wx_day_rain",
                "ic_wx_day_skc",
            ),
            iconCandidates(ParsedIcon(night = true, condition = "rain_snow", windy = false)),
        )
    }

    @Test
    fun chicagoSolsticeIsWithinAFewMinutesOfTheAlmanac() {
        val zone = ZoneId.of("America/Chicago")
        val sun = sunTimes(LocalDate.of(2026, 6, 21), 41.85, -87.65, zone)!!
        val rise = sun.sunrise.atZone(zone)
        val set = sun.sunset.atZone(zone)

        assertEquals(21, rise.dayOfMonth)
        assertEquals(5, rise.hour)
        assertTrue(abs(rise.minute - 15) <= 8)
        assertEquals(21, set.dayOfMonth)
        assertEquals(20, set.hour)
        assertTrue(abs(set.minute - 29) <= 8)
    }

    @Test
    fun daylightSwapKeepsTheConditionAndQuery() {
        val day = "https://api.weather.gov/icons/land/day/wind_bkn?size=medium"
        assertEquals("https://api.weather.gov/icons/land/night/wind_bkn?size=medium", withDaylight(day, night = true))
        assertEquals(day, withDaylight(day, night = false))
        assertNull(withDaylight(null, night = true))
    }

    @Test
    fun nightRunsUntilSunriseAndFromSunset() {
        val zone = ZoneId.of("America/Chicago")
        val latitude = 41.85
        val longitude = -87.65
        val sun = sunTimes(LocalDate.of(2026, 6, 21), latitude, longitude, zone)!!

        assertEquals(false, isNight(sun.sunrise, latitude, longitude, zone))
        assertEquals(true, isNight(sun.sunrise.minusSeconds(1), latitude, longitude, zone))
        assertEquals(false, isNight(sun.sunset.minusSeconds(1), latitude, longitude, zone))
        assertEquals(true, isNight(sun.sunset, latitude, longitude, zone))
        assertEquals(sun.sunset, nextDaylightChange(sun.sunrise, latitude, longitude, zone))
        val nextMorning = sunTimes(LocalDate.of(2026, 6, 22), latitude, longitude, zone)!!.sunrise
        assertEquals(nextMorning, nextDaylightChange(sun.sunset, latitude, longitude, zone))
    }

    @Test
    fun morningAndEveningAreTheHourBesideSunriseAndSunset() {
        val sunrise = Instant.parse("2026-06-21T10:15:00Z")
        val sunset = Instant.parse("2026-06-22T01:29:00Z")
        val sun = SunTimes(sunrise, sunset)

        assertTrue(iconDarken(sunrise.minus(Duration.ofMinutes(30)), sun))
        assertFalse(iconDarken(sunrise.plus(Duration.ofMinutes(30)), sun))
        assertTrue(iconDarken(sunset.plus(Duration.ofMinutes(30)), sun))
        assertFalse(iconDarken(sunset.minus(Duration.ofMinutes(30)), sun))
    }

    @Test
    fun moonPhaseBucketsFollowTheSynodicMonth() {
        val epoch = Instant.parse("2000-01-06T18:14:00Z")

        assertEquals(MoonPhase.NEW, moonPhase(epoch))
        assertEquals(MoonPhase.FULL, moonPhase(epoch.plus(Duration.ofHours(14 * 24 + 19))))
    }

    @Test
    fun civilDayPairsTheDaytimeHighWithTheNighttimeLow() {
        val zone = ZoneId.of("America/Chicago")
        val days = civilDays(
            dayNight = listOf(
                period("2026-11-20T12:00:00Z", daytime = true, celsius = 10.0),
                period("2026-11-21T00:00:00Z", daytime = false, celsius = 0.0),
            ),
            hourly = listOf(
                period("2026-11-20T18:00:00Z", daytime = true, celsius = 8.0, humidity = 40.0),
                period("2026-11-20T21:00:00Z", daytime = true, celsius = 9.0, humidity = 80.0),
            ),
            zone = zone,
        )

        val day = days.single()
        assertEquals(LocalDate.of(2026, 11, 20), day.date)
        assertEquals(10.0, day.highCelsius!!, 0.01)
        assertEquals(0.0, day.lowCelsius!!, 0.01)
        assertEquals(40.0, day.humidityPercent!!, 0.01)
        assertTrue(day.verboseForecast().contains("High near 10"))
    }

    @Test
    fun alertsSortBySeverity() {
        val ranked = rankedAlerts(
            listOf(
                alert("minor", "Minor"),
                alert("extreme", "Extreme"),
                alert("severe", "Severe"),
            ),
        )

        assertEquals(listOf("Extreme", "Severe", "Minor"), ranked.map { it.severity })
    }

    @Test
    fun scrubberInterpolatesBetweenHours() {
        assertEquals(65.0, interpolate(listOf(0.0 to 60.0, 2.0 to 70.0), 1.0)!!, 0.01)
        assertNull(interpolate(emptyList(), 1.0))
    }

    @Test
    fun periodIndexFollowsTheHourContainingTheTime() {
        val start = Instant.parse("2026-11-20T15:00:00Z")
        val periods = listOf(
            period("2026-11-20T15:00:00Z", daytime = true, celsius = 1.0),
            period("2026-11-20T16:00:00Z", daytime = true, celsius = 2.0),
        )

        assertEquals(0.0, periodIndexAt(periods, start), 0.01)
        assertEquals(0.5, periodIndexAt(periods, start.plus(Duration.ofMinutes(30))), 0.01)
        assertEquals(1.5, periodIndexAt(periods, start.plus(Duration.ofMinutes(90))), 0.01)
    }

    private fun period(
        start: String,
        daytime: Boolean,
        celsius: Double,
        humidity: Double? = null,
    ) = ForecastPeriod(
        start = Instant.parse(start),
        end = Instant.parse(start).plus(Duration.ofHours(if (daytime && humidity == null && celsius == 10.0) 12 else 1)),
        isDaytime = daytime,
        temperatureCelsius = celsius,
        windSpeedMetersPerSecond = null,
        windSpeedHighMetersPerSecond = null,
        windDirection = null,
        shortForecast = "Clear",
        precipitationProbabilityPercent = null,
        iconUrl = null,
        relativeHumidityPercent = humidity,
        detailedForecast = "High near ${celsius.toInt()}.",
    )

    @Test
    fun fastRefreshStaysOnForASevereSpeciOrAnActiveAlert() {
        val now = Instant.parse("2026-11-24T18:00:00Z")
        assertTrue(needsFastRefresh(report(severeSpeci = true), now))
        assertTrue(needsFastRefresh(report(alert = alert("1", "Severe", now.minusSeconds(60), now.plusSeconds(3600))), now))
        assertFalse(needsFastRefresh(report(alert = alert("1", "Severe", now.minusSeconds(3600), now.minusSeconds(60))), now))
        assertFalse(needsFastRefresh(report(alert = alert("1", "Minor", null, null)), now))
    }

    @Test
    fun refreshWaitIsTheTimeLeftInTheInterval() {
        val now = Instant.parse("2026-11-24T18:00:00Z")
        val fetched = now.minus(Duration.ofMinutes(20))
        assertEquals(Duration.ofMinutes(10), refreshWait(fetched, fast = false, now = now))
        assertEquals(Duration.ZERO, refreshWait(fetched, fast = true, now = now))
        assertEquals(Duration.ofMinutes(30), refreshWait(null, fast = false, now = now))
    }

    @Test
    fun compassPointRoundsToSixteenDirections() {
        assertEquals("N", compassPoint(0.0))
        assertEquals("N", compassPoint(355.0))
        assertEquals("ESE", compassPoint(112.5))
        assertEquals("W", compassPoint(-90.0))
    }

    private fun report(severeSpeci: Boolean = false, alert: WeatherAlert? = null) = WeatherSnapshot(
        fetchedAt = Instant.EPOCH,
        observation = Observation(
            observedAt = null,
            stationName = null,
            temperatureCelsius = null,
            dewpointCelsius = null,
            windSpeedMetersPerSecond = null,
            windDirectionDegrees = null,
            windGustMetersPerSecond = null,
            heatIndexCelsius = null,
            windChillCelsius = null,
            relativeHumidityPercent = null,
            textDescription = null,
            iconUrl = null,
            severeSpeci = severeSpeci,
        ),
        hourly = emptyList(),
        dayNight = emptyList(),
        alerts = listOfNotNull(alert),
    )

    private fun alert(id: String, severity: String, onset: Instant? = null, ends: Instant? = null) = WeatherAlert(
        id = id,
        event = severity,
        severity = severity,
        headline = severity,
        onset = onset,
        ends = ends,
        instruction = null,
        description = null,
    )
}
