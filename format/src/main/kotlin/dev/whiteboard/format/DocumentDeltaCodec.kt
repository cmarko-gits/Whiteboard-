package dev.whiteboard.format

import dev.whiteboard.core.cloud.DocumentDelta
import dev.whiteboard.core.cloud.ObjectMutation
import dev.whiteboard.core.cloud.PageMutation
import dev.whiteboard.core.cloud.ValueChange
import dev.whiteboard.core.model.Background
import org.json.JSONArray
import org.json.JSONObject

/** Strict, versioned operation transport. Unknown fields are errors rather than acknowledged, discarded edits. */
class DocumentDeltaCodec(private val documents: DocumentCodec = DocumentCodec()) {
    fun encode(delta: DocumentDelta): String = JSONObject().apply {
        put("version", VERSION)
        put("documentVersion", DocumentCodec.VERSION)
        put("fingerprintVersion", FINGERPRINT_VERSION)
        delta.name?.let { put("name", change(it.expected, it.value)) }
        delta.order?.let { put("order", JSONArray(it)) }
        put(
            "pages",
            JSONArray().apply {
                delta.pages.forEach { page ->
                    put(
                        JSONObject().apply {
                            put("id", page.id)
                            page.created?.let { put("created", JSONObject(documents.encodePageJson(it))) }
                            if (page.deleted) put("deleted", true)
                            page.name?.let { put("name", change(it.expected, it.value)) }
                            page.background?.let { put("background", change(it.expected.name, it.value.name)) }
                            page.color?.let { put("color", change(it.expected, it.value)) }
                            page.order?.let { put("order", JSONArray(it)) }
                            if (page.objects.isNotEmpty()) {
                                put(
                                    "objects",
                                    JSONArray().apply {
                                        page.objects.forEach { mutation ->
                                            put(
                                                JSONObject().apply {
                                                    put("id", mutation.id)
                                                    mutation.expectedFingerprint?.let { put("expected", it) }
                                                    mutation.value?.let { put("value", JSONObject(documents.encodeElementJson(it))) }
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    )
                }
            }
        )
    }.toString().also { require(it.length <= MAX_CHARACTERS) }

    fun decode(source: String): DocumentDelta {
        require(source.length <= MAX_CHARACTERS)
        JsonSafety.check(source)
        val root = JSONObject(source)
        fields(root, setOf("version", "documentVersion", "fingerprintVersion", "name", "pages", "order"))
        require(root.getInt("version") == VERSION && root.getInt("fingerprintVersion") == FINGERPRINT_VERSION)
        val documentVersion = root.getInt("documentVersion")
        require(root.get("documentVersion") is Number && root.getDouble("documentVersion") == documentVersion.toDouble())
        require(documentVersion in 1..DocumentCodec.VERSION)
        val pages = root.getJSONArray("pages")
        require(pages.length() <= 1000)
        return DocumentDelta(
            root.objectOrNull("name")?.let { stringChange(it) },
            List(pages.length()) { decodePage(pages.getJSONObject(it), documentVersion) },
            root.arrayOrNull("order")?.let { order(it, 1000) }
        )
    }

    private fun decodePage(root: JSONObject, documentVersion: Int): PageMutation {
        fields(root, setOf("id", "created", "deleted", "name", "background", "color", "objects", "order"))
        val objects = root.arrayOrNull("objects")
        require(objects == null || objects.length() <= 100_000)
        val result = PageMutation(
            id = root.getString("id"),
            created = root.objectOrNull("created")?.let {
                validatePage(it, documentVersion)
                documents.decodePageJson(it.toString())
            },
            deleted = root.optBoolean("deleted", false),
            name = root.objectOrNull("name")?.let { stringChange(it) },
            background = root.objectOrNull("background")?.let {
                fields(it, setOf("expected", "value"))
                ValueChange(Background.valueOf(it.getString("expected")), Background.valueOf(it.getString("value")))
            },
            color = root.objectOrNull("color")?.let {
                fields(it, setOf("expected", "value"))
                ValueChange(it.getInt("expected"), it.getInt("value"))
            },
            objects = objects?.let { values ->
                List(values.length()) { index ->
                    val value = values.getJSONObject(index)
                    fields(value, setOf("id", "expected", "value"))
                    ObjectMutation(
                        value.getString("id"),
                        if (value.has("expected") &&
                            !value.isNull("expected")
                        ) {
                            value.getString("expected")
                        } else {
                            null
                        },
                        value.objectOrNull("value")?.let {
                            validateElement(it, documentVersion)
                            documents.decodeElementJson(it.toString())
                        }
                    )
                }
            }.orEmpty(),
            order = root.arrayOrNull("order")?.let { order(it, 100_000) }
        )
        require(
            result.created != null || result.deleted || result.name != null || result.background != null || result.color != null ||
                result.objects.isNotEmpty() || result.order != null
        )
        return result
    }

    private fun stringChange(root: JSONObject): ValueChange<String> {
        fields(root, setOf("expected", "value"))
        return ValueChange(root.getString("expected"), root.getString("value"))
    }
    private fun validatePage(root: JSONObject, documentVersion: Int) {
        fields(root, setOf("id", "name", "background", "backgroundColor", "viewport", "objects"))
        fields(root.getJSONObject("viewport"), setOf("x", "y", "scale"))
        val objects = root.getJSONArray("objects")
        require(objects.length() <= 100_000)
        for (index in 0 until objects.length()) validateElement(objects.getJSONObject(index), documentVersion)
    }
    private fun validateElement(root: JSONObject, documentVersion: Int) {
        val type = root.getString("type")
        fields(root, requireNotNull(ELEMENT_FIELDS[type]) { "Unsupported operation object" })
        if (type == "connector") require(documentVersion >= 5) else require(root.getJSONArray("bounds").length() == 4)
        root.objectOrNull("style")?.let { fields(it, setOf("color", "width", "opacity")) }
        when (type) {
            "affine" -> {
                require(root.getJSONArray("placement").length() == 6)
                val source = root.getJSONObject("source")
                require(source.getString("type") in setOf("shape", "text", "note", "media"))
                validateElement(source, documentVersion)
            }
            "group" -> {
                val children = root.getJSONArray("children")
                require(children.length() in 2..100_000)
                for (index in 0 until children.length()) {
                    val child = children.getJSONObject(index)
                    require(child.getString("type") !in setOf("group", "connector"))
                    validateElement(child, documentVersion)
                }
            }
            "stroke" -> {
                val points = root.getJSONArray("points")
                require(points.length() in 1..2_000_000)
                for (index in 0 until points.length()) require(points.getJSONArray(index).length() == 2)
            }
            "line" -> require(root.getJSONArray("start").length() == 2 && root.getJSONArray("end").length() == 2)
            "media" -> require(root.getJSONArray("crop").length() == 4)
        }
    }
    private fun change(expected: Any, value: Any) = JSONObject().put("expected", expected).put("value", value)
    private fun order(array: JSONArray, maximum: Int): List<String> {
        require(array.length() <= maximum)
        return List(array.length()) { array.getString(it).also { id -> require(id.length in 1..128) } }
    }
    private fun fields(root: JSONObject, allowed: Set<String>) {
        require(root.keys().asSequence().all { it in allowed }) { "Unknown operation field" }
    }
    private fun JSONObject.objectOrNull(key: String): JSONObject? = if (has(key) && !isNull(key)) getJSONObject(key) else null
    private fun JSONObject.arrayOrNull(key: String): JSONArray? = if (has(key) && !isNull(key)) getJSONArray(key) else null
    companion object {
        private val COMMON = setOf("id", "bounds", "type")
        private val ELEMENT_FIELDS = mapOf(
            "stroke" to COMMON + setOf("tool", "style", "points"),
            "shape" to COMMON + setOf("kind", "style", "fill"),
            "line" to COMMON + setOf("start", "end", "style", "arrow"),
            "text" to COMMON + setOf("text", "color", "fontSize", "bold", "italic", "alignment"),
            "note" to COMMON + setOf("text", "color", "fontSize"),
            "media" to COMMON + setOf("assetId", "pdfPage", "rotation", "crop"),
            "group" to COMMON + "children",
            "affine" to COMMON + setOf("source", "placement"),
            "connector" to setOf("id", "type", "start", "end", "style", "arrow")
        )
        const val VERSION = 1
        const val FINGERPRINT_VERSION = 1
        const val MAX_CHARACTERS = 8 * 1024 * 1024
    }
}
