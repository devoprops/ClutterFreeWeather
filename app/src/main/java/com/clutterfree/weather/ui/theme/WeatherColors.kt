package com.clutterfree.weather.ui.theme

import androidx.compose.ui.graphics.Color
import com.clutterfree.weather.weather.AppConfig

/** One color for light mode and one for dark mode, both readable on the card surface. */
class ThemeColor(val light: Color, val night: Color) {
    fun resolve(nightMode: Boolean): Color = if (nightMode) night else light
}

object WeatherColors {
    val rain = ThemeColor(Color(0xFF0655BF), Color(0xFF267DF0))
    val wind = ThemeColor(Color(0xFF424242), Color(0xFFE0E0E0))
    val humidity = ThemeColor(Color(0xFFBA77ED), Color(0xFFC898ED))
    val dewPoint = ThemeColor(Color(0xFF2E7D32), Color(0xFF81C784))
    val pressure = ThemeColor(Color(0xFFEBF062), Color(0xFFE9F032))

    val coldPurple = ThemeColor(Color(0xFF4A148C), Color(0xFFE1BEE7))
    val royalBlue = ThemeColor(Color(0xFF1565C0), Color(0xFF90CAF9))
    val skyBlue = ThemeColor(Color(0xFF0277BD), Color(0xFF81D4FA))
    val teal = ThemeColor(Color(0xFF00838F), Color(0xFF80DEEA))
    val freshGreen = ThemeColor(Color(0xFF2E7D32), Color(0xFFA5D6A7))
    val warmYellow = ThemeColor(Color(0xFFF9A825), Color(0xFFFFF59D))
    val orange = ThemeColor(Color(0xFFEF6C00), Color(0xFFFFCC80))
    val crimson = ThemeColor(Color(0xFFC62828), Color(0xFFEF9A9A))
    val maroon = ThemeColor(Color(0xFF8E0000), Color(0xFFFFCDD2))

    val widgetTemperature = Color(0xFF1A2330)
    val widgetTemperatureEdge = Color(0xFFF7FBFF)

    val sun = Color(0xFFF5C542)
    val cloud = Color(0xFFD0D9E0)
    val cloudEdge = Color(0xFF607484)
    val standInRain = Color(0xFF3D7EDB)
    val snow = Color(0xFF9FB4C4)
    val ice = Color(0xFF7FB4D4)
    val bolt = Color(0xFFF0C84A)
    val fog = Color(0xFFB7C3CC)
    val moonLight = Color(0xFFF4F0E4)
    val moonShade = Color(0xFF3C495C)
    val iconDarken = Color.Black.copy(alpha = AppConfig.ICON_DARKEN_ALPHA)
}
