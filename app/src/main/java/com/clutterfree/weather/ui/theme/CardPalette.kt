package com.clutterfree.weather.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.clutterfree.weather.weather.AppConfig
import com.clutterfree.weather.weather.CardTheme
import com.clutterfree.weather.weather.Season
import com.clutterfree.weather.weather.SkyMood
import com.clutterfree.weather.weather.parseIconUrl
import com.clutterfree.weather.weather.seasonOf
import com.clutterfree.weather.weather.skyMood
import java.time.LocalDate

data class CardPalette(
    val page: Color,
    val card: Color,
    val subcard: Color,
    val bandEven: Color,
    val bandOdd: Color,
)

val LocalCardPalette = staticCompositionLocalOf<CardPalette?> { null }

fun cardPalette(
    scheme: ColorScheme,
    theme: CardTheme,
    date: LocalDate,
    iconUrl: String?,
): CardPalette {
    val accent = when (theme) {
        CardTheme.PLAIN -> null
        CardTheme.SEASONAL -> seasonColor(seasonOf(date))
        CardTheme.WEATHER -> {
            val sky = skyColor(skyMood(iconUrl))
            if (parseIconUrl(iconUrl).night) lerp(sky, WeatherColors.nightShade, AppConfig.NIGHT_THEME_SHADE) else sky
        }
    }
    fun tint(base: Color, amount: Float) = if (accent == null) base else lerp(base, accent, amount)
    return CardPalette(
        page = tint(scheme.background, AppConfig.PAGE_TINT),
        card = tint(scheme.surfaceContainer, AppConfig.CARD_TINT),
        subcard = tint(scheme.surfaceContainerHigh, AppConfig.SUBCARD_TINT),
        bandEven = tint(scheme.surfaceContainerLow, AppConfig.SUBCARD_TINT),
        bandOdd = tint(scheme.surfaceContainerHighest, AppConfig.BAND_TINT),
    )
}

private fun seasonColor(season: Season): Color = when (season) {
    Season.WINTER -> WeatherColors.winter
    Season.SPRING -> WeatherColors.spring
    Season.SUMMER -> WeatherColors.summer
    Season.AUTUMN -> WeatherColors.autumn
}

private fun skyColor(mood: SkyMood): Color = when (mood) {
    SkyMood.CLEAR -> WeatherColors.skyClear
    SkyMood.PARTLY -> WeatherColors.skyPartly
    SkyMood.CLOUDY -> WeatherColors.skyCloudy
    SkyMood.RAIN -> WeatherColors.skyRain
    SkyMood.SNOW -> WeatherColors.skySnow
    SkyMood.STORM -> WeatherColors.skyStorm
    SkyMood.FOG -> WeatherColors.skyFog
}
