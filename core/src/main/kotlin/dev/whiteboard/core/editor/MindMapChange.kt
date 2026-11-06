package dev.whiteboard.core.editor

import dev.whiteboard.core.model.MindMapPageIndex
import dev.whiteboard.core.model.MindMapTree
import java.util.Collections

/** Immutable semantic portion of the same history command as its Note/Connector deltas. */
class MindMapChange(before: List<MindMapTree>, after: List<MindMapTree>) {
    init {
        require(before.size <= MindMapPageIndex.MAX_TREES && after.size <= MindMapPageIndex.MAX_TREES)
    }
    val before: List<MindMapTree> = Collections.unmodifiableList(before.toList())
    val after: List<MindMapTree> = Collections.unmodifiableList(after.toList())
    val estimatedBytes: Long = 64L + (this.before + this.after).sumOf { it.estimatedBytes }
    init {
        require(before != after) { "Empty semantic change" }
    }
}
