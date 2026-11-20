package com.clutterfree.weather.data

import java.io.InputStream
import java.text.Normalizer

data class UsPlace(
    val name: String,
    val state: String,
    val latitude: Double,
    val longitude: Double,
) {
    val label: String = "$name, $state"
    val key: String = foldPlaceName(name)
}

class UsPlaceIndex(private val places: List<UsPlace>) {
    fun suggest(query: String, limit: Int = 8): List<UsPlace> {
        val parsed = parseQuery(query) ?: return emptyList()
        val matches = ArrayList<UsPlace>(limit)
        for (place in places) {
            if (parsed.state != null && !place.state.equals(parsed.state, ignoreCase = true)) continue
            if (!place.key.startsWith(parsed.name)) continue
            matches.add(place)
            if (matches.size == limit) break
        }
        return matches
    }

    companion object {
        fun load(stream: InputStream): UsPlaceIndex {
            val places = stream.bufferedReader().lineSequence().mapNotNull(::parseLine).toList()
            return UsPlaceIndex(places)
        }

        private fun parseLine(line: String): UsPlace? {
            val parts = line.split('\t')
            if (parts.size != 4) return null
            val latitude = parts[2].toDoubleOrNull() ?: return null
            val longitude = parts[3].toDoubleOrNull() ?: return null
            if (parts[0].isBlank() || parts[1].isBlank()) return null
            return UsPlace(parts[0], parts[1], latitude, longitude)
        }
    }
}

private data class PlaceQuery(val name: String, val state: String?)

private fun parseQuery(raw: String): PlaceQuery? {
    val folded = foldPlaceName(raw)
    if (folded.isEmpty()) return null
    val space = folded.lastIndexOf(' ')
    if (space > 0) {
        val state = folded.substring(space + 1)
        val name = folded.substring(0, space).trim()
        if (state.length == 2 && state in STATE_CODES && name.length >= 2) {
            return PlaceQuery(name, state)
        }
    }
    if (folded.length < 2) return null
    return PlaceQuery(folded, null)
}

private fun foldPlaceName(value: String): String {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
    val builder = StringBuilder(normalized.length)
    for (char in normalized) {
        if (Character.getType(char) == Character.NON_SPACING_MARK.toInt()) continue
        if (char == '.' || char == ',') continue
        builder.append(char.lowercaseChar())
    }
    return WHITESPACE.replace(builder.toString(), " ").trim()
}

private val WHITESPACE = Regex("\\s+")

private val STATE_CODES = setOf(
    "al", "ak", "az", "ar", "ca", "co", "ct", "dc", "de", "fl", "ga", "gu", "hi", "ia", "id",
    "il", "in", "ks", "ky", "la", "ma", "md", "me", "mi", "mn", "mo", "mp", "ms", "mt", "nc",
    "nd", "ne", "nh", "nj", "nm", "nv", "ny", "oh", "ok", "or", "pa", "pr", "ri", "sc", "sd",
    "tn", "tx", "ut", "va", "vi", "vt", "wa", "wi", "wv", "wy", "as",
)
