package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*

/** Private application clipboard. Immutable objects remain editable; no system clipboard access. */
class ObjectClipboard {
    private var source = Page()
    private var pasteCount = 0
    val available get() = source.elements.isNotEmpty()
    val assetIds get() = source.elements.referencedAssets()
    fun copy(elements: List<PageObject>) {
        source = Page(elements = elements.toList())
        pasteCount = 0
    }
    fun copy(page: Page, ids: Set<String>) {
        val copied = ConnectionCopies.duplicate(page, page.elements.filterIsInstance<Connector>(), ids)
        source = Page(elements = copied.objects)
        pasteCount = 0
    }
    fun paste(): List<PageObject> {
        if (!available) return emptyList()
        val nextCount = pasteCount % 20 + 1
        val offset = 24f * nextCount
        val copied = ConnectionCopies.duplicate(
            source,
            source.elements.filterIsInstance<Connector>(),
            source.elements.map { it.id }.toSet(),
            Point(offset, offset)
        ).objects
        pasteCount = nextCount
        return copied
    }
}
