package com.clutterfree.weather.weather

data class HomeSettings(
    val cardOrder: List<HomeCard> = listOf(
        HomeCard.TODAY,
        HomeCard.HOURLY,
        HomeCard.EXTENDED,
        HomeCard.RADAR,
    ),
    val showTemperature: Boolean = true,
    val showRainChance: Boolean = true,
    val showHumidity: Boolean = false,
    val showWind: Boolean = false,
    val showDewpoint: Boolean = false,
    val extendedVisibleDays: Int = 3,
    val radarMiles: Int = 50,
)

enum class HomeCard {
    TODAY,
    HOURLY,
    EXTENDED,
    RADAR,
}
