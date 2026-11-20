package com.clutterfree.weather.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.clutterfree.weather.R
import com.clutterfree.weather.data.UsPlace
import com.clutterfree.weather.data.UsPlaceIndex
import com.clutterfree.weather.ui.theme.ClutterFreeWeatherTheme
import com.clutterfree.weather.weather.DisplayUnits
import com.clutterfree.weather.weather.ForecastPeriod
import com.clutterfree.weather.weather.HomeCard
import com.clutterfree.weather.weather.HomeSettings
import com.clutterfree.weather.weather.LoadResult
import com.clutterfree.weather.weather.Observation
import com.clutterfree.weather.weather.Place
import com.clutterfree.weather.weather.SearchResult
import com.clutterfree.weather.weather.WeatherAlert
import com.clutterfree.weather.weather.WeatherRepository
import com.clutterfree.weather.weather.WeatherSnapshot
import com.clutterfree.weather.weather.rankedAlerts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

@Composable
fun HomeScreen(repository: WeatherRepository) {
    val scope = rememberCoroutineScope()
    val gate = remember { Mutex() }
    val generation = remember { AtomicInteger(0) }
    var place by remember { mutableStateOf<Place?>(null) }
    var snapshot by remember { mutableStateOf<WeatherSnapshot?>(null) }
    var units by remember { mutableStateOf(DisplayUnits()) }
    var settings by remember { mutableStateOf(HomeSettings()) }
    var query by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var placeIndex by remember { mutableStateOf<UsPlaceIndex?>(null) }
    var showAlertList by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var selectedAlertId by rememberSaveable { mutableStateOf<String?>(null) }
    val noMatch = stringResource(R.string.no_matching_place)
    val outside = stringResource(R.string.outside_coverage)
    val context = LocalContext.current
    val suggestions = remember(query, placeIndex) { placeIndex?.suggest(query).orEmpty() }

    fun apply(result: LoadResult, loadedPlace: Place?) {
        place = loadedPlace
        when (result) {
            is LoadResult.Cached -> {
                snapshot = result.snapshot
                status = null
            }
            is LoadResult.Refreshed -> {
                snapshot = result.snapshot
                status = null
            }
            is LoadResult.Unavailable -> {
                snapshot = result.previous
                status = result.message
            }
            LoadResult.NoPlace -> snapshot = null
        }
    }

    LaunchedEffect(Unit) {
        placeIndex = withContext(Dispatchers.IO) {
            context.assets.open("us_places.txt").use { UsPlaceIndex.load(it) }
        }
        val ticket = generation.get()
        val stored = withContext(Dispatchers.IO) {
            gate.withLock {
                if (ticket != generation.get()) {
                    null
                } else {
                    StoredHome(
                        place = repository.selectedPlace(),
                        snapshot = repository.snapshot(),
                        units = repository.displayUnits(),
                        settings = repository.homeSettings(),
                    )
                }
            }
        } ?: return@LaunchedEffect
        if (ticket != generation.get()) return@LaunchedEffect
        place = stored.place
        snapshot = stored.snapshot
        units = stored.units
        settings = stored.settings
        if (stored.place == null) return@LaunchedEffect
        refreshing = true
        val loaded = withContext(Dispatchers.IO) {
            gate.withLock {
                if (ticket != generation.get()) {
                    null
                } else {
                    repository.load() to repository.selectedPlace()
                }
            }
        }
        if (loaded != null && ticket == generation.get()) {
            apply(loaded.first, loaded.second)
        }
        if (ticket == generation.get()) refreshing = false
    }

    fun selectPlace(selected: UsPlace) {
        if (searching) return
        val ticket = generation.incrementAndGet()
        searching = true
        refreshing = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                gate.withLock {
                    if (ticket != generation.get()) {
                        null
                    } else {
                        when (val result = repository.choosePlace(selected.label, selected.latitude, selected.longitude)) {
                            is SearchResult.Found -> {
                                val loaded = repository.load()
                                SearchOutcome.Found(result.place, loaded, repository.selectedPlace())
                            }
                            SearchResult.NoMatchingPlace -> SearchOutcome.Message(noMatch)
                            SearchResult.OutsideCoverage -> SearchOutcome.Message(outside)
                            is SearchResult.Failed -> SearchOutcome.Message(result.message)
                        }
                    }
                }
            }
            if (outcome != null && ticket == generation.get()) {
                when (outcome) {
                    is SearchOutcome.Found -> {
                        query = ""
                        status = null
                        apply(outcome.loaded, outcome.selected ?: outcome.place)
                    }
                    is SearchOutcome.Message -> status = outcome.text
                }
            }
            if (ticket == generation.get()) {
                searching = false
                refreshing = false
            }
        }
    }

    fun search() {
        val selected = suggestions.firstOrNull()
        if (selected == null) {
            status = noMatch
            return
        }
        selectPlace(selected)
    }

    fun closeAlert() {
        if (selectedAlertId != null && showAlertList) {
            selectedAlertId = null
        } else {
            selectedAlertId = null
            showAlertList = false
        }
    }

    val alertsOpen = showAlertList || selectedAlertId != null
    BackHandler(enabled = alertsOpen || showSettings) {
        if (alertsOpen) closeAlert() else showSettings = false
    }

    HomeContent(
        place = place,
        snapshot = snapshot,
        units = units,
        settings = settings,
        now = Instant.now(),
        query = query,
        onQuery = {
            query = it
            status = null
        },
        onSearch = ::search,
        suggestions = suggestions,
        onPick = ::selectPlace,
        searching = searching,
        refreshing = refreshing,
        status = status,
        showAlertList = showAlertList,
        selectedAlertId = selectedAlertId,
        onOpenAlerts = {
            val ranked = rankedAlerts(snapshot?.alerts.orEmpty())
            if (ranked.size > 1) {
                showAlertList = true
            } else {
                selectedAlertId = ranked.firstOrNull()?.id
            }
        },
        onSelectAlert = { selectedAlertId = it },
        onCloseAlerts = ::closeAlert,
        showSettings = showSettings,
        onOpenSettings = { showSettings = true },
        onCloseSettings = { showSettings = false },
        onSettings = { updated ->
            settings = updated
            scope.launch {
                withContext(Dispatchers.IO) {
                    gate.withLock { repository.setHomeSettings(updated) }
                }
            }
        },
    )
}

@Composable
private fun HomeContent(
    place: Place?,
    snapshot: WeatherSnapshot?,
    units: DisplayUnits,
    settings: HomeSettings,
    now: Instant,
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    suggestions: List<UsPlace>,
    onPick: (UsPlace) -> Unit,
    searching: Boolean,
    refreshing: Boolean,
    status: String?,
    showAlertList: Boolean,
    selectedAlertId: String?,
    onOpenAlerts: () -> Unit,
    onSelectAlert: (String) -> Unit,
    onCloseAlerts: () -> Unit,
    showSettings: Boolean,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onSettings: (HomeSettings) -> Unit,
) {
    val wide = currentWindowAdaptiveInfoV2()
        .windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    val selected = snapshot?.alerts?.firstOrNull { it.id == selectedAlertId }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        val columnWidth = if (wide) minOf(maxWidth, 720.dp) else maxWidth
        Column(
            Modifier
                .width(columnWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (refreshing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when {
                selected != null -> AlertDetail(selected, onCloseAlerts)
                showAlertList -> AlertList(snapshot?.alerts.orEmpty(), onSelectAlert, onCloseAlerts)
                showSettings -> SettingsPane(settings, onSettings, onCloseSettings)
                else -> {
                    if (snapshot != null && snapshot.alerts.isNotEmpty()) {
                        AlertBanner(snapshot.alerts, onOpenAlerts)
                    }
                    LocationBar(place, query, onQuery, onSearch, suggestions, onPick, searching, onOpenSettings)
                    status?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error)
                    }
                    if (snapshot != null && place != null) {
                        ForecastColumn(place, snapshot, units, settings, now)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationBar(
    place: Place?,
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    suggestions: List<UsPlace>,
    onPick: (UsPlace) -> Unit,
    searching: Boolean,
    onOpenSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = place?.displayName ?: stringResource(R.string.place_prompt),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.settings))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.place_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
            Button(
                onClick = onSearch,
                enabled = query.isNotBlank() && !searching,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.search))
            }
        }
        suggestions.forEach { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onPick(suggestion) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = suggestion.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ForecastColumn(
    place: Place,
    snapshot: WeatherSnapshot,
    units: DisplayUnits,
    settings: HomeSettings,
    now: Instant,
) {
    val zone = runCatching { ZoneId.of(place.timeZone) }.getOrElse { ZoneId.systemDefault() }
    settings.cardOrder.forEach { card ->
        androidx.compose.runtime.key(card) {
            when (card) {
                HomeCard.TODAY -> TodayCard(
                    snapshot = snapshot,
                    zone = zone,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    now = now,
                    units = units,
                    settings = settings,
                )
                HomeCard.HOURLY -> HourlyCard(
                    hours = snapshot.hourly,
                    zone = zone,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    units = units,
                    settings = settings,
                )
                HomeCard.EXTENDED -> ExtendedCard(
                    dayNight = snapshot.dayNight,
                    hourly = snapshot.hourly,
                    zone = zone,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    units = units,
                    settings = settings,
                )
                HomeCard.RADAR -> Unit
            }
        }
    }
}

private data class StoredHome(
    val place: Place?,
    val snapshot: WeatherSnapshot?,
    val units: DisplayUnits,
    val settings: HomeSettings,
)

private sealed interface SearchOutcome {
    data class Found(val place: Place, val loaded: LoadResult, val selected: Place?) : SearchOutcome

    data class Message(val text: String) : SearchOutcome
}

@Preview(name = "Compact", widthDp = 360, heightDp = 900)
@Preview(name = "Medium", widthDp = 700, heightDp = 900)
@Preview(name = "Expanded", widthDp = 1000, heightDp = 900)
@Composable
private fun HomePreview() {
    ClutterFreeWeatherTheme {
        HomeContent(
            place = previewPlace(),
            snapshot = previewSnapshot(),
            units = DisplayUnits(),
            settings = HomeSettings(),
            now = Instant.parse("2026-11-20T18:00:00Z"),
            query = "",
            onQuery = {},
            onSearch = {},
            suggestions = emptyList(),
            onPick = {},
            searching = false,
            refreshing = false,
            status = null,
            showAlertList = false,
            selectedAlertId = null,
            onOpenAlerts = {},
            onSelectAlert = {},
            onCloseAlerts = {},
            showSettings = false,
            onOpenSettings = {},
            onCloseSettings = {},
            onSettings = {},
        )
    }
}

private fun previewPlace() = Place(
    displayName = "Chicago, IL",
    latitude = 41.8781,
    longitude = -87.6298,
    office = "LOT",
    gridX = 72,
    gridY = 69,
    timeZone = "America/Chicago",
    forecastUrl = "https://api.weather.gov/gridpoints/LOT/72,69/forecast",
    hourlyForecastUrl = "https://api.weather.gov/gridpoints/LOT/72,69/forecast/hourly",
    observationStationsUrl = "https://api.weather.gov/gridpoints/LOT/72,69/stations",
    gridResolvedAt = Instant.parse("2026-11-20T18:00:00Z"),
)

private fun previewSnapshot(): WeatherSnapshot {
    val start = Instant.parse("2026-11-20T15:00:00Z")
    val hourly = List(12) { index ->
        previewPeriod(
            start = start.plus(Duration.ofHours(index.toLong())),
            hours = 1,
            daytime = index < 8,
            celsius = 8.0 + index * 0.4,
            pop = if (index > 6) 30.0 else 10.0,
            humidity = 50.0 + index,
            icon = if (index < 8) {
                "https://api.weather.gov/icons/land/day/sct?size=medium"
            } else {
                "https://api.weather.gov/icons/land/night/few?size=medium"
            },
        )
    }
    return WeatherSnapshot(
        fetchedAt = Instant.parse("2026-11-20T18:00:00Z"),
        observation = Observation(
            observedAt = Instant.parse("2026-11-20T17:50:00Z"),
            stationName = "Chicago O'Hare",
            temperatureCelsius = 10.0,
            dewpointCelsius = 4.0,
            windSpeedMetersPerSecond = 4.0,
            windDirectionDegrees = 270.0,
            windGustMetersPerSecond = null,
            heatIndexCelsius = null,
            windChillCelsius = null,
            relativeHumidityPercent = 55.0,
            textDescription = "Partly Cloudy",
            iconUrl = "https://api.weather.gov/icons/land/day/sct?size=medium",
        ),
        hourly = hourly,
        dayNight = listOf(
            previewPeriod(
                start = Instant.parse("2026-11-20T12:00:00Z"),
                hours = 12,
                daytime = true,
                celsius = 12.0,
                pop = 20.0,
                humidity = null,
                icon = "https://api.weather.gov/icons/land/day/sct?size=medium",
                detailed = "Partly cloudy, with a high near 54. Northwest wind around 10 mph.",
            ),
            previewPeriod(
                start = Instant.parse("2026-11-21T00:00:00Z"),
                hours = 12,
                daytime = false,
                celsius = 2.0,
                pop = 10.0,
                humidity = null,
                icon = "https://api.weather.gov/icons/land/night/few?size=medium",
                detailed = "Mostly clear, with a low around 36.",
            ),
        ),
        alerts = listOf(
            WeatherAlert(
                id = "severe",
                event = "Wind Advisory",
                severity = "Severe",
                headline = "Wind Advisory until 9 PM",
                onset = null,
                ends = null,
                instruction = "Secure loose objects.",
                description = "West winds 25 to 35 mph.",
            ),
            WeatherAlert(
                id = "minor",
                event = "Frost Advisory",
                severity = "Minor",
                headline = "Frost Advisory overnight",
                onset = null,
                ends = null,
                instruction = null,
                description = "Frost is expected.",
            ),
        ),
    )
}

private fun previewPeriod(
    start: Instant,
    hours: Long,
    daytime: Boolean,
    celsius: Double,
    pop: Double,
    humidity: Double?,
    icon: String,
    detailed: String? = null,
) = ForecastPeriod(
    start = start,
    end = start.plus(Duration.ofHours(hours)),
    isDaytime = daytime,
    temperatureCelsius = celsius,
    windSpeedMetersPerSecond = 4.5,
    windSpeedHighMetersPerSecond = null,
    windDirection = "NW",
    shortForecast = "Partly cloudy",
    precipitationProbabilityPercent = pop,
    iconUrl = icon,
    relativeHumidityPercent = humidity,
    dewpointCelsius = celsius - 6.0,
    detailedForecast = detailed,
)
