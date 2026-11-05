package com.example.pokemonalertsv2.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.ui.theme.Dimens

/**
 * Centres this element and caps it at [maxWidth], so a column of rows reads as one block on a
 * tablet instead of stretching edge to edge. A no-op on a phone, which is narrower than the cap.
 *
 * Put it on the content inside a scroll container, not on the container: the container then
 * still takes scroll and pull-to-refresh gestures across the whole width.
 */
fun Modifier.readableWidth(maxWidth: Dp = Dimens.ReadableContentWidth): Modifier =
    fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = maxWidth)
        // wrapContentWidth lets the child shrink to its content; this makes it take the cap.
        .fillMaxWidth()

/**
 * Horizontal padding that centres a lazy list's items in a column at most [maxContentWidth]
 * wide, never less than [minimum]. For a LazyColumn, whose items cannot carry [readableWidth]
 * one by one without each re-centring on its own.
 */
fun readableHorizontalPadding(
    availableWidth: Dp,
    maxContentWidth: Dp = Dimens.ReadableContentWidth,
    minimum: Dp = 16.dp
): Dp = maxOf(minimum, (availableWidth - maxContentWidth) / 2)

/** [readableHorizontalPadding] as list content padding, with [vertical] top and bottom. */
fun readableListPadding(
    availableWidth: Dp,
    vertical: Dp = 16.dp,
    maxContentWidth: Dp = Dimens.ReadableContentWidth,
    minimum: Dp = 16.dp
): PaddingValues = PaddingValues(
    horizontal = readableHorizontalPadding(availableWidth, maxContentWidth, minimum),
    vertical = vertical
)

/** Columns for a grid of cards: one on a phone, more as the width allows. */
val AdaptiveCardColumns: GridCells = GridCells.Adaptive(minSize = Dimens.GridCardMinWidth)
