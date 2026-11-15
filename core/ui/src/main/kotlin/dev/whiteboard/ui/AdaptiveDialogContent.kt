package dev.whiteboard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** Keep the field's natural height when the IME leaves only a compact strip. */
@Composable
fun AdaptiveDialogSurface(content: @Composable ColumnScope.(compact: Boolean, showTitle: Boolean) -> Unit) {
    BoxWithConstraints {
        val compact = maxHeight < 240.dp
        val showTitle = maxHeight >= 360.dp
        Surface(
            modifier = Modifier.padding(vertical = if (compact) 0.dp else 8.dp).widthIn(max = 560.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(horizontal = if (compact) 8.dp else 24.dp, vertical = if (compact) 0.dp else 24.dp)) {
                content(compact, showTitle)
            }
        }
    }
}

/** The input and action row keep the same parents while IME insets change their placement. */
@Composable
fun AdaptiveDialogContent(
    compact: Boolean,
    input: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    body: @Composable () -> Unit
) {
    Layout(content = {
        Box(Modifier.fillMaxWidth()) { input() }
        Box(Modifier.fillMaxWidth()) { body() }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }) { items, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val sideBySide = compact && width >= 320.dp.roundToPx()
        if (sideBySide) {
            val gap = 8.dp.roundToPx().coerceAtMost(width)
            val buttons = items[2].measure(Constraints(maxWidth = (width * 0.6f).toInt(), maxHeight = height))
            val field = items[0].measure(Constraints(maxWidth = (width - buttons.width - gap).coerceAtLeast(0), maxHeight = height))
            val hidden = items[1].measure(Constraints.fixed(0, 0))
            val measuredHeight = maxOf(field.height, buttons.height).coerceIn(constraints.minHeight, height)
            layout(width, measuredHeight) {
                field.placeRelative(0, (measuredHeight - field.height) / 2)
                buttons.placeRelative(width - buttons.width, (measuredHeight - buttons.height) / 2)
                hidden.placeRelative(0, 0)
            }
        } else {
            val gap = 12.dp.roundToPx()
            val buttons = items[2].measure(Constraints(maxWidth = width, maxHeight = height))
            val field = items[0].measure(Constraints(maxWidth = width, maxHeight = (height - buttons.height - gap).coerceAtLeast(0)))
            val remaining = (height - field.height - buttons.height - 2 * gap).coerceAtLeast(0)
            val content = items[1].measure(Constraints(maxWidth = width, maxHeight = remaining))
            val gaps = if (content.height > 0) 2 * gap else gap
            val measuredHeight = (field.height + content.height + buttons.height + gaps).coerceIn(constraints.minHeight, height)
            layout(width, measuredHeight) {
                field.placeRelative(0, 0)
                content.placeRelative(0, field.height + gap)
                buttons.placeRelative(width - buttons.width, (measuredHeight - buttons.height).coerceAtLeast(0))
            }
        }
    }
}
