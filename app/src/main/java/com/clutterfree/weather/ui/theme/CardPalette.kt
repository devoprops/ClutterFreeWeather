package com.clutterfree.weather.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class CardPalette(
    val page: Color,
    val card: Color,
    val subcard: Color,
    val bandEven: Color,
    val bandOdd: Color,
)

val LocalCardPalette = staticCompositionLocalOf<CardPalette?> { null }

fun cardPalette(scheme: ColorScheme): CardPalette = CardPalette(
    page = scheme.background,
    card = scheme.surfaceContainer,
    subcard = scheme.surfaceContainerHigh,
    bandEven = scheme.surfaceContainerLow,
    bandOdd = scheme.surfaceContainerHighest,
)
