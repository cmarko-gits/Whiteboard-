package dev.whiteboard.core.model

/** Immutable world port geometry for one element-list snapshot, independent of viewport or Android. */
class PagePortIndex private constructor(
    private val pageId: String,
    private val elements: List<PageObject>,
    private val targets: (String) -> Target?
) {
    internal constructor(pageId: String, elements: List<PageObject>, checkpoint: () -> Unit) :
        this(pageId, elements, lookup(elements, checkpoint))
    constructor(page: Page, checkpoint: () -> Unit = {}) : this(page.id, page.elements, checkpoint)

    /** The caller has checked stable target identities/kinds; the overlay never changes membership. */
    internal fun replacing(elements: List<PageObject>, replacements: List<Element>, checkpoint: () -> Unit): PagePortIndex {
        val changed = buildMap {
            replacements.forEach { element ->
                checkpoint()
                target(element)?.let { put(element.id, it) }
                if (element is ElementGroup) {
                    element.children.forEach { child ->
                        checkpoint()
                        target(child)?.let { put(child.id, it) }
                    }
                }
            }
        }
        checkpoint()
        return PagePortIndex(pageId, elements) { id -> changed[id] ?: targets(id) }
    }

    private companion object {
        fun lookup(elements: List<PageObject>, checkpoint: () -> Unit): (String) -> Target? {
            val indexed = buildMap {
                elements.forEach { element ->
                    checkpoint()
                    target(element)?.let { put(element.id, it) }
                    if (element is ElementGroup) {
                        element.children.forEach { child ->
                            checkpoint()
                            target(child)?.let { put(child.id, it) }
                        }
                    }
                }
            }
            return indexed::get
        }

        fun target(element: PageObject): Target? {
            if (element !is Element || element is Stroke || element is Line || !isValidConnectionId(element.id)) return null
            element.renderBounds
            return when (element) {
                is Shape -> Target(element.bounds, triangle = element.kind == Tool.TRIANGLE)
                is Note, is TextElement -> Target(element.bounds)
                is ElementGroup -> Target(element.selectionBounds)
                is MediaElement -> Target(
                    element.bounds,
                    placement = AffineTransform2D.rotation(element.rotation.toDouble(), element.bounds.center)
                )
                is AffineElement -> Target(
                    element.source.bounds,
                    triangle = (element.source as? Shape)?.kind == Tool.TRIANGLE,
                    placement = element.placement
                )
                is Stroke, is Line -> null
            }
        }
    }

    fun matches(page: Page): Boolean = page.id == pageId && page.elements === elements
    fun supports(id: String): Boolean = targets(id) != null

    fun point(id: String, port: ConnectionPort): Point {
        val target = requireNotNull(targets(id)) { "Missing or unsupported connection target" }
        val frame = target.frame
        val triangleInset = if (target.triangle) frame.width / 4 else 0f
        val local = when (port) {
            ConnectionPort.LEFT -> Point(frame.left + triangleInset, frame.center.y)
            ConnectionPort.TOP -> Point(frame.center.x, frame.top)
            ConnectionPort.RIGHT -> Point(frame.right - triangleInset, frame.center.y)
            ConnectionPort.BOTTOM -> Point(frame.center.x, frame.bottom)
            ConnectionPort.CENTER -> frame.center
        }
        return target.placement.map(local).also { Bounds(it.x, it.y, it.x, it.y) }
    }

    private data class Target(
        val frame: Bounds,
        val triangle: Boolean = false,
        val placement: AffineTransform2D = AffineTransform2D.IDENTITY
    )
}
