package dev.whiteboard.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whiteboard.ui.R

@Composable
fun ColorPickerButton(color: Int, favorites: List<Int>, saveFavorites: (List<Int>) -> Unit, select: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    FilledTonalIconButton(
        onClick = { open = true },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color(color),
            contentColor = if (Color(color).luminance() > 0.179f) Color.Black else Color.White
        )
    ) { Icon(Icons.Outlined.ColorLens, stringResource(R.string.custom_color)) }
    if (open) {
        CustomColorDialog(color, favorites, saveFavorites, { open = false }) {
            select(it)
            open = false
        }
    }
}

@Composable
fun ColorChoiceRow(color: Int, defaults: List<Int>, favorites: List<Int>, saveFavorites: (List<Int>) -> Unit, select: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorPickerButton(color, favorites, saveFavorites, select)
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf(color) + defaults + favorites).distinct().forEach { value -> ColorButton(value, value == color) { select(value) } }
        }
    }
}
