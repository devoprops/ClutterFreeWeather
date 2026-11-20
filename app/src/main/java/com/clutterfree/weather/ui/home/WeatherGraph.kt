package com.clutterfree.weather.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clutterfree.weather.R
import com.clutterfree.weather.weather.DisplayUnits
import com.clutterfree.weather.weather.ForecastPeriod
import com.clutterfree.weather.weather.HomeSettings
import com.clutterfree.weather.weather.UnitFormatter
import com.clutterfree.weather.weather.interpolate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class GraphPoint(
    val time: Instant,
    val temperatureCelsius: Double?,
    val rainPercent: Double?,
    val humidityPercent: Double?,
    val windMetersPerSecond: Double?,
    val dewpointCelsius: Double?,
)

data class AxisLabel(val at: Double, val text: String)

fun ForecastPeriod.toGraphPoint() = GraphPoint(
    time = start,
    temperatureCelsius = temperatureCelsius,
    rainPercent = precipitationProbabilityPercent,
    humidityPercent = relativeHumidityPercent,
    windMetersPerSecond = windSpeedMetersPerSecond,
    dewpointCelsius = dewpointCelsius,
)

@Composable
fun WeatherGraph(
    points: List<GraphPoint>,
    xOf: (GraphPoint) -> Double?,
    domainStart: Double,
    domainEnd: Double,
    nightBands: List<ClosedFloatingPointRange<Double>>,
    dayTints: List<ClosedFloatingPointRange<Double>>,
    labels: List<AxisLabel>,
    scrubberFraction: Float,
    onScrubberFraction: (Float) -> Unit,
    settings: HomeSettings,
    units: DisplayUnits,
    timeAt: (Double) -> Instant,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val formatter = UnitFormatter(units)
    val span = (domainEnd - domainStart).coerceAtLeast(0.0)
    val fraction = scrubberFraction.coerceIn(0f, 1f)
    val at = domainStart + fraction * span
    val series = plotSeries(settings, formatter)
    val readout = buildList {
        add(CLOCK.format(timeAt(at).atZone(zone)))
        series.filter { it.enabled }.forEach { item ->
            val samples = points.mapNotNull { point ->
                val x = xOf(point) ?: return@mapNotNull null
                val value = item.value(point) ?: return@mapNotNull null
                x to value
            }
            interpolate(samples, at)?.let { add(item.readout(it)) }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = readout.joinToString("  "),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (labels.isNotEmpty() && span > 0.0) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
                labels.forEach { label ->
                    val place = ((label.at - domainStart) / span).toFloat()
                    if (place < 0f || place > 1f) return@forEach
                    val x = (maxWidth * place - 18.dp).coerceIn(0.dp, (maxWidth - 36.dp).coerceAtLeast(0.dp))
                    Text(
                        text = label.text,
                        modifier = Modifier.offset(x = x),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        GraphCanvas(
            points = points,
            xOf = xOf,
            domainStart = domainStart,
            domainEnd = domainEnd,
            span = span,
            fraction = fraction,
            onScrubberFraction = onScrubberFraction,
            nightBands = nightBands,
            dayTints = dayTints,
            series = series,
            modifier = Modifier.fillMaxWidth().height(150.dp),
        )
    }
}

@Composable
private fun GraphCanvas(
    points: List<GraphPoint>,
    xOf: (GraphPoint) -> Double?,
    domainStart: Double,
    domainEnd: Double,
    span: Double,
    fraction: Float,
    onScrubberFraction: (Float) -> Unit,
    nightBands: List<ClosedFloatingPointRange<Double>>,
    dayTints: List<ClosedFloatingPointRange<Double>>,
    series: List<PlottedSeries>,
    modifier: Modifier,
) {
    val onScrub by rememberUpdatedState(onScrubberFraction)
    val night = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    val scrubber = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                fun report(x: Float) {
                    if (size.width <= 0f) return
                    onScrub((x / size.width).coerceIn(0f, 1f))
                }
                report(down.position.x)
                drag(down.id) { change ->
                    report(change.position.x)
                    change.consume()
                }
            }
        },
    ) {
        if (span <= 0.0) return@Canvas
        val plotLeft = 4.dp.toPx()
        val plotRight = size.width - plotLeft
        val plotTop = 4.dp.toPx()
        val plotBottom = size.height - plotTop
        fun xToPx(x: Double): Float {
            val place = ((x - domainStart) / span).toFloat()
            return plotLeft + place * (plotRight - plotLeft)
        }
        fun yToPx(value: Double, min: Double, max: Double): Float {
            val place = if (max == min) 0.5f else ((value - min) / (max - min)).toFloat()
            return plotBottom - place * (plotBottom - plotTop)
        }
        fun drawBand(range: ClosedFloatingPointRange<Double>, color: Color) {
            val start = range.start.coerceAtLeast(domainStart)
            val end = range.endInclusive.coerceAtMost(domainEnd)
            if (end <= start) return
            drawRect(
                color = color,
                topLeft = Offset(xToPx(start), plotTop),
                size = androidx.compose.ui.geometry.Size(xToPx(end) - xToPx(start), plotBottom - plotTop),
            )
        }
        dayTints.forEach { drawBand(it, tint) }
        nightBands.forEach { drawBand(it, night) }
        clipRect(plotLeft, plotTop, plotRight, plotBottom) {
            series.filter { it.enabled }.forEach { item ->
                val located = points.mapNotNull { point ->
                    val x = xOf(point) ?: return@mapNotNull null
                    val value = item.value(point) ?: return@mapNotNull null
                    x to value
                }.sortedBy { it.first }
                if (located.isEmpty()) return@forEach
                val min = located.minOf { it.second }
                val max = located.maxOf { it.second }
                if (located.size == 1) {
                    drawCircle(
                        color = item.color,
                        radius = 3.dp.toPx(),
                        center = Offset(xToPx(located[0].first), yToPx(located[0].second, min, max)),
                    )
                    return@forEach
                }
                val path = Path()
                located.forEachIndexed { index, sample ->
                    val px = xToPx(sample.first)
                    val py = yToPx(sample.second, min, max)
                    if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(
                    path = path,
                    color = item.color,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        val scrubberX = xToPx(domainStart + fraction * span)
        drawLine(
            color = scrubber,
            start = Offset(scrubberX, plotTop),
            end = Offset(scrubberX, plotBottom),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

@Composable
private fun plotSeries(settings: HomeSettings, formatter: UnitFormatter): List<PlottedSeries> {
    val rain = stringResource(R.string.readout_rain)
    val humidity = stringResource(R.string.readout_humidity)
    val dew = stringResource(R.string.readout_dewpoint)
    return listOf(
        PlottedSeries(
            enabled = settings.showTemperature,
            color = MaterialTheme.colorScheme.primary,
            readout = formatter::temperatureCompact,
            value = GraphPoint::temperatureCelsius,
        ),
        PlottedSeries(
            enabled = settings.showRainChance,
            color = MaterialTheme.colorScheme.tertiary,
            readout = { rain.format(formatter.percent(it)) },
            value = GraphPoint::rainPercent,
        ),
        PlottedSeries(
            enabled = settings.showHumidity,
            color = MaterialTheme.colorScheme.secondary,
            readout = { humidity.format(formatter.percent(it)) },
            value = GraphPoint::humidityPercent,
        ),
        PlottedSeries(
            enabled = settings.showWind,
            color = MaterialTheme.colorScheme.error,
            readout = { formatter.wind(it) },
            value = GraphPoint::windMetersPerSecond,
        ),
        PlottedSeries(
            enabled = settings.showDewpoint,
            color = MaterialTheme.colorScheme.outline,
            readout = { dew.format(formatter.temperatureCompact(it)) },
            value = GraphPoint::dewpointCelsius,
        ),
    )
}

private data class PlottedSeries(
    val enabled: Boolean,
    val color: Color,
    val readout: (Double) -> String,
    val value: (GraphPoint) -> Double?,
)

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
