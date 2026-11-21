package com.clutterfree.weather.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import com.clutterfree.weather.R
import com.clutterfree.weather.ui.DateFormats
import androidx.compose.ui.unit.Dp
import com.clutterfree.weather.ui.theme.Dimens
import com.clutterfree.weather.ui.theme.LocalCardPalette
import com.clutterfree.weather.ui.theme.WeatherColors
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

    @Composable
    fun HourIcon(index: Int, period: ForecastPeriod, iconModifier: Modifier) {
        Box(iconModifier.padding(Dimens.hairline).background(dayBand(bandByIndex[index]))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .border(Dimens.hairline, MaterialTheme.colorScheme.outlineVariant, shape),
            ) {
                val live = observation?.takeIf {
                    it.iconUrl != null && !now.isBefore(period.start) && now.isBefore(period.end)
                }
                WeatherIcon(
                    iconUrl = live?.iconUrl ?: period.iconUrl,
                    at = live?.observedAt ?: period.start,
                    latitude = latitude,
                    longitude = longitude,
                    zone = zone,
                    contentDescription = live?.textDescription ?: period.shortForecast,
                    modifier = Modifier.fillMaxSize().padding(Dimens.iconInset),
                )
                Text(
                    text = period.temperatureCelsius?.let(formatter::temperatureCompact) ?: missing,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    color = WeatherColors.iconLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
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
                val heightPx = with(density) { plotHeight.toPx() }
                val visible = (heightPx / with(density) { Dimens.verticalHourRow.toPx() }).toInt()
                    .coerceIn(1, upcoming.size)
                val rowPx = heightPx / visible
                val rowDp = with(density) { rowPx.toDp() }
                val domainStart = if (rowPx < 1f) 0.0 else scroll.value / rowPx.toDouble()
                val firstOfDay = runs.runningFold(0) { start, run -> start + run.count }.toSet()
                val rows: @Composable () -> Unit = {
                    Column(
                        Modifier
                            .width(Dimens.verticalHourColumn)
                            .height(plotHeight)
                            .verticalScroll(scroll),
                    ) {
                        upcoming.forEachIndexed { index, period ->
                            Row(
                                Modifier.height(rowDp).background(dayBand(bandByIndex[index])),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f).padding(horizontal = Dimens.tightGap)) {
                                    if (index in firstOfDay) {
                                        Text(
                                            text = period.start.atZone(zone).format(DateFormats.weekday),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                        )
                                    }
                                    Text(
                                        text = period.start.atZone(zone).format(DateFormats.hour),
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                    )
                                }
                                HourIcon(index, period, Modifier.width(rowDp).height(rowDp))
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
            val visible = minOf(AppConfig.HOURLY_VISIBLE_HOURS, upcoming.size)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val pad = Dimens.graphPadding
                val innerPx = constraints.maxWidth - with(LocalDensity.current) { (pad * 2).roundToPx() }
                val boxPx = innerPx / visible.toFloat()
                val boxDp = with(LocalDensity.current) { boxPx.toDp() }
                val domainStart = if (boxPx < 1f) 0.0 else scroll.value / boxPx.toDouble()
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
                    SubCard {
                        Column(Modifier.padding(pad).horizontalScroll(scroll)) {
                        Row {
                            runs.forEachIndexed { index, run ->
                                Box(
                                    Modifier
                                        .width(boxDp * run.count)
                                        .height(Dimens.dayLabel)
                                        .background(dayBand(index)),
                                ) {
                                    Text(
                                        text = run.date.format(DateFormats.weekday),
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .fillMaxWidth()
                                            .padding(horizontal = Dimens.tightGap),
                                        style = MaterialTheme.typography.labelLarge,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        softWrap = false,
                                        autoSize = TextAutoSize.StepBased(
                                            minFontSize = Dimens.minAutoSize,
                                            maxFontSize = MaterialTheme.typography.labelLarge.fontSize,
                                        ),
                                    )
                                }
                            }
                        }
                        Row {
                            upcoming.forEachIndexed { index, period ->
                                HourIcon(index, period, Modifier.width(boxDp).height(boxDp))
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
