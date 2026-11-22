package com.clutterfree.weather.weather

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

fun milesBetween(
    latitude: Double,
    longitude: Double,
    otherLatitude: Double,
    otherLongitude: Double,
): Double {
    val radiusMiles = Measures.EARTH_CIRCUMFERENCE_METERS / (2 * PI) / Measures.METERS_PER_MILE
    val startLatitude = Math.toRadians(latitude)
    val endLatitude = Math.toRadians(otherLatitude)
    val latitudeChange = endLatitude - startLatitude
    val longitudeChange = Math.toRadians(otherLongitude - longitude)
    val haversine = sin(latitudeChange / 2) * sin(latitudeChange / 2) +
        cos(startLatitude) * cos(endLatitude) * sin(longitudeChange / 2) * sin(longitudeChange / 2)
    return 2 * radiusMiles * atan2(sqrt(haversine), sqrt(1 - haversine))
}
