package com.clutterfree.weather.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.devoprop.clutterfreeweather.R
import com.clutterfree.weather.MainActivity
import com.clutterfree.weather.data.WeatherStore
import com.clutterfree.weather.ui.DateFormats
import com.clutterfree.weather.ui.conditionString
import com.clutterfree.weather.ui.theme.Dimens
import com.clutterfree.weather.weather.DisplayUnits
import com.clutterfree.weather.weather.Measures
import com.clutterfree.weather.weather.UnitFormatter
import com.clutterfree.weather.weather.WidgetModel
import com.clutterfree.weather.weather.iconCandidates
import com.clutterfree.weather.weather.isNight
import com.clutterfree.weather.weather.parseIconUrl
import com.clutterfree.weather.weather.widgetModel
import com.clutterfree.weather.weather.withDaylight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class WeatherWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = withContext(Dispatchers.IO) { readWidget(context) }
        provideContent {
            GlanceTheme {
                WidgetFace(state)
            }
        }
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = WeatherWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        widgetPlaced(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRefresh.schedule(context)
    }

    override fun onDisabled(context: Context) {
        widgetRemoved(context)
        super.onDisabled(context)
    }
}

class CurrentWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = withContext(Dispatchers.IO) { readWidget(context) }
        provideContent {
            GlanceTheme {
                CurrentFace(state)
            }
        }
    }
}

class CurrentWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = CurrentWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        widgetPlaced(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRefresh.schedule(context)
    }

    override fun onDisabled(context: Context) {
        widgetRemoved(context)
        super.onDisabled(context)
    }
}

suspend fun updateWeatherWidgets(context: Context) {
    WeatherWidget().updateAll(context)
    CurrentWidget().updateAll(context)
}

private fun widgetPlaced(context: Context) {
    WidgetRefresh.schedule(context)
    WidgetRefresh.runNow(context)
}

private fun widgetRemoved(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val placed = listOf(WeatherWidgetReceiver::class.java, CurrentWidgetReceiver::class.java).any { type ->
        manager.getAppWidgetIds(ComponentName(context, type)).isNotEmpty()
    }
    if (!placed) WidgetRefresh.cancel(context)
}

private data class WidgetFaceState(
    val model: WidgetModel?,
    val placeName: String?,
    val units: DisplayUnits,
)

private fun readWidget(context: Context): WidgetFaceState {
    val store = WeatherStore(context.filesDir)
    val place = store.readPlace()
    val snapshot = store.readSnapshot()
    val model = if (place != null && snapshot != null) widgetModel(place, snapshot, Instant.now()) else null
    return WidgetFaceState(model = model, placeName = place?.displayName, units = store.readUnits())
}

@Composable
private fun WidgetFace(state: WidgetFaceState) {
    val model = state.model
    val wide = LocalSize.current.width >= Dimens.widgetSideBySide
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(Dimens.dayCorner)
            .clickable(actionStartActivity<MainActivity>())
            .padding(Dimens.cardPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        when {
            model != null && wide -> Row(
                GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) {
                CurrentHeadline(model, state.units, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(Dimens.cardGap))
                Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                    HourRow(model, state.units, GlanceModifier.fillMaxWidth())
                    Spacer(GlanceModifier.height(Dimens.cardGap))
                    DetailPane(model, state.units, GlanceModifier.defaultWeight().fillMaxWidth(), stacked = true)
                }
            }
            model != null -> Column(GlanceModifier.fillMaxSize()) {
                CurrentHeadline(model, state.units, GlanceModifier.fillMaxWidth())
                Spacer(GlanceModifier.height(Dimens.cardGap))
                HourRow(model, state.units, GlanceModifier.fillMaxWidth())
                Spacer(GlanceModifier.height(Dimens.cardGap))
                DetailPane(model, state.units, GlanceModifier.fillMaxWidth(), stacked = true)
            }
            else -> Text(
                text = androidx.glance.LocalContext.current.getString(
                    if (state.placeName == null) R.string.widget_empty else R.string.widget_updating,
                ),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            )
        }
    }
}

@Composable
private fun CurrentFace(state: WidgetFaceState) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(Dimens.dayCorner)
            .clickable(actionStartActivity<MainActivity>())
            .padding(Dimens.cardPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        val model = state.model
        if (model == null) {
            Text(
                text = LocalContext.current.getString(
                    if (state.placeName == null) R.string.widget_empty else R.string.widget_updating,
                ),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            )
        } else {
            CurrentBlock(model, state.units, GlanceModifier.fillMaxSize())
        }
    }
}

@Composable
private fun CurrentHeadline(model: WidgetModel, units: DisplayUnits, modifier: GlanceModifier) {
    val formatter = UnitFormatter(units)
    val missing = LocalContext.current.getString(R.string.missing)
    val at = model.observedAt ?: Instant.now()
    val icon = iconId(model.iconUrl, at, model)
    val temperature = model.temperatureCelsius?.let(formatter::temperatureCompact) ?: missing
    val condition = conditionText(LocalContext.current, daylightUrl(model.iconUrl, at, model))
    Column(modifier) {
        Text(
            text = model.placeName,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
        model.observedAt?.let { observed ->
            Text(
                text = observed.atZone(model.zone).format(DateFormats.clock()),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
        Spacer(GlanceModifier.height(Dimens.tightGap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != 0) {
                Image(
                    provider = ImageProvider(icon),
                    contentDescription = condition,
                    modifier = GlanceModifier.size(Dimens.widgetCurrentIcon),
                )
                Spacer(GlanceModifier.width(Dimens.cardGap))
            }
            Text(
                text = temperature,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun DetailPane(
    model: WidgetModel,
    units: DisplayUnits,
    modifier: GlanceModifier,
    stacked: Boolean,
) {
    val context = LocalContext.current
    val formatter = UnitFormatter(units)
    val missing = context.getString(R.string.missing)
    val at = model.observedAt ?: Instant.now()
    val condition = conditionText(context, daylightUrl(model.iconUrl, at, model))
    val high = model.highCelsius?.let(formatter::temperatureCompact) ?: missing
    val low = model.lowCelsius?.let(formatter::temperatureCompact) ?: missing
    val wind = windLine(model, formatter)
    val showRange = model.highCelsius != null || model.lowCelsius != null
    if (condition == null && !showRange && wind == null) return
    val range = if (showRange) context.getString(R.string.high_low, high, low) else null
    val summary = listOfNotNull(condition, range).joinToString("  ")
    Column(
        modifier
            .background(GlanceTheme.colors.surface)
            .cornerRadius(Dimens.hourCorner)
            .padding(Dimens.hourPanelPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stacked) {
            condition?.let { line(it, GlanceTheme.colors.onSurface) }
            range?.let { line(it, GlanceTheme.colors.onSurfaceVariant) }
        } else if (summary.isNotEmpty()) {
            line(summary, GlanceTheme.colors.onSurface)
        }
        wind?.let { line(it, GlanceTheme.colors.onSurfaceVariant, maxLines = 2) }
    }
}

@Composable
private fun line(text: String, color: androidx.glance.unit.ColorProvider, maxLines: Int = 1) {
    Text(
        text = text,
        maxLines = maxLines,
        style = TextStyle(color = color, fontSize = 13.sp),
    )
}

@Composable
private fun CurrentBlock(model: WidgetModel, units: DisplayUnits, modifier: GlanceModifier) {
    val formatter = UnitFormatter(units)
    val missing = LocalContext.current.getString(R.string.missing)
    val at = model.observedAt ?: Instant.now()
    val icon = iconId(model.iconUrl, at, model)
    val temperature = model.temperatureCelsius?.let(formatter::temperatureCompact) ?: missing
    val condition = conditionText(LocalContext.current, daylightUrl(model.iconUrl, at, model))
    Column(modifier) {
        Text(
            text = model.placeName,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
        model.observedAt?.let { observed ->
            Text(
                text = observed.atZone(model.zone).format(DateFormats.clock()),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
        Spacer(GlanceModifier.height(Dimens.tightGap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != 0) {
                Image(
                    provider = ImageProvider(icon),
                    contentDescription = condition,
                    modifier = GlanceModifier.size(Dimens.widgetIcon),
                )
                Spacer(GlanceModifier.width(Dimens.cardGap))
            }
            Text(
                text = temperature,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(GlanceModifier.height(Dimens.tightGap))
        DetailPane(model, units, GlanceModifier.defaultWeight().fillMaxWidth(), stacked = false)
    }
}

@Composable
private fun HourRow(model: WidgetModel, units: DisplayUnits, modifier: GlanceModifier) {
    val formatter = UnitFormatter(units)
    val missing = androidx.glance.LocalContext.current.getString(R.string.missing)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        model.hours.forEach { hour ->
            val icon = iconId(hour.iconUrl, hour.start, model)
            val condition = conditionText(LocalContext.current, daylightUrl(hour.iconUrl, hour.start, model))
            Column(
                GlanceModifier.defaultWeight().padding(horizontal = Dimens.tightGap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = hour.start.atZone(model.zone).format(DateFormats.hour()),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
                if (icon != 0) {
                    Image(
                        provider = ImageProvider(icon),
                        contentDescription = condition,
                        modifier = GlanceModifier.size(Dimens.widgetHourIcon),
                    )
                }
                Text(
                    text = hour.temperatureCelsius?.let(formatter::temperatureCompact) ?: missing,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

@Composable
private fun windLine(model: WidgetModel, formatter: UnitFormatter): String? {
    val context = LocalContext.current
    val speed = model.windMetersPerSecond ?: return null
    return if (speed < Measures.WIND_DISPLAY_GAP) {
        context.getString(R.string.current_calm)
    } else {
        val reading = listOfNotNull(model.windDirection, formatter.wind(speed)).joinToString(" ")
        context.getString(R.string.current_wind, reading)
    }
}

@Composable
private fun iconId(url: String?, at: Instant, model: WidgetModel): Int {
    val context = LocalContext.current
    val names = iconCandidates(parseIconUrl(daylightUrl(url, at, model)))
    return names.firstNotNullOfOrNull { name ->
        context.resources.getIdentifier(name, "drawable", context.packageName).takeIf { it != 0 }
    } ?: 0
}

private fun conditionText(context: Context, url: String?): String? {
    if (url.isNullOrBlank()) return null
    val parsed = parseIconUrl(url)
    val name = context.getString(conditionString(parsed.condition))
    return if (parsed.windy) context.getString(R.string.condition_windy, name) else name
}

private fun daylightUrl(url: String?, at: Instant, model: WidgetModel): String? {
    val night = isNight(at, model.latitude, model.longitude, model.zone) ?: return url
    return withDaylight(url, night) ?: url
}
