package com.clutterfree.weather.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.clutterfree.weather.ui.theme.Dimens
import com.clutterfree.weather.ui.theme.LocalCardPalette

@Composable
fun ForecastCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = LocalCardPalette.current?.card ?: MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

@Composable
fun SubCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = LocalCardPalette.current?.subcard ?: MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}
