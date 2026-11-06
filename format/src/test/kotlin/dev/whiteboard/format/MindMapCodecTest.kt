package dev.whiteboard.format

import dev.whiteboard.core.cloud.*
import dev.whiteboard.core.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MindMapCodecTest {
    private val codec = DocumentCodec()
    private val root = Note(id = "root", bounds = Bounds(0f, 0f, 100f, 80f), text = "Tema Ћирилица")
    private val child = root.copy(id = "child", bounds = Bounds(200f, 100f, 300f, 180f), text = "Dete", color = 0xFF123456.toInt())
    private val edge =
        Connector(
            id = "edge",
            start = ConnectorEnd.Bound(root.id, ConnectionPort.RIGHT),
            end = ConnectorEnd.Bound(child.id, ConnectionPort.LEFT)
        )
    private val tree = MindMapTree("map", listOf(MindMapNode(root.id, collapsed = true), MindMapNode(child.id, root.id, edge.id)))
    private val page = Page(elements = listOf(root, edge, child), mindMaps = listOf(tree))
    private val board = Board(name = "Map", pages = listOf(page))
    private fun json() = JSONObject(codec.encode(board))
    private fun scene(source: JSONObject) = source.getJSONArray("pages").getJSONObject(0)
    private fun map(source: JSONObject) = scene(source).getJSONArray("mindMaps").getJSONObject(0)
    private fun rejected(action: () -> Unit) {
        assertThrows(Exception::class.java) { action() }
    }

    @Test fun boardAndStandalonePageRoundTripsKeepTopologyTextColorsHiddenObjectsAndOrder() {
        val encoded = json()
        assertEquals(6, encoded.getInt("version"))
        val data = map(encoded)
        assertEquals(setOf("id", "nodes"), data.keys().asSequence().toSet())
        assertTrue(data.getJSONArray("nodes").getJSONObject(0).isNull("parentId"))
        assertEquals(board, codec.decode(encoded.toString()))
        val restored = codec.decodePageJson(codec.encodePageJson(page))
        assertEquals(page, restored)
        assertEquals(listOf(root), restored.mindMapIndex?.visibleObjects)
        assertEquals(listOf(root, edge, child), restored.elements)
        assertEquals(child.color, (restored.elements.last() as Note).color)
        val rotated = AffineElement.fromDocument(child, AffineTransform2D.rotation(37.0, child.bounds.center))
        val affine = page.copy(elements = listOf(root, edge, rotated))
        assertEquals(affine, codec.decodePageJson(codec.encodePageJson(affine)))
    }

    @Test fun legacyVersionsRemainReadableAndSemanticFieldsCannotBeSmuggledIntoAnOldVersion() {
        val plain = board.withPage(page.copy(mindMaps = emptyList()))
        for (version in 1..5) {
            val objects = if (version < 5) listOf(root, child) else plain.activePage.elements
            val legacy = plain.withPage(plain.activePage.copy(elements = objects))
            assertEquals(legacy, codec.decode(JSONObject(codec.encode(legacy)).put("version", version).toString()))
            rejected { codec.decode(json().put("version", version).toString()) }
        }
        val legacyPage = JSONObject(codec.encodePageJson(plain.activePage)).apply { remove("version") }
        assertEquals(plain.activePage, codec.decodePageJson(legacyPage.toString()))
        rejected { codec.decodePageJson(JSONObject(codec.encodePageJson(page)).apply { remove("version") }.toString()) }
        listOf<Any>(7, "6", 6.5).forEach { version ->
            rejected { codec.decode(json().put("version", version).toString()) }
            rejected { codec.decodePageJson(JSONObject(codec.encodePageJson(page)).put("version", version).toString()) }
        }
        rejected { codec.decode(json().also { scene(it).put("version", 7) }.toString()) }
        rejected { codec.decode(json().also { scene(it).put("version", 5) }.toString()) }
    }

    @Test fun unknownMissingAndCoercedFieldsCannotChangeTheMeaningOfATree() {
        val edits: List<(JSONObject) -> Unit> = listOf(
            { map(it).put("title", "Duplicate content owner") },
            { map(it).put("id", 123) },
            { map(it).getJSONArray("nodes").getJSONObject(0).remove("parentId") },
            { map(it).getJSONArray("nodes").getJSONObject(0).put("collapsed", "true") },
            { map(it).getJSONArray("nodes").getJSONObject(1).put("id", 123) },
            { map(it).getJSONArray("nodes").getJSONObject(1).put("parentId", JSONObject.NULL) },
            { map(it).getJSONArray("nodes").getJSONObject(1).put("extra", true) },
            { scene(it).put("mindMaps", JSONObject.NULL) }
        )
        edits.forEach { edit ->
            val source = json()
            edit(source)
            rejected { codec.decode(source.toString()) }
        }
    }

    @Test fun duplicateDanglingCyclicAndIncorrectlyBoundDefinitionsAreRejected() {
        val edits: List<(JSONObject) -> Unit> = listOf(
            { scene(it).getJSONArray("mindMaps").put(JSONObject(map(it).toString())) },
            { map(it).getJSONArray("nodes").getJSONObject(1).put("id", root.id) },
            { map(it).getJSONArray("nodes").getJSONObject(1).put("parentId", "foreign") },
            { map(it).getJSONArray("nodes").getJSONObject(0).put("parentId", child.id).put("connectionId", "cycle-edge") },
            { map(it).getJSONArray("nodes").getJSONObject(1).put("connectionId", "foreign-edge") },
            { scene(it).getJSONArray("objects").getJSONObject(1).getJSONObject("start").put("targetId", child.id) }
        )
        edits.forEach { edit ->
            val source = json()
            edit(source)
            rejected { codec.decode(source.toString()) }
        }
    }

    @Test fun deeplyNestedLogicalTreesUseFlatJsonAndKeepHiddenDescendantsInEditableBackup() {
        val nodes = List(128) { index -> root.copy(id = "node-$index", text = "Node $index") }
        val edges =
            List(nodes.size - 1) { index ->
                edge.copy(
                    id = "edge-$index",
                    start = ConnectorEnd.Bound(nodes[index].id, ConnectionPort.RIGHT),
                    end = ConnectorEnd.Bound(
                        nodes[
                            index +
                                1
                        ].id,
                        ConnectionPort.LEFT
                    )
                )
            }
        val topology = MindMapTree(
            "deep-map",
            nodes.mapIndexed { index, note ->
                if (index == 0) MindMapNode(note.id, collapsed = true) else MindMapNode(note.id, nodes[index - 1].id, edges[index - 1].id)
            }
        )
        val original = board.withPage(page.copy(elements = nodes + edges, mindMaps = listOf(topology)))
        val output = ByteArrayOutputStream()
        BoardArchive().write(listOf(original), output)
        val restored = BoardArchive().read(ByteArrayInputStream(output.toByteArray())).single()
        assertEquals(original, restored)
        assertEquals(128, restored.activePage.mindMaps.single().pathTo(nodes.last().id).size)
        assertEquals(1, restored.activePage.mindMapIndex?.visibleObjects?.size)
    }

    @Test fun collapseOnlyDeltaIsExplicitAndPreservesTheCanonicalGeometrySnapshot() {
        val expanded = board.withPage(page.copy(mindMaps = listOf(tree.withCollapsed(root.id, false))))
        val engine = DeltaEngine(ElementDigest())
        val change = engine.between(board, expanded)
        assertFalse(change.empty)
        assertTrue(change.pages.single().objects.isEmpty())
        assertNotNull(change.pages.single().mindMaps)
        val transport = DocumentDeltaCodec()
        val encoded = transport.encode(change)
        assertEquals(2, JSONObject(encoded).getInt("version"))
        assertEquals(6, JSONObject(encoded).getInt("documentVersion"))
        val restored = transport.decode(encoded)
        assertEquals(change, restored)
        val applied = engine.apply(board, restored).board
        assertEquals(expanded, applied)
        assertSame(page.elements, applied.activePage.elements)
        assertFalse(page.sharesSceneWith(applied.activePage))
        val independent = board.withPage(
            page.copy(
                elements = page.elements.map {
                    if (it.id ==
                        child.id
                    ) {
                        child.copy(text = "Independent edit")
                    } else {
                        it
                    }
                }
            )
        )
        val merged = engine.apply(independent, restored).board
        assertEquals("Independent edit", (merged.activePage.elements.last() as Note).text)
        assertEquals(expanded.activePage.mindMaps, merged.activePage.mindMaps)
    }

    @Test fun semanticConflictsAndMalformedTransportCannotBeAcknowledgedAsSuccessfulEdits() {
        val engine = DeltaEngine(ElementDigest())
        val expanded = board.withPage(page.copy(mindMaps = listOf(tree.withCollapsed(root.id, false))))
        val delta = engine.between(board, expanded)
        val other = board.withPage(page.copy(mindMaps = listOf(tree.withCollapsed(child.id, true))))
        assertThrows(DeltaConflict::class.java) { engine.apply(other, delta) }
        val transport = DocumentDeltaCodec()
        val encoded = transport.encode(delta)
        rejected { transport.decode(JSONObject(encoded).put("version", 1).toString()) }
        rejected { transport.decode(JSONObject(encoded).put("documentVersion", 5).toString()) }
        rejected { transport.decode(JSONObject(encoded).put("version", "2").toString()) }
        val invalid = JSONObject(encoded)
        invalid.getJSONArray("pages").getJSONObject(0).put("mindMaps", JSONObject.NULL)
        rejected { transport.decode(invalid.toString()) }
        assertEquals(tree, board.activePage.mindMaps.single())
    }

    @Test fun createdPageDeltaCarriesTheExplicitPageVersionAndLegacyTransportStillReads() {
        val before = Board(name = board.name)
        val after = before.copy(pages = before.pages + page)
        val engine = DeltaEngine(ElementDigest())
        val transport = DocumentDeltaCodec()
        val encoded = JSONObject(transport.encode(engine.between(before, after)))
        val created = encoded.getJSONArray("pages").getJSONObject(0).getJSONObject("created")
        assertEquals(6, created.getInt("version"))
        assertEquals(after, engine.apply(before, transport.decode(encoded.toString())).board)
        created.put("version", 7)
        rejected { transport.decode(encoded.toString()) }
        val plainAfter = before.withPage(before.activePage.copy(name = "Legacy rename"))
        val legacy = JSONObject(transport.encode(engine.between(before, plainAfter))).put("version", 1).put("documentVersion", 5)
        assertEquals(plainAfter, engine.apply(before, transport.decode(legacy.toString())).board)
    }

    @Test fun semanticMetadataUsesTheSamePreflightBudgetForReadingAndWriting() {
        val limited = DocumentCodec(allocationBudget = 512)
        val encoded = codec.encode(board)
        listOf<() -> Unit>({ limited.encode(board) }, { limited.decode(encoded) }, { limited.encodePageJson(page) }).forEach { operation ->
            val failure = assertThrows(DocumentFailure::class.java) { operation() }
            assertEquals(DocumentError.DOCUMENT_LIMIT, failure.error)
        }
        assertEquals(board, codec.decode(encoded))
    }

    @Test fun invalidOrRepeatedMetadataCannotBeEncodedAsAnAcceptedOperation() {
        val transport = DocumentDeltaCodec()
        val duplicate = PageMutation(page.id, mindMaps = ValueChange(emptyList(), listOf(tree, tree)))
        rejected { transport.encode(DocumentDelta(pages = listOf(duplicate))) }
        val shared = PageMutation(page.id, mindMaps = ValueChange(emptyList(), listOf(tree, MindMapTree("other-map", tree.nodes))))
        rejected { transport.encode(DocumentDelta(pages = listOf(shared))) }
    }

    @Test fun fractionalExponentAndRoundedVersionHeadersCannotBeCoercedIntoIntegerVersions() {
        val document = codec.encode(board)
        val standalone = codec.encodePageJson(page)
        for (token in listOf("6.0", "6e0", "6.0000000000000000000001", "4294967302")) {
            rejected { codec.decode(document.replace("\"version\":6", "\"version\":$token")) }
            rejected { codec.decodePageJson(standalone.replace("\"version\":6", "\"version\":$token")) }
        }
        val operation = DocumentDeltaCodec().encode(DocumentDelta(name = ValueChange("Before", "After")))
        rejected { DocumentDeltaCodec().decode(operation.replace("\"version\":2", "\"version\":2.000000000000000000001")) }
    }
}
