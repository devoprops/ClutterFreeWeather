package com.clutterfree.weather.data

import com.clutterfree.weather.weather.AppConfig
import com.clutterfree.weather.weather.DisplayUnits
import com.clutterfree.weather.weather.mergeElapsedToday
import com.clutterfree.weather.weather.HomeSettings
import com.clutterfree.weather.weather.LoadResult
import com.clutterfree.weather.weather.Place
import com.clutterfree.weather.weather.RefreshResult
import com.clutterfree.weather.weather.SearchResult
import com.clutterfree.weather.weather.WeatherRepository
import com.clutterfree.weather.weather.WeatherSnapshot
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class NwsWeatherRepository(
    directory: File,
    private val http: TextHttp = TextHttp(),
    private val now: () -> Instant = Instant::now,
) : WeatherRepository {
    private val nws = NwsClient(http)
    private val store = WeatherStore(directory)

    override fun choosePlace(displayName: String, latitude: Double, longitude: Double): SearchResult {
        val points = try {
            nws.points(latitude, longitude)
        } catch (_: Exception) {
            return SearchResult.Failed(WEATHER_UNAVAILABLE)
        }
        if (points.isMissing) return SearchResult.OutsideCoverage
        if (!points.isSuccess) return SearchResult.Failed(WEATHER_UNAVAILABLE)
        val place = try {
            NwsParser.parsePlace(
                json = points.body,
                displayName = displayName,
                latitude = latitude,
                longitude = longitude,
                resolvedAt = now(),
            )
        } catch (_: Exception) {
            return SearchResult.Failed(WEATHER_UNAVAILABLE)
        }
        store.writePlace(place)
        store.clearSnapshot()
        return SearchResult.Found(place)
    }

    override fun selectedPlace(): Place? = store.readPlace()

    override fun savedPlaces(): List<Place> = store.readSaved()

    override fun switchPlace(displayName: String): Place? {
        val place = store.readSaved().firstOrNull { it.displayName == displayName } ?: return null
        if (store.readPlace()?.displayName == displayName) return place
        store.writePlace(place)
        store.clearSnapshot()
        return place
    }

    override fun removePlace(displayName: String) {
        store.removeSaved(displayName)
    }

    override fun reorderPlaces(displayNames: List<String>) {
        store.reorderSaved(displayNames)
    }

    override fun refresh(): RefreshResult {
        val place = store.readPlace() ?: return RefreshResult.NoPlace
        val previous = store.readSnapshot()
        val first = fetch(place)
        val snapshot = when (first) {
            is Fetch.Ready -> first.snapshot
            is Fetch.GridMoved -> {
                val resolved = resolveAgain(place) ?: return RefreshResult.Failed(WEATHER_UNAVAILABLE, previous)
                when (val second = fetch(resolved)) {
                    is Fetch.Ready -> second.snapshot
                    is Fetch.GridMoved -> return RefreshResult.Failed(WEATHER_UNAVAILABLE, previous)
                    is Fetch.Failed -> return RefreshResult.Failed(second.message, previous)
                }
            }
            is Fetch.Failed -> return RefreshResult.Failed(first.message, previous)
        }
        val zone = runCatching { ZoneId.of(place.timeZone) }.getOrElse { ZoneId.systemDefault() }
        val merged = snapshot.copy(
            hourly = mergeElapsedToday(previous?.hourly.orEmpty(), snapshot.hourly, zone, now()),
        )
        store.writeSnapshot(merged)
        return RefreshResult.Updated(merged)
    }

    override fun snapshot(): WeatherSnapshot? = store.readSnapshot()

    override fun load(): LoadResult {
        if (store.readPlace() == null) return LoadResult.NoPlace
        val cached = store.readSnapshot()
        if (cached != null && !isStale(cached)) return LoadResult.Cached(cached)
        return when (val refreshed = refresh()) {
            is RefreshResult.Updated -> LoadResult.Refreshed(refreshed.snapshot)
            is RefreshResult.Failed -> LoadResult.Unavailable(refreshed.message, refreshed.previous)
            RefreshResult.NoPlace -> LoadResult.NoPlace
        }
    }

    override fun displayUnits(): DisplayUnits = store.readUnits()

    override fun setDisplayUnits(units: DisplayUnits) {
        store.writeUnits(units)
    }

    override fun homeSettings(): HomeSettings = store.readHome()

    override fun setHomeSettings(settings: HomeSettings) {
        store.writeHome(settings)
    }

    private fun isStale(snapshot: WeatherSnapshot): Boolean {
        val age = Duration.between(snapshot.fetchedAt, now())
        return age.isNegative || age >= STALE_AFTER
    }

    private fun resolveAgain(place: Place): Place? {
        val points = try {
            nws.points(place.latitude, place.longitude)
        } catch (_: Exception) {
            return null
        }
        if (!points.isSuccess) return null
        val resolved = try {
            NwsParser.parsePlace(
                json = points.body,
                displayName = place.displayName,
                latitude = place.latitude,
                longitude = place.longitude,
                resolvedAt = now(),
            )
        } catch (_: Exception) {
            return null
        }
        store.writePlace(resolved)
        return resolved
    }

    private fun fetch(place: Place): Fetch {
        val hourly = document(place.hourlyForecastUrl)
        val daily = document(place.forecastUrl)
        if (hourly is NwsRead.Missing || daily is NwsRead.Missing) return Fetch.GridMoved
        if (hourly is NwsRead.Failed) return Fetch.Failed(hourly.message)
        if (daily is NwsRead.Failed) return Fetch.Failed(daily.message)
        val stations = document(place.observationStationsUrl)
        if (stations is NwsRead.Missing || stations is NwsRead.Failed) {
            return Fetch.Failed(WEATHER_UNAVAILABLE)
        }
        val station = try {
            NwsParser.parseFirstStation((stations as NwsRead.Ok).body)
        } catch (_: Exception) {
            return Fetch.Failed(WEATHER_UNAVAILABLE)
        } ?: return Fetch.Failed(WEATHER_UNAVAILABLE)
        val observation = document(station.observationUrl)
        if (observation !is NwsRead.Ok) return Fetch.Failed(WEATHER_UNAVAILABLE)
        val alerts = try {
            nws.alerts(place.latitude, place.longitude)
        } catch (_: Exception) {
            return Fetch.Failed(WEATHER_UNAVAILABLE)
        }
        if (!alerts.isSuccess) return Fetch.Failed(WEATHER_UNAVAILABLE)
        val hourlyBody = (hourly as NwsRead.Ok).body
        val dailyBody = (daily as NwsRead.Ok).body
        return try {
            Fetch.Ready(
                WeatherSnapshot(
                    fetchedAt = now(),
                    observation = NwsParser.parseObservation(observation.body, station.name, station.id),
                    hourly = NwsParser.parsePeriods(hourlyBody),
                    dayNight = NwsParser.parsePeriods(dailyBody),
                    alerts = NwsParser.parseAlerts(alerts.body),
                ),
            )
        } catch (_: Exception) {
            Fetch.Failed(WEATHER_UNAVAILABLE)
        }
    }

    private fun document(url: String): NwsRead {
        val document = try {
            nws.get(url)
        } catch (_: Exception) {
            return NwsRead.Failed(WEATHER_UNAVAILABLE)
        }
        return when {
            document.isMissing -> NwsRead.Missing
            document.isSuccess -> NwsRead.Ok(document.body)
            else -> NwsRead.Failed(WEATHER_UNAVAILABLE)
        }
    }

    private sealed interface Fetch {
        data class Ready(val snapshot: WeatherSnapshot) : Fetch

        data object GridMoved : Fetch

        data class Failed(val message: String) : Fetch
    }

    private sealed interface NwsRead {
        data class Ok(val body: String) : NwsRead

        data object Missing : NwsRead

        data class Failed(val message: String) : NwsRead
    }

    private companion object {
        val STALE_AFTER: Duration = Duration.ofMinutes(AppConfig.CACHE_MINUTES)
        const val WEATHER_UNAVAILABLE = "Weather service is unavailable."
    }
}
