package com.clutterfree.weather.weather

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

data class ParsedIcon(
    val night: Boolean,
    val condition: String,
    val windy: Boolean,
)

data class SunTimes(
    val sunrise: Instant,
    val sunset: Instant,
)

enum class MoonPhase {
    NEW,
    WAXING_CRESCENT,
    FIRST_QUARTER,
    WAXING_GIBBOUS,
    FULL,
    WANING_GIBBOUS,
    LAST_QUARTER,
    WANING_CRESCENT,
}

data class CivilDay(
    val date: LocalDate,
    val highCelsius: Double?,
    val lowCelsius: Double?,
    val humidityPercent: Double?,
    val daytime: ForecastPeriod?,
    val nighttime: ForecastPeriod?,
)

fun parseIconUrl(url: String?): ParsedIcon {
    if (url.isNullOrBlank()) return ParsedIcon(night = false, condition = "skc", windy = false)
    val path = url.substringBefore("?").substringAfter("://").substringAfter("/")
    val parts = path.split("/").filter { it.isNotEmpty() }
    val icons = parts.indexOf("icons")
    val body = if (icons >= 0) parts.drop(icons + 1) else parts
    val timeIndex = body.indexOfFirst { it == "day" || it == "night" }
    val night = timeIndex >= 0 && body[timeIndex] == "night"
    val raw = if (timeIndex >= 0 && timeIndex + 1 < body.size) body[timeIndex + 1] else "skc"
    val code = raw.substringBefore(",")
    return ParsedIcon(
        night = night,
        condition = code.removePrefix("wind_"),
        windy = code == "blizzard" || code.startsWith("wind_"),
    )
}

fun iconCandidates(icon: ParsedIcon): List<String> {
    val resolved = resolvedCondition(icon.condition)
    val conditions = listOf(icon.condition, resolved).distinct()
    val times = if (icon.night) listOf("night", "day") else listOf("day")
    return buildList {
        for (time in times) {
            for (condition in conditions) {
                add("ic_wx_${time}_$condition")
            }
        }
        add("ic_wx_day_skc")
    }.distinct()
}

fun resolvedCondition(condition: String): String {
    CONDITION_ALIASES[condition]?.let { return it }
    if (condition == "rain_showers") return condition
    val head = condition.substringBefore("_")
    if (head != condition && head in PRECIP_HEADS) return head
    return condition
}

fun sunTimes(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): SunTimes? {
    val rise = sunEventHours(date, latitude, longitude, rising = true) ?: return null
    val set = sunEventHours(date, latitude, longitude, rising = false) ?: return null
    if (rise.isNaN() || set.isNaN()) return null
    val sunrise = utToInstant(date, rise)
    var sunset = utToInstant(date, set)
    if (!sunrise.isBefore(sunset)) sunset = sunset.plus(Duration.ofDays(1))
    if (!sunrise.isBefore(sunset)) return null
    return SunTimes(sunrise, sunset)
}

fun iconDarken(time: Instant, sun: SunTimes): Boolean {
    val morning = !time.isBefore(sun.sunrise.minus(Duration.ofHours(1))) && time.isBefore(sun.sunrise)
    val evening = time.isAfter(sun.sunset) && !time.isAfter(sun.sunset.plus(Duration.ofHours(1)))
    return morning || evening
}

fun moonPhase(time: Instant): MoonPhase {
    val days = (time.epochSecond - NEW_MOON_EPOCH.epochSecond) / 86_400.0
    val age = positiveModulo(days, SYNODIC_MONTH_DAYS)
    val index = (age / SYNODIC_MONTH_DAYS * 8.0).toInt().coerceIn(0, 7)
    return MoonPhase.entries[index]
}

fun civilDays(
    dayNight: List<ForecastPeriod>,
    hourly: List<ForecastPeriod>,
    zone: ZoneId,
): List<CivilDay> {
    val dates = dayNight.map { it.start.atZone(zone).toLocalDate() }.distinct()
    return dates.map { date ->
        val periods = dayNight.filter { it.start.atZone(zone).toLocalDate() == date }
        val daytime = periods.firstOrNull { it.isDaytime }
        val nighttime = periods.firstOrNull { !it.isDaytime }
        CivilDay(
            date = date,
            highCelsius = daytime?.temperatureCelsius,
            lowCelsius = nighttime?.temperatureCelsius,
            humidityPercent = humidityNearNoon(hourly, date, zone),
            daytime = daytime,
            nighttime = nighttime,
        )
    }
}

fun CivilDay.verboseForecast(): String = listOfNotNull(daytime, nighttime).mapNotNull { period ->
    period.detailedForecast?.takeIf { it.isNotBlank() } ?: period.shortForecast?.takeIf { it.isNotBlank() }
}.joinToString("\n\n")

fun rankedAlerts(alerts: List<WeatherAlert>): List<WeatherAlert> =
    alerts.sortedBy { alert ->
        val rank = SEVERITY_RANK.indexOf(alert.severity?.lowercase())
        if (rank < 0) SEVERITY_RANK.size else rank
    }

fun interpolate(samples: List<Pair<Double, Double>>, at: Double): Double? {
    if (samples.isEmpty()) return null
    val sorted = samples.sortedBy { it.first }
    if (at <= sorted.first().first) return sorted.first().second
    if (at >= sorted.last().first) return sorted.last().second
    val upperIndex = sorted.indexOfFirst { it.first >= at }
    val upper = sorted[upperIndex]
    val lower = sorted[upperIndex - 1]
    val span = upper.first - lower.first
    if (span == 0.0) return upper.second
    val portion = (at - lower.first) / span
    return lower.second + portion * (upper.second - lower.second)
}

fun periodIndexAt(periods: List<ForecastPeriod>, time: Instant): Double {
    if (periods.isEmpty()) return 0.0
    if (!time.isAfter(periods.first().start)) return 0.0
    val last = periods.last()
    if (!time.isBefore(last.end)) return periods.size.toDouble()
    val index = periods.indexOfLast { !it.start.isAfter(time) }.coerceAtLeast(0)
    val period = periods[index]
    val span = Duration.between(period.start, period.end).toMillis().coerceAtLeast(1)
    val into = Duration.between(period.start, time).toMillis().coerceIn(0, span)
    return index + into.toDouble() / span
}

fun nightIndexBands(
    periods: List<ForecastPeriod>,
    zone: ZoneId,
    latitude: Double,
    longitude: Double,
): List<ClosedFloatingPointRange<Double>> {
    if (periods.isEmpty()) return emptyList()
    val startDate = periods.first().start.atZone(zone).toLocalDate()
    val endDate = periods.last().end.atZone(zone).toLocalDate()
    val ranges = mutableListOf<ClosedFloatingPointRange<Double>>()
    var date = startDate
    while (!date.isAfter(endDate)) {
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val sun = sunTimes(date, latitude, longitude, zone)
        if (sun == null) {
            addBand(ranges, periodIndexAt(periods, dayStart), periodIndexAt(periods, dayEnd))
        } else {
            addBand(ranges, periodIndexAt(periods, dayStart), periodIndexAt(periods, sun.sunrise))
            addBand(ranges, periodIndexAt(periods, sun.sunset), periodIndexAt(periods, dayEnd))
        }
        date = date.plusDays(1)
    }
    return ranges
}

private fun humidityNearNoon(
    hourly: List<ForecastPeriod>,
    date: LocalDate,
    zone: ZoneId,
): Double? {
    val noon = date.atTime(12, 0).atZone(zone).toInstant()
    return hourly
        .asSequence()
        .filter { it.relativeHumidityPercent != null && it.start.atZone(zone).toLocalDate() == date }
        .minByOrNull { kotlin.math.abs(Duration.between(it.start, noon).seconds) }
        ?.relativeHumidityPercent
}

private fun addBand(
    ranges: MutableList<ClosedFloatingPointRange<Double>>,
    start: Double,
    end: Double,
) {
    if (end > start) ranges += start..end
}

private fun sunEventHours(
    date: LocalDate,
    latitude: Double,
    longitude: Double,
    rising: Boolean,
): Double? {
    val zenith = 90.8333
    val month = date.monthValue
    val year = date.year
    val day = date.dayOfMonth
    val n1 = floor(275.0 * month / 9.0)
    val n2 = floor((month + 9) / 12.0)
    val n3 = 1.0 + floor((year - 4.0 * floor(year / 4.0) + 2.0) / 3.0)
    val dayOfYear = n1 - (n2 * n3) + day - 30.0
    val longitudeHour = longitude / 15.0
    val approximate = dayOfYear + ((if (rising) 6.0 else 18.0) - longitudeHour) / 24.0
    val anomaly = (0.9856 * approximate) - 3.289
    val longitudeSun = wrap360(anomaly + (1.916 * sind(anomaly)) + (0.020 * sind(2.0 * anomaly)) + 282.634)
    var rightAscension = atanDegrees(0.91764 * tand(longitudeSun))
    val longitudeQuadrant = floor(longitudeSun / 90.0) * 90.0
    val ascensionQuadrant = floor(rightAscension / 90.0) * 90.0
    rightAscension = (rightAscension + (longitudeQuadrant - ascensionQuadrant)) / 15.0
    val sinDeclination = 0.39782 * sind(longitudeSun)
    val cosDeclination = cosd(asinDegrees(sinDeclination))
    val cosHour = (cosd(zenith) - (sinDeclination * sind(latitude))) / (cosDeclination * cosd(latitude))
    if (cosHour > 1.0 || cosHour < -1.0 || cosHour.isNaN()) return null
    val hourAngle = (if (rising) 360.0 - acosDegrees(cosHour) else acosDegrees(cosHour)) / 15.0
    val localMean = hourAngle + rightAscension - (0.06571 * approximate) - 6.622
    return wrap24(localMean - longitudeHour)
}

private fun utToInstant(date: LocalDate, utHours: Double): Instant {
    val millis = (utHours * 3_600_000.0).roundToLong()
    return date.atStartOfDay().toInstant(ZoneOffset.UTC).plusMillis(millis)
}

private fun sind(degrees: Double): Double = sin(degrees * PI / 180.0)

private fun cosd(degrees: Double): Double = cos(degrees * PI / 180.0)

private fun tand(degrees: Double): Double = tan(degrees * PI / 180.0)

private fun asinDegrees(value: Double): Double = asin(value) * 180.0 / PI

private fun acosDegrees(value: Double): Double = acos(value) * 180.0 / PI

private fun atanDegrees(value: Double): Double = atan(value) * 180.0 / PI

private fun wrap360(value: Double): Double {
    val wrapped = value % 360.0
    return if (wrapped < 0) wrapped + 360.0 else wrapped
}

private fun wrap24(value: Double): Double {
    val wrapped = value % 24.0
    return if (wrapped < 0) wrapped + 24.0 else wrapped
}

private fun positiveModulo(value: Double, divisor: Double): Double {
    val wrapped = value % divisor
    return if (wrapped < 0) wrapped + divisor else wrapped
}

private val CONDITION_ALIASES = mapOf(
    "rain_showers_hi" to "rain_showers",
    "tsra_sct" to "tsra",
    "tsra_hi" to "tsra",
    "dust" to "fog",
    "smoke" to "fog",
    "haze" to "fog",
    "hot" to "skc",
    "cold" to "skc",
    "blizzard" to "snow",
)

private val PRECIP_HEADS = setOf("rain", "snow", "sleet", "fzra")

private val SEVERITY_RANK = listOf("extreme", "severe", "moderate", "minor")

private val NEW_MOON_EPOCH: Instant = Instant.parse("2000-01-06T18:14:00Z")

private const val SYNODIC_MONTH_DAYS = 29.530588853
