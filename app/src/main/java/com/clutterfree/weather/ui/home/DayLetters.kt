package com.clutterfree.weather.ui.home

fun compactHour(hour: Int): String {
    val clock = when {
        hour == 0 || hour == 24 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val suffix = if (hour < 12) "a" else "p"
    return "$clock$suffix"
}
