package com.clutterfree.weather.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class GraphScaleTest {
    @Test
    fun percentUsesTheFullScale() {
        val range = percentRange(listOf(10.0, 40.0))

        assertEquals(0.0, range.min, 0.01)
        assertEquals(100.0, range.max, 0.01)
    }

    @Test
    fun temperatureWindowIsFiftyFahrenheitDegreesAroundTheHighLowAverage() {
        val center = 20.0
        val range = temperatureRange(center, listOf(18.0, 22.0))

        assertEquals(50.0, (range.max - range.min) * 9.0 / 5.0, 0.01)
        assertEquals(center, (range.min + range.max) / 2.0, 0.01)
    }

    @Test
    fun temperatureWindowOpensWhenAValueFallsOutside() {
        val range = temperatureRange(20.0, listOf(20.0, 40.0))

        assertEquals(true, range.max > 40.0)
        assertEquals(20.0 - 25.0 * 5.0 / 9.0, range.min, 0.01)
    }

    @Test
    fun windUsesZeroToFortyMphUntilTheForecastIsStronger() {
        val calm = windRange(listOf(2.0))
        val strong = windRange(listOf(30.0))

        assertEquals(0.0, calm.min, 0.01)
        assertEquals(40.0, calm.max * 2.2369362920544, 0.1)
        assertEquals(true, strong.max * 2.2369362920544 > 40.0)
    }
}
