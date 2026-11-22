package com.clutterfree.weather.ui

import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormats {
    fun dayCard(): DateTimeFormatter = pattern("EEE MMM d")

    fun weekday(): DateTimeFormatter = pattern("EEEE")

    fun detailDate(): DateTimeFormatter = pattern("EEEE, MMMM d")

    fun hour(): DateTimeFormatter = pattern("h a")

    fun dayHour(): DateTimeFormatter = pattern("EEE h a")

    fun clock(): DateTimeFormatter = pattern("h:mm a")

    private fun pattern(pattern: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
}
