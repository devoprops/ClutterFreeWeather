package com.clutterfree.weather.radar

import com.clutterfree.weather.weather.AppConfig
import com.clutterfree.weather.weather.Measures
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.sin
import kotlin.math.sinh

/** Web Mercator in normalized units: x and y run 0 to 1 across the world, y downward. */
object WebMercator {
    fun x(longitude: Double): Double = (longitude + 180.0) / Measures.DEGREES_PER_CIRCLE

    fun y(latitude: Double): Double {
        val s = sin(Math.toRadians(latitude))
        return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
    }

    fun longitude(x: Double): Double = x * Measures.DEGREES_PER_CIRCLE - 180.0

    fun latitude(y: Double): Double = Math.toDegrees(atan(sinh(PI * (1 - 2 * y))))
}

/** Map zoom that fits [miles] on each side of the center across [widthPx]. */
fun zoomForMiles(miles: Double, latitude: Double, widthPx: Float): Double {
    val metersPerPixel = 2 * miles * Measures.METERS_PER_MILE / widthPx
    val zoom = log2(Measures.EARTH_CIRCUMFERENCE_METERS * cos(Math.toRadians(latitude)) / (AppConfig.RADAR_TILE_PX * metersPerPixel))
    return zoom.coerceIn(AppConfig.RADAR_MIN_ZOOM, AppConfig.RADAR_MAX_ZOOM)
}

/** Tile zoom used for radar images at map [zoom]. Radar is coarser than the map, so tiles are drawn enlarged. */
fun radarTileZoom(zoom: Double): Int =
    (zoom.toInt() - AppConfig.RADAR_TILE_ZOOM_DROP).coerceIn(AppConfig.RADAR_MIN_TILE_ZOOM, AppConfig.RADAR_MAX_TILE_ZOOM)

/** Minutes before the latest radar image for loop [frame], where the last frame is the latest. */
fun frameOffsetMinutes(frame: Int): Long = (AppConfig.RADAR_FRAMES - 1 - frame) * AppConfig.RADAR_FRAME_STEP_MINUTES

data class TileKey(val frame: Int, val z: Int, val x: Int, val y: Int)

fun radarTileUrl(key: TileKey): String {
    val offset = frameOffsetMinutes(key.frame)
    val layer = if (offset == 0L) RADAR_LAYER else String.format(Locale.US, "%s-m%02dm", RADAR_LAYER, offset)
    return "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/$layer/${key.z}/${key.x}/${key.y}.png"
}

/** Tiles at zoom [z] that cover the normalized box from ([minX], [minY]) to ([maxX], [maxY]). */
fun tilesCovering(z: Int, minX: Double, minY: Double, maxX: Double, maxY: Double): List<Pair<Int, Int>> {
    val count = 1 shl z
    fun index(value: Double) = (value * count).toInt().coerceIn(0, count - 1)
    return buildList {
        for (y in index(minY)..index(maxY)) {
            for (x in index(minX)..index(maxX)) add(x to y)
        }
    }
}

private const val RADAR_LAYER = "nexrad-n0q-900913"
