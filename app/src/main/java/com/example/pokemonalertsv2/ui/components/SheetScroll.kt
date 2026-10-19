package com.example.pokemonalertsv2.ui.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * Keeps a list's leftover downward scroll and fling inside the sheet. Without it, scrolling a
 * list back to its top carried on into the sheet and dragged or flung it closed. The handle,
 * header and tabs still drag the sheet.
 */
internal fun Modifier.keepScrollInSheet(): Modifier = nestedScroll(SheetScrollGuard)

private object SheetScrollGuard : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        Offset(0f, available.y.coerceAtLeast(0f))

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(0f, available.y.coerceAtLeast(0f))
}

/**
 * Claims vertical drags that start on parts of a sheet's body with nothing to scroll, such as a
 * search field or a row of buttons, so they can not pull the sheet closed either. Scrollers under
 * it still win their own drags; only the handle and header above it drag the sheet.
 */
internal fun Modifier.claimSheetDrags(): Modifier = composed {
    draggable(rememberDraggableState {}, Orientation.Vertical)
}
