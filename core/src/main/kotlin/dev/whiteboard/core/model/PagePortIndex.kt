package dev.whiteboard.core.model

/** Immutable world port geometry for one element-list snapshot, independent of viewport or Android. */
class PagePortIndex internal constructor(private val pageId: String, private val elements: List<PageObject>, checkpoint: () -> Unit) {
    constructor(page: Page, checkpoint: () -> Unit = {}) : this(page.id, page.elements, checkpoint)

    private val targets: Map<String, Target> = buildMap {
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

    fun matches(page: Page): Boolean = page.id == pageId && page.elements === elements
    fun supports(id: String): Boolean = id in targets

    fun point(id: String, port: ConnectionPort): Point {
        val target = requireNotNull(targets[id]) { "Missing or unsupported connection target" }
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

    private fun target(element: PageObject): Target? {
        if (element !is Element || element is Stroke || element is Line || !isValidConnectionId(element.id)) return null
        // Validate each supported frame once, rather than rescanning a large group for every edge.
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
