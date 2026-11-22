package com.clutterfree.weather.weather

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class DisplayUnits(
    val temperature: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    val wind: WindUnit = WindUnit.MILES_PER_HOUR,
    val precipitation: PrecipitationUnit = PrecipitationUnit.INCHES,
)

enum class TemperatureUnit {
    FAHRENHEIT,
    CELSIUS,
}

enum class WindUnit {
    MILES_PER_HOUR,
    KILOMETERS_PER_HOUR,
}

enum class PrecipitationUnit {
    INCHES,
    MILLIMETERS,
}

class UnitFormatter(private val units: DisplayUnits) {
    fun temperature(celsius: Double): String {
        val value = when (units.temperature) {
            TemperatureUnit.FAHRENHEIT -> celsius * Measures.FAHRENHEIT_PER_CELSIUS + Measures.FAHRENHEIT_OFFSET
            TemperatureUnit.CELSIUS -> celsius
        }
        val suffix = if (units.temperature == TemperatureUnit.FAHRENHEIT) "°F" else "°C"
        return "${value.roundToInt()}$suffix"
    }

    fun temperatureCompact(celsius: Double): String = "${temperatureNumber(celsius)}°"

    fun percent(value: Double): String = "${value.roundToInt()}%"

    fun wind(lowMetersPerSecond: Double, highMetersPerSecond: Double? = null, rangeWord: String = "to"): String {
        val low = windValue(lowMetersPerSecond)
        val high = highMetersPerSecond?.let(::windValue)
        val suffix = if (units.wind == WindUnit.MILES_PER_HOUR) "mph" else "km/h"
        return if (high == null || abs(high - low) < Measures.WIND_DISPLAY_GAP) {
            "${low.roundToInt()} $suffix"
        } else {
            "${low.roundToInt()} $rangeWord ${high.roundToInt()} $suffix"
        }
    }

    fun distance(miles: Double): String = when (units.wind) {
        WindUnit.MILES_PER_HOUR -> "${miles.roundToInt()} mi"
        WindUnit.KILOMETERS_PER_HOUR -> "${(miles * Measures.METERS_PER_MILE / Measures.METERS_PER_KILOMETER).roundToInt()} km"
    }

    fun precipitation(millimeters: Double): String = when (units.precipitation) {
        PrecipitationUnit.INCHES -> "${formatAmount(millimeters / Measures.MILLIMETERS_PER_INCH)} in"
        PrecipitationUnit.MILLIMETERS -> "${formatAmount(millimeters)} mm"
    }

    private fun temperatureNumber(celsius: Double): Int {
        val value = when (units.temperature) {
            TemperatureUnit.FAHRENHEIT -> celsius * Measures.FAHRENHEIT_PER_CELSIUS + Measures.FAHRENHEIT_OFFSET
            TemperatureUnit.CELSIUS -> celsius
        }
        return value.roundToInt()
    }

    private fun windValue(metersPerSecond: Double): Double = when (units.wind) {
        WindUnit.MILES_PER_HOUR -> metersPerSecond * Measures.MPH_PER_METER_PER_SECOND
        WindUnit.KILOMETERS_PER_HOUR -> metersPerSecond * Measures.KMH_PER_METER_PER_SECOND
    }

    private fun formatAmount(value: Double): String {
        val rounded = (value * 10.0).roundToInt() / 10.0
        return if (rounded == rounded.toInt().toDouble()) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", rounded)
        }
    }

}
