package com.clutterfree.weather.weather

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
            TemperatureUnit.FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
            TemperatureUnit.CELSIUS -> celsius
        }
        val suffix = if (units.temperature == TemperatureUnit.FAHRENHEIT) "°F" else "°C"
        return "${value.roundToInt()}$suffix"
    }

    fun temperatureCompact(celsius: Double): String = "${temperatureNumber(celsius)}°"

    fun percent(value: Double): String = "${value.roundToInt()}%"

    fun wind(lowMetersPerSecond: Double, highMetersPerSecond: Double? = null): String {
        val low = windValue(lowMetersPerSecond)
        val high = highMetersPerSecond?.let(::windValue)
        val suffix = if (units.wind == WindUnit.MILES_PER_HOUR) "mph" else "km/h"
        return if (high == null || abs(high - low) < 0.5) {
            "${low.roundToInt()} $suffix"
        } else {
            "${low.roundToInt()} to ${high.roundToInt()} $suffix"
        }
    }

    fun precipitation(millimeters: Double): String = when (units.precipitation) {
        PrecipitationUnit.INCHES -> "${formatAmount(millimeters / 25.4)} in"
        PrecipitationUnit.MILLIMETERS -> "${formatAmount(millimeters)} mm"
    }

    private fun temperatureNumber(celsius: Double): Int {
        val value = when (units.temperature) {
            TemperatureUnit.FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
            TemperatureUnit.CELSIUS -> celsius
        }
        return value.roundToInt()
    }

    private fun windValue(metersPerSecond: Double): Double = when (units.wind) {
        WindUnit.MILES_PER_HOUR -> metersPerSecond * METERS_PER_SECOND_TO_MPH
        WindUnit.KILOMETERS_PER_HOUR -> metersPerSecond * METERS_PER_SECOND_TO_KMH
    }

    private fun formatAmount(value: Double): String {
        val rounded = (value * 10.0).roundToInt() / 10.0
        return if (rounded == rounded.toInt().toDouble()) {
            rounded.toInt().toString()
        } else {
            rounded.toString()
        }
    }

    private companion object {
        const val METERS_PER_SECOND_TO_MPH = 2.2369362920544
        const val METERS_PER_SECOND_TO_KMH = 3.6
    }
}
