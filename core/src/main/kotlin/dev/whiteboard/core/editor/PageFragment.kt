package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*
import java.util.Collections

/** Complete editable clipboard value; topology must travel with its canonical objects. */
class PageFragment(objects: List<PageObject>, mindMaps: List<MindMapTree> = emptyList()) {
    init {
        require(objects.size <= Page.MAX_ELEMENTS && mindMaps.size <= MindMapPageIndex.MAX_TREES)
    }
    val objects: List<PageObject> = Collections.unmodifiableList(objects.toList())
    val mindMaps: List<MindMapTree>
    val estimatedBytes: Long
    internal val page: Page

    init {
        val validated = Page(elements = this.objects, mindMaps = mindMaps)
        validated.contentBounds
        page = validated
        this.mindMaps = validated.mindMaps
        estimatedBytes = 128L + this.objects.sumOf { it.estimatedBytes() } + this.mindMaps.sumOf { it.estimatedBytes }
    }
}
