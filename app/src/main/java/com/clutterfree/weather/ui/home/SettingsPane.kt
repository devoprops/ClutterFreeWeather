package com.clutterfree.weather.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.clutterfree.weather.R
import com.clutterfree.weather.weather.HomeSettings

@Composable
fun SettingsPane(
    settings: HomeSettings,
    onSettings: (HomeSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.back))
        }
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.graph_lines),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        GraphLine(stringResource(R.string.series_temperature), settings.showTemperature) {
            onSettings(settings.copy(showTemperature = it))
        }
        GraphLine(stringResource(R.string.series_rain), settings.showRainChance) {
            onSettings(settings.copy(showRainChance = it))
        }
        GraphLine(stringResource(R.string.series_humidity), settings.showHumidity) {
            onSettings(settings.copy(showHumidity = it))
        }
        GraphLine(stringResource(R.string.series_wind), settings.showWind) {
            onSettings(settings.copy(showWind = it))
        }
        GraphLine(stringResource(R.string.series_dewpoint), settings.showDewpoint) {
            onSettings(settings.copy(showDewpoint = it))
        }
    }
}

@Composable
private fun GraphLine(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
