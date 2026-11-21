package com.clutterfree.weather.radar

import com.clutterfree.weather.weather.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapMathTest {
    @Test
    fun mercatorRoundTripsHuntsville() {
        val x = WebMercator.x(-86.675)
        val y = WebMercator.y(34.689)
        assertEquals(-86.675, WebMercator.longitude(x), 1e-9)
        assertEquals(34.689, WebMercator.latitude(y), 1e-9)
        assertEquals(0.5, WebMercator.y(0.0), 1e-12)
    }

    @Test
    fun fiftyMilesOnAPhoneIsNearZoomNine() {
        val zoom = zoomForMiles(50.0, 34.7, 1080f)
        assertTrue("zoom was $zoom", zoom in 9.0..10.5)
        assertEquals(8, radarTileZoom(zoom))
    }

    @Test
    fun loopFramesStepBackFromTheLatest() {
        assertEquals(0L, frameOffsetMinutes(AppConfig.RADAR_FRAMES - 1))
        assertEquals((AppConfig.RADAR_FRAMES - 1) * AppConfig.RADAR_FRAME_STEP_MINUTES, frameOffsetMinutes(0))
        assertEquals(
            "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/nexrad-n0q-900913/6/16/25.png",
            radarTileUrl(TileKey(AppConfig.RADAR_FRAMES - 1, 6, 16, 25)),
        )
        assertEquals(
            "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/nexrad-n0q-900913-m50m/6/16/25.png",
            radarTileUrl(TileKey(0, 6, 16, 25)),
        )
    }

    @Test
    fun tilesCoverTheBoxAndStayOnTheWorld() {
        assertEquals(listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), tilesCovering(1, 0.1, 0.1, 0.9, 0.9))
        assertEquals(listOf(0 to 0), tilesCovering(1, -0.5, -0.5, 0.2, 0.2))
    }
}
