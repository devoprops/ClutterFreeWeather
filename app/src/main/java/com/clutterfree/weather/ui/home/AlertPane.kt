package com.clutterfree.weather.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.devoprop.clutterfreeweather.R
import com.clutterfree.weather.ui.theme.Dimens
import com.clutterfree.weather.weather.WeatherAlert
import com.clutterfree.weather.weather.rankedAlerts

@Composable
fun AlertBanner(alerts: List<WeatherAlert>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ranked = rankedAlerts(alerts)
    val top = ranked.firstOrNull() ?: return
    val colors = alertColors(top.severity)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.first, contentColor = colors.second),
    ) {
        Column(Modifier.padding(horizontal = Dimens.screenPadding, vertical = Dimens.cardPadding)) {
            Text(
                text = top.headline ?: top.event ?: stringResource(R.string.weather_alert),
                style = MaterialTheme.typography.titleMedium,
            )
            if (ranked.size > 1) {
                Text(
                    text = stringResource(R.string.alert_count, ranked.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun AlertList(
    alerts: List<WeatherAlert>,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
        BackButton(onBack)
        rankedAlerts(alerts).forEach { alert ->
            Card(
                onClick = { onSelect(alert.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.touchTarget),
            ) {
                Text(
                    text = alert.headline ?: alert.event ?: stringResource(R.string.weather_alert),
                    modifier = Modifier.padding(Dimens.screenPadding),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
fun AlertDetail(alert: WeatherAlert, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        BackButton(onBack)
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap)) {
                Text(
                    text = alert.headline ?: alert.event ?: stringResource(R.string.weather_alert),
                    style = MaterialTheme.typography.titleLarge,
                )
                alert.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
                alert.instruction?.takeIf { it.isNotBlank() }?.let { instruction ->
                    Text(text = stringResource(R.string.alert_instruction), style = MaterialTheme.typography.titleMedium)
                    Text(instruction)
                }
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = Dimens.touchTarget)) {
        Text(stringResource(R.string.back))
    }
}

@Composable
private fun alertColors(severity: String?): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val scheme = MaterialTheme.colorScheme
    return when (severity?.lowercase()) {
        "extreme", "severe" -> scheme.errorContainer to scheme.onErrorContainer
        "moderate", "minor" -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
}
