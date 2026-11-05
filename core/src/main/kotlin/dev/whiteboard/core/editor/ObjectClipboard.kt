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
        val nextCount = pasteCount % 20 + 1
        val offset = 24f * nextCount
        val copied = ObjectCopies.duplicate(elements, Point(offset, offset)).elements
        pasteCount = nextCount
        return copied
    }
}
