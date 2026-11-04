package dev.whiteboard.core.model

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
    internal val content: PageContent
) {
    constructor(
        id: String = newId(),
        name: String = "Page 1",
        background: Background = Background.DOTS,
        backgroundColor: Int = 0xFFFAFBF7.toInt(),
        elements: List<Element> = emptyList(),
        viewport: Viewport = Viewport()
    ) : this(name, background, backgroundColor, viewport, PageContent(id, elements))

    val id: String get() = content.pageId
    val elements: List<Element> get() = content.elements
    val geometry: PageGeometry by lazy { PageGeometry(this) }
    val contentBounds: Bounds? get() = content.bounds

    fun copy(
        id: String = this.id,
        name: String = this.name,
        background: Background = this.background,
        backgroundColor: Int = this.backgroundColor,
        elements: List<Element> = this.elements,
        viewport: Viewport = this.viewport
    ): Page = Page(
        name,
        background,
        backgroundColor,
        viewport,
        if (id == this.id && elements === this.elements) content else PageContent(id, elements)
    )

    operator fun component1(): String = id
    operator fun component2(): String = name
    operator fun component3(): Background = background
    operator fun component4(): Int = backgroundColor
    operator fun component5(): List<Element> = elements
    operator fun component6(): Viewport = viewport

    override fun equals(other: Any?): Boolean = this === other || other is Page &&
        id == other.id && name == other.name && background == other.background && backgroundColor == other.backgroundColor &&
        elements == other.elements && viewport == other.viewport

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + background.hashCode()
        result = 31 * result + backgroundColor
        result = 31 * result + elements.hashCode()
        return 31 * result + viewport.hashCode()
    }

    override fun toString(): String =
        "Page(id=$id, name=$name, background=$background, backgroundColor=$backgroundColor, elements=$elements, viewport=$viewport)"

    companion object {
        const val MAX_ELEMENTS = 100_000
    }
}

/** No page facade or mutable global cache is retained by this shared, content-owned snapshot. */
internal class PageContent(val pageId: String, val elements: List<Element>) {
    private val ids = HashSet<String>()
    val objectCount: Int

    init {
        require(elements.size <= Page.MAX_ELEMENTS)
        var count = 0
        elements.forEach { element ->
            require(ids.add(element.id)) { "Duplicate page object ID" }
            if (element is ElementGroup) {
                count += element.children.size
                element.children.forEach { require(ids.add(it.id)) { "Duplicate page object ID" } }
            } else {
                count++
            }
            require(count <= Page.MAX_ELEMENTS) { "Too many page objects" }
        }
        objectCount = count
    }

    fun containsId(id: String): Boolean = id in ids

    val bounds: Bounds? by lazy { elements.asSequence().map { it.renderBounds }.reduceOrNull(Bounds::union) }

    @Volatile private var ports: PagePortIndex? = null

    fun connectionPorts(checkpoint: () -> Unit): PagePortIndex {
        checkpoint()
        ports?.let { return it }
        // Cancellation or invalid geometry leaves no partial cache. Concurrent readers may prepare
        // independent candidates, but only a complete index is published under this short lock.
        val prepared = PagePortIndex(pageId, elements, checkpoint)
        checkpoint()
        return synchronized(this) { ports ?: prepared.also { ports = it } }
    }
}
