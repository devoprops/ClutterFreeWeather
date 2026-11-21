package com.clutterfree.weather.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified

/** Height of [lines] of this style at the user's font scale. */
@Composable
fun TextStyle.scaledHeight(lines: Int): Dp {
    val line = if (lineHeight.isSpecified) lineHeight else fontSize
    return with(LocalDensity.current) { (line * lines).toDp() }
}

/** Width of [ems] of this style at the user's font scale. */
@Composable
fun TextStyle.scaledWidth(ems: Int): Dp =
    with(LocalDensity.current) { (fontSize * ems).toDp() }
