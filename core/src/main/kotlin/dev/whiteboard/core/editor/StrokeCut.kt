package dev.whiteboard.core.editor

import dev.whiteboard.core.model.Stroke

/** Original snapshot prevents a delayed gesture from replacing a subsequently edited stroke. */
data class StrokeCut(val original: Stroke, val fragments: List<Stroke>) {
    init {
        require(fragments.map { it.id }.distinct().size == fragments.size)
        require(fragments.all { it.style == original.style && it.tool == original.tool })
    }
}
