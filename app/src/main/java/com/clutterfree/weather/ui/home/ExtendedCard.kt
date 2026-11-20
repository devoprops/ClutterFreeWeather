package com.clutterfree.weather.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clutterfree.weather.R
import com.clutterfree.weather.weather.CivilDay
import com.clutterfree.weather.weather.DisplayUnits
import com.clutterfree.weather.weather.ForecastPeriod
import com.clutterfree.weather.weather.HomeSettings
import com.clutterfree.weather.weather.UnitFormatter
import com.clutterfree.weather.weather.civilDays
import com.clutterfree.weather.weather.sunTimes
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ExtendedCard(
    dayNight: List<ForecastPeriod>,
    hourly: List<ForecastPeriod>,
    zone: ZoneId,
    latitude: Double,
    longitude: Double,
    units: DisplayUnits,
    settings: HomeSettings,
    modifier: Modifier = Modifier,
) {
    val days = civilDays(dayNight, hourly, zone)
    if (days.isEmpty()) return
    val formatter = UnitFormatter(units)
    val missing = stringResource(R.string.missing)
    val scroll = rememberScrollState()
    val visible = settings.extendedVisibleDays.coerceIn(1, 7).coerceAtMost(days.size)
    val dayIndex = days.withIndex().associate { it.value.date to it.index }
    var pinned by rememberSaveable { mutableStateOf<String?>(null) }
    var scrubber by rememberSaveable { mutableStateOf(0f) }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.isScrollInProgress }
            .distinctUntilChanged()
            .collect { moving -> if (moving) pinned = null }
    }

    ForecastCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.extended), style = MaterialTheme.typography.titleMedium)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val pad = 8.dp
                val innerPx = constraints.maxWidth - with(LocalDensity.current) { (pad * 2).roundToPx() }
                val boxPx = innerPx / visible.toFloat()
                val boxDp = with(LocalDensity.current) { boxPx.toDp() }
                val domainStart = if (boxPx < 1f) 0.0 else scroll.value / boxPx.toDouble()
                val leftmost = days[(domainStart.toInt()).coerceIn(0, days.lastIndex)]
                val pinnedDate = pinned?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val detail = days.firstOrNull { it.date == pinnedDate } ?: leftmost
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubCard {
                        Row(Modifier.padding(pad).horizontalScroll(scroll)) {
                        days.forEach { day ->
                            DayBox(
                                day = day,
                                width = boxDp,
                                pinned = day.date == pinnedDate,
                                temperature = day.highCelsius?.let(formatter::temperatureCompact) ?: missing,
                                latitude = latitude,
                                longitude = longitude,
                                zone = zone,
                                onTap = { pinned = day.date.toString() },
                            )
                        }
                        }
                    }
                    SubCard {
                        WeatherGraph(
                            points = hourly.map { it.toGraphPoint() },
                            xOf = { point -> extendedX(point.time, dayIndex, zone) },
                            domainStart = domainStart,
                            domainEnd = domainStart + visible,
                            nightBands = nightBands(days, zone, latitude, longitude),
                            dayTints = days.indices.filter { it % 2 == 1 }.map { it.toDouble()..(it + 1).toDouble() },
                            labels = emptyList(),
                            scrubberFraction = scrubber,
                            onScrubberFraction = { scrubber = it },
                            settings = settings,
                            units = units,
                            timeAt = { x -> timeAtDay(days, zone, x) },
                            zone = zone,
                            modifier = Modifier.padding(pad),
                        )
                    }
                    SubCard {
                        DayDetail(
                            day = detail,
                            zone = zone,
                            latitude = latitude,
                            longitude = longitude,
                            units = units,
                            modifier = Modifier.padding(12.dp),
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
    width: androidx.compose.ui.unit.Dp,
    pinned: Boolean,
    temperature: String,
    latitude: Double,
    longitude: Double,
    zone: ZoneId,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val border = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val iconPeriod = day.daytime ?: day.nighttime
    Box(
        Modifier
            .width(width)
            .height(width)
            .padding(1.dp)
            .clip(shape)
            .border(if (pinned) 2.dp else 1.dp, border, shape)
            .clickable(onClick = onTap),
    ) {
        WeatherIcon(
            iconUrl = iconPeriod?.iconUrl,
            at = iconPeriod?.start ?: day.date.atStartOfDay(zone).toInstant(),
            latitude = latitude,
            longitude = longitude,
            zone = zone,
            contentDescription = iconPeriod?.shortForecast,
            modifier = Modifier.fillMaxSize().padding(2.dp),
        )
        Text(
            text = day.date.format(DAY_CARD),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 2.dp),
            color = iconLabelColor,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 10.sp,
                maxFontSize = MaterialTheme.typography.titleMedium.fontSize,
            ),
        )
        Text(
            text = temperature,
            modifier = Modifier.align(Alignment.BottomCenter),
            color = iconLabelColor,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private val DAY_CARD = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)

private fun extendedX(
    time: java.time.Instant,
    dayIndex: Map<LocalDate, Int>,
    zone: ZoneId,
): Double? {
    val date = time.atZone(zone).toLocalDate()
    val index = dayIndex[date] ?: return null
    val start = date.atStartOfDay(zone).toInstant()
    val fraction = Duration.between(start, time).seconds / 86_400.0
    return index + fraction.coerceIn(0.0, 1.0)
}

private fun timeAtDay(days: List<CivilDay>, zone: ZoneId, x: Double): java.time.Instant {
    val index = x.toInt().coerceIn(0, days.lastIndex)
    val fraction = (x - index).coerceIn(0.0, 1.0)
    val start = days[index].date.atStartOfDay(zone).toInstant()
    return start.plusMillis((fraction * 86_400_000.0).toLong())
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
                val fraction = Duration.between(start, instant).seconds.coerceIn(0, 86_400) / 86_400.0
                return index + fraction
            }
            add(index.toDouble()..position(sun.sunrise))
            add(position(sun.sunset)..(index + 1).toDouble())
        }
    }
}.filter { it.endInclusive > it.start }
