package dev.whiteboard.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dev.whiteboard.core.model.*
import dev.whiteboard.core.repository.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")
class AppSettings(context: Context) : SettingsRepository {
    private val store = context.applicationContext.settingsStore
    private val brushes = BrushPresetCodec()
    override val values = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { values ->
        UserSettings(
            theme = ThemeMode.entries.firstOrNull { it.name == values[THEME] } ?: ThemeMode.SYSTEM,
            fingerDrawing = values[FINGER] ?: true, onboarded = values[ONBOARD] ?: false, autosave = values[AUTOSAVE] ?: false,
            smartShapes = values[SMART] ?: false, snap = values[SNAP] ?: false,
            lastTool = Tool.entries.firstOrNull { it.name == values[LAST_TOOL] } ?: Tool.PEN,
            lastInk = InkStyle(
                values[LAST_COLOR] ?: InkStyle().color,
                values[LAST_WIDTH]?.takeIf { it.isFinite() }?.coerceIn(0.1f, 200f) ?: 3f,
                values[LAST_OPACITY]?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
            ),
            brushes = brushes.decode(values[PRESETS]),
            favoriteColors = FavoriteColors.decode(values[FAVORITE_COLORS])
        )
    }
    override suspend fun theme(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }
    override suspend fun fingerDrawing(enabled: Boolean) {
        store.edit { it[FINGER] = enabled }
    }
    override suspend fun finishOnboarding() {
        store.edit { it[ONBOARD] = true }
    }
    override suspend fun autosave(enabled: Boolean) {
        store.edit { it[AUTOSAVE] = enabled }
    }
    override suspend fun smartShapes(enabled: Boolean) {
        store.edit { it[SMART] = enabled }
    }
    override suspend fun snap(enabled: Boolean) {
        store.edit { it[SNAP] = enabled }
    }
    override suspend fun lastBrush(tool: Tool, ink: InkStyle) {
        store.edit {
            it[LAST_TOOL] = tool.name
            it[LAST_COLOR] = ink.color
            it[LAST_WIDTH] = ink.width
            it[LAST_OPACITY] = ink.opacity
        }
    }
    override suspend fun addBrush(name: String, tool: Tool, ink: InkStyle) {
        store.edit { values ->
            val current = brushes.decode(values[PRESETS])
            require(current.size < 12)
            values[PRESETS] = brushes.encode(current + BrushPreset(name = name.trim().take(40), tool = tool, ink = ink))
        }
    }
    override suspend fun deleteBrush(id: String) {
        store.edit { values ->
            values[PRESETS] =
                brushes.encode(brushes.decode(values[PRESETS]).filterNot { it.id == id })
        }
    }
    override suspend fun favoriteColors(colors: List<Int>) {
        store.edit { it[FAVORITE_COLORS] = FavoriteColors.encode(colors) }
    }
    companion object {
        private val THEME = stringPreferencesKey("theme")
        private val FINGER = booleanPreferencesKey("finger_drawing")
        private val ONBOARD = booleanPreferencesKey("onboarded")
        private val AUTOSAVE = booleanPreferencesKey("autosave")
        private val SMART = booleanPreferencesKey("smart_shapes")
        private val SNAP = booleanPreferencesKey("snap")
        private val LAST_TOOL = stringPreferencesKey("last_tool")
        private val LAST_COLOR = intPreferencesKey("last_color")
        private val LAST_WIDTH = floatPreferencesKey("last_width")
        private val LAST_OPACITY = floatPreferencesKey("last_opacity")
        private val PRESETS = stringPreferencesKey("brush_presets")
        private val FAVORITE_COLORS = stringPreferencesKey("favorite_colors")
    }
}
