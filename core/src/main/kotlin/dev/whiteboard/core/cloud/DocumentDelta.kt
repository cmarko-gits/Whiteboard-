package dev.whiteboard.core.cloud

import dev.whiteboard.core.model.*

data class ValueChange<T>(val expected: T, val value: T)
data class ObjectMutation(val id: String, val expectedFingerprint: String?, val value: PageObject?) {
    init {
        require(id.length in 1..128 && (value != null || expectedFingerprint != null))
        require(value == null || value.id == id)
        require(expectedFingerprint == null || expectedFingerprint.matches(Regex("[a-f0-9]{64}")))
    }
}
data class PageMutation(
    val id: String,
    val created: Page? = null,
    val deleted: Boolean = false,
    val name: ValueChange<String>? = null,
    val background: ValueChange<Background>? = null,
    val color: ValueChange<Int>? = null,
    val objects: List<ObjectMutation> = emptyList(),
    val order: List<String>? = null
) {
    init {
        require(id.length in 1..128)
        require(objects.size <= 100_000 && objects.map { it.id }.toSet().size == objects.size)
        require(created == null || created.id == id)
        require(!(deleted && created != null))
        if (deleted || created != null) require(name == null && background == null && color == null && objects.isEmpty() && order == null)
        require(order == null || (order.size <= 100_000 && order.toSet().size == order.size))
    }
}
data class DocumentDelta(
    val name: ValueChange<String>? = null,
    val pages: List<PageMutation> = emptyList(),
    val order: List<String>? = null
) {
    init {
        require(pages.size <= 1000 && pages.map { it.id }.toSet().size == pages.size)
        require(order == null || (order.size <= 1000 && order.toSet().size == order.size))
    }
    val empty get() = name == null && pages.isEmpty() && order == null
}

data class DocumentTombstones(val pages: Set<String> = emptySet(), val objects: Map<String, Set<String>> = emptyMap())
data class DeltaResult(val board: Board, val tombstones: DocumentTombstones)
enum class DeltaConflictReason { OBJECT_MODIFIED, OBJECT_DELETED, PAGE_DELETED, ID_REUSED, METADATA_MODIFIED, LAST_PAGE }
class DeltaConflict(val reason: DeltaConflictReason, val targetId: String) : Exception(reason.name)
fun interface ElementFingerprint {
    fun of(element: PageObject): String
}

/** Atomic object operations: independent edits merge; conflicting edits require review rather than data replacement. */
class DeltaEngine(private val fingerprints: ElementFingerprint) {
    fun between(before: Board, after: Board): DocumentDelta {
        require(before.id == after.id)
        val oldPages = before.pages.associateBy { it.id }
        val newPages = after.pages.associateBy { it.id }
        val changes = mutableListOf<PageMutation>()
        before.pages.filter { it.id !in newPages }.forEach { changes += PageMutation(it.id, deleted = true) }
        after.pages.forEach { page ->
            val previous = oldPages[page.id]
            if (previous == null) {
                changes += PageMutation(page.id, created = page.copy(viewport = Viewport()))
            } else {
                val oldObjects = previous.elements.associateBy { it.id }
                val newObjects = page.elements.associateBy { it.id }
                val objects = mutableListOf<ObjectMutation>()
                previous.elements.filter { it.id !in newObjects }.forEach { objects += ObjectMutation(it.id, fingerprints.of(it), null) }
                page.elements.filter { it != oldObjects[it.id] }.forEach {
                    objects += ObjectMutation(it.id, oldObjects[it.id]?.let(fingerprints::of), it)
                }
                val mutation = PageMutation(
                    page.id,
                    name = changed(previous.name, page.name),
                    background = changed(previous.background, page.background),
                    color = changed(previous.backgroundColor, page.backgroundColor),
                    objects = objects,
                    order = changedOrder(previous.elements.map { it.id }, page.elements.map { it.id })
                )
                if (mutation.name != null || mutation.background != null || mutation.color != null || objects.isNotEmpty() ||
                    mutation.order != null
                ) {
                    changes += mutation
                }
            }
        }
        return DocumentDelta(changed(before.name, after.name), changes, changedOrder(before.pages.map { it.id }, after.pages.map { it.id }))
    }

    fun apply(current: Board, delta: DocumentDelta, tombstones: DocumentTombstones = DocumentTombstones()): DeltaResult {
        val pages = current.pages.associateByTo(linkedMapOf()) { it.id }
        val removedPages = tombstones.pages.toMutableSet()
        val removedObjects = tombstones.objects.toMutableMap()
        delta.pages.forEach { mutation ->
            when {
                mutation.deleted -> {
                    if (pages.remove(mutation.id) == null) conflict(DeltaConflictReason.PAGE_DELETED, mutation.id)
                    removedPages += mutation.id
                    removedObjects.remove(mutation.id)
                }
                mutation.created != null -> {
                    if (mutation.id in pages || mutation.id in removedPages) conflict(DeltaConflictReason.ID_REUSED, mutation.id)
                    pages[mutation.id] = mutation.created.copy(viewport = Viewport())
                }
                else -> {
                    val previous = pages[mutation.id] ?: conflict(DeltaConflictReason.PAGE_DELETED, mutation.id)
                    val objects = previous.elements.associateByTo(linkedMapOf()) { it.id }
                    mutation.objects.forEach { change ->
                        val existing = objects[change.id]
                        when {
                            change.value == null -> {
                                // Structural regrouping needs review; never acknowledge deletion of an object that is no longer top-level.
                                if (existing == null) conflict(DeltaConflictReason.OBJECT_DELETED, change.id)
                                objects.remove(change.id) // Deletion wins against an intervening edit of the same object.
                            }
                            change.expectedFingerprint == null -> {
                                if (existing != null) conflict(DeltaConflictReason.ID_REUSED, change.id)
                                objects[change.id] = change.value
                            }
                            existing == null -> conflict(DeltaConflictReason.OBJECT_DELETED, change.id)
                            fingerprints.of(existing) != change.expectedFingerprint && existing != change.value ->
                                conflict(DeltaConflictReason.OBJECT_MODIFIED, change.id)
                            else -> objects[change.id] = change.value
                        }
                    }
                    val oldIds = previous.elements.allIds()
                    val newIds = objects.values.allIds()
                    val retired = removedObjects[mutation.id].orEmpty()
                    (newIds - oldIds).firstOrNull { it in retired }?.let { conflict(DeltaConflictReason.ID_REUSED, it) }
                    removedObjects[mutation.id] = retired + (oldIds - newIds)
                    pages[mutation.id] = previous.copy(
                        name = merge(previous.name, mutation.name, mutation.id),
                        background = merge(previous.background, mutation.background, mutation.id),
                        backgroundColor = merge(previous.backgroundColor, mutation.color, mutation.id),
                        elements = reorder(objects, mutation.order)
                    )
                }
            }
        }
        if (pages.isEmpty()) conflict(DeltaConflictReason.LAST_PAGE, current.id)
        val ordered = reorder(pages, delta.order)
        return DeltaResult(
            current.copy(
                name = merge(current.name, delta.name, current.id),
                pages = ordered,
                activePageId = current.activePageId.takeIf { it in pages } ?: ordered.first().id
            ),
            DocumentTombstones(removedPages.toSet(), removedObjects.toMap())
        )
    }

    private fun <T> merge(current: T, change: ValueChange<T>?, id: String): T {
        if (change == null) return current
        if (current != change.expected && current != change.value) conflict(DeltaConflictReason.METADATA_MODIFIED, id)
        return change.value
    }
    private fun <T> changed(before: T, after: T): ValueChange<T>? = if (before == after) null else ValueChange(before, after)
    private fun changedOrder(before: List<String>, after: List<String>): List<String>? {
        val oldIds = before.toSet()
        val newIds = after.toSet()
        val natural = before.filter { it in newIds } + after.filter { it !in oldIds }
        return after.takeUnless { it == natural }
    }
    private fun <T> reorder(values: Map<String, T>, order: List<String>?): List<T> {
        if (order == null) return values.values.toList()
        val requested = order.toSet()
        return order.mapNotNull(values::get) + values.filterKeys { it !in requested }.values
    }
    private fun Iterable<PageObject>.allIds(): Set<String> = flatMap {
        if (it is ElementGroup) listOf(it.id) + it.children.map { child -> child.id } else listOf(it.id)
    }.toSet()
    private fun conflict(reason: DeltaConflictReason, id: String): Nothing = throw DeltaConflict(reason, id)
}
