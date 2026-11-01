package dev.whiteboard.core.repository

import dev.whiteboard.core.model.*
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val values: Flow<UserSettings>
    suspend fun theme(mode: ThemeMode)
    suspend fun fingerDrawing(enabled: Boolean)
    suspend fun finishOnboarding()
    suspend fun autosave(enabled: Boolean)
    suspend fun smartShapes(enabled: Boolean)
    suspend fun snap(enabled: Boolean)
    suspend fun lastBrush(tool: Tool, ink: InkStyle)
    suspend fun addBrush(name: String, tool: Tool, ink: InkStyle)
    suspend fun deleteBrush(id: String)
    suspend fun favoriteColors(colors: List<Int>)
    suspend fun eraserMode(mode: EraserMode)
}
