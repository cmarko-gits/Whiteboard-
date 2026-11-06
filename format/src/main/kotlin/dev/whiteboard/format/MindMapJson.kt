package dev.whiteboard.format

import dev.whiteboard.core.model.MindMapNode
import dev.whiteboard.core.model.MindMapPageIndex
import dev.whiteboard.core.model.MindMapTree
import dev.whiteboard.core.model.Page
import org.json.JSONArray
import org.json.JSONObject

/** Flat topology keeps JSON nesting bounded independently of the tree's logical depth. */
internal object MindMapJson {
    private val treeFields = setOf("id", "nodes")
    private val nodeFields = setOf("id", "parentId", "connectionId", "collapsed")
    fun encode(trees: List<MindMapTree>): JSONArray {
        validateTopology(trees)
        return JSONArray().apply {
            trees.forEach { tree ->
                put(
                    JSONObject().put("id", tree.id).put(
                        "nodes",
                        JSONArray().apply {
                            tree.nodes.forEach { node ->
                                put(
                                    JSONObject().put("id", node.id).put("parentId", node.parentId ?: JSONObject.NULL)
                                        .put("connectionId", node.connectionId ?: JSONObject.NULL).put("collapsed", node.collapsed)
                                )
                            }
                        }
                    )
                )
            }
        }
    }

    fun decode(array: JSONArray): List<MindMapTree> {
        require(array.length() <= MindMapPageIndex.MAX_TREES) { "Too many mind maps" }
        var objectCount = 0L
        return List(array.length()) { index ->
            val tree = array.getJSONObject(index)
            fields(tree, treeFields)
            val nodes = tree.getJSONArray("nodes")
            require(nodes.length() in 1..MindMapTree.MAX_NODES)
            objectCount += nodes.length().toLong() * 2 - 1
            require(objectCount <= Page.MAX_ELEMENTS) { "Mind-map object limit exceeded" }
            MindMapTree(
                string(tree, "id"),
                List(nodes.length()) { position ->
                    val node = nodes.getJSONObject(position)
                    fields(node, nodeFields)
                    val collapsed = node.get("collapsed")
                    require(collapsed is Boolean) { "Invalid collapse state" }
                    MindMapNode(
                        string(node, "id"),
                        nullableString(node, "parentId"),
                        nullableString(node, "connectionId"),
                        collapsed
                    )
                }
            )
        }.also(::validateTopology)
    }

    private fun validateTopology(trees: List<MindMapTree>) {
        require(trees.size <= MindMapPageIndex.MAX_TREES)
        val maps = HashSet<String>()
        val objects = HashSet<String>()
        var count = 0L
        trees.forEach { tree ->
            require(maps.add(tree.id)) { "Duplicate mind-map ID" }
            count += tree.nodes.size.toLong() * 2 - 1
            require(count <= Page.MAX_ELEMENTS) { "Mind-map object limit exceeded" }
            tree.nodes.forEach { node ->
                require(objects.add(node.id)) { "Shared mind-map node" }
                node.connectionId?.let { require(objects.add(it)) { "Shared mind-map connection" } }
            }
        }
        require(maps.none { it in objects }) { "Mind-map identity collides with its objects" }
    }

    private fun string(root: JSONObject, name: String): String {
        val value = root.get(name)
        require(value is String) { "Invalid mind-map string field" }
        return value
    }
    private fun nullableString(root: JSONObject, name: String): String? = if (root.isNull(name)) null else string(root, name)
    private fun fields(root: JSONObject, required: Set<String>) {
        require(root.length() == required.size && root.keys().asSequence().all { it in required }) { "Unknown or missing mind-map field" }
    }
}
