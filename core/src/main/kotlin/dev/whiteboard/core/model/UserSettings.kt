package dev.whiteboard.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class EraserMode { STROKE, PARTIAL }

data class UserSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val fingerDrawing: Boolean = true,
    val onboarded: Boolean = false,
    val autosave: Boolean = false,
    val smartShapes: Boolean = false,
    val snap: Boolean = false,
    val lastTool: Tool = Tool.PEN,
    val lastInk: InkStyle = InkStyle(),
    val brushes: List<BrushPreset> = defaultBrushPresets,
    val favoriteColors: List<Int> = emptyList(),
    val eraserMode: EraserMode = EraserMode.STROKE
)
