package com.clutterfree.weather.weather

data class ValueRange(val min: Double, val max: Double)

fun percentRange(samples: List<Double>): ValueRange {
    val peak = samples.maxOrNull() ?: return ValueRange(0.0, AppConfig.PERCENT_SCALE)
    return ValueRange(0.0, maxOf(AppConfig.PERCENT_SCALE, peak))
}

fun temperatureRange(centerCelsius: Double, samples: List<Double>): ValueRange {
    var min = centerCelsius - TEMPERATURE_HALF_SPAN_CELSIUS
    var max = centerCelsius + TEMPERATURE_HALF_SPAN_CELSIUS
    val low = samples.minOrNull()
    val high = samples.maxOrNull()
    if (low != null && low < min) min = low - TEMPERATURE_PAD_CELSIUS
    if (high != null && high > max) max = high + TEMPERATURE_PAD_CELSIUS
    if (max <= min) max = min + TEMPERATURE_HALF_SPAN_CELSIUS
    return ValueRange(min, max)
}

fun windRange(samplesMetersPerSecond: List<Double>): ValueRange {
    val peak = samplesMetersPerSecond.maxOrNull() ?: return ValueRange(0.0, WIND_NORMAL_MAX_MPS)
    val max = if (peak > WIND_NORMAL_MAX_MPS) peak + WIND_PAD_MPS else WIND_NORMAL_MAX_MPS
    return ValueRange(0.0, max)
}

fun forecastTemperatureCenter(highsAndLowsCelsius: List<Double>): Double? =
    highsAndLowsCelsius.takeIf { it.isNotEmpty() }?.average()

private const val TEMPERATURE_HALF_SPAN_CELSIUS =
    AppConfig.TEMPERATURE_WINDOW_FAHRENHEIT / 2.0 / Measures.FAHRENHEIT_PER_CELSIUS
private const val TEMPERATURE_PAD_CELSIUS =
    AppConfig.TEMPERATURE_PAD_FAHRENHEIT / Measures.FAHRENHEIT_PER_CELSIUS
private const val WIND_NORMAL_MAX_MPS = AppConfig.WIND_SCALE_MPH / Measures.MPH_PER_METER_PER_SECOND
private const val WIND_PAD_MPS = AppConfig.WIND_PAD_MPH / Measures.MPH_PER_METER_PER_SECOND
