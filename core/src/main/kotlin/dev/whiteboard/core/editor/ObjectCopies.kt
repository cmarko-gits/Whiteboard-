package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*

/** Complete identity mapping is allocated before any copied object is constructed. */
data class ObjectCopy(val elements: List<Element>, val identities: Map<String, String>)

object ObjectCopies {
    fun duplicate(
        elements: List<Element>,
        offset: Point = Point(0f, 0f),
        identity: () -> String = ::newId,
        checkpoint: () -> Unit = {}
    ): ObjectCopy {
        require(elements.size <= Page.MAX_ELEMENTS)
        val originals = LinkedHashSet<String>()
        var count = 0
        fun register(element: Element) {
            checkpoint()
            require(originals.add(element.id)) { "Duplicate source object ID" }
        }
        elements.forEach { element ->
            register(element)
            element.renderBounds
            if (element is ElementGroup) {
                count += element.children.size
                element.children.forEach(::register)
            } else {
                count++
            }
            require(count <= Page.MAX_ELEMENTS) { "Too many copied objects" }
        }
        val allocated = HashSet<String>()
        val identities = originals.associateWith {
            checkpoint()
            identity().also { id ->
                require(id.isNotBlank() && id.length <= 128 && id !in originals && allocated.add(id)) { "Invalid copied object ID" }
            }
        }
        val copied = elements.map { original ->
            checkpoint()
            val copy = copyIdentity(original, identities, checkpoint)
            if (offset.x == 0f && offset.y == 0f) {
                copy
            } else {
                ObjectTranslation.translate(copy, offset, checkpoint)
            }.also { it.renderBounds }
        }
        checkpoint()
        return ObjectCopy(copied, identities)
    }

    private fun copyIdentity(element: Element, identities: Map<String, String>, checkpoint: () -> Unit): Element {
        checkpoint()
        val id = identities.getValue(element.id)
        return when (element) {
            is Stroke -> element.copy(id = id)
            is Shape -> element.copy(id = id)
            is Line -> element.copy(id = id)
            is TextElement -> element.copy(id = id)
            is Note -> element.copy(id = id)
            is MediaElement -> element.copy(id = id)
            is AffineElement -> element.copy(source = copyIdentity(element.source, identities, checkpoint))
            is ElementGroup -> element.copy(id = id, children = element.children.map { copyIdentity(it, identities, checkpoint) })
        }
    }
}
