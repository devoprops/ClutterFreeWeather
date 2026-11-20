package com.clutterfree.weather.ui.home

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.clutterfree.weather.R
import com.clutterfree.weather.weather.MoonPhase
import com.clutterfree.weather.weather.iconCandidates
import com.clutterfree.weather.weather.iconDarken
import com.clutterfree.weather.weather.moonPhase
import com.clutterfree.weather.weather.parseIconUrl
import com.clutterfree.weather.weather.resolvedCondition
import com.clutterfree.weather.weather.sunTimes
import java.time.Instant
import java.time.ZoneId

@Composable
fun WeatherIcon(
    iconUrl: String?,
    at: Instant,
    latitude: Double,
    longitude: Double,
    zone: ZoneId,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val description = contentDescription ?: stringResource(R.string.weather_icon)
    val parsed = remember(iconUrl) { parseIconUrl(iconUrl) }
    val drawable = remember(iconUrl) {
        val resolved = resolvedCondition(parsed.condition)
        val found = findDrawable(context, iconCandidates(parsed))
        if (found != null && (found.first != "ic_wx_day_skc" || resolved == "skc" || !standInKnows(resolved))) {
            found
        } else {
            null
        }
    }
    val darken = remember(at, latitude, longitude, zone) {
        val sun = sunTimes(at.atZone(zone).toLocalDate(), latitude, longitude, zone)
        sun != null && iconDarken(at, sun)
    }
    val phase = remember(at) { moonPhase(at) }
    val overlayMoon = parsed.night && drawable != null && !drawable.first.startsWith("ic_wx_night")

    Box(modifier.semantics { this.contentDescription = description }) {
        if (drawable != null) {
            Image(
                painter = painterResource(drawable.second),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            if (overlayMoon) {
                MoonBadge(phase, Modifier.align(Alignment.TopEnd).fillMaxSize(0.36f))
            }
            if (parsed.windy) {
                WindBadge(Modifier.align(Alignment.BottomEnd).fillMaxSize(0.4f))
            }
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val mark = size.minDimension / 160f
                translate((size.width - 160f * mark) / 2f, (size.height - 160f * mark) / 2f) {
                    scale(mark, mark, pivot = Offset.Zero) {
                        standIn(resolvedCondition(parsed.condition), parsed.night, phase, parsed.windy)
                    }
                }
            }
        }
        if (darken) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
        }
    }
}

@Composable
private fun MoonBadge(phase: MoonPhase, modifier: Modifier) {
    val context = LocalContext.current
    val name = moonFile(phase)
    val id = remember(name) { context.resources.getIdentifier(name, "drawable", context.packageName) }
    if (id != 0) {
        Image(painter = painterResource(id), contentDescription = null, modifier = modifier)
    } else {
        Canvas(modifier) {
            moon(Offset(size.width / 2f, size.height / 2f), size.minDimension / 2.2f, phase)
        }
    }
}

@Composable
private fun WindBadge(modifier: Modifier) {
    val context = LocalContext.current
    val id = remember { context.resources.getIdentifier("ic_wx_wind", "drawable", context.packageName) }
    if (id != 0) {
        Image(painter = painterResource(id), contentDescription = null, modifier = modifier)
    } else {
        Canvas(modifier) { wind(size.width, size.height) }
    }
}

private fun findDrawable(context: Context, names: List<String>): Pair<String, Int>? {
    return names.firstNotNullOfOrNull { name ->
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (id != 0) name to id else null
    }
}

private fun standInKnows(condition: String): Boolean = when (condition) {
    "skc", "few", "sct", "bkn", "ovc", "rain", "rain_showers", "snow", "sleet", "fzra", "tsra", "fog" -> true
    else -> false
}

private fun moonFile(phase: MoonPhase): String = when (phase) {
    MoonPhase.NEW -> "ic_moon_new"
    MoonPhase.WAXING_CRESCENT -> "ic_moon_waxing_crescent"
    MoonPhase.FIRST_QUARTER -> "ic_moon_first_quarter"
    MoonPhase.WAXING_GIBBOUS -> "ic_moon_waxing_gibbous"
    MoonPhase.FULL -> "ic_moon_full"
    MoonPhase.WANING_GIBBOUS -> "ic_moon_waning_gibbous"
    MoonPhase.LAST_QUARTER -> "ic_moon_last_quarter"
    MoonPhase.WANING_CRESCENT -> "ic_moon_waning_crescent"
}

private fun DrawScope.standIn(condition: String, night: Boolean, phase: MoonPhase, windy: Boolean) {
    when (condition) {
        "skc" -> if (night) moon(Offset(80f, 80f), 34f, phase) else sun(80f, 78f, 32f)
        "few" -> {
            if (night) moon(Offset(48f, 50f), 16f, phase) else sun(48f, 50f, 18f)
            cloud(Offset(78f, 86f), 0.75f)
        }
        "sct", "rain_showers" -> {
            if (night) moon(Offset(122f, 36f), 14f, phase) else sun(46f, 46f, 16f)
            cloud(Offset(58f, 74f), 1f)
            if (condition == "rain_showers") drops(70f, 118f)
        }
        "bkn" -> {
            if (!night) sun(42f, 58f, 14f)
            cloud(Offset(52f, 68f), 1.15f)
            if (night) moon(Offset(124f, 34f), 13f, phase)
        }
        "ovc" -> {
            cloud(Offset(46f, 64f), 1.25f)
            if (night) moon(Offset(124f, 34f), 12f, phase)
        }
        "rain", "fzra" -> {
            cloud(Offset(48f, 52f), 1.1f)
            drops(62f, 108f)
            if (condition == "fzra") {
                drawLine(ICE, Offset(58f, 132f), Offset(112f, 132f), strokeWidth = 3f)
            }
            if (night) moon(Offset(124f, 32f), 12f, phase)
        }
        "snow" -> {
            cloud(Offset(48f, 52f), 1.1f)
            snow(64f, 112f)
            if (night) moon(Offset(124f, 32f), 12f, phase)
        }
        "sleet" -> {
            cloud(Offset(48f, 50f), 1.05f)
            drops(58f, 108f)
            snow(92f, 112f)
            if (night) moon(Offset(124f, 32f), 12f, phase)
        }
        "tsra" -> {
            cloud(Offset(46f, 48f), 1.15f)
            bolt()
            if (night) moon(Offset(126f, 30f), 12f, phase)
        }
        "fog" -> fog()
        else -> cloud(Offset(48f, 64f), 1.1f)
    }
    if (windy) wind(148f, 148f)
}

private fun DrawScope.sun(cx: Float, cy: Float, radius: Float) {
    drawCircle(SUN, radius, Offset(cx, cy))
}

private fun DrawScope.cloud(origin: Offset, scale: Float) {
    fun puff(dx: Float, dy: Float, radius: Float) {
        drawCircle(CLOUD, radius * scale, Offset(origin.x + dx * scale, origin.y + dy * scale))
    }
    puff(0f, 10f, 18f)
    puff(24f, 0f, 24f)
    puff(48f, 12f, 16f)
    drawRoundRect(
        color = CLOUD,
        topLeft = Offset(origin.x - 10f * scale, origin.y + 10f * scale),
        size = Size(68f * scale, 24f * scale),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f * scale, 12f * scale),
    )
}

private fun DrawScope.drops(x: Float, y: Float) {
    repeat(3) { index ->
        drawLine(
            color = RAIN,
            start = Offset(x + index * 16f, y),
            end = Offset(x + index * 16f - 6f, y + 18f),
            strokeWidth = 3f,
        )
    }
}

private fun DrawScope.snow(x: Float, y: Float) {
    repeat(3) { index ->
        drawCircle(SNOW, 4f, Offset(x + index * 16f, y + (index % 2) * 8f))
    }
}

private fun DrawScope.bolt() {
    val path = Path().apply {
        moveTo(78f, 78f)
        lineTo(64f, 104f)
        lineTo(78f, 104f)
        lineTo(66f, 128f)
        lineTo(100f, 96f)
        lineTo(84f, 96f)
        close()
    }
    drawPath(path, BOLT)
}

private fun DrawScope.fog() {
    repeat(3) { index ->
        drawRoundRect(
            color = FOG,
            topLeft = Offset(36f, 58f + index * 22f),
            size = Size(88f - index * 12f, 12f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
        )
    }
}

private fun DrawScope.wind(right: Float, bottom: Float) {
    drawLine(CLOUD_EDGE, Offset(right - 40f, bottom - 28f), Offset(right - 4f, bottom - 32f), strokeWidth = 4f)
    drawLine(CLOUD_EDGE, Offset(right - 32f, bottom - 16f), Offset(right - 4f, bottom - 20f), strokeWidth = 4f)
    drawLine(CLOUD_EDGE, Offset(right - 24f, bottom - 4f), Offset(right - 4f, bottom - 8f), strokeWidth = 4f)
}

private fun DrawScope.moon(center: Offset, radius: Float, phase: MoonPhase) {
    val disc = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
    }
    clipPath(disc) {
        drawCircle(MOON_SHADE, radius, center)
        when (phase) {
            MoonPhase.NEW -> Unit
            MoonPhase.FULL -> drawCircle(MOON_LIGHT, radius, center)
            MoonPhase.FIRST_QUARTER -> drawRect(
                MOON_LIGHT,
                Offset(center.x, center.y - radius),
                Size(radius, radius * 2f),
            )
            MoonPhase.LAST_QUARTER -> drawRect(
                MOON_LIGHT,
                Offset(center.x - radius, center.y - radius),
                Size(radius, radius * 2f),
            )
            MoonPhase.WAXING_CRESCENT -> drawCircle(MOON_LIGHT, radius, Offset(center.x + radius * 0.72f, center.y))
            MoonPhase.WANING_CRESCENT -> drawCircle(MOON_LIGHT, radius, Offset(center.x - radius * 0.72f, center.y))
            MoonPhase.WAXING_GIBBOUS -> {
                drawCircle(MOON_LIGHT, radius, center)
                drawCircle(MOON_SHADE, radius, Offset(center.x - radius * 0.78f, center.y))
            }
            MoonPhase.WANING_GIBBOUS -> {
                drawCircle(MOON_LIGHT, radius, center)
                drawCircle(MOON_SHADE, radius, Offset(center.x + radius * 0.78f, center.y))
            }
        }
    }
    drawCircle(MOON_SHADE, radius, center, style = Stroke(width = 2f))
}

internal val iconLabelColor = Color(0xFF1C2834)

private val SUN = Color(0xFFF5C542)
private val CLOUD = Color(0xFFD0D9E0)
private val CLOUD_EDGE = Color(0xFF607484)
private val RAIN = Color(0xFF3D7EDB)
private val SNOW = Color(0xFF9FB4C4)
private val ICE = Color(0xFF7FB4D4)
private val BOLT = Color(0xFFF0C84A)
private val FOG = Color(0xFFB7C3CC)
private val MOON_LIGHT = Color(0xFFF4F0E4)
private val MOON_SHADE = Color(0xFF243040)
