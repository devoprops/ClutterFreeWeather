package com.clutterfree.weather.weather

interface WeatherRepository {
    fun choosePlace(displayName: String, latitude: Double, longitude: Double): SearchResult

    fun selectedPlace(): Place?

    fun refresh(): RefreshResult

    fun snapshot(): WeatherSnapshot?

    fun load(): LoadResult

    fun displayUnits(): DisplayUnits

    fun setDisplayUnits(units: DisplayUnits)

    fun homeSettings(): HomeSettings

    fun setHomeSettings(settings: HomeSettings)
}

sealed interface SearchResult {
    data class Found(val place: Place) : SearchResult

    data object NoMatchingPlace : SearchResult

    data object OutsideCoverage : SearchResult

    data class Failed(val message: String) : SearchResult
}

sealed interface RefreshResult {
    data class Updated(val snapshot: WeatherSnapshot) : RefreshResult

    data object NoPlace : RefreshResult

    data class Failed(val message: String, val previous: WeatherSnapshot?) : RefreshResult
}

sealed interface LoadResult {
    data class Cached(val snapshot: WeatherSnapshot) : LoadResult

    data class Refreshed(val snapshot: WeatherSnapshot) : LoadResult

    data class Unavailable(val message: String, val previous: WeatherSnapshot?) : LoadResult

    data object NoPlace : LoadResult
}
