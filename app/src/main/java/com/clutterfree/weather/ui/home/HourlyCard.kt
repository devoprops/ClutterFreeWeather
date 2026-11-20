package com.clutterfree.weather.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clutterfree.weather.R
import com.clutterfree.weather.weather.DisplayUnits
import com.clutterfree.weather.weather.ForecastPeriod
import com.clutterfree.weather.weather.HomeSettings
import com.clutterfree.weather.weather.UnitFormatter
import com.clutterfree.weather.weather.nightIndexBands
import java.time.Duration
import java.time.ZoneId

@Composable
fun HourlyCard(
    hours: List<ForecastPeriod>,
    zone: ZoneId,
    latitude: Double,
    longitude: Double,
    units: DisplayUnits,
    settings: HomeSettings,
    modifier: Modifier = Modifier,
) {
    if (hours.isEmpty()) return
    val formatter = UnitFormatter(units)
    val missing = stringResource(R.string.missing)
    val scroll = rememberScrollState()
    val visible = minOf(6, hours.size)
    val indexByStart = hours.withIndex().associate { it.value.start to it.index.toDouble() }
    var moved by rememberSaveable { mutableStateOf(false) }
    var savedFraction by rememberSaveable { mutableStateOf(0f) }
    val shape = RoundedCornerShape(8.dp)

    ForecastCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.hourly), style = MaterialTheme.typography.titleMedium)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val pad = 8.dp
                val innerPx = constraints.maxWidth - with(LocalDensity.current) { (pad * 2).roundToPx() }
                val boxPx = innerPx / visible.toFloat()
                val boxDp = with(LocalDensity.current) { boxPx.toDp() }
                val domainStart = if (boxPx < 1f) 0.0 else scroll.value / boxPx.toDouble()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubCard {
                        Column(Modifier.padding(pad).horizontalScroll(scroll)) {
                        Row {
                            hours.forEachIndexed { index, period ->
                                val date = period.start.atZone(zone).toLocalDate()
                                val previous = hours.getOrNull(index - 1)?.start?.atZone(zone)?.toLocalDate()
                                Box(Modifier.width(boxDp).height(24.dp)) {
                                    if (date != previous) {
                                        if (index > 0) {
                                            Box(
                                                Modifier
                                                    .width(2.dp)
                                                    .fillMaxHeight()
                                                    .background(MaterialTheme.colorScheme.outline),
                                            )
                                        }
                                        Text(
                                            text = dayLetter(date.dayOfWeek),
                                            modifier = Modifier.align(Alignment.Center),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            }
                        }
                        Row {
                            hours.forEachIndexed { index, period ->
                                val date = period.start.atZone(zone).toLocalDate()
                                val previous = hours.getOrNull(index - 1)?.start?.atZone(zone)?.toLocalDate()
                                Box(Modifier.width(boxDp).height(boxDp).padding(1.dp)) {
                                    if (index > 0 && date != previous) {
                                        Box(
                                            Modifier
                                                .align(Alignment.CenterStart)
                                                .width(2.dp)
                                                .fillMaxHeight()
                                                .background(MaterialTheme.colorScheme.outline),
                                        )
                                    }
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .clip(shape)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
                                    ) {
                                        WeatherIcon(
                                            iconUrl = period.iconUrl,
                                            at = period.start,
                                            latitude = latitude,
                                            longitude = longitude,
                                            zone = zone,
                                            contentDescription = period.shortForecast,
                                            modifier = Modifier.fillMaxSize().padding(2.dp),
                                        )
                                        Text(
                                            text = period.temperatureCelsius?.let(formatter::temperatureCompact) ?: missing,
                                            modifier = Modifier.align(Alignment.BottomCenter),
                                            color = iconLabelColor,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            }
                        }
                        }
                    }
                    SubCard {
                        WeatherGraph(
                            points = hours.map { it.toGraphPoint() },
                            xOf = { point -> indexByStart[point.time] },
                            domainStart = domainStart,
                            domainEnd = domainStart + visible,
                            nightBands = nightIndexBands(hours, zone, latitude, longitude),
                            dayTints = emptyList(),
                            labels = emptyList(),
                            scrubberFraction = if (moved) savedFraction else 0f,
                            onScrubberFraction = {
                                moved = true
                                savedFraction = it
                            },
                            settings = settings,
                            units = units,
                            timeAt = { index -> timeAtIndex(hours, index) },
                            zone = zone,
                            modifier = Modifier.padding(pad),
                        )
                    }
                }
            }
        }
    }
}

private fun timeAtIndex(hours: List<ForecastPeriod>, index: Double): java.time.Instant {
    val slot = index.toInt().coerceIn(0, hours.lastIndex)
    val fraction = (index - slot).coerceIn(0.0, 1.0)
    val period = hours[slot]
    val span = Duration.between(period.start, period.end).toMillis().coerceAtLeast(1)
    return period.start.plusMillis((span * fraction).toLong())
}
