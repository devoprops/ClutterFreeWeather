package com.clutterfree.weather.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextOverflow
import com.devoprop.clutterfreeweather.R
import com.clutterfree.weather.ui.DateFormats
import com.clutterfree.weather.ui.conditionLabel
import androidx.compose.ui.unit.Dp
import com.clutterfree.weather.ui.theme.Dimens
import com.clutterfree.weather.ui.theme.scaledHeight
import com.clutterfree.weather.ui.theme.scaledWidth
import com.clutterfree.weather.ui.theme.LocalCardPalette
import com.clutterfree.weather.weather.AppConfig
import com.clutterfree.weather.weather.DisplayUnits
import com.clutterfree.weather.weather.ForecastPeriod
import com.clutterfree.weather.weather.HomeSettings
import com.clutterfree.weather.weather.Observation
import com.clutterfree.weather.weather.UnitFormatter
import com.clutterfree.weather.weather.civilDays
import com.clutterfree.weather.weather.forecastTemperatureCenter
import com.clutterfree.weather.weather.nightIndexBands
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HourlyCard(
    hours: List<ForecastPeriod>,
    dayNight: List<ForecastPeriod>,
    zone: ZoneId,
    latitude: Double,
    longitude: Double,
    units: DisplayUnits,
    settings: HomeSettings,
    now: Instant,
    observation: Observation?,
    showTitle: Boolean,
    plotHeight: Dp,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    val upcoming = hours.filter { it.end.isAfter(now) }
    if (upcoming.isEmpty()) return
    val formatter = UnitFormatter(units)
    val missing = stringResource(R.string.missing)
    val scroll = rememberScrollState()
    val indexByStart = upcoming.withIndex().associate { it.value.start to it.index.toDouble() }
    val runs = dayRuns(upcoming, zone)
    val bandByIndex = IntArray(upcoming.size)
    var cursor = 0
    runs.forEachIndexed { band, run ->
        repeat(run.count) { bandByIndex[cursor++] = band }
    }
    var moved by rememberSaveable { mutableStateOf(false) }
    var savedFraction by rememberSaveable { mutableStateOf(0f) }
    val shape = RoundedCornerShape(Dimens.hourCorner)
    val temperatureCenter = forecastTemperatureCenter(
        civilDays(dayNight, hours, zone).flatMap { listOfNotNull(it.highCelsius, it.lowCelsius) },
    )

    fun liveFor(period: ForecastPeriod): Observation? = observation?.takeIf {
        it.iconUrl != null && !now.isBefore(period.start) && now.isBefore(period.end)
    }
    val statuses = upcoming.map { period ->
        val live = liveFor(period)
        conditionLabel(live?.iconUrl ?: period.iconUrl) ?: live?.textDescription ?: period.shortForecast.orEmpty()
    }
    val panelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = AppConfig.HOUR_PANEL_ALPHA)

    @Composable
    fun HourIcon(index: Int, period: ForecastPeriod, iconModifier: Modifier, inPanel: Boolean = false) {
        val outer = if (inPanel) iconModifier else iconModifier.padding(Dimens.hairline).background(dayBand(bandByIndex[index]))
        Box(outer) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .border(Dimens.hairline, MaterialTheme.colorScheme.outlineVariant, shape),
            ) {
                val live = liveFor(period)
                val iconUrl = live?.iconUrl ?: period.iconUrl
                WeatherIcon(
                    iconUrl = iconUrl,
                    at = live?.observedAt ?: period.start,
                    latitude = latitude,
                    longitude = longitude,
                    zone = zone,
                    contentDescription = conditionLabel(iconUrl) ?: live?.textDescription ?: period.shortForecast,
                    modifier = Modifier.fillMaxSize().padding(Dimens.iconInset),
                )
                if (!inPanel) {
                    Text(
                        text = period.temperatureCelsius?.let(formatter::temperatureCompact) ?: missing,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    ForecastCard(modifier) {
        Column(Modifier.padding(Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
            if (showTitle) {
                Text(text = stringResource(R.string.hourly), style = MaterialTheme.typography.titleMedium)
            }
            if (vertical) {
                val density = LocalDensity.current
                val hourStyle = MaterialTheme.typography.labelLarge
                val tempStyle = MaterialTheme.typography.titleMedium
                val statusStyle = MaterialTheme.typography.labelMedium
                val panelChrome = Dimens.hourPanelGap + Dimens.hourPanelPadding * 2
                val minRow = maxOf(
                    Dimens.verticalHourRow,
                    hourStyle.scaledHeight(1) + tempStyle.scaledHeight(1) + statusStyle.scaledHeight(1) + panelChrome,
                )
                val heightPx = with(density) { plotHeight.toPx() }
                val visible = (heightPx / with(density) { minRow.toPx() }).toInt().coerceIn(1, upcoming.size)
                val rowPx = heightPx / visible
                val rowDp = with(density) { rowPx.toDp() }
                val iconSize = rowDp - panelChrome
                val columnWidth = maxOf(
                    Dimens.verticalHourColumn,
                    hourStyle.scaledWidth(6) + iconSize + Dimens.hourPanelPadding * 2 + Dimens.tightGap,
                )
                val domainStart = if (rowPx < 1f) 0.0 else scroll.value / rowPx.toDouble()
                val firstOfDay = runs.runningFold(0) { start, run -> start + run.count }.toSet()
                val rows: @Composable () -> Unit = {
                    Column(
                        Modifier
                            .width(columnWidth)
                            .height(plotHeight)
                            .verticalScroll(scroll),
                    ) {
                        upcoming.forEachIndexed { index, period ->
                            val time = period.start.atZone(zone)
                                .format(if (index in firstOfDay) DateFormats.dayHour() else DateFormats.hour())
                            Box(
                                Modifier
                                    .height(rowDp)
                                    .fillMaxWidth()
                                    .background(dayBand(bandByIndex[index]))
                                    .padding(vertical = Dimens.hourPanelGap / 2),
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(shape)
                                        .background(panelColor)
                                        .padding(Dimens.hourPanelPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f).padding(end = Dimens.tightGap)) {
                                        Text(
                                            text = time,
                                            style = hourStyle,
                                            fontWeight = if (index in firstOfDay) FontWeight.SemiBold else null,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = period.temperatureCelsius?.let(formatter::temperatureCompact) ?: missing,
                                            style = tempStyle,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = statuses[index],
                                            style = statusStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    HourIcon(index, period, Modifier.size(iconSize), inPanel = true)
                                }
                            }
                        }
                    }
                }
                SubCard {
                    if (settings.hourlyGraph.visible) {
                        WeatherGraph(
                            points = upcoming.map { it.toGraphPoint() },
                            xOf = { point -> indexByStart[point.time] },
                            domainStart = domainStart,
                            domainEnd = domainStart + visible,
                            nightBands = nightIndexBands(upcoming, zone, latitude, longitude),
                            dayTints = emptyList(),
                            labels = emptyList(),
                            scrubberFraction = if (moved) savedFraction else 0f,
                            onScrubberFraction = {
                                moved = true
                                savedFraction = it
                            },
                            graph = settings.hourlyGraph,
                            units = units,
                            temperatureCenterCelsius = temperatureCenter,
                            timeAt = { index -> timeAtIndex(upcoming, index) },
                            zone = zone,
                            plotHeight = plotHeight,
                            vertical = true,
                            leading = rows,
                            modifier = Modifier.padding(Dimens.graphPadding),
                        )
                    } else {
                        Box(Modifier.padding(Dimens.graphPadding)) { rows() }
                    }
                }
                return@Column
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val pad = Dimens.graphPadding
                val density = LocalDensity.current
                val innerPx = constraints.maxWidth - with(density) { (pad * 2).roundToPx() }
                val timeStyle = MaterialTheme.typography.labelMedium
                val tempStyle = MaterialTheme.typography.titleSmall
                val statusStyle = MaterialTheme.typography.labelSmall
                val measurer = rememberTextMeasurer()
                val times = upcoming.map { it.start.atZone(zone).format(DateFormats.hour()) }
                val textPx = remember(statuses, times, statusStyle, timeStyle) {
                    val statusPx = statuses.distinct().maxOfOrNull { twoLineWidth(measurer, it, statusStyle) } ?: 0
                    val timePx = times.distinct().maxOfOrNull { measurer.measure(it, timeStyle).size.width } ?: 0
                    maxOf(statusPx, timePx)
                }
                val chromePx = with(density) { (Dimens.hourPanelGap + Dimens.hourPanelPadding * 2).toPx() }
                val minBoxPx = maxOf(
                    with(density) { MaterialTheme.typography.labelLarge.scaledHeight(2).toPx() },
                    textPx + chromePx,
                )
                val visible = minOf(
                    AppConfig.HOURLY_VISIBLE_HOURS,
                    upcoming.size,
                    (innerPx / minBoxPx).toInt().coerceAtLeast(1),
                )
                val boxPx = innerPx / visible.toFloat()
                val boxDp = with(density) { boxPx.toDp() }
                val domainStart = if (boxPx < 1f) 0.0 else scroll.value / boxPx.toDouble()
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
                    SubCard {
                        Column(Modifier.padding(pad).horizontalScroll(scroll)) {
                        Row {
                            runs.forEachIndexed { index, run ->
                                Box(
                                    Modifier
                                        .width(boxDp * run.count)
                                        .heightIn(min = Dimens.dayLabel)
                                        .background(dayBand(index)),
                                ) {
                                    Text(
                                        text = run.date.format(DateFormats.weekday()),
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .fillMaxWidth()
                                            .padding(horizontal = Dimens.tightGap, vertical = Dimens.tightGap),
                                        style = MaterialTheme.typography.labelLarge,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Row {
                            upcoming.forEachIndexed { index, period ->
                                Box(
                                    Modifier
                                        .width(boxDp)
                                        .background(dayBand(bandByIndex[index]))
                                        .padding(horizontal = Dimens.hourPanelGap / 2, vertical = Dimens.hourPanelGap),
                                ) {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(shape)
                                            .background(panelColor)
                                            .padding(Dimens.hourPanelPadding),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            text = times[index],
                                            style = timeStyle,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        HourIcon(
                                            index,
                                            period,
                                            Modifier.padding(vertical = Dimens.tightGap).size(boxDp - Dimens.hourPanelGap - Dimens.hourPanelPadding * 2),
                                            inPanel = true,
                                        )
                                        Text(
                                            text = period.temperatureCelsius?.let(formatter::temperatureCompact) ?: missing,
                                            style = tempStyle,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = statuses[index],
                                            style = statusStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            minLines = 2,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                        }
                    }
                    if (settings.hourlyGraph.visible) {
                        SubCard {
                            WeatherGraph(
                                points = upcoming.map { it.toGraphPoint() },
                                xOf = { point -> indexByStart[point.time] },
                                domainStart = domainStart,
                                domainEnd = domainStart + visible,
                                nightBands = nightIndexBands(upcoming, zone, latitude, longitude),
                                dayTints = emptyList(),
                                labels = emptyList(),
                                scrubberFraction = if (moved) savedFraction else 0f,
                                onScrubberFraction = {
                                    moved = true
                                    savedFraction = it
                                },
                                graph = settings.hourlyGraph,
                                units = units,
                                temperatureCenterCelsius = temperatureCenter,
                                timeAt = { index -> timeAtIndex(upcoming, index) },
                                zone = zone,
                                plotHeight = plotHeight,
                                modifier = Modifier.padding(pad),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun dayBand(index: Int): Color {
    val palette = LocalCardPalette.current
    val scheme = MaterialTheme.colorScheme
    return if (index % 2 == 0) {
        palette?.bandEven ?: scheme.surfaceContainerLow
    } else {
        palette?.bandOdd ?: scheme.surfaceContainerHighest
    }
}

private fun twoLineWidth(measurer: TextMeasurer, text: String, style: TextStyle): Int {
    val full = measurer.measure(text, style, softWrap = false).size.width
    val longestWord = text.split(' ').maxOf { measurer.measure(it, style, softWrap = false).size.width }
    var width = maxOf(longestWord, (full + 1) / 2)
    while (width < full && measurer.measure(text, style, constraints = Constraints(maxWidth = width)).lineCount > 2) {
        width += longestWord / 4 + 1
    }
    return minOf(width, full)
}

private data class DayRun(val date: LocalDate, val count: Int)

private fun dayRuns(hours: List<ForecastPeriod>, zone: ZoneId): List<DayRun> {
    if (hours.isEmpty()) return emptyList()
    val runs = mutableListOf<DayRun>()
    var date = hours.first().start.atZone(zone).toLocalDate()
    var count = 0
    hours.forEach { period ->
        val next = period.start.atZone(zone).toLocalDate()
        if (next != date) {
            runs += DayRun(date, count)
            date = next
            count = 0
        }
        count += 1
    }
    runs += DayRun(date, count)
    return runs
}

private fun timeAtIndex(hours: List<ForecastPeriod>, index: Double): Instant {
    val slot = index.toInt().coerceIn(0, hours.lastIndex)
    val fraction = (index - slot).coerceIn(0.0, 1.0)
    val period = hours[slot]
    val span = Duration.between(period.start, period.end).toMillis().coerceAtLeast(1)
    return period.start.plusMillis((span * fraction).toLong())
}
