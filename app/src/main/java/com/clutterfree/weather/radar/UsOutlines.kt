package com.clutterfree.weather.radar

import org.json.JSONArray
import org.json.JSONObject

/** One boundary segment in normalized Web Mercator, stored as x, y pairs. */
class OutlineLine(
    val state: Boolean,
    val points: FloatArray,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
)

class UsOutlines(val lines: List<OutlineLine>) {
    companion object {
        /**
         * Reads the us-atlas counties TopoJSON. Every arc is a county edge. Arcs used by the
         * states or nation objects are also state or coast lines.
         */
        fun parse(json: String): UsOutlines {
            val topology = JSONObject(json)
            val transform = topology.getJSONObject("transform")
            val scale = transform.getJSONArray("scale")
            val translate = transform.getJSONArray("translate")
            val objects = topology.getJSONObject("objects")
            val stateArcs = HashSet<Int>()
            listOf("states", "nation").forEach { name ->
                objects.optJSONObject(name)?.optJSONArray("geometries")?.let { collectArcs(it, stateArcs) }
            }
            val arcs = topology.getJSONArray("arcs")
            val lines = List(arcs.length()) { index ->
                val arc = arcs.getJSONArray(index)
                val points = FloatArray(arc.length() * 2)
                var qx = 0
                var qy = 0
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE
                for (step in 0 until arc.length()) {
                    val delta = arc.getJSONArray(step)
                    qx += delta.getInt(0)
                    qy += delta.getInt(1)
                    val longitude = qx * scale.getDouble(0) + translate.getDouble(0)
                    val latitude = qy * scale.getDouble(1) + translate.getDouble(1)
                    val x = WebMercator.x(longitude).toFloat()
                    val y = WebMercator.y(latitude).toFloat()
                    points[step * 2] = x
                    points[step * 2 + 1] = y
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
                OutlineLine(index in stateArcs, points, minX, minY, maxX, maxY)
            }
            return UsOutlines(lines)
        }

        private fun collectArcs(array: JSONArray, into: MutableSet<Int>) {
            for (index in 0 until array.length()) {
                when (val item = array.get(index)) {
                    is JSONObject -> item.optJSONArray("arcs")?.let { collectArcs(it, into) }
                    is JSONArray -> collectArcs(item, into)
                    is Number -> item.toInt().let { into += if (it >= 0) it else it.inv() }
                }
            }
        }
    }
}
