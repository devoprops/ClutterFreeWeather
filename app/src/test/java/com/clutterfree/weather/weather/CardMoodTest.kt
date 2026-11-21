package com.clutterfree.weather.weather

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CardMoodTest {
    @Test
    fun seasonsFollowMeteorologicalMonths() {
        assertEquals(Season.AUTUMN, seasonOf(LocalDate.of(2026, 11, 21)))
        assertEquals(Season.WINTER, seasonOf(LocalDate.of(2026, 12, 1)))
        assertEquals(Season.SPRING, seasonOf(LocalDate.of(2027, 3, 1)))
        assertEquals(Season.SUMMER, seasonOf(LocalDate.of(2027, 8, 31)))
    }

    @Test
    fun skyMoodReadsTheIconCondition() {
        assertEquals(SkyMood.PARTLY, skyMood("https://api.weather.gov/icons/land/day/bkn?size=medium"))
        assertEquals(SkyMood.RAIN, skyMood("https://api.weather.gov/icons/land/night/rain_showers,40"))
        assertEquals(SkyMood.SNOW, skyMood("https://api.weather.gov/icons/land/day/snow_sleet"))
        assertEquals(SkyMood.STORM, skyMood("https://api.weather.gov/icons/land/day/tsra_sct,60"))
        assertEquals(SkyMood.FOG, skyMood("https://api.weather.gov/icons/land/day/haze"))
        assertEquals(SkyMood.CLEAR, skyMood(null))
    }
}
