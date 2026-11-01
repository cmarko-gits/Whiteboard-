package dev.whiteboard.core.model

data class BrushPreset(val id: String = newId(), val name: String, val tool: Tool, val ink: InkStyle) {
    init {
        require(name.isNotBlank() && name.length <= 40)
        require(tool in DRAWING_TOOLS)
    }
    companion object {
        val DRAWING_TOOLS = setOf(Tool.PEN, Tool.PENCIL, Tool.MARKER, Tool.HIGHLIGHTER)
    }
}

val defaultBrushPresets = listOf(
    BrushPreset("black-pen", "Black pen", Tool.PEN, InkStyle()),
    BrushPreset("blue-pen", "Blue pen", Tool.PEN, InkStyle(color = 0xFF3D6EC4.toInt())),
    BrushPreset("red-marker", "Red marker", Tool.MARKER, InkStyle(color = 0xFFBE544E.toInt(), width = 10f)),
    BrushPreset(
        "yellow-highlight",
        "Yellow highlighter",
        Tool.HIGHLIGHTER,
        InkStyle(color = 0xFFFFD54F.toInt(), width = 22f, opacity = 0.35f)
    )
)
