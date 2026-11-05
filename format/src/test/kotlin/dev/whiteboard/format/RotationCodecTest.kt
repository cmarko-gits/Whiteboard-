package dev.whiteboard.format

import dev.whiteboard.core.editor.DocumentSession
import dev.whiteboard.core.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RotationCodecTest {
    private val codec = DocumentCodec()
    private val note = Note(bounds = Bounds(100f, 200f, 400f, 350f), text = "Keep text and placement")

    private fun rotated(): Board {
        val shape = Shape(bounds = Bounds(0f, 0f, 100f, 200f), kind = Tool.TRIANGLE, style = InkStyle(), fill = 0xFF12AB34.toInt())
        val media = MediaElement(bounds = Bounds(400f, 100f, 600f, 300f), assetId = "${newId()}.png", rotation = 90)
        val text = TextElement(bounds = Bounds(0f, 400f, 300f, 500f), text = "Rotated text")
        val group = ElementGroup(children = listOf(shape, note, media, text))
        return DocumentSession(Board(name = "Round trip", pages = listOf(Page(elements = listOf(group))))).apply {
            rotate(setOf(group.id), 180.0)
        }.board
    }

    @Test fun allPlacedContentAndGroupIdsRoundTripThroughDocumentPageAndElementStorage() {
        val original = rotated()
        assertEquals(DocumentCodec.VERSION, JSONObject(codec.encode(original)).getInt("version"))
        assertEquals(original, codec.decode(codec.encode(original)))
        assertEquals(original.activePage, codec.decodePageJson(codec.encodePageJson(original.activePage)))
        val element = original.activePage.elements.single()
        assertEquals(element, codec.decodeElementJson(codec.encodeElementJson(element)))
        assertEquals(original.referencedAssets(), codec.decode(codec.encode(original)).referencedAssets())
    }

    @Test fun oldDocumentVersionsAndLegacyMediaAnglesRemainReadable() {
        val media = MediaElement(bounds = Bounds(0f, 0f, 200f, 100f), assetId = "${newId()}.png", rotation = 270)
        val original = Board(name = "Legacy", pages = listOf(Page(elements = listOf(note, media))))
        (1..3).forEach { version ->
            val source = JSONObject(codec.encode(original)).put("version", version).toString()
            assertEquals(original, codec.decode(source))
        }
    }

    @Test fun aSingularOrUnboundedPlacementIsRejectedBeforeImport() {
        val placed = AffineElement.fromDocument(note)
        listOf(
            listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            listOf(1.0, 0.0, 1e10, 0.0, 1.0, 0.0),
            listOf(1e-12, 0.0, 0.0, 0.0, 1.0, 0.0)
        ).forEach { matrix ->
            val root = JSONObject(codec.encodeElementJson(placed)).put("placement", JSONArray(matrix))
            assertThrows(IllegalArgumentException::class.java) { codec.decodeElementJson(root.toString()) }
        }
    }

    @Test fun nestedPlacementsAndAReusedSourceIdAreRejected() {
        val placed = AffineElement.fromDocument(note)
        val root = JSONObject(codec.encodeElementJson(placed))
        root.getJSONObject("source").put("id", newId())
        assertThrows(IllegalArgumentException::class.java) { codec.decodeElementJson(root.toString()) }
        val nested = JSONObject(codec.encodeElementJson(placed)).put("source", JSONObject(codec.encodeElementJson(placed)))
        assertThrows(IllegalArgumentException::class.java) { codec.decodeElementJson(nested.toString()) }
    }

    @Test fun anUncenteredLocalFrameIsRejectedInsteadOfLosingItsCoordinates() {
        val root = JSONObject(codec.encodeElementJson(AffineElement.fromDocument(note)))
        root.getJSONObject("source").put("bounds", JSONArray(listOf(100, 200, 400, 350)))
        assertThrows(IllegalArgumentException::class.java) { codec.decodeElementJson(root.toString()) }
    }

    @Test fun fingerprintsStayStableAcrossJsonRoundTripsAndDistinguishDifferentPlacements() {
        val first = AffineElement.fromDocument(note, AffineTransform2D.rotation(180.0, note.bounds.center))
        val restored = codec.decodeElementJson(codec.encodeElementJson(first))
        val second = AffineElement.fromDocument(note, AffineTransform2D.rotation(179.0, note.bounds.center))
        val digest = ElementDigest(codec)
        assertEquals(first, restored)
        assertEquals(digest.of(first), digest.of(restored))
        assertNotEquals(digest.of(first), digest.of(second))
    }

    @Test fun decodingValidatesInkInDocumentCoordinatesInsteadOfExpandingTheLocalSource() {
        val shape = Shape(
            bounds = Bounds(-1_000_000_000f, -1_000_000_000f, 1_000_000_000f, 1_000_000_000f),
            kind = Tool.RECTANGLE,
            style = InkStyle(width = 200f)
        )
        val placed = AffineElement(shape, AffineTransform2D.scale(0.000001, 0.000001))
        assertEquals(placed, codec.decodeElementJson(codec.encodeElementJson(placed)))
    }
}
