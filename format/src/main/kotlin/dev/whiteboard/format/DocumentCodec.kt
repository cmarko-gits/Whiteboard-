package dev.whiteboard.format

import dev.whiteboard.core.model.*
import org.json.JSONArray
import org.json.JSONObject

typealias UnsupportedDocumentVersion = dev.whiteboard.core.model.UnsupportedDocumentVersion

/** Explicit, versioned format: no reflective class names or executable serialized objects. */
class DocumentCodec(private val allocationBudget: Long = JsonSafety.DEFAULT_ALLOCATION_BUDGET) {
    init {
        require(allocationBudget > 0)
    }
    fun encodePageJson(page: Page): String = encodePage(page).toString().also {
        require(it.length <= MAX_DOCUMENT_CHARACTERS)
        JsonSafety.check(it, 32, allocationBudget)
    }
    fun decodePageJson(source: String): Page {
        require(source.length <= MAX_DOCUMENT_CHARACTERS)
        JsonSafety.check(source, 32, allocationBudget)
        return decodePage(JSONObject(source))
    }

    fun encodeElementJson(element: PageObject): String = encodeElement(element).toString().also {
        require(it.length <= MAX_DOCUMENT_CHARACTERS)
        JsonSafety.check(it, 32, allocationBudget)
    }
    fun decodeElementJson(source: String): PageObject {
        require(source.length <= MAX_DOCUMENT_CHARACTERS)
        JsonSafety.check(source, 32, allocationBudget)
        return decodeElement(JSONObject(source))
    }

    fun encode(board: Board): String = JSONObject().apply {
        put("version", VERSION)
        put("id", board.id)
        put("name", board.name)
        put("createdAt", board.createdAt)
        put("updatedAt", board.updatedAt)
        put("activePageId", board.activePageId)
        put("favorite", board.favorite)
        put("folderId", board.folderId)
        put("trashedAt", board.trashedAt)
        put("pages", JSONArray().apply { board.pages.forEach { put(encodePage(it)) } })
    }.toString().also {
        require(it.length <= MAX_DOCUMENT_CHARACTERS) { "Document is too large to save safely" }
        JsonSafety.check(it, 32, allocationBudget)
    }

    fun decode(source: String): Board {
        require(source.length <= MAX_DOCUMENT_CHARACTERS) { "Document is too large" }
        JsonSafety.check(source, 32, allocationBudget)
        val root = JSONObject(source)
        val version = root.getInt("version")
        require(root.get("version") is Number && root.getDouble("version") == version.toDouble()) { "Invalid document version" }
        if (version !in 1..VERSION) throw UnsupportedDocumentVersion(version)
        val pages = root.getJSONArray("pages")
        require(pages.length() in 1..1000)
        return Board(
            id = root.getString(
                "id"
            ),
            name = root.getString("name"), createdAt = root.getLong("createdAt"), updatedAt = root.getLong("updatedAt"),
            pages = List(pages.length()) { decodePage(pages.getJSONObject(it), version) }, activePageId = root.getString("activePageId"),
            favorite = root.optBoolean("favorite"), folderId = root.optionalString("folderId"),
            trashedAt = if (root.has("trashedAt") && !root.isNull("trashedAt")) root.getLong("trashedAt") else null
        )
    }

    private fun encodePage(page: Page) = JSONObject().apply {
        page.contentBounds
        put("id", page.id)
        put("name", page.name)
        put("background", page.background.name)
        put("backgroundColor", page.backgroundColor)
        put(
            "viewport",
            JSONObject().apply {
                putFloat("x", page.viewport.offset.x)
                putFloat("y", page.viewport.offset.y)
                putFloat("scale", page.viewport.scale)
            }
        )
        put("objects", JSONArray().apply { page.elements.forEach { put(encodeElement(it)) } })
    }
    private fun decodePage(root: JSONObject, version: Int = VERSION): Page {
        val objects = root.getJSONArray("objects")
        require(objects.length() <= 100_000)
        val view = root.getJSONObject("viewport")
        return Page(
            root.getString("id"),
            root.getString("name"),
            Background.valueOf(root.getString("background")),
            root.getInt("backgroundColor"),
            List(objects.length()) {
                decodeElement(objects.getJSONObject(it), version = version)
            },
            Viewport(Point(view.float("x"), view.float("y")), view.float("scale"))
        ).also { it.contentBounds }
    }
    private fun encodeElement(element: PageObject): JSONObject = JSONObject().apply {
        put("id", element.id)
        if (element is Element) {
            put("bounds", JSONArray(listOf(element.bounds.left, element.bounds.top, element.bounds.right, element.bounds.bottom)))
        }
        when (element) {
            is AffineElement -> {
                put("type", "affine")
                put("source", encodeElement(element.source))
                val p = element.placement
                put("placement", JSONArray(listOf(p.xx, p.xy, p.tx, p.yx, p.yy, p.ty)))
            }
            is ElementGroup -> {
                put("type", "group")
                put("children", JSONArray().apply { element.children.forEach { put(encodeElement(it)) } })
            }
            is MediaElement -> {
                put("type", "media")
                put("assetId", element.assetId)
                put("pdfPage", element.pdfPage)
                put("rotation", element.rotation)
                put("crop", JSONArray(listOf(element.crop.left, element.crop.top, element.crop.right, element.crop.bottom)))
            }
            is Stroke -> {
                put("type", "stroke")
                put("tool", element.tool.name)
                put("style", encodeStyle(element.style))
                put("points", JSONArray().apply { element.points.forEach { put(JSONArray(listOf(it.x, it.y))) } })
            }
            is Shape -> {
                put("type", "shape")
                put("kind", element.kind.name)
                put("style", encodeStyle(element.style))
                put("fill", element.fill)
            }
            is Line -> {
                put("type", "line")
                put("start", JSONArray(listOf(element.start.x, element.start.y)))
                put("end", JSONArray(listOf(element.end.x, element.end.y)))
                put("style", encodeStyle(element.style))
                put("arrow", element.arrow)
            }
            is Connector -> {
                put("type", "connector")
                put("start", encodeEnd(element.start))
                put("end", encodeEnd(element.end))
                put("style", encodeStyle(element.style))
                put("arrow", element.arrow)
            }
            is TextElement -> {
                put("type", "text")
                put("text", element.text)
                put("color", element.color)
                putFloat("fontSize", element.fontSize)
                put("bold", element.bold)
                put("italic", element.italic)
                put("alignment", element.alignment.name)
            }
            is Note -> {
                put("type", "note")
                put("text", element.text)
                put("color", element.color)
                putFloat("fontSize", element.fontSize)
            }
        }
    }
    private fun decodeElement(root: JSONObject, validateRenderBounds: Boolean = true, version: Int = VERSION): PageObject {
        val id = root.getString("id")
        val type = root.getString("type")
        if (type == "connector") {
            require(version >= 5) { "Connections require document version 5" }
            fields(root, setOf("id", "type", "start", "end", "style", "arrow"))
            require(root.get("id") is String && root.get("arrow") is Boolean)
            val style = root.getJSONObject("style")
            fields(style, setOf("color", "width", "opacity"))
            require(style.get("color") is Number && style.getDouble("color") == style.getInt("color").toDouble())
            require(style.get("width") is Number && style.get("opacity") is Number)
            return Connector(
                id,
                decodeEnd(root.getJSONObject("start")),
                decodeEnd(root.getJSONObject("end")),
                decodeStyle(style),
                root.getBoolean("arrow")
            )
        }
        val b = root.getJSONArray("bounds")
        val bounds = Bounds(b.getDouble(0).toFloat(), b.getDouble(1).toFloat(), b.getDouble(2).toFloat(), b.getDouble(3).toFloat())
        return when (type) {
            "affine" -> {
                val source = root.getJSONObject("source")
                require(source.getString("type") in setOf("shape", "text", "note", "media"))
                val p = root.getJSONArray("placement")
                require(p.length() == 6 && source.getString("id") == id)
                val decoded = decodeElement(source, validateRenderBounds = false, version = version)
                require(decoded is Element)
                AffineElement(
                    decoded,
                    AffineTransform2D(p.getDouble(0), p.getDouble(1), p.getDouble(2), p.getDouble(3), p.getDouble(4), p.getDouble(5))
                )
            }
            "group" -> {
                val children = root.getJSONArray("children")
                require(children.length() in 2..100_000)
                require((0 until children.length()).none { children.getJSONObject(it).getString("type") in setOf("group", "connector") })
                ElementGroup(
                    id,
                    List(children.length()) {
                        val child = decodeElement(children.getJSONObject(it), version = version)
                        require(child is Element)
                        child
                    }
                )
            }
            "media" -> {
                val c = root.getJSONArray("crop")
                MediaElement(
                    id,
                    bounds,
                    root.getString("assetId"),
                    if (root.isNull("pdfPage")) null else root.getInt("pdfPage"),
                    root.getInt("rotation"),
                    Bounds(c.getDouble(0).toFloat(), c.getDouble(1).toFloat(), c.getDouble(2).toFloat(), c.getDouble(3).toFloat())
                )
            }
            "stroke" -> {
                val points = root.getJSONArray("points")
                require(points.length() in 1..2_000_000)
                Stroke(
                    id,
                    List(points.length()) {
                        points.getJSONArray(it).point()
                    },
                    decodeStyle(root.getJSONObject("style")),
                    Tool.valueOf(root.getString("tool"))
                )
            }
            "shape" -> Shape(
                id,
                bounds,
                Tool.valueOf(root.getString("kind")),
                decodeStyle(root.getJSONObject("style")),
                if (root.has("fill") &&
                    !root.isNull("fill")
                ) {
                    root.getInt("fill")
                } else {
                    null
                }
            )
            "line" -> Line(
                id,
                root.getJSONArray("start").point(),
                root.getJSONArray("end").point(),
                decodeStyle(root.getJSONObject("style")),
                root.getBoolean("arrow")
            )
            "text" -> TextElement(
                id,
                bounds,
                root.getString("text"),
                root.getInt("color"),
                root.float("fontSize"),
                root.getBoolean("bold"),
                root.getBoolean("italic"),
                TextAlignment.valueOf(root.getString("alignment"))
            )
            "note" -> Note(id, bounds, root.getString("text"), root.getInt("color"), root.float("fontSize"))
            else -> throw IllegalArgumentException("Unknown object type: $type")
        }.also { if (validateRenderBounds) it.renderBounds }
    }
    private fun encodeStyle(style: InkStyle) = JSONObject().apply {
        put("color", style.color)
        putFloat("width", style.width)
        putFloat("opacity", style.opacity)
    }
    private fun decodeStyle(root: JSONObject) = InkStyle(root.getInt("color"), root.float("width"), root.float("opacity"))

    private fun encodeEnd(end: ConnectorEnd): JSONObject = JSONObject().apply {
        when (end) {
            is ConnectorEnd.Free -> {
                put("type", "free")
                put("point", JSONArray(listOf(end.point.x, end.point.y)))
            }
            is ConnectorEnd.Bound -> {
                put("type", "bound")
                put("targetId", end.targetId)
                put("port", end.port.name)
            }
        }
    }

    private fun decodeEnd(root: JSONObject): ConnectorEnd = when (root.getString("type")) {
        "free" -> {
            fields(root, setOf("type", "point"))
            val point = root.getJSONArray("point")
            require(point.length() == 2 && point.get(0) is Number && point.get(1) is Number)
            ConnectorEnd.Free(point.point())
        }
        "bound" -> {
            fields(root, setOf("type", "targetId", "port"))
            require(root.get("targetId") is String && root.get("port") is String)
            ConnectorEnd.Bound(root.getString("targetId"), ConnectionPort.valueOf(root.getString("port")))
        }
        else -> throw IllegalArgumentException("Unknown connection end")
    }

    private fun fields(root: JSONObject, allowed: Set<String>) {
        require(root.keys().asSequence().all { it in allowed }) { "Unknown connection field" }
    }

    // The JVM JSON library also has put(String, Float); Android's platform class only supports Double.
    private fun JSONObject.putFloat(name: String, value: Float) = put(name, value.toDouble())
    private fun JSONObject.float(name: String) = getDouble(name).toFloat()
    private fun JSONArray.point() = Point(getDouble(0).toFloat(), getDouble(1).toFloat())
    private fun JSONObject.optionalString(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null
    companion object {
        const val VERSION = 5
        const val MAX_DOCUMENT_CHARACTERS = 64 * 1024 * 1024
    }
}
