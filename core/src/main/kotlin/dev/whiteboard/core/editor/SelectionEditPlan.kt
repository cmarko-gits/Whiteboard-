package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*

/** Computed off the UI thread; application must recheck the original page and element list. */
data class SelectionEditPlan(val original: Page, val replacements: List<Element>) {
    init {
        require(replacements.map(Element::id).distinct().size == replacements.size)
        val ids = original.elements.map(Element::id).toSet()
        require(replacements.all { it.id in ids })
    }
}
