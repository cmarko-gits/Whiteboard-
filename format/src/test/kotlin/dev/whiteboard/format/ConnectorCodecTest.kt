package dev.whiteboard.format

import dev.whiteboard.core.cloud.DeltaEngine
import dev.whiteboard.core.editor.DocumentSession
import dev.whiteboard.core.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConnectorCodecTest {
    private val codec = DocumentCodec()
    private val first = Note(id = "first", bounds = Bounds(0f, 0f, 100f, 100f), text = "Prvi čvor")
    private val second = Note(id = "second", bounds = Bounds(200f, 100f, 300f, 200f), text = "Second node")
    private val edge = Connector(
        id = "edge",
        start = ConnectorEnd.Bound(first.id, ConnectionPort.RIGHT),
        end = ConnectorEnd.Bound(second.id, ConnectionPort.LEFT),
        style = InkStyle(0xFF123456.toInt(), 7f, 0.4f)
    )
    private val page = Page(id = "page", elements = listOf(first, edge, second))
    private val board = Board(id = "board", name = "Connections", pages = listOf(page))
    private fun root() = JSONObject(codec.encode(board))
    private fun objects(root: JSONObject) = root.getJSONArray("pages").getJSONObject(0).getJSONArray("objects")
    private fun connection(root: JSONObject) = objects(root).getJSONObject(1)
    private fun line(page: Page, edge: Connector = this.edge) = page.geometry.resolve(edge) as Line

    @Test fun documentPageAndObjectRoundTripsStoreDefinitionsWithoutDerivedBounds() {
        val root = root()
        assertEquals(DocumentCodec.VERSION, root.getInt("version"))
        assertEquals("connector", connection(root).getString("type"))
        assertFalse(connection(root).has("bounds"))
        assertEquals(board, codec.decode(root.toString()))
        assertEquals(page, codec.decodePageJson(codec.encodePageJson(page)))
        assertEquals(edge, codec.decodeElementJson(codec.encodeElementJson(edge)))
        assertEquals(listOf("first", "edge", "second"), codec.decode(root.toString()).activePage.elements.map { it.id })
        assertEquals(line(page), line(codec.decode(root.toString()).activePage))
    }

    @Test fun freeAndMixedEndsPreserveTheirExactCoordinatesStyleAndArrowFlag() {
        listOf(
            edge.copy(start = ConnectorEnd.Free(Point(-40f, 70f))),
            edge.copy(end = ConnectorEnd.Free(Point(450f, -20f)), arrow = false),
            edge.copy(
                start = ConnectorEnd.Free(Point(10f, 20f)),
                end = ConnectorEnd.Free(Point(30f, 40f)),
                style = InkStyle(width = 0.1f, opacity = 0f)
            )
        ).forEach { definition ->
            val source = board.withPage(page.copy(elements = listOf(first, definition, second)))
            assertEquals(source, codec.decode(codec.encode(source)))
            assertEquals(definition, codec.decodeElementJson(codec.encodeElementJson(definition)))
        }
    }

    @Test fun rotatedChildrenAndGroupFramePortsSurviveSerializationWithoutFlattening() {
        val posed = AffineElement.fromDocument(second, AffineTransform2D.rotation(37.0))
        val group = ElementGroup(id = "group", children = listOf(first, posed))
        val frameEdge = edge.copy(id = "frame-edge", start = ConnectorEnd.Bound(group.id, ConnectionPort.BOTTOM))
        val original = board.withPage(page.copy(elements = listOf(group, edge, frameEdge)))
        val restored = codec.decode(codec.encode(original))
        assertEquals(original, restored)
        assertEquals(line(original.activePage), line(restored.activePage))
        assertEquals(line(original.activePage, frameEdge), line(restored.activePage, frameEdge))
        assertTrue((restored.activePage.elements.first() as ElementGroup).children.last() is AffineElement)
    }

    @Test fun versionsOneToFourRemainReadableButCannotSmuggleInANewConnectionType() {
        val withoutConnections = board.withPage(page.copy(elements = listOf(first, second)))
        (1..4).forEach { version ->
            val legacy = JSONObject(codec.encode(withoutConnections)).put("version", version)
            assertEquals(withoutConnections, codec.decode(legacy.toString()))
            rejected { codec.decode(root().put("version", version).toString()) }
        }
        listOf<Any>("5", 5.5, 999).forEach { version -> rejected { codec.decode(root().put("version", version).toString()) } }
    }

    @Test fun missingOtherPageSelfAndConnectionTargetsAreRejected() {
        listOf("missing", edge.id, first.id).forEach { target ->
            val root = root()
            connection(root).getJSONObject("end").put("targetId", target)
            rejected { codec.decode(root.toString()) }
        }
        val foreign = root()
        foreign.getJSONArray(
            "pages"
        ).put(JSONObject(codec.encodePageJson(Page(id = "other-page", elements = listOf(first.copy(id = "remote"))))))
        connection(foreign).getJSONObject("start").put("targetId", "remote")
        rejected { codec.decode(foreign.toString()) }
        val toConnection = root()
        objects(toConnection).put(JSONObject(codec.encodeElementJson(edge.copy(id = "other-edge"))))
        connection(toConnection).getJSONObject("start").put("targetId", "other-edge")
        rejected { codec.decode(toConnection.toString()) }
    }

    @Test fun duplicateIdsUnknownPortsAndUnsupportedTargetKindsAreRejected() {
        val duplicate = root()
        objects(duplicate).put(JSONObject(connection(duplicate).toString()))
        rejected { codec.decode(duplicate.toString()) }
        val unknown = root()
        connection(unknown).getJSONObject("start").put("port", "AUTO_REMOTE")
        rejected { codec.decode(unknown.toString()) }
        val stroke = root()
        objects(
            stroke
        ).put(0, JSONObject(codec.encodeElementJson(Stroke(id = first.id, points = listOf(Point(0f, 0f)), style = InkStyle()))))
        rejected { codec.decode(stroke.toString()) }
        listOf("", " ", "x".repeat(129)).forEach { id ->
            val invalid = root()
            connection(invalid).put("id", id)
            rejected { codec.decode(invalid.toString()) }
        }
    }

    @Test fun connectionsCannotBecomeGroupChildrenOrAffineSourcesThroughJson() {
        val group = JSONObject(codec.encodeElementJson(ElementGroup(id = "group", children = listOf(first, second))))
        group.getJSONArray("children").put(1, JSONObject(codec.encodeElementJson(edge)))
        rejected { codec.decodeElementJson(group.toString()) }
        val affine = JSONObject(codec.encodeElementJson(AffineElement.fromDocument(first)))
        affine.put("source", JSONObject(codec.encodeElementJson(edge))).put("id", edge.id)
        rejected { codec.decodeElementJson(affine.toString()) }
    }

    @Test fun unknownFieldsAndCoercedEndpointTypesCannotBeSilentlyAccepted() {
        listOf("uri", "script", "bounds").forEach { field ->
            val root = root()
            connection(root).put(field, "not part of a connection")
            rejected { codec.decode(root.toString()) }
        }
        val foreignOwner = root()
        connection(foreignOwner).getJSONObject("start").put("pageId", "elsewhere")
        rejected { codec.decode(foreignOwner.toString()) }
        val unknownEnd = root()
        connection(unknownEnd).getJSONObject("start").put("type", "relative")
        rejected { codec.decode(unknownEnd.toString()) }
        listOf(JSONArray(listOf("1", 2)), JSONArray(listOf(1, 2, 3)), JSONArray(listOf(1e300, 0))).forEach { point ->
            val root = root()
            connection(root).put("start", JSONObject().put("type", "free").put("point", point))
            rejected { codec.decode(root.toString()) }
        }
        val boolean = root()
        connection(boolean).put("arrow", "true")
        rejected { codec.decode(boolean.toString()) }
    }

    @Test fun invalidStyleAndInkOutsideTheDocumentLimitAreRejectedBeforeReturningABoard() {
        listOf("width" to 201.0, "opacity" to -0.1, "color" to 1.5).forEach { (name, value) ->
            val root = root()
            connection(root).getJSONObject("style").put(name, value)
            rejected { codec.decode(root.toString()) }
        }
        val overflow = edge.copy(
            start = ConnectorEnd.Free(Point(999_999_936f, 0f)),
            end = ConnectorEnd.Free(Point(1_000_000_000f, 0f)),
            style = InkStyle(width = 200f)
        )
        val root = root()
        objects(root).put(1, JSONObject(codec.encodeElementJson(overflow)))
        rejected { codec.decode(root.toString()) }
        val invalid = board.withPage(page.copy(elements = listOf(overflow)))
        rejected { codec.encode(invalid) }
    }

    @Test fun fingerprintTracksBindingIntentRatherThanTheTargetsDerivedPosition() {
        val digest = ElementDigest()
        val before = digest.of(edge)
        val moved = page.geometry.preview(listOf(first.copy(bounds = Bounds(50f, 60f, 150f, 160f)))).page
        assertNotEquals(line(page).start, line(moved).start)
        assertEquals(before, digest.of(moved.elements.filterIsInstance<Connector>().single()))
        assertNotEquals(before, digest.of(edge.copy(arrow = false)))
        assertNotEquals(before, digest.of(edge.copy(start = ConnectorEnd.Bound(first.id, ConnectionPort.TOP))))
        assertNotEquals(before, digest.of(edge.copy(end = ConnectorEnd.Free(line(page).end))))
    }

    @Test fun graphAndAffineMutationsUseTheStrictDeltaCodecAndPreserveLayerOrder() {
        val base = board.withPage(page.copy(elements = listOf(first, second)))
        val placed = AffineElement.fromDocument(first, AffineTransform2D.rotation(37.0))
        val after = base.withPage(page.copy(elements = listOf(placed, edge, second)))
        val engine = DeltaEngine(ElementDigest())
        val deltaCodec = DocumentDeltaCodec()
        val delta = engine.between(base, after)
        val encoded = deltaCodec.encode(delta)
        assertEquals(DocumentCodec.VERSION, JSONObject(encoded).getInt("documentVersion"))
        val restored = deltaCodec.decode(encoded)
        assertEquals(delta, restored)
        assertEquals(after, engine.apply(base, restored).board)
        rejected { deltaCodec.decode(JSONObject(encoded).put("documentVersion", 4).toString()) }
        val affineOnly = engine.between(base, base.withPage(page.copy(elements = listOf(placed, second))))
        val legacy = JSONObject(deltaCodec.encode(affineOnly)).put("documentVersion", 4)
        assertEquals(affineOnly, deltaCodec.decode(legacy.toString()))
    }

    @Test fun targetRemovalAndDetachmentApplyAsOneDeltaWithoutAnIntermediateDanglingPage() {
        val session = DocumentSession(board)
        session.delete(setOf(first.id))
        val after = session.board.copy(updatedAt = board.updatedAt)
        val engine = DeltaEngine(ElementDigest())
        val deltaCodec = DocumentDeltaCodec()
        val delta = deltaCodec.decode(deltaCodec.encode(engine.between(board, after)))
        assertEquals(2, delta.pages.single().objects.size)
        assertEquals(after, engine.apply(board, delta).board)
        val incomplete = delta.copy(
            pages = delta.pages.map { mutation ->
                mutation.copy(objects = mutation.objects.filter { it.id != edge.id })
            }
        )
        rejected { engine.apply(board, incomplete) }
        assertEquals(page.elements, board.activePage.elements)
    }

    @Test fun editableBackupPreservesConnectionsAndStoresTheirMediaAttachmentOnce() {
        val media = MediaElement(id = "media", bounds = Bounds(200f, 100f, 300f, 200f), assetId = "${newId()}.png")
        val toMedia = edge.copy(end = ConnectorEnd.Bound(media.id, ConnectionPort.LEFT))
        val original = board.withPage(page.copy(elements = listOf(first, toMedia, media)))
        val data = byteArrayOf(1, 2, 3, 4)
        val output = ByteArrayOutputStream()
        BoardArchive().write(listOf(original), output) { ByteArrayInputStream(data) }
        val restored = BoardArchive().readContents(ByteArrayInputStream(output.toByteArray()))
        assertEquals(listOf(original), restored.boards)
        assertEquals(setOf(media.assetId), restored.assets.keys)
        assertArrayEquals(data, restored.assets.getValue(media.assetId))
        assertEquals(line(original.activePage, toMedia), line(restored.boards.single().activePage, toMedia))
    }

    @Test fun cyclesBetweenDistinctNodesRemainValidWithoutRecursiveConnectionTargets() {
        val third = first.copy(id = "third", bounds = Bounds(400f, 200f, 500f, 300f))
        val secondEdge = edge.copy(
            id = "second-edge",
            start = ConnectorEnd.Bound(second.id, ConnectionPort.RIGHT),
            end = ConnectorEnd.Bound(third.id, ConnectionPort.LEFT)
        )
        val thirdEdge = edge.copy(
            id = "third-edge",
            start = ConnectorEnd.Bound(third.id, ConnectionPort.TOP),
            end = ConnectorEnd.Bound(first.id, ConnectionPort.BOTTOM)
        )
        val cycle = board.withPage(page.copy(elements = listOf(first, edge, second, secondEdge, third, thirdEdge)))
        assertEquals(cycle, codec.decode(codec.encode(cycle)))
    }

    private fun rejected(action: () -> Any?) {
        try {
            action()
            fail("Expected invalid connection data")
        } catch (_: IllegalArgumentException) { } catch (_: JSONException) { }
    }
}
