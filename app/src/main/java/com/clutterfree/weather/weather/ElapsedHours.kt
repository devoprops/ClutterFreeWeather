package com.clutterfree.weather.weather

import java.time.Instant
import java.time.ZoneId

fun mergeElapsedToday(
    previous: List<ForecastPeriod>,
    fresh: List<ForecastPeriod>,
    zone: ZoneId,
    now: Instant,
): List<ForecastPeriod> {
    val today = now.atZone(zone).toLocalDate()
    val freshStarts = fresh.mapTo(HashSet()) { it.start }
    val kept = previous.filter { period ->
        period.start.atZone(zone).toLocalDate() == today &&
            period.start.isBefore(now) &&
            period.start !in freshStarts
    }
    return (kept + fresh).sortedBy { it.start }
}
