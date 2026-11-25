package com.clutterfree.weather.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.devoprop.clutterfreeweather.R
import com.clutterfree.weather.ui.DateFormats
import com.clutterfree.weather.ui.theme.Dimens
import com.clutterfree.weather.ui.theme.scaledWidth
import com.clutterfree.weather.ui.theme.WeatherColors
import com.clutterfree.weather.weather.AppConfig
import com.clutterfree.weather.weather.Measures
import com.clutterfree.weather.weather.DisplayUnits
import com.clutterfree.weather.weather.ForecastPeriod
import com.clutterfree.weather.weather.GraphSettings
import com.clutterfree.weather.weather.UnitFormatter
import com.clutterfree.weather.weather.ValueRange
import com.clutterfree.weather.weather.interpolate
import com.clutterfree.weather.weather.percentRange
import com.clutterfree.weather.weather.temperatureRange
import com.clutterfree.weather.weather.windRange
import java.time.Instant
import java.time.ZoneId

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
    graph: GraphSettings,
    units: DisplayUnits,
    temperatureCenterCelsius: Double?,
    timeAt: (Double) -> Instant,
    zone: ZoneId,
    pastUntil: Double? = null,
    emptyAfter: Double? = null,
    plotHeight: Dp = Dimens.graphHeight,
    vertical: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val formatter = UnitFormatter(units)
    val span = (domainEnd - domainStart).coerceAtLeast(0.0)
    val fraction = scrubberFraction.coerceIn(0f, 1f)
    val at = domainStart + fraction * span
    val series = plotSeries(graph, formatter)
    val time = DateFormats.clock().format(timeAt(at).atZone(zone))
    val noData = stringResource(R.string.graph_no_data)
    val extendedLabel = stringResource(R.string.graph_extended)
    val readout = buildList {
        add(time)
        series.filter { it.enabled }.forEach { item ->
            val samples = points.mapNotNull { point ->
                val x = xOf(point) ?: return@mapNotNull null
                val value = item.value(point) ?: return@mapNotNull null
                x to value
            }
            interpolate(samples, at)?.let { add(item.readout(it)) }
        }
    }.joinToString(", ")

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.tightGap)) {
        Legend(series.filter { it.enabled })
        if (vertical) {
            Row(Modifier.fillMaxWidth().height(plotHeight)) {
                leading?.invoke()
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
                    temperatureCenterCelsius = temperatureCenterCelsius,
                    pastUntil = pastUntil,
                    emptyAfter = emptyAfter,
                    noData = noData,
                    extendedLabel = extendedLabel,
                    vertical = true,
                    time = time,
                    readout = readout,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            return@Column
        }
        if (labels.isNotEmpty() && span > 0.0) {
            AxisLabels(labels, domainStart, span)
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
            temperatureCenterCelsius = temperatureCenterCelsius,
            pastUntil = pastUntil,
            emptyAfter = emptyAfter,
            noData = noData,
            extendedLabel = extendedLabel,
            vertical = false,
            time = time,
            readout = readout,
            modifier = Modifier.fillMaxWidth().height(plotHeight),
        )
    }
}

/**
 * Hour labels across the top of a horizontal graph. A plain layout, because the Today row measures
 * its children with [androidx.compose.foundation.layout.IntrinsicSize] and a subcompose layout cannot answer that.
 */
@Composable
private fun AxisLabels(labels: List<AxisLabel>, domainStart: Double, span: Double) {
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelWidth = labelStyle.scaledWidth(AppConfig.AXIS_LABEL_EM).coerceAtLeast(Dimens.axisLabelWidth)
    val labelWidthPx = with(LocalDensity.current) { labelWidth.roundToPx() }
    val shown = labels.filter {
        val place = ((it.at - domainStart) / span).toFloat()
        place in 0f..1f
    }
    Layout(
        content = {
            shown.forEach { label ->
                Text(
                    text = label.text,
                    modifier = Modifier.width(labelWidth),
                    style = labelStyle,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        measurePolicy = object : MeasurePolicy {
            override fun MeasureScope.measure(
                measurables: List<Measurable>,
                constraints: Constraints,
            ): MeasureResult {
                val width = constraints.maxWidth
                val childConstraints = constraints.copy(
                    minWidth = 0,
                    minHeight = 0,
                    maxWidth = labelWidthPx.coerceIn(0, width),
                )
                val placeables = measurables.map { it.measure(childConstraints) }
                val height = placeables.maxOfOrNull { it.height } ?: 0
                return layout(width, height) {
                    shown.forEachIndexed { index, label ->
                        val place = ((label.at - domainStart) / span).toFloat()
                        val child = placeables[index]
                        val x = (width * place - child.width / 2f).toInt()
                            .coerceIn(0, (width - child.width).coerceAtLeast(0))
                        child.placeRelative(x, 0)
                    }
                }
            }

            override fun IntrinsicMeasureScope.minIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int,
            ): Int = measurables.maxOfOrNull { it.minIntrinsicHeight(labelWidthPx) } ?: 0

            override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int,
            ): Int = minIntrinsicHeight(measurables, width)
        },
    )
}

@Composable
private fun Legend(series: List<PlottedSeries>) {
    if (series.isEmpty()) return
    val nightMode = isSystemInDarkTheme()
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardGap),
        verticalArrangement = Arrangement.spacedBy(Dimens.tightGap),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        series.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(Dimens.legendSwatch).height(Dimens.graphStroke)) {
                    if (item.gradient) {
                        drawRect(Brush.horizontalGradient(temperatureLegendColors(nightMode)))
                    } else {
                        drawRect(item.color)
                    }
                }
                Text(
                    text = stringResource(item.label),
                    modifier = Modifier.padding(start = Dimens.tightGap),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun temperatureLegendColors(nightMode: Boolean): List<Color> = listOf(
    WeatherColors.coldPurple,
    WeatherColors.royalBlue,
    WeatherColors.teal,
    WeatherColors.freshGreen,
    WeatherColors.orange,
    WeatherColors.crimson,
).map { it.resolve(nightMode) }

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
    temperatureCenterCelsius: Double?,
    pastUntil: Double?,
    emptyAfter: Double?,
    noData: String,
    extendedLabel: String,
    vertical: Boolean,
    time: String,
    readout: String,
    modifier: Modifier,
) {
    val onScrub by rememberUpdatedState(onScrubberFraction)
    val night = MaterialTheme.colorScheme.onSurface.copy(alpha = AppConfig.NIGHT_BAND_ALPHA)
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = AppConfig.DAY_TINT_ALPHA)
    val scrubber = MaterialTheme.colorScheme.onSurface
    val gridline = MaterialTheme.colorScheme.onSurface.copy(alpha = AppConfig.GRIDLINE_ALPHA)
    val tipBackground = MaterialTheme.colorScheme.surface.copy(alpha = AppConfig.TIP_BACKGROUND_ALPHA)
    val tipStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val timeColor = MaterialTheme.colorScheme.onSurface
    val pastOverlay = MaterialTheme.colorScheme.onSurface.copy(alpha = AppConfig.PAST_OVERLAY_ALPHA)
    val noteColor = MaterialTheme.colorScheme.onSurfaceVariant
    val noteStyle = MaterialTheme.typography.labelMedium.copy(color = noteColor, textAlign = TextAlign.Center)
    val nightMode = isSystemInDarkTheme()
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier
            .semantics { contentDescription = readout }
            .pointerInput(vertical) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                fun report(position: Offset) {
                    val length = if (vertical) size.height else size.width
                    if (length <= 0) return
                    val along = if (vertical) position.y else position.x
                    onScrub((along / length).coerceIn(0f, 1f))
                }
                report(down.position)
                drag(down.id) { change ->
                    report(change.position)
                    change.consume()
                }
            }
        },
    ) {
        if (span <= 0.0) return@Canvas
        val plotLeft = Dimens.graphInset.toPx()
        val plotRight = size.width - plotLeft
        val plotTop = Dimens.graphInset.toPx()
        val plotBottom = size.height - plotTop
        fun along(x: Double): Float {
            val place = ((x - domainStart) / span).toFloat()
            return if (vertical) plotTop + place * (plotBottom - plotTop) else plotLeft + place * (plotRight - plotLeft)
        }
        fun across(value: Double, range: ValueRange): Float {
            val width = range.max - range.min
            val place = if (width == 0.0) 0.5f else ((value - range.min) / width).toFloat()
            return if (vertical) plotLeft + place * (plotRight - plotLeft) else plotBottom - place * (plotBottom - plotTop)
        }
        fun point(x: Double, value: Double, range: ValueRange): Offset =
            if (vertical) Offset(across(value, range), along(x)) else Offset(along(x), across(value, range))
        fun fillAlong(start: Double, end: Double, color: Color) {
            if (vertical) {
                drawRect(color, Offset(plotLeft, along(start)), Size(plotRight - plotLeft, along(end) - along(start)))
            } else {
                drawRect(color, Offset(along(start), plotTop), Size(along(end) - along(start), plotBottom - plotTop))
            }
        }
        fun drawBand(range: ClosedFloatingPointRange<Double>, color: Color) {
            val start = range.start.coerceAtLeast(domainStart)
            val end = range.endInclusive.coerceAtMost(domainEnd)
            if (end <= start) return
            fillAlong(start, end, color)
        }
        dayTints.forEach { drawBand(it, tint) }
        nightBands.forEach { drawBand(it, night) }
        val scrubberX = domainStart + fraction * span
        val tips = mutableListOf<Tip>()
        clipRect(plotLeft, plotTop, plotRight, plotBottom) {
            val locatedSeries = series.filter { it.enabled }.map { item ->
                item to points.mapNotNull { point ->
                    val x = xOf(point) ?: return@mapNotNull null
                    val value = item.value(point) ?: return@mapNotNull null
                    x to value
                }.sortedBy { it.first }
            }
            val temperatureSamples = locatedSeries
                .filter { (item, _) -> item.scale == GraphScale.TEMPERATURE }
                .flatMap { (_, located) -> located.map { it.second } }
            val center = temperatureCenterCelsius
                ?: temperatureSamples.average().takeIf { temperatureSamples.isNotEmpty() }
                ?: AppConfig.DEFAULT_TEMPERATURE_CELSIUS
            val sharedTemperature = temperatureRange(center, temperatureSamples)
            val percentSamples = locatedSeries
                .filter { (item, _) -> item.scale == GraphScale.PERCENT }
                .flatMap { (_, located) -> located.map { it.second } }
            val dash = PathEffect.dashPathEffect(floatArrayOf(Dimens.gridDash.toPx(), Dimens.gridDash.toPx()))
            fun reference(value: Double, range: ValueRange) {
                val at = across(value, range)
                drawLine(
                    color = gridline,
                    start = if (vertical) Offset(at, plotTop) else Offset(plotLeft, at),
                    end = if (vertical) Offset(at, plotBottom) else Offset(plotRight, at),
                    strokeWidth = Dimens.hairline.toPx(),
                    pathEffect = dash,
                )
            }
            if (locatedSeries.any { (item, _) -> item.scale == GraphScale.TEMPERATURE }) {
                if (Measures.FREEZING_CELSIUS in sharedTemperature.min..sharedTemperature.max) {
                    reference(Measures.FREEZING_CELSIUS, sharedTemperature)
                }
            }
            if (locatedSeries.any { (item, _) -> item.scale == GraphScale.PERCENT }) {
                reference(AppConfig.GRIDLINE_PERCENT, percentRange(percentSamples))
            }
            locatedSeries.forEach { (item, located) ->
                if (located.isEmpty()) return@forEach
                val range = when (item.scale) {
                    GraphScale.TEMPERATURE -> sharedTemperature
                    GraphScale.PERCENT -> percentRange(located.map { it.second })
                    GraphScale.WIND -> windRange(located.map { it.second })
                }
                interpolate(located, scrubberX)?.let { value ->
                    tips += Tip(across(value, range), item.tip(value), pointColor(item, value, nightMode))
                }
                val stroke = Stroke(width = Dimens.graphStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                if (located.size == 1) {
                    drawCircle(
                        color = pointColor(item, located[0].second, nightMode),
                        radius = Dimens.graphPoint.toPx(),
                        center = point(located[0].first, located[0].second, range),
                    )
                    return@forEach
                }
                val path = Path()
                located.forEachIndexed { index, sample ->
                    val at = point(sample.first, sample.second, range)
                    if (index == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
                }
                if (item.gradient) {
                    val brush = if (vertical) {
                        temperatureBrush(range, start = plotRight, end = plotLeft, vertical = false, nightMode = nightMode)
                    } else {
                        temperatureBrush(range, start = plotTop, end = plotBottom, vertical = true, nightMode = nightMode)
                    }
                    drawPath(path, brush, style = stroke)
                } else {
                    drawPath(path, item.color, style = stroke)
                }
            }
        }
        if (pastUntil != null) {
            val end = pastUntil.coerceAtMost(domainEnd)
            if (end > domainStart) fillAlong(domainStart, end, pastOverlay)
        }
        if (emptyAfter != null && emptyAfter < domainEnd) {
            val start = emptyAfter.coerceAtLeast(domainStart)
            val gap = Dimens.tightGap.toPx()
            val title = measurer.measure(noData, noteStyle)
            val subtitle = measurer.measure(extendedLabel, noteStyle)
            val blockWidth = maxOf(title.size.width, subtitle.size.width).toFloat()
            val blockHeight = title.size.height + gap + subtitle.size.height
            val alongRoom = along(domainEnd) - along(start)
            val acrossRoom = plotRight - plotLeft
            val fits = if (vertical) alongRoom >= blockHeight && acrossRoom >= blockWidth else alongRoom >= blockWidth && acrossRoom >= blockHeight
            if (start < domainEnd && fits) {
                val midAlong = (along(start) + along(domainEnd)) / 2f
                val midAcross = (plotLeft + plotRight) / 2f
                val center = if (vertical) Offset(midAcross, midAlong) else Offset(midAlong, midAcross)
                val top = center.y - blockHeight / 2f
                drawText(title, topLeft = Offset(center.x - title.size.width / 2f, top))
                drawText(subtitle, topLeft = Offset(center.x - subtitle.size.width / 2f, top + title.size.height + gap))
            }
        }
        val scrubberAt = along(scrubberX)
        drawLine(
            color = scrubber,
            start = if (vertical) Offset(plotLeft, scrubberAt) else Offset(scrubberAt, plotTop),
            end = if (vertical) Offset(plotRight, scrubberAt) else Offset(scrubberAt, plotBottom),
            strokeWidth = Dimens.scrubber.toPx(),
        )
        val pad = Dimens.tipPadding.toPx()
        val gap = Dimens.tipGap.toPx()
        val corner = CornerRadius(Dimens.tipCorner.toPx())
        val timeTip = Tip(if (vertical) plotLeft else plotTop, time, timeColor)
        val laidOut = (listOf(timeTip) + tips.sortedBy { it.across }).map { tip ->
            tip to measurer.measure(tip.text, tipStyle.copy(color = tip.color))
        }
        fun extent(layout: TextLayoutResult) =
            if (vertical) layout.size.width + pad * 2 else layout.size.height + pad * 2
        val starts = FloatArray(laidOut.size)
        var next = Float.NEGATIVE_INFINITY
        laidOut.forEachIndexed { index, (tip, layout) ->
            val length = extent(layout)
            val wanted = if (index == 0) tip.across else tip.across - length / 2
            starts[index] = maxOf(wanted, next)
            next = starts[index] + length + gap
        }
        val limit = if (vertical) size.width else size.height
        val overflow = (starts.last() + extent(laidOut.last().second) - limit).coerceAtLeast(0f)
        laidOut.forEachIndexed { index, (_, layout) ->
            val width = layout.size.width + pad * 2
            val height = layout.size.height + pad * 2
            val start = (starts[index] - overflow).coerceAtLeast(0f)
            val topLeft = if (vertical) {
                val below = scrubberAt + gap
                Offset(start, if (below + height > size.height) scrubberAt - gap - height else below)
            } else {
                val right = scrubberAt + gap
                Offset(if (right + width > size.width) scrubberAt - gap - width else right, start)
            }
            drawRoundRect(tipBackground, topLeft, Size(width, height), corner)
            drawText(layout, topLeft = topLeft + Offset(pad, pad))
        }
    }
}

private data class Tip(val across: Float, val text: String, val color: Color)

@Composable
private fun plotSeries(settings: GraphSettings, formatter: UnitFormatter): List<PlottedSeries> {
    val nightMode = isSystemInDarkTheme()
    val rain = stringResource(R.string.readout_rain)
    val humidity = stringResource(R.string.readout_humidity)
    val dew = stringResource(R.string.readout_dewpoint)
    return listOf(
        PlottedSeries(
            enabled = settings.temperature,
            label = R.string.series_temperature,
            color = WeatherColors.orange.resolve(nightMode),
            scale = GraphScale.TEMPERATURE,
            gradient = true,
            readout = formatter::temperatureCompact,
            tip = formatter::temperatureCompact,
            value = GraphPoint::temperatureCelsius,
        ),
        PlottedSeries(
            enabled = settings.rainChance,
            label = R.string.series_rain,
            color = WeatherColors.rain.resolve(nightMode),
            scale = GraphScale.PERCENT,
            readout = { rain.format(formatter.percent(it)) },
            tip = formatter::percent,
            value = GraphPoint::rainPercent,
        ),
        PlottedSeries(
            enabled = settings.humidity,
            label = R.string.series_humidity,
            color = WeatherColors.humidity.resolve(nightMode),
            scale = GraphScale.PERCENT,
            readout = { humidity.format(formatter.percent(it)) },
            tip = formatter::percent,
            value = GraphPoint::humidityPercent,
        ),
        PlottedSeries(
            enabled = settings.wind,
            label = R.string.series_wind,
            color = WeatherColors.wind.resolve(nightMode),
            scale = GraphScale.WIND,
            readout = { formatter.wind(it) },
            tip = { formatter.wind(it) },
            value = GraphPoint::windMetersPerSecond,
        ),
        PlottedSeries(
            enabled = settings.dewpoint,
            label = R.string.series_dewpoint,
            color = WeatherColors.dewPoint.resolve(nightMode),
            scale = GraphScale.TEMPERATURE,
            readout = { dew.format(formatter.temperatureCompact(it)) },
            tip = formatter::temperatureCompact,
            value = GraphPoint::dewpointCelsius,
        ),
    )
}

private enum class GraphScale { TEMPERATURE, PERCENT, WIND }

private data class PlottedSeries(
    val enabled: Boolean,
    @StringRes val label: Int,
    val color: Color,
    val scale: GraphScale,
    val gradient: Boolean = false,
    val readout: (Double) -> String,
    val tip: (Double) -> String,
    val value: (GraphPoint) -> Double?,
)

private fun pointColor(series: PlottedSeries, celsius: Double, nightMode: Boolean): Color =
    if (series.gradient) temperatureColor(fahrenheit(celsius), nightMode) else series.color

/** Gradient from the warmest color at [start] to the coldest at [end]. */
private fun temperatureBrush(range: ValueRange, start: Float, end: Float, vertical: Boolean, nightMode: Boolean): Brush {
    val steps = AppConfig.TEMPERATURE_GRADIENT_STEPS
    val colors = List(steps) { index ->
        val celsius = range.max + (range.min - range.max) * index / (steps - 1)
        temperatureColor(fahrenheit(celsius), nightMode)
    }
    return if (vertical) {
        Brush.verticalGradient(colors = colors, startY = start, endY = end)
    } else {
        Brush.horizontalGradient(colors = colors, startX = start, endX = end)
    }
}

private fun fahrenheit(celsius: Double): Double =
    celsius * Measures.FAHRENHEIT_PER_CELSIUS + Measures.FAHRENHEIT_OFFSET

private fun band(value: Double, start: Double, end: Double): Float =
    ((value - start) / (end - start)).toFloat()

private fun temperatureColor(fahrenheit: Double, nightMode: Boolean): Color = when {
    fahrenheit <= AppConfig.TEMPERATURE_FLOOR_F -> WeatherColors.coldPurple.resolve(nightMode)
    fahrenheit < AppConfig.TEMPERATURE_FREEZING_F -> lerp(
        WeatherColors.coldPurple.resolve(nightMode),
        WeatherColors.royalBlue.resolve(nightMode),
        band(fahrenheit, AppConfig.TEMPERATURE_FLOOR_F, AppConfig.TEMPERATURE_FREEZING_F),
    )
    fahrenheit < AppConfig.TEMPERATURE_COOL_F -> lerp(
        WeatherColors.skyBlue.resolve(nightMode),
        WeatherColors.teal.resolve(nightMode),
        band(fahrenheit, AppConfig.TEMPERATURE_FREEZING_F, AppConfig.TEMPERATURE_COOL_F),
    )
    fahrenheit < AppConfig.TEMPERATURE_MILD_F -> lerp(
        WeatherColors.freshGreen.resolve(nightMode),
        WeatherColors.warmYellow.resolve(nightMode),
        band(fahrenheit, AppConfig.TEMPERATURE_COOL_F, AppConfig.TEMPERATURE_MILD_F),
    )
    fahrenheit < AppConfig.TEMPERATURE_HOT_F -> lerp(
        WeatherColors.orange.resolve(nightMode),
        WeatherColors.crimson.resolve(nightMode),
        band(fahrenheit, AppConfig.TEMPERATURE_MILD_F, AppConfig.TEMPERATURE_HOT_F),
    )
    fahrenheit < AppConfig.TEMPERATURE_EXTREME_F -> lerp(
        WeatherColors.crimson.resolve(nightMode),
        WeatherColors.maroon.resolve(nightMode),
        band(fahrenheit, AppConfig.TEMPERATURE_HOT_F, AppConfig.TEMPERATURE_EXTREME_F),
    )
    else -> WeatherColors.maroon.resolve(nightMode)
}
