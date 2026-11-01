package dev.whiteboard.data

import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class BrushPresetCodecTest {
    @Test fun `favorite tools retain all drawing properties`() {
        val presets = listOf(BrushPreset(name = "Moj marker", tool = Tool.MARKER, ink = InkStyle(0xFF12A3CC.toInt(), 17f, 0.6f)))
        assertEquals(presets, BrushPresetCodec().decode(BrushPresetCodec().encode(presets)))
    }

    @Test fun `damaged preferences do not prevent startup`() {
        assertEquals(defaultBrushPresets, BrushPresetCodec().decode("not valid json"))
    }

    @Test fun `removing all presets is preserved`() {
        assertEquals(emptyList<BrushPreset>(), BrushPresetCodec().decode("[]"))
    }
}
