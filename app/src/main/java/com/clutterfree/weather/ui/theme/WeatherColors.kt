package com.clutterfree.weather.ui.theme

import androidx.compose.ui.graphics.Color
import com.clutterfree.weather.weather.AppConfig

object WeatherColors {
    val iconLabel = Color(0xFF1C2834)
    val rain = Color(0xFF1565C0)
    val wind = Color(0xFF424242)
    val humidity = Color(0xFF00838F)
    val dewPoint = Color(0xFF2E7D32)
    val pastOverlay = wind.copy(alpha = AppConfig.PAST_OVERLAY_ALPHA)

    val coldPurple = Color(0xFF4A148C)
    val royalBlue = Color(0xFF1565C0)
    val skyBlue = Color(0xFF29B6F6)
    val teal = Color(0xFF00ACC1)
    val freshGreen = Color(0xFF66BB6A)
    val warmYellow = Color(0xFFFFEE58)
    val orange = Color(0xFFFFA726)
    val crimson = Color(0xFFE53935)
    val maroon = Color(0xFF8E0000)

    val sun = Color(0xFFF5C542)
    val cloud = Color(0xFFD0D9E0)
    val cloudEdge = Color(0xFF607484)
    val standInRain = Color(0xFF3D7EDB)
    val snow = Color(0xFF9FB4C4)
    val ice = Color(0xFF7FB4D4)
    val bolt = Color(0xFFF0C84A)
    val fog = Color(0xFFB7C3CC)
    val moonLight = Color(0xFFF4F0E4)
    val moonShade = Color(0xFF243040)
    val iconDarken = Color.Black.copy(alpha = AppConfig.ICON_DARKEN_ALPHA)

    val winter = Color(0xFF5C8FB8)
    val spring = Color(0xFF5E9E5A)
    val summer = Color(0xFFD9A23A)
    val autumn = Color(0xFFC0702F)

    val skyClear = Color(0xFF3F8AD8)
    val skyPartly = Color(0xFF6E93BA)
    val skyCloudy = Color(0xFF7F8B98)
    val skyRain = Color(0xFF46658A)
    val skySnow = Color(0xFF9EC0DE)
    val skyStorm = Color(0xFF5B4B8A)
    val skyFog = Color(0xFF959FA6)
    val nightShade = Color(0xFF101828)
}
