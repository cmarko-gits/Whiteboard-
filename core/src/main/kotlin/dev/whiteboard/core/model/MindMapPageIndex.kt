package dev.whiteboard.core.model

import java.util.Collections

/**
 * One immutable ownership/visibility index for all trees on a page. No Page facade is retained.
 * A caller must also check this semantic snapshot's identity when accepting an asynchronous plan.
 * Document persistence and editor integration are intentionally a subsequent step.
 */
class MindMapPageIndex private constructor(page: Page, trees: List<MindMapTree>, checkpoint: () -> Unit, source: MindMapPageIndex?) {
    constructor(page: Page, trees: List<MindMapTree>, checkpoint: () -> Unit = {}) : this(page, trees, checkpoint, null)
    private val content = page.content
    val trees: List<MindMapTree>
    val visibleObjects: List<PageObject>
    private val byId: Map<String, MindMapTree>
    private val owners: Map<String, MindMapTree>
    private val hiddenObjects: Set<String>

    init {
        checkpoint()
        require(trees.size <= MAX_TREES) { "Too many mind maps" }
        if (source != null) {
            require(content.isPreviewOf(source.content)) { "Semantic preview requires validated stable membership" }
            this.trees = source.trees
            byId = source.byId
            owners = source.owners
            hiddenObjects = source.hiddenObjects
            val visible = if (hiddenObjects.isEmpty()) {
                page.elements
            } else {
                page.elements.filter {
                    checkpoint()
                    it.id !in hiddenObjects
                }
            }
            visibleObjects = Collections.unmodifiableList(visible)
        } else if (trees.isEmpty()) {
            this.trees = emptyList()
            byId = emptyMap()
            owners = emptyMap()
            hiddenObjects = emptySet()
            visibleObjects = Collections.unmodifiableList(page.elements)
        } else {
            val prepared = ArrayList<MindMapTree>(trees.size)
            val maps = LinkedHashMap<String, MindMapTree>(trees.size)
            var objectCount = 0L
            trees.forEach {
                checkpoint()
                objectCount += it.nodes.size.toLong() * 2 - 1
                require(objectCount <= Page.MAX_ELEMENTS) { "Mind-map object limit exceeded" }
                require(maps.put(it.id, it) == null) { "Duplicate mind-map ID" }
                prepared.add(it)
            }
            val objects = HashMap<String, PageObject>(page.elements.size)
            page.elements.forEach {
                checkpoint()
                objects[it.id] = it
            }
            val occupied = LinkedHashMap<String, MindMapTree>()
            val hiddenNodes = HashSet<String>()
            prepared.forEach { tree ->
                checkpoint()
                tree.validateObjects(objects, content::containsId, checkpoint)
                val visible = HashSet<String>()
                tree.visibleNodes(checkpoint).forEach {
                    checkpoint()
                    visible.add(it.id)
                }
                tree.nodes.forEach { node ->
                    checkpoint()
                    require(occupied.put(node.id, tree) == null) { "A node belongs to more than one mind map" }
                    node.connectionId?.let {
                        require(occupied.put(it, tree) == null) { "A connection belongs to more than one mind map" }
                    }
                    if (node.id !in visible) hiddenNodes.add(node.id)
                }
            }
            val hidden = HashSet<String>()
            val visible = if (hiddenNodes.isEmpty()) {
                page.elements
            } else {
                buildList {
                    page.elements.forEach { element ->
                        checkpoint()
                        val hide = element.id in hiddenNodes || element is Connector &&
                            (
                                (element.start as? ConnectorEnd.Bound)?.targetId in hiddenNodes ||
                                    (element.end as? ConnectorEnd.Bound)?.targetId in hiddenNodes
                                )
                        if (hide) hidden.add(element.id) else add(element)
                    }
                }
            }
            checkpoint()
            this.trees = Collections.unmodifiableList(prepared)
            byId = Collections.unmodifiableMap(maps)
            owners = Collections.unmodifiableMap(occupied)
            hiddenObjects = Collections.unmodifiableSet(hidden)
            visibleObjects = Collections.unmodifiableList(visible)
        }
        checkpoint()
    }

    /** Metadata-only page copies share canonical geometry. Semantic identity is checked separately. */
    fun matchesGeometry(page: Page): Boolean = content === page.content

    /** Reuses topology/visibility only after PageContent verified stable identities and definitions. */
    internal fun rebindPreview(page: Page, checkpoint: () -> Unit): MindMapPageIndex? =
        if (page.content.isPreviewOf(content)) MindMapPageIndex(page, trees, checkpoint, this) else null

    fun tree(id: String): MindMapTree = requireNotNull(byId[id]) { "Unknown mind map" }

    /** Both nodes and their managed connections have an owner; ordinary objects return null. */
    fun ownerOfObject(id: String): MindMapTree? = owners[id]

    fun isVisible(id: String): Boolean = content.containsId(id) && id !in hiddenObjects

    companion object {
        const val MAX_TREES = 1024
    }
}
