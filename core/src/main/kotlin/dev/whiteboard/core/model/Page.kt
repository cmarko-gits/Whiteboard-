package dev.whiteboard.core.model

import kotlin.math.max
import kotlin.math.min

/**
 * Immutable page value. Content validation and world geometry belong to its element-list snapshot;
 * changing viewport, name or background reuses that snapshot without scanning the document again.
 * As with the other document values, supplied element lists must not be mutated after construction.
 */
class Page private constructor(
    val name: String,
    val background: Background,
    val backgroundColor: Int,
    val viewport: Viewport,
    internal val content: PageContent,
    maps: List<MindMapTree>,
    sharedIndex: MindMapPageIndex? = null,
    checkpoint: () -> Unit = {}
) {
    constructor(
        id: String = newId(),
        name: String = "Page 1",
        background: Background = Background.DOTS,
        backgroundColor: Int = 0xFFFAFBF7.toInt(),
        elements: List<PageObject> = emptyList(),
        viewport: Viewport = Viewport(),
        mindMaps: List<MindMapTree> = emptyList()
    ) : this(name, background, backgroundColor, viewport, PageContent(id, elements), mindMaps)

    val mindMapIndex: MindMapPageIndex? = if (maps.isEmpty()) {
        null
    } else if (sharedIndex != null && sharedIndex.trees === maps) {
        if (sharedIndex.matchesGeometry(this)) {
            sharedIndex
        } else {
            sharedIndex.rebindPreview(this, checkpoint)
                ?: MindMapPageIndex(this, maps, checkpoint)
        }
    } else {
        MindMapPageIndex(this, maps, checkpoint)
    }
    val mindMaps: List<MindMapTree> get() = mindMapIndex?.trees ?: emptyList()

    val id: String get() = content.pageId
    val elements: List<PageObject> get() = content.elements
    val geometry: PageGeometry by lazy { PageGeometry(this) }
    val contentBounds: Bounds? get() = content.bounds

    fun copy(
        id: String = this.id,
        name: String = this.name,
        background: Background = this.background,
        backgroundColor: Int = this.backgroundColor,
        elements: List<PageObject> = this.elements,
        viewport: Viewport = this.viewport,
        mindMaps: List<MindMapTree> = this.mindMaps
    ): Page = Page(
        name,
        background,
        backgroundColor,
        viewport,
        if (id == this.id && elements === this.elements) content else PageContent(id, elements),
        mindMaps,
        mindMapIndex
    )

    internal fun withPreview(elements: List<PageObject>, checkpoint: () -> Unit): Page = Page(
        name,
        background,
        backgroundColor,
        viewport,
        PageContent.preview(content, elements, checkpoint),
        mindMaps,
        mindMapIndex,
        checkpoint
    )

    /** Geometry and semantic identity; names, background and camera are independent metadata. */
    fun sharesSceneWith(other: Page): Boolean = content === other.content && mindMapIndex === other.mindMapIndex

    operator fun component1(): String = id
    operator fun component2(): String = name
    operator fun component3(): Background = background
    operator fun component4(): Int = backgroundColor
    operator fun component5(): List<PageObject> = elements
    operator fun component6(): Viewport = viewport
    operator fun component7(): List<MindMapTree> = mindMaps

    override fun equals(other: Any?): Boolean = this === other || other is Page &&
        id == other.id && name == other.name && background == other.background && backgroundColor == other.backgroundColor &&
        elements == other.elements && viewport == other.viewport && mindMaps == other.mindMaps

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + background.hashCode()
        result = 31 * result + backgroundColor
        result = 31 * result + elements.hashCode()
        result = 31 * result + viewport.hashCode()
        return 31 * result + mindMaps.hashCode()
    }

    override fun toString(): String =
        "Page(id=$id, name=$name, background=$background, backgroundColor=$backgroundColor, elements=$elements, viewport=$viewport, mindMaps=$mindMaps)"

    companion object {
        const val MAX_ELEMENTS = 100_000
    }
}

/** No page facade or mutable global cache is retained by this shared, content-owned snapshot. */
internal class PageContent private constructor(
    val pageId: String,
    val elements: List<PageObject>,
    private val previewState: PreviewState?
) {
    constructor(pageId: String, elements: List<PageObject>) : this(pageId, elements, null)
    private val ids: Set<String>
    val objectCount: Int
    val connections: Map<String, Connector>

    init {
        require(elements.size <= Page.MAX_ELEMENTS)
        if (previewState != null) {
            ids = previewState.source.ids
            objectCount = previewState.source.objectCount
            connections = previewState.source.connections
        } else {
            val occupied = HashSet<String>()
            var count = 0
            val targets = HashSet<String>()
            val edges = LinkedHashMap<String, Connector>()
            fun target(element: PageObject) {
                if (element is Element && element !is Stroke && element !is Line) targets.add(element.id)
            }
            elements.forEach { element ->
                require(occupied.add(element.id)) { "Duplicate page object ID" }
                target(element)
                if (element is Connector) edges[element.id] = element
                if (element is ElementGroup) {
                    count += element.children.size
                    element.children.forEach {
                        require(occupied.add(it.id)) { "Duplicate page object ID" }
                        target(it)
                    }
                } else {
                    count++
                }
                require(count <= Page.MAX_ELEMENTS) { "Too many page objects" }
            }
            edges.values.forEach { edge ->
                listOf(edge.start, edge.end).filterIsInstance<ConnectorEnd.Bound>().forEach {
                    require(it.targetId in targets) { "Missing or unsupported connection target" }
                }
            }
            objectCount = count
            connections = edges
            ids = occupied
        }
    }

    fun containsId(id: String): Boolean = id in ids

    internal fun isPreviewOf(source: PageContent): Boolean = previewState?.source === source

    private data class RenderFrame(val definition: PageObject, val bounds: Bounds)
    private class RenderFrames(private val entries: Map<String, RenderFrame>, private val fallback: RenderFrames? = null) {
        fun find(element: PageObject): Bounds? {
            val frame = entries[element.id]
            return if (frame?.definition === element) frame.bounds else fallback?.find(element)
        }
    }
    private data class PreviewState(
        val source: PageContent,
        val ports: PagePortIndex,
        val lines: Map<String, Line>,
        val frames: RenderFrames,
        val bounds: Bounds?
    )

    @Volatile private var renderFrames: RenderFrames? = previewState?.frames
    val bounds: Bounds? by lazy {
        if (previewState != null) return@lazy previewState.bounds
        // Unconnected pages do not pay for a cache used only by connected previews.
        val prepared = if (connections.isNotEmpty()) LinkedHashMap<String, RenderFrame>() else null
        val result = frameUnion(elements) { element ->
            val frame = renderBounds(element)
            prepared?.put(element.id, RenderFrame(element, frame))
            frame
        }
        // A failed complete validation never publishes a partially prepared cache.
        if (prepared != null) renderFrames = RenderFrames(prepared)
        result
    }
    private val connectionGeometry by lazy { PageConnectionGeometry(this, connections.values.toList()) }

    fun resolve(element: PageObject): Element = when (element) {
        is Element -> element
        is Connector -> {
            require(connections[element.id] == element) { "Connection belongs to a different page snapshot" }
            previewState?.let { it.lines[element.id] ?: it.source.resolve(element) } ?: connectionGeometry.line(element.id)
        }
    }

    fun renderBounds(element: PageObject): Bounds {
        if (element is Connector) require(connections[element.id] == element) { "Connection belongs to a different page snapshot" }
        return renderFrames?.find(element) ?: resolve(element).renderBounds
    }

    @Volatile private var ports: PagePortIndex? = previewState?.ports

    fun connectionPorts(checkpoint: () -> Unit): PagePortIndex {
        checkpoint()
        ports?.let { return it }
        // Cancellation or invalid geometry leaves no partial cache. Concurrent readers may prepare
        // independent candidates, but only a complete index is published under this short lock.
        val prepared = PagePortIndex(pageId, elements, checkpoint)
        checkpoint()
        return synchronized(this) { ports ?: prepared.also { ports = it } }
    }

    companion object {
        fun preview(source: PageContent, elements: List<PageObject>, checkpoint: () -> Unit): PageContent {
            checkpoint()
            val frames = source.renderFrames
            // Cold/structural scenes keep the full validation path. A preview never retains
            // another preview, and an invalid original can still be repaired by a full rebuild.
            if (source.previewState != null || frames == null || source.connections.isEmpty() || elements.size != source.elements.size) {
                return PageContent(source.pageId, elements)
            }
            val changed = ArrayList<Element>()
            for (index in elements.indices) {
                checkpoint()
                val before = source.elements[index]
                val after = elements[index]
                if (before !== after) {
                    if (!stableReplacement(before, after)) return PageContent(source.pageId, elements)
                    if (after is Element) changed.add(after)
                }
            }
            val ports = source.connectionPorts(checkpoint).replacing(elements, changed, checkpoint)
            val targets = changed.allObjectIds()
            val lines = LinkedHashMap<String, Line>()
            val updatedFrames = LinkedHashMap<String, RenderFrame>()
            changed.forEach {
                checkpoint()
                updatedFrames[it.id] = RenderFrame(it, it.renderBounds)
            }
            // One cheap definition scan avoids a second persistent adjacency map. Only
            // incident connections allocate new line geometry; unchanged lines/frames share.
            source.connections.values.forEach { edge ->
                checkpoint()
                if ((edge.start as? ConnectorEnd.Bound)?.targetId in targets || (edge.end as? ConnectorEnd.Bound)?.targetId in targets) {
                    val line = PageConnectionGeometry.resolveLine(edge, ports)
                    lines[edge.id] = line
                    updatedFrames[edge.id] = RenderFrame(edge, line.renderBounds)
                }
            }
            val preparedFrames = RenderFrames(updatedFrames, frames)
            val bounds = frameUnion(elements) { element ->
                checkpoint()
                preparedFrames.find(element) ?: source.resolve(element).renderBounds
            }
            checkpoint()
            return PageContent(source.pageId, elements, PreviewState(source, ports, lines, preparedFrames, bounds))
        }

        /** Each frame is already validated; materialize one exact union instead of one per object. */
        private inline fun frameUnion(elements: List<PageObject>, frame: (PageObject) -> Bounds): Bounds? {
            if (elements.isEmpty()) return null
            var left = Float.POSITIVE_INFINITY
            var top = Float.POSITIVE_INFINITY
            var right = Float.NEGATIVE_INFINITY
            var bottom = Float.NEGATIVE_INFINITY
            elements.forEach {
                val bounds = frame(it)
                left = min(left, bounds.left)
                top = min(top, bounds.top)
                right = max(right, bounds.right)
                bottom = max(bottom, bounds.bottom)
            }
            return Bounds(left, top, right, bottom)
        }

        private fun stableReplacement(before: PageObject, after: PageObject): Boolean {
            if (before.id != after.id || before.javaClass != after.javaClass) return false
            if (before is Connector) return before == after
            if (before !is Element || after !is Element) return false
            return when (before) {
                is ElementGroup ->
                    after is ElementGroup && before.children.size == after.children.size &&
                        before.children.indices.all { index ->
                            val first = before.children[index]
                            val second = after.children[index]
                            stableLeaf(first) && stableLeaf(second) && first.id == second.id && first.javaClass == second.javaClass
                        }
                is Stroke, is Line, is Shape, is Note, is TextElement, is MediaElement, is AffineElement -> true
            }
        }

        private fun stableLeaf(element: Element): Boolean = when (element) {
            is ElementGroup -> false
            is Stroke, is Line, is Shape, is Note, is TextElement, is MediaElement, is AffineElement -> true
        }
    }
}
