package dev.whiteboard.core.model

/** Groups are deliberately flat; grouping existing groups merges their editable children. */
data class ElementGroup(override val id: String = newId(), val children: List<Element>) : Element {
    init {
        require(children.size in 2..100_000 && children.none { it is ElementGroup })
        require(children.map { it.id }.toSet().size == children.size && children.none { it.id == id })
    }
    override val bounds: Bounds by lazy { children.map { it.bounds }.reduce(Bounds::union) }
    override fun duplicate() = copy(id = newId(), children = children.map { it.duplicate() })
    override fun transform(target: Bounds) = copy(
        children = children.map { element ->
            val b = element.bounds
            val first = Point(b.left, b.top).remap(bounds, target)
            val last = Point(b.right, b.bottom).remap(bounds, target)
            element.transform(Bounds.between(first, last))
        }
    )
}

fun Element.leaves(): List<Element> = if (this is ElementGroup) children else listOf(this)
fun Element.mapLeaves(transform: (Element) -> Element): Element =
    if (this is ElementGroup) copy(children = children.map(transform)) else transform(this)
fun Iterable<Element>.referencedAssets(): Set<String> = flatMap { it.leaves() }.filterIsInstance<MediaElement>().map { it.assetId }.toSet()

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
