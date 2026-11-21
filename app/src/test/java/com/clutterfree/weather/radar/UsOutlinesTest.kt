package com.clutterfree.weather.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UsOutlinesTest {
    @Test
    fun decodesDeltaArcsAndMarksStateEdges() {
        val topology = """
            {
              "type": "Topology",
              "transform": {"scale": [1, 1], "translate": [-90, 30]},
              "objects": {
                "counties": {"type": "GeometryCollection", "geometries": [{"type": "Polygon", "arcs": [[0, 1]]}]},
                "states": {"type": "GeometryCollection", "geometries": [{"type": "Polygon", "arcs": [[-1]]}]}
              },
              "arcs": [[[0, 0], [2, 0]], [[2, 0], [0, 3]]]
            }
        """.trimIndent()

        val outlines = UsOutlines.parse(topology)
        assertEquals(2, outlines.lines.size)
        assertTrue(outlines.lines[0].state)
        assertFalse(outlines.lines[1].state)
        val last = outlines.lines[1].points
        assertEquals(WebMercator.x(-88.0).toFloat(), last[2], 1e-6f)
        assertEquals(WebMercator.y(33.0).toFloat(), last[3], 1e-6f)
    }

    @Test
    fun bundledFileHasStateAndCountyLines() {
        val file = listOf(File("src/main/assets/us_counties_10m.json"), File("app/src/main/assets/us_counties_10m.json"))
            .first { it.exists() }
        val outlines = UsOutlines.parse(file.readText())
        assertTrue(outlines.lines.any { it.state })
        assertTrue(outlines.lines.count { !it.state } > outlines.lines.count { it.state })
    }
}
