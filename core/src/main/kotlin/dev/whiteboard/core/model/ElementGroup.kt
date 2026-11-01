package dev.whiteboard.core.model

/** Groups are deliberately flat; grouping existing groups merges their editable children. */
data class ElementGroup(override val id: String = newId(), val children: List<Element>) : Element {
    init {
        require(children.size in 2..Page.MAX_ELEMENTS && children.none { it is ElementGroup })
        require(children.map { it.id }.toSet().size == children.size && children.none { it.id == id })
    }
    override val bounds: Bounds by lazy { children.map { it.selectionBounds }.reduce(Bounds::union) }
    override fun duplicate() = copy(id = newId(), children = children.map { it.duplicate() })
    override fun transform(target: Bounds) = copy(
        children = children.map { element ->
            val b = element.selectionBounds
            val first = Point(b.left, b.top).remap(bounds, target)
            val last = Point(b.right, b.bottom).remap(bounds, target)
            element.transform(Bounds.between(first, last))
        }
    )
}

fun Element.leaves(): List<Element> = if (this is ElementGroup) children else listOf(this)
fun Element.mapLeaves(transform: (Element) -> Element): Element = when (this) {
    is ElementGroup -> copy(children = children.map { it.mapLeaves(transform) })
    is AffineElement -> withContent(transform(source))
    else -> transform(this)
}
fun Iterable<Element>.referencedAssets(): Set<String> = flatMap { it.leaves() }.map { it.content() }
    .filterIsInstance<MediaElement>().map { it.assetId }.toSet()

fun Element.withoutStrokes(ids: Set<String>): Element? = if (ids.isEmpty()) {
    this
} else {
    when (this) {
        is ElementGroup -> {
            val remaining = children.filterNot { it is Stroke && it.id in ids }
            when (remaining.size) {
                0 -> null
                1 -> remaining.single()
                else -> if (remaining == children) this else copy(children = remaining)
            }
        }
        is Stroke -> takeUnless { id in ids }
        else -> this
    }
}

/** Retains z-order and existing group identity; zero/one-child groups collapse normally. */
fun Element.withStrokeFragments(replacements: Map<String, List<Stroke>>): List<Element> = when (this) {
    is Stroke -> replacements[id] ?: listOf(this)
    is ElementGroup -> {
        if (children.none { it is Stroke && it.id in replacements }) {
            listOf(this)
        } else {
            val remaining = children.flatMap { if (it is Stroke) replacements[it.id] ?: listOf(it) else listOf(it) }
            if (remaining.size > Page.MAX_ELEMENTS) throw DocumentFailure(DocumentError.EDIT_LIMIT)
            when (remaining.size) {
                0 -> emptyList()
                1 -> listOf(remaining.single())
                else -> listOf(copy(children = remaining))
            }
        }
    }
    else -> listOf(this)
}
