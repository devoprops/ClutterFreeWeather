package com.clutterfree.weather.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.devoprop.clutterfreeweather.R
import com.clutterfree.weather.ui.DateFormats
import com.clutterfree.weather.ui.conditionLabel
import androidx.compose.ui.unit.Dp
import com.clutterfree.weather.ui.theme.Dimens
import com.clutterfree.weather.ui.theme.scaledHeight
import com.clutterfree.weather.ui.theme.scaledWidth
import com.clutterfree.weather.weather.AppConfig
import com.clutterfree.weather.weather.Measures
import com.clutterfree.weather.weather.CivilDay
import com.clutterfree.weather.weather.DisplayUnits
import com.clutterfree.weather.weather.ExtendedDay
import com.clutterfree.weather.weather.ForecastPeriod
import com.clutterfree.weather.weather.HomeSettings
import com.clutterfree.weather.weather.UnitFormatter
import com.clutterfree.weather.weather.civilDays
import com.clutterfree.weather.weather.forecastTemperatureCenter
import com.clutterfree.weather.weather.mergeExtendedDays
import com.clutterfree.weather.weather.toCivilDay
import com.clutterfree.weather.weather.sunTimes
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The Daily card. See [com.clutterfree.weather.weather.HomeCard.EXTENDED]. */
@Composable
fun ExtendedCard(
    dayNight: List<ForecastPeriod>,
    hourly: List<ForecastPeriod>,
    zone: ZoneId,
    latitude: Double,
    longitude: Double,
    units: DisplayUnits,
    settings: HomeSettings,
    now: Instant,
    extendedDays: List<ExtendedDay> = emptyList(),
    showTitle: Boolean,
    plotHeight: Dp,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    val today = now.atZone(zone).toLocalDate()
    val days = mergeExtendedDays(
        civilDays(dayNight, hourly, zone),
        extendedDays.map { it.toCivilDay(zone) },
    ).filter { !it.date.isBefore(today) }
    if (days.isEmpty()) return
    val formatter = UnitFormatter(units)
    val missing = stringResource(R.string.missing)
    val scroll = rememberScrollState()
    val visible = settings.extendedVisibleDays
        .coerceIn(AppConfig.EXTENDED_DAY_MIN, AppConfig.EXTENDED_DAY_MAX)
        .coerceAtMost(days.size)
    val temperatureCenter = forecastTemperatureCenter(
        days.flatMap { listOfNotNull(it.highCelsius, it.lowCelsius) },
    )
    val dayIndex = days.withIndex().associate { it.value.date to it.index }
    var pinned by rememberSaveable { mutableStateOf<String?>(null) }
    var moved by rememberSaveable { mutableStateOf(false) }
    var savedFraction by rememberSaveable { mutableStateOf(0f) }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.isScrollInProgress }
            .distinctUntilChanged()
            .collect { moving -> if (moving) pinned = null }
    }

    ForecastCard(modifier) {
        Column(Modifier.padding(Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
            if (showTitle) {
                Text(text = stringResource(R.string.extended), style = MaterialTheme.typography.titleMedium)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val pad = Dimens.graphPadding
                val density = LocalDensity.current
                val lengthPx = if (vertical) {
                    with(density) { plotHeight.toPx() }
                } else {
                    constraints.maxWidth - with(density) { (pad * 2).toPx() }
                }
                val dayStyle = MaterialTheme.typography.titleMedium
                val minTilePx = with(density) { dayStyle.scaledHeight(4).toPx() }
                val shown = minOf(visible, (lengthPx / minTilePx).toInt().coerceAtLeast(1))
                val boxPx = lengthPx / shown.toFloat()
                val columnWidth = maxOf(Dimens.verticalDayColumn, dayStyle.scaledWidth(6))
                val boxDp = with(density) { boxPx.toDp() }
                val domainStart = if (boxPx < 1f) 0.0 else scroll.value / boxPx.toDouble()
                val leftmost = days[(domainStart.toInt()).coerceIn(0, days.lastIndex)]
                val pinnedDate = pinned?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val detail = days.firstOrNull { it.date == pinnedDate } ?: leftmost
                val nowX = extendedX(now, dayIndex, zone)
                val liveFraction = if (nowX == null) {
                    0f
                } else {
                    ((nowX - domainStart) / shown).toFloat().coerceIn(0f, 1f)
                }
                val fraction = if (moved) savedFraction else liveFraction
                @Composable
                fun Box(day: CivilDay, width: Dp, height: Dp) {
                    DayBox(
                        day = day,
                        width = width,
                        height = height,
                        pinned = day.date == pinnedDate,
                        temperature = day.highCelsius?.let(formatter::temperatureCompact) ?: missing,
                        latitude = latitude,
                        longitude = longitude,
                        zone = zone,
                        onTap = { pinned = day.date.toString() },
                    )
                }
                val dayColumn: @Composable () -> Unit = {
                    Column(
                        Modifier
                            .width(columnWidth)
                            .height(plotHeight)
                            .verticalScroll(scroll),
                    ) {
                        days.forEach { day -> Box(day, columnWidth, boxDp) }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
                    if (!vertical) {
                        SubCard {
                            Row(Modifier.padding(pad).horizontalScroll(scroll)) {
                                days.forEach { day -> Box(day, boxDp, boxDp) }
                            }
                        }
                    } else if (!settings.extendedGraph.visible) {
                        SubCard {
                            androidx.compose.foundation.layout.Box(Modifier.padding(pad)) { dayColumn() }
                        }
                    }
                    if (settings.extendedGraph.visible) {
                        SubCard {
                            WeatherGraph(
                                points = hourly.map { it.toGraphPoint() },
                                xOf = { point -> extendedX(point.time, dayIndex, zone) },
                                domainStart = domainStart,
                                domainEnd = domainStart + shown,
                                nightBands = nightBands(days, zone, latitude, longitude),
                                dayTints = days.indices.filter { it % 2 == 1 }.map { it.toDouble()..(it + 1).toDouble() },
                                labels = emptyList(),
                                scrubberFraction = fraction,
                                onScrubberFraction = {
                                    moved = true
                                    savedFraction = it
                                },
                                graph = settings.extendedGraph,
                                units = units,
                                temperatureCenterCelsius = temperatureCenter,
                                timeAt = { x -> timeAtDay(days, zone, x) },
                                zone = zone,
                                pastUntil = nowX,
                                emptyAfter = days.indexOfFirst { it.extended }.takeIf { it >= 0 }?.toDouble(),
                                plotHeight = plotHeight,
                                vertical = vertical,
                                leading = dayColumn.takeIf { vertical },
                                modifier = Modifier.padding(pad),
                            )
                        }
                    }
                    SubCard {
                        DayDetail(
                            day = detail,
                            zone = zone,
                            latitude = latitude,
                            longitude = longitude,
                            units = units,
                            details = settings.details,
                            showDate = true,
                            showHighLow = true,
                            isToday = detail.date == today,
                            modifier = Modifier.padding(Dimens.cardPadding),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayBox(
    day: CivilDay,
    width: Dp,
    height: Dp,
    pinned: Boolean,
    temperature: String,
    latitude: Double,
    longitude: Double,
    zone: ZoneId,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(Dimens.dayCorner)
    val border = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val iconPeriod = day.daytime ?: day.nighttime
    Box(
        Modifier
            .width(width)
            .height(height)
            .padding(Dimens.hairline)
            .clip(shape)
            .border(if (pinned) Dimens.emphasisBorder else Dimens.hairline, border, shape)
            .clickable(onClick = onTap),
    ) {
        WeatherIcon(
            iconUrl = iconPeriod?.iconUrl,
            at = iconPeriod?.start ?: day.date.atStartOfDay(zone).toInstant(),
            latitude = latitude,
            longitude = longitude,
            zone = zone,
            contentDescription = conditionLabel(iconPeriod?.iconUrl) ?: iconPeriod?.shortForecast,
            modifier = Modifier.fillMaxSize().padding(Dimens.iconInset),
        )
        Text(
            text = day.date.format(DateFormats.dayCard()),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = Dimens.iconInset),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = temperature,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun extendedX(
    time: java.time.Instant,
    dayIndex: Map<LocalDate, Int>,
    zone: ZoneId,
): Double? {
    val date = time.atZone(zone).toLocalDate()
    val index = dayIndex[date] ?: return null
    val start = date.atStartOfDay(zone).toInstant()
    val fraction = Duration.between(start, time).seconds / Measures.SECONDS_PER_DAY
    return index + fraction.coerceIn(0.0, 1.0)
}

private fun timeAtDay(days: List<CivilDay>, zone: ZoneId, x: Double): java.time.Instant {
    val index = x.toInt().coerceIn(0, days.lastIndex)
    val fraction = (x - index).coerceIn(0.0, 1.0)
    val start = days[index].date.atStartOfDay(zone).toInstant()
    return start.plusMillis((fraction * Measures.MILLIS_PER_DAY).toLong())
}

private fun nightBands(
    days: List<CivilDay>,
    zone: ZoneId,
    latitude: Double,
    longitude: Double,
): List<ClosedFloatingPointRange<Double>> = buildList {
    days.forEachIndexed { index, day ->
        val start = day.date.atStartOfDay(zone).toInstant()
        val sun = sunTimes(day.date, latitude, longitude, zone)
        if (sun == null) {
            add(index.toDouble()..(index + 1).toDouble())
        } else {
            fun position(instant: java.time.Instant): Double {
                val fraction = Duration.between(start, instant).seconds.coerceIn(0, Measures.SECONDS_PER_DAY.toLong()) /
                    Measures.SECONDS_PER_DAY
                return index + fraction
            }
            add(index.toDouble()..position(sun.sunrise))
            add(position(sun.sunset)..(index + 1).toDouble())
        }
    }
}.filter { it.endInclusive > it.start }
