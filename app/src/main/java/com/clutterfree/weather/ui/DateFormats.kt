package com.clutterfree.weather.ui

import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormats {
    val dayCard: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)
    val weekday: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.US)
    val detailDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)
    val hour: DateTimeFormatter = DateTimeFormatter.ofPattern("h a", Locale.US)
    val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
}
