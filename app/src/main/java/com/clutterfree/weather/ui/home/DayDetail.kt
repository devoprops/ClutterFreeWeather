package com.clutterfree.weather.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clutterfree.weather.R
import com.clutterfree.weather.weather.CivilDay
import com.clutterfree.weather.weather.DisplayUnits
import com.clutterfree.weather.weather.UnitFormatter
import com.clutterfree.weather.weather.sunTimes
import com.clutterfree.weather.weather.verboseForecast
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DayDetail(
    day: CivilDay,
    zone: ZoneId,
    latitude: Double,
    longitude: Double,
    units: DisplayUnits,
    modifier: Modifier = Modifier,
) {
    val formatter = UnitFormatter(units)
    val missing = stringResource(R.string.missing)
    val sun = sunTimes(day.date, latitude, longitude, zone)
    val story = day.verboseForecast()
    Column(modifier) {
        Text(
            text = day.date.format(DATE),
            style = MaterialTheme.typography.titleMedium,
        )
        if (story.isNotBlank()) {
            Text(
                text = story,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(stringResource(R.string.detail_sunrise, sun?.sunrise?.atZone(zone)?.format(CLOCK) ?: missing))
        Text(stringResource(R.string.detail_sunset, sun?.sunset?.atZone(zone)?.format(CLOCK) ?: missing))
        Text(stringResource(R.string.detail_high, day.highCelsius?.let(formatter::temperature) ?: missing))
        Text(stringResource(R.string.detail_low, day.lowCelsius?.let(formatter::temperature) ?: missing))
        Text(
            stringResource(
                R.string.detail_humidity,
                day.humidityPercent?.let(formatter::percent) ?: missing,
            ),
        )
    }
}

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
