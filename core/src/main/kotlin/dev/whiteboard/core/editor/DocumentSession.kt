package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*
import java.util.ArrayDeque

data class LocatedElement(val index: Int, val element: Element)
data class ElementDelta(val before: LocatedElement?, val after: LocatedElement?) {
    init {
        require(before != null || after != null)
        require(before == null || after == null || before.element.id == after.element.id)
    }
}
interface DocumentChange {
    fun apply(board: Board, forward: Boolean): Board
    val estimatedBytes: Long
    val assetIds: Set<String>
}

data class PageListChange(val before: List<Page>, val after: List<Page>, val beforeActive: String, val afterActive: String) :
    DocumentChange {
    override val assetIds = (before + after).flatMap { it.elements }.referencedAssets()
    override val estimatedBytes = (before + after).sumOf { page -> 128L + page.elements.sumOf { it.estimatedBytes() } }
    override fun apply(board: Board, forward: Boolean) =
        board.copy(pages = if (forward) after else before, activePageId = if (forward) afterActive else beforeActive)
}

data class EditCommand(val pageId: String, val deltas: List<ElementDelta>) : DocumentChange {
    override val assetIds = deltas.flatMap { listOfNotNull(it.before?.element, it.after?.element) }
        .referencedAssets()
    override val estimatedBytes = deltas.sumOf {
        (it.before?.element?.estimatedBytes() ?: 0L) + (it.after?.element?.estimatedBytes() ?: 0L) +
            64L
    }
    init {
        require(deltas.isNotEmpty())
    }
    override fun apply(board: Board, forward: Boolean): Board {
        val page = board.pages.first { it.id == pageId }
        val ids = deltas.flatMap { listOfNotNull(it.before?.element?.id, it.after?.element?.id) }.toSet()
        val retained = page.elements.filterNot { it.id in ids }
        val additions = deltas.mapNotNull { if (forward) it.after else it.before }.sortedBy { it.index }
        val ordered = additions.indices.all { index ->
            additions[index].index >= 0 && (index == 0 || additions[index].index > additions[index - 1].index)
        }
        val result = if (ordered) {
            // Normal commands have distinct nonnegative destinations. Merge in one pass instead
            // of shifting the retained array once for every replaced object in a large selection.
            buildList(retained.size + additions.size) {
                var next = 0
                additions.forEachIndexed { count, addition ->
                    val destination = addition.index.coerceAtMost(retained.size + count)
                    while (size < destination) add(retained[next++])
                    add(addition.element)
                }
                while (next < retained.size) add(retained[next++])
            }
        } else {
            retained.toMutableList().apply {
                additions.forEach { add(it.index.coerceIn(0, size), it.element) }
            }.toList()
        }
        return board.withPage(page.copy(elements = result)).copy(activePageId = pageId)
    }
}

/** Owns document mutations. Gesture samples remain outside the history until committed. */
class DocumentSession(initial: Board, private val historyLimit: Int = 150, private val historyByteLimit: Long = 32L * 1024 * 1024) {
    init {
        require(historyLimit > 0 && historyByteLimit > 0)
    }
    private data class Entry(val command: DocumentChange, val before: Long, val after: Long)
    private val undoStack = ArrayDeque<Entry>()
    private val redoStack = ArrayDeque<Entry>()
    private var sequence = 0L
    private var undoBytes = 0L
    var revision = 0L
        private set
    var board = initial
        private set
    var savedRevision = 0L
        private set
    val dirty get() = revision != savedRevision
    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()
    val editBudgetBytes get() = historyByteLimit
    val page get() = board.activePage
    val retainedAssetIds get() = board.referencedAssets() + (undoStack.toList() + redoStack.toList()).flatMap { it.command.assetIds }

    fun execute(command: DocumentChange) {
        val updated = command.apply(board, true)
        val next = ++sequence
        undoStack.addLast(Entry(command, revision, next))
        undoBytes += command.estimatedBytes
        while ((undoStack.size > historyLimit || undoBytes > historyByteLimit) &&
            undoStack.isNotEmpty()
        ) {
            undoBytes -= undoStack.removeFirst().command.estimatedBytes
        }
        redoStack.clear()
        board = updated.copy(updatedAt = System.currentTimeMillis())
        revision = next
    }
    fun add(element: Element) = execute(EditCommand(page.id, listOf(ElementDelta(null, LocatedElement(page.elements.size, element)))))
    fun replace(element: Element) {
        val index = page.elements.indexOfFirst { it.id == element.id }
        require(index >= 0)
        if (page.elements[index] == element) return
        execute(EditCommand(page.id, listOf(ElementDelta(LocatedElement(index, page.elements[index]), LocatedElement(index, element)))))
    }
    fun transformBy(id: String, translation: Point, factor: Float): Boolean {
        require(factor.isFinite() && factor in 0.1f..10f)
        val element = page.elements.firstOrNull { it.id == id } ?: return false
        val bounds = element.selectionBounds
        val halfWidth = (bounds.width * factor / 2).coerceAtLeast(0.05f)
        val halfHeight = (bounds.height * factor / 2).coerceAtLeast(0.05f)
        val x = bounds.center.x + translation.x
        val y = bounds.center.y + translation.y
        replace(element.transform(Bounds(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight)))
        return true
    }
    fun addAll(elements: List<Element>) {
        if (elements.isEmpty()) return
        require(elements.map { it.id }.toSet().size == elements.size)
        val existingIds = page.elements.map { it.id }.toSet()
        require(elements.none { it.id in existingIds })
        execute(
            EditCommand(
                page.id,
                elements.mapIndexed { index, element ->
                    ElementDelta(null, LocatedElement(page.elements.size + index, element))
                }
            )
        )
    }
    fun replaceAll(elements: List<Element>) {
        val replacements = elements.associateBy { it.id }
        val existingIds = page.elements.map { it.id }.toSet()
        require(replacements.size == elements.size && existingIds.containsAll(replacements.keys))
        val deltas = page.elements.mapIndexedNotNull { index, before ->
            replacements[before.id]?.takeIf { it != before }?.let { after ->
                ElementDelta(LocatedElement(index, before), LocatedElement(index, after))
            }
        }
        if (deltas.isNotEmpty()) execute(EditCommand(page.id, deltas))
    }
    fun reorder(ids: Set<String>, front: Boolean): Boolean {
        val selected = page.elements.filter { it.id in ids }
        val rest = page.elements.filterNot { it.id in ids }
        val reordered = if (front) rest + selected else selected + rest
        return applyLayerOrder(reordered)
    }
    fun stepLayer(ids: Set<String>, step: LayerStep): Boolean = applyLayerOrder(LayerOrdering.step(page.elements, ids, step))

    private fun applyLayerOrder(reordered: List<Element>): Boolean {
        if (reordered === page.elements) return false
        val before = page.elements.map(Element::id)
        val after = reordered.map(Element::id)
        if (before == after) return false
        val command = LayerOrderChange(page.id, before, after)
        if (command.estimatedBytes > historyByteLimit) throw DocumentFailure(DocumentError.EDIT_LIMIT)
        execute(command)
        return true
    }
    fun rotate(ids: Set<String>, degrees: Double): Boolean = applySelectionEdit(ObjectRotation.plan(page, ids, degrees, historyByteLimit))

    fun align(ids: Set<String>, alignment: ObjectAlignment): Boolean =
        applySelectionEdit(SelectionAlignment.plan(page, ids, alignment, historyByteLimit))

    fun applySelectionEdit(plan: SelectionEditPlan): Boolean {
        if (page.id != plan.original.id || page.elements !== plan.original.elements || plan.replacements.isEmpty()) return false
        val replacements = plan.replacements.associateBy(Element::id)
        val deltas = page.elements.mapIndexedNotNull { index, before ->
            replacements[before.id]?.takeIf { it != before }?.let { after ->
                ElementDelta(LocatedElement(index, before), LocatedElement(index, after))
            }
        }
        if (deltas.isEmpty()) return false
        val command = EditCommand(page.id, deltas)
        if (command.estimatedBytes > historyByteLimit) throw DocumentFailure(DocumentError.EDIT_LIMIT)
        execute(command)
        return true
    }
    fun group(ids: Set<String>): ElementGroup? {
        val selected = page.elements.mapIndexedNotNull { index, element ->
            if (element.id in ids) LocatedElement(index, element) else null
        }
        if (selected.size < 2) return null
        val group = ElementGroup(children = selected.flatMap { it.element.leaves() })
        val location = selected.last().index - selected.size + 1
        execute(EditCommand(page.id, selected.map { ElementDelta(it, null) } + ElementDelta(null, LocatedElement(location, group))))
        return group
    }
    fun ungroup(ids: Set<String>): Set<String> {
        val changes = mutableListOf<ElementDelta>()
        val selected = mutableSetOf<String>()
        var destination = 0
        page.elements.forEachIndexed { index, element ->
            if (element.id in ids && element is ElementGroup) {
                changes.add(ElementDelta(LocatedElement(index, element), null))
                element.children.forEach { child ->
                    changes.add(ElementDelta(null, LocatedElement(destination++, child)))
                    selected.add(child.id)
                }
            } else {
                destination++
                if (element.id in ids) selected.add(element.id)
            }
        }
        if (changes.isNotEmpty()) execute(EditCommand(page.id, changes))
        return selected
    }
    fun delete(ids: Set<String>) {
        val changes = page.elements.mapIndexedNotNull { index, element ->
            if (element.id in
                ids
            ) {
                ElementDelta(LocatedElement(index, element), null)
            } else {
                null
            }
        }
        if (changes.isNotEmpty()) execute(EditCommand(page.id, changes))
    }
    fun eraseStrokes(ids: Set<String>) {
        val deltas = page.elements.flatMapIndexed { index, element ->
            val after = element.withoutStrokes(ids)
            when {
                after == element -> emptyList()
                after == null -> listOf(ElementDelta(LocatedElement(index, element), null))
                after.id == element.id -> listOf(ElementDelta(LocatedElement(index, element), LocatedElement(index, after)))
                else -> listOf(ElementDelta(LocatedElement(index, element), null), ElementDelta(null, LocatedElement(index, after)))
            }
        }
        if (deltas.isNotEmpty()) execute(EditCommand(page.id, deltas))
    }
    fun eraseParts(cuts: List<StrokeCut>): Boolean {
        if (cuts.isEmpty()) return false
        require(cuts.map { it.original.id }.distinct().size == cuts.size)
        val originals = page.elements.flatMap { it.leaves() }.filterIsInstance<Stroke>().associateBy { it.id }
        if (cuts.any { originals[it.original.id] != it.original }) return false
        val occupied = page.elements.flatMap { listOf(it.id) + it.leaves().map(Element::id) }.toSet()
        val fragmentIds = mutableSetOf<String>()
        cuts.forEach { cut ->
            if (cut.fragments.size > PartialStrokeEraser.MAX_FRAGMENTS) throw DocumentFailure(DocumentError.EDIT_LIMIT)
            require(
                cut.fragments.all { fragment ->
                    fragmentIds.add(fragment.id) && (fragment.id == cut.original.id || fragment.id !in occupied)
                }
            ) { "Stroke fragments must not replace other objects" }
        }
        val replacements = cuts.filter { it.fragments != listOf(it.original) }.associate { it.original.id to it.fragments }
        if (replacements.isEmpty()) return false
        val deltas = mutableListOf<ElementDelta>()
        var destination = 0
        var leaves = 0
        page.elements.forEachIndexed { index, element ->
            val fragments = element.withStrokeFragments(replacements)
            leaves += fragments.sumOf { it.leaves().size }
            if (fragments.size == 1 && fragments.single() === element) {
                destination++
            } else {
                deltas.add(ElementDelta(LocatedElement(index, element), null))
                fragments.forEach { deltas.add(ElementDelta(null, LocatedElement(destination++, it))) }
            }
            if (destination > Page.MAX_ELEMENTS || leaves > Page.MAX_ELEMENTS) throw DocumentFailure(DocumentError.EDIT_LIMIT)
        }
        val command = EditCommand(page.id, deltas)
        // Partial erasing must never silently lose its own Undo entry.
        if (command.estimatedBytes > historyByteLimit) throw DocumentFailure(DocumentError.EDIT_LIMIT)
        execute(command)
        return true
    }
    fun undo() {
        if (undoStack.isEmpty()) return
        val entry = undoStack.removeLast()
        undoBytes -= entry.command.estimatedBytes
        board = entry.command.apply(board, false).copy(updatedAt = System.currentTimeMillis())
        revision = entry.before
        redoStack.addLast(entry)
    }
    fun redo() {
        if (redoStack.isEmpty()) return
        val entry = redoStack.removeLast()
        board = entry.command.apply(board, true).copy(updatedAt = System.currentTimeMillis())
        revision = entry.after
        undoStack.addLast(entry)
        undoBytes += entry.command.estimatedBytes
    }
    fun markSaved(revision: Long) {
        savedRevision = revision
    }
    fun markRecovered() {
        savedRevision = -1L
    }
    fun updateViewport(viewport: Viewport) {
        board = board.withPage(page.copy(viewport = viewport))
    }
    fun selectPage(id: String) {
        require(board.pages.any { it.id == id })
        board = board.copy(activePageId = id)
    }
    fun addPage() {
        require(board.pages.size < 1000)
        val added = Page(name = "Page ${board.pages.size + 1}")
        changePages(board.pages + added, added.id)
    }
    fun addPages(pages: List<Page>) {
        require(pages.isNotEmpty() && board.pages.size + pages.size <= 1000)
        val existing = if (board.pages.size == 1 && page.elements.isEmpty()) emptyList() else board.pages
        changePages(existing + pages, pages.first().id)
    }
    fun duplicatePage(id: String) {
        require(board.pages.size < 1000)
        val original = board.pages.first { it.id == id }
        val added = original.copy(
            id = newId(),
            name = "${original.name.take(115)} copy",
            elements = original.elements.map {
                it.duplicate()
            }
        )
        val pages = board.pages.toMutableList().apply { add(indexOfFirst { it.id == id } + 1, added) }
        changePages(pages, added.id)
    }
    fun deletePage(id: String) {
        require(board.pages.size > 1) { "Keep at least one page" }
        val remaining = board.pages.filterNot { it.id == id }
        changePages(remaining, if (board.activePageId == id) remaining.first().id else board.activePageId)
    }
    fun renamePage(id: String, name: String) {
        require(name.isNotBlank() && name.length <= 120)
        changePages(board.pages.map { if (it.id == id) it.copy(name = name.trim()) else it }, board.activePageId)
    }
    fun movePage(id: String, delta: Int) {
        val pages = board.pages.toMutableList()
        val index = pages.indexOfFirst { it.id == id }
        require(index >= 0)
        val destination = (index + delta).coerceIn(0, pages.lastIndex)
        if (index == destination) return
        pages.add(destination, pages.removeAt(index))
        changePages(pages, board.activePageId)
    }
    fun background(pattern: Background, color: Int) {
        if (page.background == pattern && page.backgroundColor == color) return
        changePages(board.pages.map { if (it.id == page.id) it.copy(background = pattern, backgroundColor = color) else it }, page.id)
    }
    fun fill(position: Point, color: Int) {
        val opaque = color or 0xFF000000.toInt()
        when (val target = FillEngine.target(page, position)) {
            FillEngine.Target.Background -> background(page.background, opaque)
            FillEngine.Target.Blocked -> Unit
            is FillEngine.Target.Surface -> replace(
                target.container.mapLeaves { leaf ->
                    when {
                        leaf.id != target.leafId -> leaf
                        leaf is Shape -> leaf.copy(fill = opaque)
                        leaf is Note -> leaf.copy(color = opaque)
                        else -> leaf
                    }
                }
            )
        }
    }
    private fun changePages(pages: List<Page>, active: String) = execute(PageListChange(board.pages, pages, board.activePageId, active))
}
