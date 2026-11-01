package dev.whiteboard.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.whiteboard.core.model.SavedColorPalette
import dev.whiteboard.ui.R
import java.util.Locale
import kotlin.math.roundToInt

private fun colorHex(color: Int) = String.format(Locale.ROOT, "%06X", color and 0xFFFFFF)

@Composable
fun CustomColorDialog(initial: Int, favorites: List<Int>, saveFavorites: (List<Int>) -> Unit, dismiss: () -> Unit, submit: (Int) -> Unit) {
    val initialHsv = remember(initial) { FloatArray(3).also { AndroidColor.colorToHSV(initial, it) } }
    var selected by rememberSaveable(initial) { mutableIntStateOf(initial or 0xFF000000.toInt()) }
    var hue by rememberSaveable(initial) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable(initial) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by rememberSaveable(initial) { mutableFloatStateOf(initialHsv[2]) }
    var hex by rememberSaveable(initial) { mutableStateOf(colorHex(initial)) }
    var saved by rememberSaveable { mutableStateOf(SavedColorPalette.normalize(favorites)) }
    var rgb by rememberSaveable { mutableStateOf(false) }
    val valid = hex.matches(Regex("[0-9A-F]{6}"))
    val applyFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val compact = windowHeight < 500.dp
    fun choose(color: Int) {
        selected = color or 0xFF000000.toInt()
        val hsv = FloatArray(3).also { AndroidColor.colorToHSV(selected, it) }
        if (hsv[1] > 0f) hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
        hex = colorHex(selected)
    }
    fun chooseHsv(h: Float, s: Float, v: Float) {
        hue = h
        saturation = s
        brightness = v
        selected = AndroidColor.HSVToColor(floatArrayOf(h, s, v))
        hex = colorHex(selected)
    }
    val visual: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SaturationBrightness(hue, saturation, brightness, if (compact) 116 else 164) { s, v -> chooseHsv(hue, s, v) }
            HueControl(hue) { chooseHsv(it, saturation, brightness) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPreview(initial, stringResource(R.string.color_original), Modifier.weight(1f)) { choose(initial) }
                ColorPreview(selected, "#${colorHex(selected)}", Modifier.weight(1f)) { }
            }
        }
    }
    val hexInput: @Composable (Boolean) -> Unit = { showHelp ->
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        OutlinedTextField(
            value = hex,
            onValueChange = { raw ->
                val next = raw.trim().removePrefix("#").take(8).uppercase(Locale.ROOT)
                if (next.matches(Regex("[0-9A-F]{6}"))) choose(next.toInt(16)) else hex = next
            },
            label = { Text(stringResource(R.string.color_hex)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = !valid,
            supportingText = if (!valid && showHelp) ({ Text(stringResource(R.string.color_hex_help)) }) else null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focus.clearFocus(force = true)
                if (valid) applyFocus.requestFocus() else cancelFocus.requestFocus()
                keyboard?.hide()
            })
        )
    }
    val details: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val favorite = selected in saved
            TextButton(
                onClick = { saved = if (favorite) saved - selected else saved + selected },
                enabled = valid && (favorite || saved.size < SavedColorPalette.LIMIT)
            ) {
                Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (favorite) R.string.color_remove_saved else R.string.color_save))
            }
            if (saved.isNotEmpty()) {
                Text(stringResource(R.string.color_saved), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    saved.forEach { color -> ColorButton(color, color == selected) { choose(color) } }
                }
            }
            if (saved.size ==
                SavedColorPalette.LIMIT
            ) {
                Text(stringResource(R.string.color_saved_limit), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { rgb = !rgb }) {
                Text(stringResource(if (rgb) R.string.color_hide_rgb else R.string.color_show_rgb))
                Icon(if (rgb) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (rgb) {
                listOf(R.string.color_red, R.string.color_green, R.string.color_blue).forEachIndexed { channel, label ->
                    val shift = (2 - channel) * 8
                    val value = (selected ushr shift) and 255
                    val name = stringResource(label)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name.take(1), Modifier.width(16.dp))
                        Slider(
                            value.toFloat(),
                            { next ->
                                choose((selected and (255 shl shift).inv()) or (next.roundToInt() shl shift))
                            },
                            valueRange = 0f..255f,
                            steps = 254,
                            modifier = Modifier.weight(1f).semantics { contentDescription = name }
                        )
                        Text(value.toString(), Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    val commit = {
        if (saved != favorites) saveFavorites(saved)
        submit(selected)
    }
    val currentCommit by rememberUpdatedState(commit)
    val currentDismiss by rememberUpdatedState(dismiss)
    val currentValid by rememberUpdatedState(valid)
    // Preserve the focused button when hiding the IME moves actions from the input row to the footer.
    val actions = remember {
        movableContentOf {
            TextButton(
                onClick = { currentDismiss() },
                modifier = Modifier.focusProperties {
                    canFocus = true
                }.focusRequester(cancelFocus)
            ) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { currentCommit() },
                enabled = currentValid,
                modifier = Modifier.focusProperties {
                    canFocus = currentValid
                }.focusRequester(applyFocus)
            ) {
                Text(stringResource(R.string.apply))
            }
        }
    }
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BoxWithConstraints(Modifier.systemBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
            val inlineActions = maxHeight < 240.dp
            val showTitle = maxHeight >= 360.dp
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(Modifier.padding(if (inlineActions) 8.dp else 24.dp)) {
                    if (showTitle) {
                        Text(stringResource(R.string.custom_color), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                    }
                    // Keep the input in the same composition slot while the IME changes the available space.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { hexInput(!inlineActions) }
                        if (inlineActions) {
                            Spacer(Modifier.width(8.dp))
                            actions()
                        }
                    }
                    if (!inlineActions) {
                        Spacer(Modifier.height(12.dp))
                        BoxWithConstraints(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                            if (compact && maxWidth >= 360.dp) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { visual() }
                                    Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { details() }
                                }
                            } else {
                                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    visual()
                                    details()
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            actions()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaturationBrightness(hue: Float, saturation: Float, brightness: Float, height: Int, onChange: (Float, Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    val description = stringResource(R.string.color_saturation_brightness)
    val increaseSaturation = stringResource(R.string.increase_saturation)
    val decreaseSaturation = stringResource(R.string.decrease_saturation)
    val increaseBrightness = stringResource(R.string.increase_brightness)
    val decreaseBrightness = stringResource(R.string.decrease_brightness)
    Canvas(
        Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(12.dp))
            .semantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(increaseSaturation) {
                        change((saturation + 0.05f).coerceAtMost(1f), brightness)
                        true
                    },
                    CustomAccessibilityAction(decreaseSaturation) {
                        change((saturation - 0.05f).coerceAtLeast(0f), brightness)
                        true
                    },
                    CustomAccessibilityAction(increaseBrightness) {
                        change(saturation, (brightness + 0.05f).coerceAtMost(1f))
                        true
                    },
                    CustomAccessibilityAction(decreaseBrightness) {
                        change(saturation, (brightness - 0.05f).coerceAtLeast(0f))
                        true
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun pick(point: Offset) {
                        if (size.width > 0 && size.height > 0) {
                            change((point.x / size.width).coerceIn(0f, 1f), (1f - point.y / size.height).coerceIn(0f, 1f))
                        }
                    }
                    pick(down.position)
                    down.consume()
                    do {
                        val pointer = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        pick(pointer.position)
                        pointer.consume()
                    } while (pointer.pressed)
                }
            }
    ) {
        // IME/rotation transitions can temporarily constrain this Canvas to zero or a very small size.
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val radius = minOf(7.dp.toPx(), size.minDimension / 2f)
        val center = Offset(
            (saturation * size.width).coerceIn(radius, size.width - radius),
            ((1f - brightness) * size.height).coerceIn(radius, size.height - radius)
        )
        drawCircle(Color.Black, radius + 2.dp.toPx(), center, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White, radius, center, style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun HueControl(value: Float, change: (Float) -> Unit) {
    val description = stringResource(R.string.color_hue)
    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().height(12.dp).background(
                Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)),
                RoundedCornerShape(6.dp)
            )
        )
        Slider(
            value,
            change,
            valueRange = 0f..360f,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
            colors = SliderDefaults.colors(
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                thumbColor = Color.hsv(value, 1f, 1f)
            )
        )
    }
}

@Composable
private fun ColorPreview(color: Int, label: String, modifier: Modifier, click: () -> Unit) {
    Surface(
        onClick = click,
        color = Color(color),
        contentColor = if (Color(color).luminance() > 0.179f) Color.Black else Color.White,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
