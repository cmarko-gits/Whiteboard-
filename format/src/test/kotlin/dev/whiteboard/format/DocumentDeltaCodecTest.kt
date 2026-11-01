package dev.whiteboard.format

import dev.whiteboard.core.cloud.DeltaEngine
import dev.whiteboard.core.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DocumentDeltaCodecTest {
    private val codec = DocumentDeltaCodec()
    private val engine = DeltaEngine(ElementDigest())

    @Test fun nestedObjectFieldsAndExtraPointDimensionsCannotBeDiscarded() {
        val before = Board(name = "Before")
        val stroke = Stroke(points = listOf(Point(1f, 2f)), style = InkStyle())
        val after = before.withPage(before.activePage.copy(elements = listOf(stroke)))
        val encoded = codec.encode(engine.between(before, after))
        fun value(root: JSONObject) =
            root.getJSONArray("pages").getJSONObject(0).getJSONArray("objects").getJSONObject(0).getJSONObject("value")
        val unknown = JSONObject(encoded)
        value(unknown).getJSONObject("style").put("unsupportedStyle", true)
        rejected { codec.decode(unknown.toString()) }
        val extraDimension = JSONObject(encoded)
        value(extraDimension).getJSONArray("points").getJSONArray(0).put(0.5)
        rejected { codec.decode(extraDimension.toString()) }
    }

    @Test fun metadataObjectsGroupsAndNewPagesRoundTrip() {
        val first = Note(id = "first", bounds = Bounds(1f, 2f, 30f, 40f), text = "One")
        val second = first.copy(id = "second", text = "Two")
        val page = Page(id = "page", elements = listOf(first, second))
        val initial = Board(id = "board", name = "Initial", pages = listOf(page))
        val updatedPage = page.copy(
            name = "Nova strana",
            background = Background.GRID,
            backgroundColor = 7,
            elements = listOf(ElementGroup(children = listOf(first, second)))
        )
        val changed = initial.copy(name = "Život Ћирилица 🖊", pages = listOf(updatedPage, Page(id = "new-page")))
        val delta = engine.between(initial, changed)
        assertEquals(delta, codec.decode(codec.encode(delta)))
        assertEquals(changed, engine.apply(initial, codec.decode(codec.encode(delta))).board)
    }

    @Test fun unknownFieldsAndProtocolVersionsCannotBeSilentlyDiscarded() {
        val before = Board(name = "Before")
        val encoded = codec.encode(engine.between(before, before.copy(name = "After")))
        listOf("version", "documentVersion", "fingerprintVersion").forEach { key ->
            rejected { codec.decode(JSONObject(encoded).put(key, 999).toString()) }
        }
        rejected { codec.decode(JSONObject(encoded).put("unexpectedEdit", "must not be lost").toString()) }
        val typo = JSONObject(encoded).apply { getJSONObject("name").put("typo", "ignored?") }
        rejected { codec.decode(typo.toString()) }
    }

    private fun rejected(action: () -> Unit) {
        try {
            action()
            fail("Expected invalid operation")
        } catch (_: IllegalArgumentException) { }
    }
}
