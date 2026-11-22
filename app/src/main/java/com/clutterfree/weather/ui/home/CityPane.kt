package com.clutterfree.weather.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.devoprop.clutterfreeweather.R
import com.clutterfree.weather.data.UsPlace
import com.clutterfree.weather.ui.theme.Dimens
import com.clutterfree.weather.weather.Place

@Composable
fun CityPane(
    current: Place?,
    saved: List<Place>,
    query: String,
    onQuery: (String) -> Unit,
    suggestions: List<UsPlace>,
    onPick: (UsPlace) -> Unit,
    onSubmit: () -> Unit,
    onSwitch: (Place) -> Unit,
    onDelete: (Place) -> Unit,
    onReorder: (List<Place>) -> Unit,
    busy: Boolean,
    status: String?,
    onUseDevice: () -> Unit,
    nearest: UsPlace? = null,
    stationCode: String? = null,
    onPickNearest: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var explainLocation by remember { mutableStateOf(false) }
    var order by remember(saved) { mutableStateOf(saved) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(0f) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.tightGap)) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = Dimens.touchTarget)) {
            Text(stringResource(R.string.back))
        }
        Text(text = stringResource(R.string.cities), style = MaterialTheme.typography.titleLarge)
        TextButton(
            onClick = { explainLocation = true },
            enabled = !busy,
            modifier = Modifier.heightIn(min = Dimens.touchTarget),
        ) {
            Text(stringResource(R.string.use_device_location))
        }
        if (explainLocation) {
            AlertDialog(
                onDismissRequest = { explainLocation = false },
                title = { Text(stringResource(R.string.device_location_title)) },
                text = { Text(stringResource(R.string.device_location_reason)) },
                confirmButton = {
                    TextButton(onClick = {
                        explainLocation = false
                        onUseDevice()
                    }) { Text(stringResource(R.string.device_location_continue)) }
                },
                dismissButton = {
                    TextButton(onClick = { explainLocation = false }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardGap),
            label = { Text(stringResource(R.string.add_city)) },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
        status?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
        suggestions.forEach { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.touchTarget)
                    .clickable(enabled = !busy) { onPick(suggestion) }
                    .padding(horizontal = Dimens.cardPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = suggestion.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
        val nearestIsSaved = nearest != null && saved.any { it.displayName == nearest.label }
        if (nearest != null && !nearestIsSaved) {
            val nearestTitle = if (stationCode.isNullOrBlank()) {
                nearest.label
            } else {
                stringResource(R.string.location_with_station, stationCode, nearest.label)
            }
            HorizontalDivider(Modifier.padding(top = Dimens.sectionGap))
            Text(
                text = stringResource(R.string.current_location),
                modifier = Modifier.padding(top = Dimens.cardGap),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.touchTarget)
                    .clickable(enabled = !busy) { onPickNearest() }
                    .padding(end = Dimens.cardPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(Dimens.touchTarget))
                Text(
                    text = nearestTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        if (saved.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(top = Dimens.sectionGap))
            Text(
                text = stringResource(R.string.saved_cities),
                modifier = Modifier.padding(top = Dimens.cardGap),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        order.forEachIndexed { index, place ->
            val isCurrent = place.displayName == current?.displayName
            val dragging = index == dragIndex
            Row(
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                    .onSizeChanged { rowHeight = it.height.toFloat() }
                    .fillMaxWidth()
                    .heightIn(min = Dimens.touchTarget)
                    .clickable(enabled = !busy) { onSwitch(place) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = stringResource(R.string.reorder_city, place.displayName),
                    modifier = Modifier
                        .size(Dimens.touchTarget)
                        .padding(Dimens.cardPadding)
                        .pointerInput(place.displayName) {
                            detectDragGestures(
                                onDragStart = {
                                    dragIndex = order.indexOfFirst { it.displayName == place.displayName }
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    dragIndex = -1
                                    dragOffset = 0f
                                    onReorder(order)
                                },
                                onDragCancel = {
                                    dragIndex = -1
                                    dragOffset = 0f
                                },
                            ) { change, amount ->
                                change.consume()
                                if (dragIndex < 0 || rowHeight <= 0f) return@detectDragGestures
                                dragOffset += amount.y
                                val step = when {
                                    dragOffset > rowHeight / 2 && dragIndex < order.lastIndex -> 1
                                    dragOffset < -rowHeight / 2 && dragIndex > 0 -> -1
                                    else -> 0
                                }
                                if (step != 0) {
                                    order = order.toMutableList().apply { add(dragIndex + step, removeAt(dragIndex)) }
                                    dragIndex += step
                                    dragOffset -= step * rowHeight
                                }
                            }
                        },
                )
                val city = if (nearest?.label == place.displayName && isCurrent && !stationCode.isNullOrBlank()) {
                    stringResource(R.string.location_with_station, stationCode, place.displayName)
                } else {
                    place.displayName
                }
                val title = if (nearest?.label == place.displayName) {
                    stringResource(R.string.nearest_tag, city)
                } else {
                    city
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else null,
                )
                if (isCurrent) {
                    Text(
                        text = stringResource(R.string.current_city),
                        modifier = Modifier.padding(horizontal = Dimens.cardPadding),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    IconButton(onClick = { onDelete(place) }, enabled = !busy) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.delete_city, place.displayName),
                        )
                    }
                }
            }
        }
    }
}
