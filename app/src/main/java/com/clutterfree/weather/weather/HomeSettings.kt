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
    val cardTheme: CardTheme = CardTheme.PLAIN,
    val chartOrientation: ChartOrientation = ChartOrientation.AUTO,
    val suggestClosest: Boolean = true,
)

data class GraphSettings(
    val visible: Boolean = true,
    val temperature: Boolean = true,
    val rainChance: Boolean = true,
    val humidity: Boolean = false,
    val wind: Boolean = false,
    val dewpoint: Boolean = false,
)

/** Names are stored in settings files, so they stay fixed when the on-screen label changes. */
enum class HomeCard {
    TODAY,
    HOURLY,

    /** The Daily card. "Extended" in code and stored settings means Daily everywhere. */
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
