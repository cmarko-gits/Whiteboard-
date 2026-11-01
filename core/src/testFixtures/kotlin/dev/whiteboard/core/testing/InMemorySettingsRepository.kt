package dev.whiteboard.core.testing

import dev.whiteboard.core.model.*
import dev.whiteboard.core.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class InMemorySettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    override val values = MutableStateFlow(initial)
    override suspend fun theme(mode: ThemeMode) {
        values.update { it.copy(theme = mode) }
    }
    override suspend fun fingerDrawing(enabled: Boolean) {
        values.update { it.copy(fingerDrawing = enabled) }
    }
    override suspend fun finishOnboarding() {
        values.update { it.copy(onboarded = true) }
    }
    override suspend fun autosave(enabled: Boolean) {
        values.update { it.copy(autosave = enabled) }
    }
    override suspend fun smartShapes(enabled: Boolean) {
        values.update { it.copy(smartShapes = enabled) }
    }
    override suspend fun snap(enabled: Boolean) {
        values.update { it.copy(snap = enabled) }
    }
    override suspend fun eraserMode(mode: EraserMode) {
        values.update { it.copy(eraserMode = mode) }
    }
    override suspend fun lastBrush(tool: Tool, ink: InkStyle) {
        values.update { it.copy(lastTool = tool, lastInk = ink) }
    }
    override suspend fun addBrush(name: String, tool: Tool, ink: InkStyle) {
        values.update { it.copy(brushes = it.brushes + BrushPreset(name = name, tool = tool, ink = ink)) }
    }
    override suspend fun deleteBrush(id: String) {
        values.update { it.copy(brushes = it.brushes.filterNot { brush -> brush.id == id }) }
    }
    override suspend fun favoriteColors(colors: List<Int>) {
        values.update { it.copy(favoriteColors = SavedColorPalette.normalize(colors)) }
    }
}
