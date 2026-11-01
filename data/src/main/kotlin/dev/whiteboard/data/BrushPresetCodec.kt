package dev.whiteboard.data

import dev.whiteboard.core.model.*
import org.json.JSONArray
import org.json.JSONObject

class BrushPresetCodec {
    fun encode(presets: List<BrushPreset>): String {
        require(presets.size <= 12)
        return JSONArray().apply {
            presets.forEach { preset ->
                put(
                    JSONObject().apply {
                        put("id", preset.id)
                        put("name", preset.name)
                        put("tool", preset.tool.name)
                        put("color", preset.ink.color)
                        put("width", preset.ink.width)
                        put("opacity", preset.ink.opacity)
                    }
                )
            }
        }.toString()
    }
    fun decode(value: String?): List<BrushPreset> {
        if (value == null) return defaultBrushPresets
        return try {
            require(value.length < 16_384)
            val array = JSONArray(value)
            require(array.length() <= 12)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                BrushPreset(
                    item.getString("id"),
                    item.getString("name"),
                    Tool.valueOf(item.getString("tool")),
                    InkStyle(item.getInt("color"), item.getDouble("width").toFloat(), item.getDouble("opacity").toFloat())
                )
            }
        } catch (_: Exception) {
            defaultBrushPresets
        }
    }
}
