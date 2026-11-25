package com.clutterfree.weather.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.devoprop.clutterfreeweather.R
import com.clutterfree.weather.ui.DateFormats
import com.clutterfree.weather.ui.conditionLabel
import com.clutterfree.weather.ui.theme.Dimens
import com.clutterfree.weather.weather.AppConfig
import com.clutterfree.weather.weather.CivilDay
import com.clutterfree.weather.weather.DetailItem
import com.clutterfree.weather.weather.DisplayUnits
import com.clutterfree.weather.weather.Measures
import com.clutterfree.weather.weather.Observation
import com.clutterfree.weather.weather.UnitFormatter
import com.clutterfree.weather.weather.compassPoint
import com.clutterfree.weather.weather.narrative
import com.clutterfree.weather.weather.sunTimes
import java.time.ZoneId

@Composable
fun DayDetail(
    day: CivilDay,
    zone: ZoneId,
    latitude: Double,
    longitude: Double,
    units: DisplayUnits,
    details: Set<DetailItem>,
    showDate: Boolean,
    showHighLow: Boolean,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    current: Observation? = null,
) {
    val formatter = UnitFormatter(units)
    val missing = stringResource(R.string.missing)
    val sun = sunTimes(day.date, latitude, longitude, zone)
    val extendedSummary = if (day.extended) {
        val condition = conditionLabel(day.daytime?.iconUrl)
        val amount = day.precipitationMillimeters
            ?.takeIf { it >= Measures.MILLIMETERS_PER_INCH / 20.0 }
            ?.let(formatter::precipitation)
        listOfNotNull(condition, amount).joinToString(", ").ifBlank { null }
    } else {
        null
    }
    val stories = listOfNotNull(
        current?.takeIf { DetailItem.CURRENT_TEXT in details }
            ?.let { currentSummary(it, formatter) }
            ?.let { stringResource(R.string.detail_current, it) },
        day.daytime?.narrative()
            ?.takeIf { !day.extended && DetailItem.DAY_TEXT in details }
            ?.let { stringResource(if (isToday) R.string.detail_today else R.string.detail_day, it) },
        day.nighttime?.narrative()
            ?.takeIf { !day.extended && DetailItem.NIGHT_TEXT in details }
            ?.let { stringResource(if (isToday) R.string.detail_tonight else R.string.detail_night, it) },
    )
    val windPeriod = day.daytime ?: day.nighttime
    val wind = windPeriod?.windSpeedMetersPerSecond?.let { low ->
        listOfNotNull(
            windPeriod.windDirection,
            formatter.wind(low, windPeriod.windSpeedHighMetersPerSecond, stringResource(R.string.range_to)),
        )
            .joinToString(" ")
    }
    val facts = buildList {
        if (DetailItem.SUNRISE in details) {
            add(stringResource(R.string.detail_sunrise, sun?.sunrise?.atZone(zone)?.format(DateFormats.clock()) ?: missing))
        }
        if (DetailItem.SUNSET in details) {
            add(stringResource(R.string.detail_sunset, sun?.sunset?.atZone(zone)?.format(DateFormats.clock()) ?: missing))
        }
        if (showHighLow && DetailItem.HIGH in details) {
            add(stringResource(R.string.detail_high, day.highCelsius?.let(formatter::temperature) ?: missing))
        }
        if (showHighLow && DetailItem.LOW in details) {
            add(stringResource(R.string.detail_low, day.lowCelsius?.let(formatter::temperature) ?: missing))
        }
        if (DetailItem.HUMIDITY in details) {
            add(stringResource(R.string.detail_humidity, day.humidityPercent?.let(formatter::percent) ?: missing))
        }
        if (DetailItem.WIND in details) {
            add(stringResource(R.string.detail_wind, wind ?: missing))
        }
        if (DetailItem.WIND_CHILL_HEAT_INDEX in details) {
            current?.windChillCelsius?.let { add(stringResource(R.string.detail_wind_chill, formatter.temperature(it))) }
            current?.heatIndexCelsius?.let { add(stringResource(R.string.detail_heat_index, formatter.temperature(it))) }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
        if (showDate) {
            Text(
                text = day.date.format(DateFormats.detailDate()),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (day.extended) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.tightGap)) {
                Text(
                    text = stringResource(R.string.detail_extended),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                extendedSummary?.let { summary ->
                    Text(text = summary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        stories.forEach { text ->
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
        if (facts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.tightGap)) {
                facts.chunked(AppConfig.DETAIL_COLUMNS).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
                        row.forEach { fact ->
                            Text(text = fact, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                        repeat(AppConfig.DETAIL_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun currentSummary(observation: Observation, formatter: UnitFormatter): String? {
    val speed = observation.windSpeedMetersPerSecond
    val wind = when {
        speed == null -> null
        speed < Measures.WIND_DISPLAY_GAP -> stringResource(R.string.current_calm)
        else -> stringResource(
            R.string.current_wind,
            listOfNotNull(observation.windDirectionDegrees?.let(::compassPoint), formatter.wind(speed)).joinToString(" "),
        )
    }
    val parts = listOfNotNull(
        conditionLabel(observation.iconUrl) ?: observation.textDescription?.takeIf { it.isNotBlank() },
        observation.temperatureCelsius?.let(formatter::temperature),
        wind,
        observation.relativeHumidityPercent?.let { stringResource(R.string.current_humidity, formatter.percent(it)) },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}
