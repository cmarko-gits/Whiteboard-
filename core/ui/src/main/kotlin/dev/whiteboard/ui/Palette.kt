package dev.whiteboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

val inkColors =
    listOf(
        0xFF202621.toInt(),
        0xFF315D4C.toInt(),
        0xFF3D6EC4.toInt(),
        0xFFBE544E.toInt(),
        0xFF9660B0.toInt(),
        0xFFE8B83D.toInt(),
        0xFFFFFFFF.toInt()
    )
val noteColors = listOf(0xFFF4E6A3.toInt(), 0xFFD5E7CF.toInt(), 0xFFD4E3F3.toInt(), 0xFFF0D3CE.toInt(), 0xFFE6D9EE.toInt())

@Composable
fun ColorButton(color: Int, selected: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.color_option, "%06X".format(color and 0xFFFFFF))
    Surface(
        onClick = onClick,
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.size(48.dp).semantics {
            this.selected = selected
            contentDescription =
                description
        },
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(Modifier.padding(if (selected) 6.dp else 4.dp).background(Color(color), CircleShape))
    }
}
