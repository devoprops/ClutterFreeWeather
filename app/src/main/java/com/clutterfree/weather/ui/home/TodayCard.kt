package com.clutterfree.weather.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clutterfree.weather.R
import com.clutterfree.weather.weather.DisplayUnits
import com.clutterfree.weather.weather.HomeSettings
import com.clutterfree.weather.weather.UnitFormatter
import com.clutterfree.weather.weather.WeatherSnapshot
import com.clutterfree.weather.weather.civilDays
import com.clutterfree.weather.weather.sunTimes
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Composable
fun TodayCard(
    snapshot: WeatherSnapshot,
    zone: ZoneId,
    latitude: Double,
    longitude: Double,
    now: Instant,
    units: DisplayUnits,
    settings: HomeSettings,
    modifier: Modifier = Modifier,
) {
    val formatter = UnitFormatter(units)
    val missing = stringResource(R.string.missing)
    val date = now.atZone(zone).toLocalDate()
    val days = civilDays(snapshot.dayNight, snapshot.hourly, zone)
    val day = days.firstOrNull { it.date == date } ?: days.firstOrNull()
    val current = snapshot.hourly.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
    val temperature = snapshot.observation?.temperatureCelsius ?: current?.temperatureCelsius
    val iconUrl = snapshot.observation?.iconUrl ?: current?.iconUrl
    val iconAt = snapshot.observation?.observedAt ?: now
    val description = snapshot.observation?.textDescription ?: current?.shortForecast
    val high = day?.highCelsius?.let(formatter::temperatureCompact) ?: missing
    val low = day?.lowCelsius?.let(formatter::temperatureCompact) ?: missing
    val windowEnd = now.plus(Duration.ofHours(12))
    val points = snapshot.hourly
        .filter { period ->
            !period.start.isBefore(now.minus(Duration.ofHours(1))) && !period.start.isAfter(windowEnd)
        }
        .map { it.toGraphPoint() }
    val nightBands = twelveHourNightBands(now, windowEnd, latitude, longitude, zone)
    var moved by rememberSaveable { mutableStateOf(false) }
    var savedFraction by rememberSaveable { mutableStateOf(0f) }
    val fraction = if (moved) savedFraction else 0f

    ForecastCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.today), style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubCard(Modifier.weight(0.36f).fillMaxHeight()) {
                    Box(Modifier.fillMaxSize().padding(2.dp)) {
                        WeatherIcon(
                            iconUrl = iconUrl,
                            at = iconAt,
                            latitude = latitude,
                            longitude = longitude,
                            zone = zone,
                            contentDescription = description,
                            modifier = Modifier.align(Alignment.Center).aspectRatio(1f),
                        )
                        Row(
                            Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = temperature?.let(formatter::temperatureCompact) ?: missing,
                                color = iconLabelColor,
                                style = MaterialTheme.typography.headlineLarge,
                            )
                            Text(
                                text = stringResource(R.string.high_low, high, low),
                                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                                color = iconLabelColor,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                SubCard(Modifier.weight(0.64f)) {
                    WeatherGraph(
                        points = points,
                        xOf = { point -> hoursFrom(now, point.time) },
                        domainStart = 0.0,
                        domainEnd = 12.0,
                        nightBands = nightBands,
                        dayTints = emptyList(),
                        labels = listOf(0, 3, 6, 9, 12).map { offset ->
                            val hour = now.atZone(zone).plusHours(offset.toLong()).hour
                            AxisLabel(offset.toDouble(), compactHour(hour))
                        },
                        scrubberFraction = fraction,
                        onScrubberFraction = {
                            moved = true
                            savedFraction = it
                        },
                        settings = settings,
                        units = units,
                        timeAt = { hour -> now.plusMillis((hour * 3_600_000.0).toLong()) },
                        zone = zone,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            if (day != null) {
                SubCard {
                    DayDetail(
                        day = day,
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

private fun hoursFrom(start: Instant, time: Instant): Double =
    Duration.between(start, time).toMillis() / 3_600_000.0

private fun twelveHourNightBands(
    start: Instant,
    end: Instant,
    latitude: Double,
    longitude: Double,
    zone: ZoneId,
): List<ClosedFloatingPointRange<Double>> {
    val ranges = mutableListOf<ClosedFloatingPointRange<Double>>()
    var date = start.atZone(zone).toLocalDate()
    val lastDate = end.atZone(zone).toLocalDate()
    while (!date.isAfter(lastDate)) {
        val sun = sunTimes(date, latitude, longitude, zone)
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        if (sun == null) {
            clipBand(ranges, start, end, dayStart, dayEnd)
        } else {
            clipBand(ranges, start, end, dayStart, sun.sunrise)
            clipBand(ranges, start, end, sun.sunset, dayEnd)
        }
        date = date.plusDays(1)
    }
    return ranges
}

private fun clipBand(
    ranges: MutableList<ClosedFloatingPointRange<Double>>,
    windowStart: Instant,
    windowEnd: Instant,
    bandStart: Instant,
    bandEnd: Instant,
) {
    val start = if (bandStart.isAfter(windowStart)) bandStart else windowStart
    val end = if (bandEnd.isBefore(windowEnd)) bandEnd else windowEnd
    if (!end.isAfter(start)) return
    val from = hoursFrom(windowStart, start)
    val to = hoursFrom(windowStart, end)
    if (to > from) ranges.add(from..to)
}
