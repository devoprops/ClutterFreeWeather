package com.clutterfree.weather.data

/**
 * Reads the sky and present-weather groups of a raw METAR and returns the matching
 * NWS icon condition code, or null when the report has neither.
 */
/** A special report for a thunderstorm, tornado, hail, or squall. */
fun severeSpeci(raw: String): Boolean {
    val tokens = raw.trim().split(Regex("\\s+"))
    if (!tokens.firstOrNull().equals("SPECI", ignoreCase = true)) return false
    val weather = tokens.takeWhile { it != "RMK" }.filter { WEATHER_GROUP.matches(it) }
    return weather.any { group ->
        val code = group.trimStart('-', '+')
        "FC" in code || "TS" in code || "GR" in code || "SQ" in code
    }
}

fun metarCondition(raw: String): String? {
    val body = raw.trim().split(Regex("\\s+")).takeWhile { it != "RMK" }
    val start = body.indexOfFirst { TIME_GROUP.matches(it) } + 1
    val groups = body.drop(start)
    val weather = groups.filter { WEATHER_GROUP.matches(it) && it.trimStart('-', '+').isNotEmpty() }
    return weatherCondition(weather) ?: skyCondition(groups)
}

private fun weatherCondition(groups: List<String>): String? {
    if (groups.isEmpty()) return null
    val nearby = groups.filter { it.startsWith("VC") }
    val here = groups - nearby.toSet()
    fun has(code: String) = here.any { code in it }

    val rain = has("RA") || has("DZ")
    val snow = has("SN") || has("SG")
    return when {
        has("FC") -> "tornado"
        has("TS") -> "tsra"
        has("FZRA") || has("FZDZ") -> if (snow) "snow_fzra" else "fzra"
        has("PL") || has("IC") || has("GS") -> when {
            snow -> "snow_sleet"
            rain -> "rain_sleet"
            else -> "sleet"
        }
        snow -> if (rain) "rain_snow" else "snow"
        has("SHRA") -> "rain_showers"
        rain -> "rain"
        nearby.any { "TS" in it } -> "tsra_sct"
        nearby.any { "SH" in it } -> "rain_showers"
        has("FG") || has("BR") -> "fog"
        has("HZ") -> "haze"
        has("FU") -> "smoke"
        has("DU") || has("SA") -> "dust"
        else -> null
    }
}

private fun skyCondition(groups: List<String>): String? {
    var cover = -1
    for (group in groups) {
        val rank = when {
            group == "CLR" || group == "SKC" || group == "NCD" || group == "NSC" || group == "CAVOK" -> 0
            else -> SKY_RANK[SKY_GROUP.matchEntire(group)?.groupValues?.get(1)] ?: continue
        }
        cover = maxOf(cover, rank)
    }
    return SKY_CODES.getOrNull(cover)
}

private val TIME_GROUP = Regex("\\d{6}Z")
private val WEATHER_GROUP = Regex(
    "(-|\\+|VC)?(MI|PR|BC|DR|BL|SH|TS|FZ)*(DZ|RA|SN|SG|IC|PL|GR|GS|UP|BR|FG|FU|VA|DU|SA|HZ|PY|PO|SQ|FC|SS|DS)*",
)
private val SKY_GROUP = Regex("(FEW|SCT|BKN|OVC|VV)\\d{3}(CB|TCU)?")
private val SKY_RANK = mapOf("FEW" to 1, "SCT" to 2, "BKN" to 3, "OVC" to 4, "VV" to 4)
private val SKY_CODES = listOf("skc", "few", "sct", "bkn", "ovc")
