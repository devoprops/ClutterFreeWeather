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
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.clutterfree.weather.R
import com.clutterfree.weather.ui.DateFormats
import com.clutterfree.weather.ui.theme.Dimens
import com.clutterfree.weather.ui.theme.WeatherColors
import com.clutterfree.weather.weather.AppConfig
import com.clutterfree.weather.weather.CivilDay
import com.clutterfree.weather.weather.DisplayUnits
import com.clutterfree.weather.weather.HomeSettings
import com.clutterfree.weather.weather.Measures
import com.clutterfree.weather.weather.Observation
import com.clutterfree.weather.weather.UnitFormatter
import com.clutterfree.weather.weather.forecastTemperatureCenter
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
    showTitle: Boolean,
    plotHeight: Dp,
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
    val windowEnd = now.plus(Duration.ofHours(AppConfig.TODAY_GRAPH_HOURS.toLong()))
    val points = snapshot.hourly
        .filter { period ->
            !period.start.isBefore(now.minus(Duration.ofHours(AppConfig.TODAY_GRAPH_LEAD_HOURS))) &&
                !period.start.isAfter(windowEnd)
        }
        .map { it.toGraphPoint() }
    val nightBands = twelveHourNightBands(now, windowEnd, latitude, longitude, zone)
    val startDate = now.atZone(zone).toLocalDate()
    val endDate = windowEnd.atZone(zone).toLocalDate()
    val temperatureCenter = forecastTemperatureCenter(
        days.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }
            .flatMap { listOfNotNull(it.highCelsius, it.lowCelsius) },
    )
    var moved by rememberSaveable { mutableStateOf(false) }
    var savedFraction by rememberSaveable { mutableStateOf(0f) }
    val fraction = if (moved) savedFraction else 0f

    ForecastCard(modifier) {
        Column(Modifier.padding(Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
            if (showTitle) {
                Text(text = stringResource(R.string.today), style = MaterialTheme.typography.titleMedium)
            }
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                SubCard(Modifier.weight(AppConfig.TODAY_ICON_WEIGHT).fillMaxHeight()) {
                    Box(Modifier.fillMaxSize().padding(Dimens.iconInset)) {
                        Text(
                            text = date.format(DateFormats.dayCard),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(horizontal = Dimens.iconInset),
                            color = WeatherColors.iconLabel,
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = Dimens.minAutoSize,
                                maxFontSize = MaterialTheme.typography.titleSmall.fontSize,
                            ),
                        )
                        WeatherIcon(
                            iconUrl = iconUrl,
                            at = iconAt,
                            latitude = latitude,
                            longitude = longitude,
                            zone = zone,
                            contentDescription = description,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(top = Dimens.dateLane)
                                .aspectRatio(1f),
                        )
                        Row(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = Dimens.tightGap, vertical = Dimens.iconInset),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = temperature?.let(formatter::temperatureCompact) ?: missing,
                                modifier = Modifier.weight(AppConfig.TODAY_TEMPERATURE_WEIGHT),
                                color = WeatherColors.iconLabel,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                softWrap = false,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = Dimens.minAutoSize,
                                    maxFontSize = MaterialTheme.typography.titleLarge.fontSize,
                                ),
                            )
                            Text(
                                text = stringResource(R.string.high_low, high, low),
                                modifier = Modifier
                                    .weight(AppConfig.TODAY_HIGH_LOW_WEIGHT)
                                    .padding(start = Dimens.tightGap, bottom = Dimens.iconInset),
                                color = WeatherColors.iconLabel,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                softWrap = false,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = Dimens.minAutoSize,
                                    maxFontSize = MaterialTheme.typography.titleSmall.fontSize,
                                ),
                            )
                        }
                    }
                }
                if (settings.todayGraph.visible) {
                    SubCard(Modifier.weight(AppConfig.TODAY_GRAPH_WEIGHT)) {
                        WeatherGraph(
                            points = points,
                            xOf = { point -> hoursFrom(now, point.time) },
                            domainStart = 0.0,
                            domainEnd = AppConfig.TODAY_GRAPH_HOURS.toDouble(),
                            nightBands = nightBands,
                            dayTints = emptyList(),
                            labels = axisLabels(now, zone),
                            scrubberFraction = fraction,
                            onScrubberFraction = {
                                moved = true
                                savedFraction = it
                            },
                            graph = settings.todayGraph,
                            units = units,
                            temperatureCenterCelsius = temperatureCenter,
                            timeAt = { hour -> now.plusMillis((hour * Measures.MILLIS_PER_HOUR).toLong()) },
                            zone = zone,
                            plotHeight = plotHeight,
                            modifier = Modifier.padding(Dimens.graphPadding),
                        )
                    }
                } else if (day != null) {
                    SubCard(Modifier.weight(AppConfig.TODAY_GRAPH_WEIGHT)) {
                        TodayDetail(day, zone, latitude, longitude, units, settings, snapshot.observation, day.date == date)
                    }
                }
            }
            if (day != null && settings.todayGraph.visible) {
                SubCard {
                    TodayDetail(day, zone, latitude, longitude, units, settings, snapshot.observation, day.date == date)
                }
            }
        }
    }
}

@Composable
private fun TodayDetail(
    day: CivilDay,
    zone: ZoneId,
    latitude: Double,
    longitude: Double,
    units: DisplayUnits,
    settings: HomeSettings,
    observation: Observation?,
    isToday: Boolean,
) {
    DayDetail(
        day = day,
        zone = zone,
        latitude = latitude,
        longitude = longitude,
        units = units,
        details = settings.details,
        showDate = false,
        showHighLow = false,
        isToday = isToday,
        current = observation,
        modifier = Modifier.padding(Dimens.cardPadding),
    )
}

private fun axisLabels(now: Instant, zone: ZoneId): List<AxisLabel> {
    val labels = mutableListOf<AxisLabel>()
    var offset = 0
    while (offset <= AppConfig.TODAY_GRAPH_HOURS) {
        val hour = now.atZone(zone).plusHours(offset.toLong()).hour
        labels += AxisLabel(offset.toDouble(), compactHour(hour))
        offset += AppConfig.TODAY_AXIS_STEP_HOURS
    }
    return labels
}

private fun hoursFrom(start: Instant, time: Instant): Double =
    Duration.between(start, time).toMillis() / Measures.MILLIS_PER_HOUR

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
