package com.clutterfree.weather.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.devoprop.clutterfreeweather.R
import com.clutterfree.weather.ui.theme.Dimens
import com.clutterfree.weather.weather.AppConfig
import com.clutterfree.weather.weather.ChartOrientation
import com.clutterfree.weather.weather.DetailItem
import com.clutterfree.weather.weather.DisplayUnits
import com.clutterfree.weather.weather.GraphSettings
import com.clutterfree.weather.weather.HomeCard
import com.clutterfree.weather.weather.HomeSettings
import com.clutterfree.weather.weather.PrecipitationUnit
import com.clutterfree.weather.weather.TemperatureUnit
import com.clutterfree.weather.weather.WindUnit

/** Tabs that follow Today. Today is always the first tab. */
internal val tabCards = listOf(HomeCard.HOURLY, HomeCard.EXTENDED, HomeCard.RADAR)

@Composable
fun SettingsPane(
    settings: HomeSettings,
    onSettings: (HomeSettings) -> Unit,
    units: DisplayUnits,
    onUnits: (DisplayUnits) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    preview: (@Composable () -> Unit)? = null,
) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onReset()
                }) { Text(stringResource(R.string.reset_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.tightGap)) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = Dimens.touchTarget)) {
            Text(stringResource(R.string.back))
        }
        Text(text = stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)

        Section(R.string.settings_about)
        Text(
            text = stringResource(R.string.about_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        val links = LocalUriHandler.current
        TextButton(
            onClick = { links.openUri(AppConfig.PRIVACY_URL) },
            modifier = Modifier.heightIn(min = Dimens.touchTarget),
        ) {
            Text(stringResource(R.string.privacy_policy))
        }

        if (preview != null) {
            Section(R.string.settings_preview)
            preview()
        }

        Section(R.string.settings_location)
        CheckRow(stringResource(R.string.settings_suggest_closest), settings.suggestClosest) {
            onSettings(settings.copy(suggestClosest = it))
        }

        Section(R.string.settings_units)
        UnitChoice(
            label = R.string.units_temperature,
            options = TemperatureUnit.entries,
            selected = units.temperature,
            optionLabel = { if (it == TemperatureUnit.FAHRENHEIT) R.string.unit_fahrenheit else R.string.unit_celsius },
        ) { onUnits(units.copy(temperature = it)) }
        UnitChoice(
            label = R.string.units_wind,
            options = WindUnit.entries,
            selected = units.wind,
            optionLabel = { if (it == WindUnit.MILES_PER_HOUR) R.string.unit_mph else R.string.unit_kmh },
        ) { onUnits(units.copy(wind = it)) }
        UnitChoice(
            label = R.string.units_precipitation,
            options = PrecipitationUnit.entries,
            selected = units.precipitation,
            optionLabel = { if (it == PrecipitationUnit.INCHES) R.string.unit_inches else R.string.unit_millimeters },
        ) { onUnits(units.copy(precipitation = it)) }

        Section(R.string.settings_tab_order)
        val tabs = settings.cardOrder.filter { it in tabCards }
        tabs.forEachIndexed { index, card ->
            val name = stringResource(cardLabel(card))
            Row(
                Modifier.fillMaxWidth().heightIn(min = Dimens.touchTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(
                    onClick = { onSettings(settings.copy(cardOrder = moveTab(settings.cardOrder, index, index - 1))) },
                    enabled = index > 0,
                ) {
                    Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.move_up, name))
                }
                IconButton(
                    onClick = { onSettings(settings.copy(cardOrder = moveTab(settings.cardOrder, index, index + 1))) },
                    enabled = index < tabs.lastIndex,
                ) {
                    Icon(painterResource(R.drawable.ic_arrow_down), stringResource(R.string.move_down, name))
                }
            }
        }

        Section(R.string.settings_chart_orientation)
        ChartOrientation.entries.forEach { orientation ->
            ChoiceRow(stringResource(orientationLabel(orientation)), settings.chartOrientation == orientation) {
                onSettings(settings.copy(chartOrientation = orientation))
            }
        }

        Section(R.string.settings_cards)
        GraphChoices(R.string.today, settings.todayGraph) { onSettings(settings.copy(todayGraph = it)) }
        GraphChoices(R.string.hourly, settings.hourlyGraph) { onSettings(settings.copy(hourlyGraph = it)) }
        GraphChoices(R.string.extended, settings.extendedGraph) { onSettings(settings.copy(extendedGraph = it)) }

        Section(R.string.settings_details)
        DetailItem.entries.forEach { item ->
            CheckRow(stringResource(detailLabel(item)), item in settings.details) { on ->
                onSettings(settings.copy(details = if (on) settings.details + item else settings.details - item))
            }
        }

        Section(R.string.settings_extended_days)
        Row(
            Modifier.fillMaxWidth().heightIn(min = Dimens.touchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val days = settings.extendedVisibleDays
            val fewer = stringResource(R.string.fewer_days)
            val more = stringResource(R.string.more_days)
            TextButton(
                onClick = { onSettings(settings.copy(extendedVisibleDays = days - 1)) },
                enabled = days > AppConfig.EXTENDED_DAY_MIN,
                modifier = Modifier
                    .heightIn(min = Dimens.touchTarget)
                    .semantics { contentDescription = fewer },
            ) {
                Text(text = stringResource(R.string.minus), style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = days.toString(),
                modifier = Modifier.padding(horizontal = Dimens.cardPadding),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(
                onClick = { onSettings(settings.copy(extendedVisibleDays = days + 1)) },
                enabled = days < AppConfig.EXTENDED_DAY_MAX,
                modifier = Modifier
                    .heightIn(min = Dimens.touchTarget)
                    .semantics { contentDescription = more },
            ) {
                Text(text = stringResource(R.string.plus), style = MaterialTheme.typography.titleLarge)
            }
        }

        Section(R.string.settings_radar_miles)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().heightIn(min = Dimens.touchTarget)) {
            AppConfig.RADAR_MILES_CHOICES.forEachIndexed { index, miles ->
                SegmentedButton(
                    selected = settings.radarMiles == miles,
                    onClick = { onSettings(settings.copy(radarMiles = miles)) },
                    shape = SegmentedButtonDefaults.itemShape(index, AppConfig.RADAR_MILES_CHOICES.size),
                ) {
                    Text(stringResource(R.string.radar_miles, miles))
                }
            }
        }

        Text(
            text = stringResource(R.string.settings_credits),
            modifier = Modifier.padding(top = Dimens.sectionGap),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.credits_body),
            style = MaterialTheme.typography.bodyLarge,
        )

        HorizontalDivider(Modifier.padding(top = Dimens.sectionGap))
        TextButton(
            onClick = { confirmReset = true },
            modifier = Modifier.heightIn(min = Dimens.touchTarget),
        ) {
            Text(stringResource(R.string.reset_defaults), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Section(@StringRes title: Int) {
    HorizontalDivider(Modifier.padding(top = Dimens.sectionGap))
    Text(
        text = stringResource(title),
        modifier = Modifier.padding(top = Dimens.cardGap),
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun GraphChoices(@StringRes card: Int, graph: GraphSettings, onGraph: (GraphSettings) -> Unit) {
    val name = stringResource(card)
    Text(
        text = stringResource(R.string.graph_lines_for, name),
        modifier = Modifier.padding(top = Dimens.cardGap),
        style = MaterialTheme.typography.titleSmall,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchTarget)
            .toggleable(value = graph.visible, role = Role.Switch, onValueChange = { onGraph(graph.copy(visible = it)) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.show_graph),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(checked = graph.visible, onCheckedChange = null)
    }
    CheckRow(stringResource(R.string.series_temperature), graph.temperature, graph.visible) {
        onGraph(graph.copy(temperature = it))
    }
    CheckRow(stringResource(R.string.series_rain), graph.rainChance, graph.visible) {
        onGraph(graph.copy(rainChance = it))
    }
    CheckRow(stringResource(R.string.series_humidity), graph.humidity, graph.visible) {
        onGraph(graph.copy(humidity = it))
    }
    CheckRow(stringResource(R.string.series_wind), graph.wind, graph.visible) {
        onGraph(graph.copy(wind = it))
    }
    CheckRow(stringResource(R.string.series_dewpoint), graph.dewpoint, graph.visible) {
        onGraph(graph.copy(dewpoint = it))
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchTarget)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(
            text = label,
            modifier = Modifier.padding(start = Dimens.cardGap),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            modifier = Modifier.padding(start = Dimens.cardGap),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun <T> UnitChoice(
    @StringRes label: Int,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(stringResource(optionLabel(option)))
                }
            }
        }
    }
}

internal fun moveTab(order: List<HomeCard>, from: Int, to: Int): List<HomeCard> {
    val tabs = order.filter { it in tabCards }.toMutableList()
    if (from !in tabs.indices || to !in tabs.indices) return order
    tabs.add(to, tabs.removeAt(from))
    val next = tabs.iterator()
    return order.map { if (it in tabCards) next.next() else it }
}

@StringRes
internal fun cardLabel(card: HomeCard): Int = when (card) {
    HomeCard.TODAY -> R.string.today
    HomeCard.HOURLY -> R.string.hourly
    HomeCard.EXTENDED -> R.string.extended
    HomeCard.RADAR -> R.string.radar
}

@StringRes
private fun orientationLabel(orientation: ChartOrientation): Int = when (orientation) {
    ChartOrientation.AUTO -> R.string.orientation_auto
    ChartOrientation.HORIZONTAL -> R.string.orientation_horizontal
    ChartOrientation.VERTICAL -> R.string.orientation_vertical
}

@StringRes
private fun detailLabel(item: DetailItem): Int = when (item) {
    DetailItem.CURRENT_TEXT -> R.string.detail_item_current_text
    DetailItem.WIND_CHILL_HEAT_INDEX -> R.string.detail_item_wind_chill_heat_index
    DetailItem.DAY_TEXT -> R.string.detail_item_day_text
    DetailItem.NIGHT_TEXT -> R.string.detail_item_night_text
    DetailItem.SUNRISE -> R.string.detail_item_sunrise
    DetailItem.SUNSET -> R.string.detail_item_sunset
    DetailItem.HIGH -> R.string.detail_item_high
    DetailItem.LOW -> R.string.detail_item_low
    DetailItem.HUMIDITY -> R.string.detail_item_humidity
    DetailItem.WIND -> R.string.detail_item_wind
    DetailItem.MOON -> R.string.detail_item_moon
}
