package com.clutterfree.weather.weather

data class HomeSettings(
    val cardOrder: List<HomeCard> = listOf(
        HomeCard.TODAY,
        HomeCard.HOURLY,
        HomeCard.EXTENDED,
        HomeCard.RADAR,
    ),
    val todayGraph: GraphSettings = GraphSettings(),
    val hourlyGraph: GraphSettings = GraphSettings(),
    val extendedGraph: GraphSettings = GraphSettings(),
    val details: Set<DetailItem> = DetailItem.entries.toSet(),
    val extendedVisibleDays: Int = AppConfig.EXTENDED_VISIBLE_DAYS,
    val radarMiles: Int = AppConfig.RADAR_MILES,
    val cardTheme: CardTheme = CardTheme.SEASONAL,
    val chartOrientation: ChartOrientation = ChartOrientation.AUTO,
)

data class GraphSettings(
    val visible: Boolean = true,
    val temperature: Boolean = true,
    val rainChance: Boolean = true,
    val humidity: Boolean = false,
    val wind: Boolean = false,
    val dewpoint: Boolean = false,
)

enum class HomeCard {
    TODAY,
    HOURLY,
    EXTENDED,
    RADAR,
}

enum class DetailItem {
    CURRENT_TEXT,
    DAY_TEXT,
    NIGHT_TEXT,
    SUNRISE,
    SUNSET,
    HIGH,
    LOW,
    HUMIDITY,
    WIND,
    WIND_CHILL_HEAT_INDEX,
}

enum class CardTheme {
    SEASONAL,
    WEATHER,
    PLAIN,
}

enum class ChartOrientation {
    AUTO,
    HORIZONTAL,
    VERTICAL,
}
