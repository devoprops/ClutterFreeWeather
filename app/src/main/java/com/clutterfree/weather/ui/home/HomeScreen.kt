package com.clutterfree.weather.ui.home

import android.Manifest
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.window.core.layout.WindowSizeClass
import com.devoprop.clutterfreeweather.R
import com.clutterfree.weather.data.CoarseLocation
import com.clutterfree.weather.data.UsPlace
import com.clutterfree.weather.data.UsPlaceIndex
import com.clutterfree.weather.ui.DateFormats
import com.clutterfree.weather.ui.theme.ClutterFreeWeatherTheme
import com.clutterfree.weather.ui.theme.Dimens
import com.clutterfree.weather.ui.theme.LocalCardPalette
import com.clutterfree.weather.ui.theme.cardPalette
import com.clutterfree.weather.ui.radar.RadarPane
import com.clutterfree.weather.weather.AppConfig
import com.clutterfree.weather.weather.ChartOrientation
import com.clutterfree.weather.weather.DisplayUnits
import com.clutterfree.weather.weather.ForecastPeriod
import com.clutterfree.weather.weather.HomeCard
import com.clutterfree.weather.weather.HomeSettings
import com.clutterfree.weather.weather.LoadResult
import com.clutterfree.weather.weather.Observation
import com.clutterfree.weather.weather.Place
import com.clutterfree.weather.weather.RefreshResult
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
    var saved by remember { mutableStateOf<List<Place>>(emptyList()) }
    var snapshot by remember { mutableStateOf<WeatherSnapshot?>(null) }
    var units by remember { mutableStateOf(DisplayUnits()) }
    var settings by remember { mutableStateOf(HomeSettings()) }
    var query by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var placeIndex by remember { mutableStateOf<UsPlaceIndex?>(null) }
    var showAlertList by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCities by rememberSaveable { mutableStateOf(false) }
    var selectedAlertId by rememberSaveable { mutableStateOf<String?>(null) }
    val noMatch = stringResource(R.string.no_matching_place)
    val outside = stringResource(R.string.outside_coverage)
    val context = LocalContext.current
    val coarse = remember(context) { CoarseLocation(context) }
    var device by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val locationDenied = stringResource(R.string.device_location_denied)
    val locationUnavailable = stringResource(R.string.device_location_unavailable)
    val suggestions = remember(query, placeIndex) { placeIndex?.suggest(query).orEmpty() }
    val closer = remember(device, place, placeIndex) {
        val fix = device ?: return@remember null
        val current = place ?: return@remember null
        placeIndex?.closer(
            latitude = fix.first,
            longitude = fix.second,
            currentLabel = current.displayName,
            currentLatitude = current.latitude,
            currentLongitude = current.longitude,
        )
    }
    val nearestPlace = remember(device, placeIndex) {
        val fix = device ?: return@remember null
        placeIndex?.nearest(fix.first, fix.second)
    }
    val stationCode = if (nearestPlace?.label == place?.displayName) snapshot?.observation?.stationId else null

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

    fun selectPlace(selected: UsPlace) {
        if (busy) return
        val ticket = generation.incrementAndGet()
        busy = true
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
                                PlaceOutcome.Found(loaded, repository.selectedPlace() ?: result.place, repository.savedPlaces())
                            }
                            SearchResult.NoMatchingPlace -> PlaceOutcome.Message(noMatch)
                            SearchResult.OutsideCoverage -> PlaceOutcome.Message(outside)
                            is SearchResult.Failed -> PlaceOutcome.Message(result.message)
                        }
                    }
                }
            }
            if (outcome != null && ticket == generation.get()) {
                when (outcome) {
                    is PlaceOutcome.Found -> {
                        query = ""
                        showCities = false
                        saved = outcome.saved
                        apply(outcome.loaded, outcome.selected)
                    }
                    is PlaceOutcome.Message -> status = outcome.text
                }
            }
            if (ticket == generation.get()) {
                busy = false
                refreshing = false
            }
        }
    }

    fun useFix(selectNearest: Boolean) {
        if (busy) return
        val ticket = generation.get()
        scope.launch {
            val fix = withContext(Dispatchers.IO) { coarse.read() }
            if (ticket != generation.get()) return@launch
            device = fix
            if (!selectNearest) return@launch
            if (fix == null) {
                status = locationUnavailable
                return@launch
            }
            val nearest = placeIndex?.nearest(fix.first, fix.second)
            if (ticket != generation.get()) return@launch
            when {
                nearest != null -> selectPlace(nearest)
                placeIndex != null -> status = locationUnavailable
            }
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useFix(selectNearest = true) else status = locationDenied
    }

    fun readDevice() {
        if (!coarse.permitted()) return
        scope.launch {
            val fix = withContext(Dispatchers.IO) { coarse.read() } ?: return@launch
            device = fix
        }
    }

    LaunchedEffect(Unit) {
        readDevice()
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
                        saved = repository.savedPlaces(),
                        snapshot = repository.snapshot(),
                        units = repository.displayUnits(),
                        settings = repository.homeSettings(),
                    )
                }
            }
        } ?: return@LaunchedEffect
        if (ticket != generation.get()) return@LaunchedEffect
        place = stored.place
        saved = stored.saved
        snapshot = stored.snapshot
        units = stored.units
        settings = stored.settings
        if (stored.place == null) {
            showCities = true
            return@LaunchedEffect
        }
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

    fun submit() {
        val selected = suggestions.firstOrNull()
        if (selected == null) {
            status = noMatch
            return
        }
        selectPlace(selected)
    }

    fun switchTo(target: Place) {
        if (busy) return
        if (target.displayName == place?.displayName) {
            showCities = false
            return
        }
        val ticket = generation.incrementAndGet()
        busy = true
        refreshing = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                gate.withLock {
                    if (ticket != generation.get()) {
                        null
                    } else {
                        repository.switchPlace(target.displayName)?.let {
                            PlaceOutcome.Found(repository.load(), repository.selectedPlace() ?: it, repository.savedPlaces())
                        }
                    }
                }
            }
            if (outcome is PlaceOutcome.Found && ticket == generation.get()) {
                showCities = false
                saved = outcome.saved
                apply(outcome.loaded, outcome.selected)
            }
            if (ticket == generation.get()) {
                busy = false
                refreshing = false
            }
        }
    }

    fun delete(target: Place) {
        scope.launch {
            saved = withContext(Dispatchers.IO) {
                gate.withLock {
                    repository.removePlace(target.displayName)
                    repository.savedPlaces()
                }
            }
        }
    }

    fun refresh() {
        if (busy || refreshing) return
        readDevice()
        if (place == null) return
        val ticket = generation.get()
        refreshing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                gate.withLock {
                    if (ticket != generation.get()) null else repository.refresh() to repository.selectedPlace()
                }
            }
            if (result != null && ticket == generation.get()) {
                place = result.second
                when (val refreshed = result.first) {
                    is RefreshResult.Updated -> {
                        snapshot = refreshed.snapshot
                        status = null
                    }
                    is RefreshResult.Failed -> {
                        snapshot = refreshed.previous ?: snapshot
                        status = refreshed.message
                    }
                    RefreshResult.NoPlace -> snapshot = null
                }
            }
            if (ticket == generation.get()) refreshing = false
        }
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
    BackHandler(enabled = alertsOpen || showSettings || showCities) {
        when {
            alertsOpen -> closeAlert()
            showSettings -> showSettings = false
            else -> showCities = false
        }
    }

    HomeContent(
        place = place,
        snapshot = snapshot,
        units = units,
        settings = settings,
        now = Instant.now(),
        placeIndex = placeIndex,
        refreshing = refreshing,
        onRefresh = ::refresh,
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
        onUnits = { updated ->
            units = updated
            scope.launch {
                withContext(Dispatchers.IO) {
                    gate.withLock { repository.setDisplayUnits(updated) }
                }
            }
        },
        closer = closer,
        onUpdateClosest = { closer?.let(::selectPlace) },
        onReset = {
            settings = HomeSettings()
            units = DisplayUnits()
            scope.launch {
                withContext(Dispatchers.IO) {
                    gate.withLock {
                        repository.setHomeSettings(HomeSettings())
                        repository.setDisplayUnits(DisplayUnits())
                    }
                }
            }
        },
        showCities = showCities,
        onOpenCities = {
            status = null
            showCities = true
        },
        cities = {
            CityPane(
                current = place,
                saved = saved,
                query = query,
                onQuery = {
                    query = it
                    status = null
                },
                suggestions = suggestions,
                onPick = ::selectPlace,
                onSubmit = ::submit,
                onSwitch = ::switchTo,
                onDelete = ::delete,
                onReorder = { reordered ->
                    saved = reordered
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            gate.withLock { repository.reorderPlaces(reordered.map { it.displayName }) }
                        }
                    }
                },
                busy = busy,
                status = status,
                onUseDevice = {
                    if (coarse.permitted()) useFix(selectNearest = true)
                    else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
                nearest = nearestPlace,
                stationCode = stationCode,
                onPickNearest = {
                    val target = nearestPlace ?: return@CityPane
                    val savedMatch = saved.firstOrNull { it.displayName == target.label }
                    if (savedMatch != null) switchTo(savedMatch) else selectPlace(target)
                },
                onBack = { showCities = false },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    place: Place?,
    snapshot: WeatherSnapshot?,
    units: DisplayUnits,
    settings: HomeSettings,
    now: Instant,
    placeIndex: UsPlaceIndex?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
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
    onUnits: (DisplayUnits) -> Unit,
        onReset: () -> Unit,
        closer: UsPlace? = null,
        onUpdateClosest: () -> Unit = {},
        showCities: Boolean,
    onOpenCities: () -> Unit,
    cities: @Composable () -> Unit,
) {
    val sizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val tall = sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    val wide = tall && sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    val narrow = !sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val vertical = !wide && when (settings.chartOrientation) {
        ChartOrientation.AUTO -> narrow && tall
        ChartOrientation.HORIZONTAL -> false
        ChartOrientation.VERTICAL -> true
    }
    val selected = snapshot?.alerts?.firstOrNull { it.id == selectedAlertId }
    val zone = place?.let { runCatching { ZoneId.of(it.timeZone) }.getOrNull() } ?: ZoneId.systemDefault()
    val palette = cardPalette(
        scheme = MaterialTheme.colorScheme,
        theme = settings.cardTheme,
        date = now.atZone(zone).toLocalDate(),
        iconUrl = snapshot?.observation?.iconUrl
            ?: snapshot?.hourly?.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }?.iconUrl,
    )
    val cards = if (place != null && snapshot != null) ForecastCards(place, snapshot, units, settings, now, zone) else null
    var selectedTab by rememberSaveable { mutableStateOf(HomeCard.TODAY.name) }
    val cardState = rememberSaveableStateHolder()

    @Composable
    fun Top() {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
            if (snapshot != null && snapshot.alerts.isNotEmpty()) {
                AlertBanner(snapshot.alerts, onOpenAlerts)
            }
            Header(
                place = place,
                observation = snapshot?.observation,
                zone = zone,
                onOpenCities = onOpenCities,
                onRefresh = onRefresh,
                refreshing = refreshing,
                onOpenSettings = onOpenSettings,
                closer = closer != null,
                suggest = settings.suggestClosest && closer != null,
                onUpdateClosest = onUpdateClosest,
            )
            status?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    CompositionLocalProvider(
        LocalCardPalette provides palette,
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
    ) {
        Box(Modifier.fillMaxSize().background(palette.page)) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding(),
            ) {
                val paneOpen = selected != null || showAlertList || showSettings || showCities
                when {
                    paneOpen -> Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(Dimens.screenPadding),
                    ) {
                        when {
                            selected != null -> AlertDetail(selected, onCloseAlerts)
                            showAlertList -> AlertList(snapshot?.alerts.orEmpty(), onSelectAlert, onCloseAlerts)
                            showSettings -> SettingsPane(
                                settings = settings,
                                onSettings = onSettings,
                                units = units,
                                onUnits = onUnits,
                                onReset = onReset,
                                onBack = onCloseSettings,
                                preview = cards?.let { forecast ->
                                    { forecast.Card(HomeCard.TODAY, showTitle = false, plotHeight = Dimens.graphHeight, vertical = false) }
                                },
                            )
                            else -> cities()
                        }
                    }
                    wide -> BoxWithConstraints(Modifier.fillMaxSize()) {
                        val mainWidth = minOf(maxWidth * AppConfig.WIDE_MAIN_FRACTION, Dimens.homeColumnMax)
                        Row(Modifier.fillMaxSize()) {
                            Column(
                                Modifier
                                    .width(mainWidth)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(Dimens.screenPadding),
                                verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
                            ) {
                                Top()
                                cards?.let { forecast ->
                                    (listOf(HomeCard.TODAY) + settings.cardOrder.filter { it in tabCards && it != HomeCard.RADAR })
                                        .forEach { card ->
                                            cardState.SaveableStateProvider(card.name) {
                                                forecast.Card(card, showTitle = true, plotHeight = Dimens.graphHeight, vertical = false)
                                            }
                                        }
                                }
                            }
                            if (place != null) {
                                ForecastCard(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(vertical = Dimens.screenPadding)
                                        .padding(end = Dimens.screenPadding),
                                ) {
                                    RadarPane(place, settings.radarMiles, placeIndex, Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                    else -> Column(Modifier.fillMaxSize()) {
                        Box(Modifier.padding(start = Dimens.screenPadding, end = Dimens.screenPadding, top = Dimens.screenPadding)) {
                            Top()
                        }
                        if (place == null) return@Column
                        val tabs = listOf(HomeCard.TODAY) + settings.cardOrder.filter { it in tabCards }
                        val current = tabs.firstOrNull { it.name == selectedTab } ?: HomeCard.TODAY
                        PrimaryTabRow(selectedTabIndex = tabs.indexOf(current), containerColor = palette.page) {
                            tabs.forEach { card ->
                                Tab(
                                    selected = card == current,
                                    onClick = { selectedTab = card.name },
                                    text = {
                                        Text(
                                            stringResource(cardLabel(card)),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                        }
                        cardState.SaveableStateProvider(current.name) {
                            if (current == HomeCard.RADAR) {
                                RadarPane(place, settings.radarMiles, placeIndex, Modifier.weight(1f).fillMaxWidth())
                                return@SaveableStateProvider
                            }
                            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                                val plotHeight = when {
                                    !vertical || current == HomeCard.TODAY -> Dimens.tabGraphHeight
                                    current == HomeCard.HOURLY -> maxHeight * AppConfig.VERTICAL_HOURLY_PLOT_FRACTION
                                    else -> maxHeight * AppConfig.VERTICAL_EXTENDED_PLOT_FRACTION
                                }
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(Dimens.screenPadding),
                                ) {
                                    cards?.Card(current, showTitle = false, plotHeight = plotHeight, vertical = vertical)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    place: Place?,
    observation: Observation?,
    zone: ZoneId,
    onOpenCities: () -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
    onOpenSettings: () -> Unit,
    closer: Boolean,
    suggest: Boolean,
    onUpdateClosest: () -> Unit,
) {
    val changeCity = stringResource(R.string.change_city)
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.touchTarget)
                    .clickable(onClickLabel = changeCity, onClick = onOpenCities),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = place?.displayName ?: stringResource(R.string.place_prompt),
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (closer) {
                    val closerPlace = stringResource(R.string.closer_place)
                    Text(
                        text = stringResource(R.string.closer_mark),
                        modifier = Modifier
                            .padding(start = Dimens.tightGap)
                            .clearAndSetSemantics { contentDescription = closerPlace },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Icon(painter = painterResource(R.drawable.ic_expand_more), contentDescription = null)
            }
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                Icon(painter = painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.refresh))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
            }
        }
        val station = observation?.stationName
        val time = observation?.observedAt?.atZone(zone)?.format(DateFormats.clock)
        if (station != null || time != null) {
            val missing = stringResource(R.string.missing)
            Text(
                text = stringResource(R.string.observed_at, station ?: missing, time ?: missing),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (suggest) {
            TextButton(
                onClick = onUpdateClosest,
                modifier = Modifier.heightIn(min = Dimens.touchTarget),
            ) {
                Text(stringResource(R.string.update_closest))
            }
        }
    }
}

private class ForecastCards(
    private val place: Place,
    private val snapshot: WeatherSnapshot,
    private val units: DisplayUnits,
    private val settings: HomeSettings,
    private val now: Instant,
    private val zone: ZoneId,
) {
    @Composable
    fun Card(card: HomeCard, showTitle: Boolean, plotHeight: Dp, vertical: Boolean) {
        when (card) {
            HomeCard.TODAY -> TodayCard(
                snapshot = snapshot,
                zone = zone,
                latitude = place.latitude,
                longitude = place.longitude,
                now = now,
                units = units,
                settings = settings,
                showTitle = showTitle,
                plotHeight = plotHeight,
            )
            HomeCard.HOURLY -> HourlyCard(
                hours = snapshot.hourly,
                dayNight = snapshot.dayNight,
                zone = zone,
                latitude = place.latitude,
                longitude = place.longitude,
                units = units,
                settings = settings,
                now = now,
                observation = snapshot.observation,
                showTitle = showTitle,
                plotHeight = plotHeight,
                vertical = vertical,
            )
            HomeCard.EXTENDED -> ExtendedCard(
                dayNight = snapshot.dayNight,
                hourly = snapshot.hourly,
                zone = zone,
                latitude = place.latitude,
                longitude = place.longitude,
                units = units,
                settings = settings,
                now = now,
                showTitle = showTitle,
                plotHeight = plotHeight,
                vertical = vertical,
            )
            HomeCard.RADAR -> Unit
        }
    }
}

private data class StoredHome(
    val place: Place?,
    val saved: List<Place>,
    val snapshot: WeatherSnapshot?,
    val units: DisplayUnits,
    val settings: HomeSettings,
)

private sealed interface PlaceOutcome {
    data class Found(val loaded: LoadResult, val selected: Place, val saved: List<Place>) : PlaceOutcome

    data class Message(val text: String) : PlaceOutcome
}

@Preview(name = "Compact", widthDp = 360, heightDp = 900)
@Preview(name = "Compact dark", widthDp = 360, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
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
            placeIndex = null,
            refreshing = false,
            onRefresh = {},
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
            onUnits = {},
            onReset = {},
            showCities = false,
            onOpenCities = {},
            cities = {},
        )
    }
}

@Preview(name = "Settings", widthDp = 360, heightDp = 1600)
@Composable
private fun SettingsPreview() {
    ClutterFreeWeatherTheme {
        SettingsPane(
            settings = HomeSettings(),
            onSettings = {},
            units = DisplayUnits(),
            onUnits = {},
            onReset = {},
            onBack = {},
            modifier = Modifier.padding(Dimens.screenPadding),
        )
    }
}

@Preview(name = "Cities", widthDp = 360, heightDp = 640)
@Composable
private fun CitiesPreview() {
    ClutterFreeWeatherTheme {
        val chicago = previewPlace()
        CityPane(
            current = chicago,
            saved = listOf(chicago, chicago.copy(displayName = "Huntsville, AL")),
            query = "",
            onQuery = {},
            suggestions = emptyList(),
            onPick = {},
            onSubmit = {},
            onSwitch = {},
            onDelete = {},
            onReorder = {},
            onUseDevice = {},
            busy = false,
            status = null,
            onBack = {},
            modifier = Modifier.padding(Dimens.screenPadding),
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
