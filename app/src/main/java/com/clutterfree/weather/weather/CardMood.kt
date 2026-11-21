package com.clutterfree.weather.weather

import java.time.LocalDate
import java.time.Month

enum class Season { WINTER, SPRING, SUMMER, AUTUMN }

enum class SkyMood { CLEAR, PARTLY, CLOUDY, RAIN, SNOW, STORM, FOG }

fun seasonOf(date: LocalDate): Season = when (date.month) {
    Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> Season.WINTER
    Month.MARCH, Month.APRIL, Month.MAY -> Season.SPRING
    Month.JUNE, Month.JULY, Month.AUGUST -> Season.SUMMER
    else -> Season.AUTUMN
}

fun skyMood(iconUrl: String?): SkyMood {
    val condition = resolvedCondition(parseIconUrl(iconUrl).condition)
    return when {
        condition == "tsra" || condition == "tornado" || condition == "hurricane" ||
            condition == "tropical_storm" -> SkyMood.STORM
        condition == "snow" || condition == "sleet" || condition == "blizzard" -> SkyMood.SNOW
        condition == "rain" || condition == "rain_showers" || condition == "fzra" -> SkyMood.RAIN
        condition == "fog" -> SkyMood.FOG
        condition == "ovc" -> SkyMood.CLOUDY
        condition == "sct" || condition == "bkn" -> SkyMood.PARTLY
        else -> SkyMood.CLEAR
    }
}
