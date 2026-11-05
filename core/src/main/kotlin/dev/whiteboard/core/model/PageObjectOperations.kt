package dev.whiteboard.core.model

fun PageObject.leaves(): List<PageObject> = if (this is Element) leaves() else listOf(this)
fun PageObject.content(): PageObject = if (this is Element) content() else this

fun PageObject.withContent(updated: PageObject): PageObject {
    require(updated.id == id)
    return when (this) {
        is Element -> {
            require(updated is Element)
            withContent(updated)
        }
        is Connector -> {
            require(updated is Connector)
            updated
        }
    }
}

fun PageObject.mapLeaves(transform: (PageObject) -> PageObject): PageObject = when (this) {
    is Connector -> transform(this)
    is Element -> mapLocalLeaves { leaf ->
        val result = transform(leaf)
        require(result is Element) { "A connection cannot be a group child or affine source" }
        result
    }
}

fun PageObject.withoutStrokes(ids: Set<String>): PageObject? = if (this is Element) withoutStrokes(ids) else this
fun PageObject.withStrokeFragments(replacements: Map<String, List<Stroke>>): List<PageObject> =
    if (this is Element) withStrokeFragments(replacements) else listOf(this)

fun PageObject.estimatedBytes(): Long = when (this) {
    is Element -> estimatedBytes()
    is Connector ->
        192L + id.length * 2L +
            listOf(start, end).sumOf { if (it is ConnectorEnd.Bound) 64L + it.targetId.length * 2L else 32L }
}

val PageObject.canTransformIndependently: Boolean
    get() = this !is Connector || (start is ConnectorEnd.Free && end is ConnectorEnd.Free)

fun Iterable<PageObject>.allObjectIds(): Set<String> = buildSet {
    this@allObjectIds.forEach { element ->
        add(element.id)
        if (element is ElementGroup) element.children.forEach { add(it.id) }
    }
}

fun List<PageObject>.canTransformTogether(): Boolean {
    val bound = filterIsInstance<Connector>().flatMap { listOf(it.start, it.end) }.filterIsInstance<ConnectorEnd.Bound>()
    if (bound.isEmpty()) return true
    val ids = allObjectIds()
    return bound.all { it.targetId in ids }
}
