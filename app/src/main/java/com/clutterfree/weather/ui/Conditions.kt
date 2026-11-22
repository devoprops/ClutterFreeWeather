package com.clutterfree.weather.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.devoprop.clutterfreeweather.R
import com.clutterfree.weather.weather.parseIconUrl
import com.clutterfree.weather.weather.resolvedCondition

@Composable
fun conditionLabel(iconUrl: String?): String? {
    if (iconUrl.isNullOrBlank()) return null
    val parsed = parseIconUrl(iconUrl)
    val name = stringResource(conditionString(parsed.condition))
    return if (parsed.windy) stringResource(R.string.condition_windy, name) else name
}

@StringRes
fun conditionString(condition: String): Int =
    CONDITION_STRINGS[condition]
        ?: CONDITION_STRINGS[resolvedCondition(condition)]
        ?: R.string.weather_icon

private val CONDITION_STRINGS = mapOf(
    "skc" to R.string.condition_skc,
    "few" to R.string.condition_few,
    "sct" to R.string.condition_sct,
    "bkn" to R.string.condition_bkn,
    "ovc" to R.string.condition_ovc,
    "rain" to R.string.condition_rain,
    "rain_showers" to R.string.condition_rain_showers,
    "snow" to R.string.condition_snow,
    "sleet" to R.string.condition_sleet,
    "fzra" to R.string.condition_fzra,
    "tsra" to R.string.condition_tsra,
    "fog" to R.string.condition_fog,
    "tornado" to R.string.condition_tornado,
    "hurricane" to R.string.condition_hurricane,
    "tropical_storm" to R.string.condition_tropical_storm,
    "rain_snow" to R.string.condition_rain_snow,
    "rain_sleet" to R.string.condition_rain_sleet,
    "snow_sleet" to R.string.condition_snow_sleet,
    "rain_fzra" to R.string.condition_rain_fzra,
    "snow_fzra" to R.string.condition_snow_fzra,
)
