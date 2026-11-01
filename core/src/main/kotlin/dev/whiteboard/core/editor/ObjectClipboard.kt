package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*

/** Private application clipboard. Immutable objects remain editable; no system clipboard access. */
class ObjectClipboard {
    private var elements = emptyList<Element>()
    private var pasteCount = 0
    val available get() = elements.isNotEmpty()
    val assetIds get() = elements.referencedAssets()
    fun copy(elements: List<Element>) {
        this.elements = elements.toList()
        pasteCount = 0
    }
    fun paste(): List<Element> {
        if (!available) return emptyList()
        pasteCount = pasteCount % 20 + 1
        val offset = 24f * pasteCount
        return elements.map { element ->
            val bounds = element.selectionBounds
            element.duplicate().transform(Bounds(bounds.left + offset, bounds.top + offset, bounds.right + offset, bounds.bottom + offset))
        }
    }
}
