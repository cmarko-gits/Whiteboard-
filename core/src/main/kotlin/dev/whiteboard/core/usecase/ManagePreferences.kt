package dev.whiteboard.core.usecase

import dev.whiteboard.core.model.*
import dev.whiteboard.core.repository.SettingsRepository

/** Settings policy shared by presentation and any future local automation. */
class ManagePreferences(private val settings: SettingsRepository) {
    val values get() = settings.values
    suspend fun theme(mode: ThemeMode) = settings.theme(mode)
    suspend fun fingerDrawing(enabled: Boolean) = settings.fingerDrawing(enabled)
    suspend fun finishOnboarding() = settings.finishOnboarding()
    suspend fun autosave(enabled: Boolean) = settings.autosave(enabled)
    suspend fun smartShapes(enabled: Boolean) = settings.smartShapes(enabled)
    suspend fun snap(enabled: Boolean) = settings.snap(enabled)
    suspend fun lastBrush(tool: Tool, ink: InkStyle) = settings.lastBrush(tool, ink)
    suspend fun addBrush(name: String, tool: Tool, ink: InkStyle) {
        val title = name.trim().take(40)
        require(title.isNotEmpty()) { "Brush name is required" }
        settings.addBrush(title, tool, ink)
    }
    suspend fun deleteBrush(id: String) = settings.deleteBrush(id)
    suspend fun favoriteColors(colors: List<Int>) = settings.favoriteColors(SavedColorPalette.normalize(colors))
}
