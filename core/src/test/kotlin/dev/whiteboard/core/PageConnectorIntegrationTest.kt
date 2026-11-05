package dev.whiteboard.core

import dev.whiteboard.core.editor.*
import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class PageConnectorIntegrationTest {
    private val first = Note(id = "first", bounds = Bounds(0f, 0f, 100f, 100f), text = "A")
    private val second = Note(id = "second", bounds = Bounds(200f, 100f, 300f, 200f), text = "B")
    private val edge = Connector(
        id = "edge",
        start = ConnectorEnd.Bound(first.id, ConnectionPort.RIGHT),
        end = ConnectorEnd.Bound(second.id, ConnectionPort.LEFT)
    )
    private val page = Page(id = "page", elements = listOf(first, edge, second))
    private fun session(page: Page = this.page, budget: Long = 32L * 1024 * 1024) =
        DocumentSession(Board(name = "Connections", pages = listOf(page)), historyByteLimit = budget)
    private fun line(page: Page, edge: Connector = this.edge) = page.geometry.resolve(edge) as Line

    @Test fun canonicalPickingBoundsAndRenderGeometryShareTheSameResolvedLine() {
        val resolved = line(page)
        assertEquals(Point(100f, 50f), resolved.start)
        assertEquals(Point(200f, 150f), resolved.end)
        assertEquals(resolved.renderBounds, page.geometry.renderBounds(edge))
        assertEquals(resolved.selectionBounds, page.geometry.selectionBounds(edge))
        assertSame(edge, page.geometry.topmostAt(Point(150f, 100f), 0f))
        assertEquals(Bounds(0f, 0f, 300f, 200f), page.contentBounds)
        assertFalse(page.geometry.connectionPorts().supports(edge.id))
        assertEquals(listOf("first", "edge", "second"), page.elements.map { it.id })
    }

    @Test fun metadataUsesTheSameLineAndAStaleConnectionCannotReadANewDefinitionsGeometry() {
        val before = line(page)
        val metadata = page.copy(name = "Renamed", viewport = Viewport(scale = 3f), background = Background.GRID)
        assertSame(before, line(metadata))
        assertSame(metadata, metadata.geometry.page)
        val changed = edge.copy(end = ConnectorEnd.Free(Point(500f, 600f)))
        val updated = page.geometry.preview(listOf(changed)).page
        assertThrows(IllegalArgumentException::class.java) { line(updated, edge) }
        assertThrows(IllegalArgumentException::class.java) { line(page, changed) }
        assertEquals(Point(500f, 600f), line(updated, changed).end)
        assertEquals(Point(200f, 150f), before.end)
    }

    @Test fun previewFollowsTheMovedTargetAndCancellationKeepsTheOriginalSnapshot() {
        val moved = first.copy(bounds = Bounds(50f, 60f, 150f, 160f))
        val preview = page.geometry.preview(listOf(moved)).page
        assertEquals(Point(150f, 110f), line(preview).start)
        assertSame(edge, preview.elements[1])
        assertEquals(Point(100f, 50f), line(page).start)
        assertSame(first, page.elements.first())
    }

    @Test fun movingAndResizingATargetFollowAutomaticallyAndUndoRestoresTheExactReferences() {
        val session = session()
        assertTrue(session.transformBy(first.id, Point(20f, 30f), 2f))
        assertEquals(Point(170f, 80f), line(session.page).start)
        assertSame(edge, session.page.elements[1])
        session.undo()
        assertEquals(page.elements, session.page.elements)
        assertEquals(Point(100f, 50f), line(session.page).start)
        session.redo()
        assertEquals(Point(170f, 80f), line(session.page).start)
    }

    @Test fun deletingATargetDetachesAtItsLastPointInTheSameLayerPreservingUndoEntry() {
        val session = session()
        session.delete(setOf(first.id))
        val detached = session.page.elements.first() as Connector
        assertEquals(listOf(edge.id, second.id), session.page.elements.map { it.id })
        assertEquals(ConnectorEnd.Free(Point(100f, 50f)), detached.start)
        assertEquals(edge.end, detached.end)
        session.undo()
        assertEquals(page.elements, session.page.elements)
        assertFalse(session.canUndo)
        session.redo()
        assertEquals(listOf(detached, second), session.page.elements)
    }

    @Test fun deletingBothTargetsPreservesAFreeConnectionAndUndoRestoresBothBindings() {
        val session = session()
        session.delete(setOf(first.id, second.id))
        val detached = session.page.elements.single() as Connector
        assertTrue(detached.start is ConnectorEnd.Free && detached.end is ConnectorEnd.Free)
        assertEquals(line(page), line(session.page, detached))
        session.undo()
        assertEquals(page.elements, session.page.elements)
        assertFalse(session.dirty)
    }

    @Test fun deletingAConnectionDoesNotRemoveItsTargets() {
        val session = session()
        session.delete(setOf(edge.id))
        assertEquals(listOf(first, second), session.page.elements)
        session.undo()
        assertEquals(page.elements, session.page.elements)
    }

    @Test fun ungroupingKeepsChildBindingsAndDetachesOnlyTheRemovedGroupIdentity() {
        val group = ElementGroup(id = "group", children = listOf(first, second))
        val toGroup = edge.copy(id = "group-edge", start = ConnectorEnd.Bound(group.id, ConnectionPort.BOTTOM))
        val original = page.copy(elements = listOf(group, edge, toGroup))
        val session = session(original)
        session.ungroup(setOf(group.id))
        assertEquals(listOf(first.id, second.id, edge.id, toGroup.id), session.page.elements.map { it.id })
        assertEquals(edge, session.page.elements[2])
        val detached = session.page.elements[3] as Connector
        assertEquals(ConnectorEnd.Free(Point(150f, 200f)), detached.start)
        session.undo()
        assertEquals(original.elements, session.page.elements)
        session.redo()
        assertEquals(detached, session.page.elements.last())
    }

    @Test fun flatteningAnOldGroupRetainsChildPortsButDetachesItsOldFrameAtomically() {
        val third = second.copy(id = "third", bounds = Bounds(500f, 0f, 600f, 100f))
        val group = ElementGroup(id = "group", children = listOf(first, second))
        val toGroup = edge.copy(
            start = ConnectorEnd.Bound(group.id, ConnectionPort.RIGHT),
            end = ConnectorEnd.Bound(third.id, ConnectionPort.LEFT)
        )
        val original = page.copy(elements = listOf(group, toGroup, third))
        val session = session(original)
        val regrouped = session.group(setOf(group.id, third.id))!!
        val detached = session.page.elements.filterIsInstance<Connector>().single()
        assertEquals(ConnectorEnd.Free(Point(300f, 100f)), detached.start)
        assertEquals(toGroup.end, detached.end)
        assertEquals(setOf(first.id, second.id, third.id), regrouped.children.map { it.id }.toSet())
        session.undo()
        assertEquals(original.elements, session.page.elements)
        assertFalse(session.canUndo)
    }

    @Test fun bothErasersDetachAnAutomaticallyCollapsedGroupAndRestoreTheStrokeOnUndo() {
        val stroke = Stroke(id = "stroke", points = listOf(Point(120f, 0f), Point(150f, 50f)), style = InkStyle())
        val group = ElementGroup(id = "group", children = listOf(first, stroke))
        val toGroup = edge.copy(start = ConnectorEnd.Bound(group.id, ConnectionPort.RIGHT))
        val original = page.copy(elements = listOf(group, toGroup, second))
        val position = line(original, toGroup).start
        listOf(false, true).forEach { partial ->
            val session = session(original)
            if (partial) assertTrue(session.eraseParts(listOf(StrokeCut(stroke, emptyList())))) else session.eraseStrokes(setOf(stroke.id))
            assertEquals(first, session.page.elements.first())
            assertEquals(ConnectorEnd.Free(position), (session.page.elements[1] as Connector).start)
            session.undo()
            assertEquals(original.elements, session.page.elements)
            assertFalse(session.canUndo)
        }
    }

    @Test fun duplicatePagePreservesInterleavedLayersAndRemapsTheWholeGraph() {
        val session = session()
        session.duplicatePage(page.id)
        val copied = session.page
        assertEquals(listOf(Note::class, Connector::class, Note::class), copied.elements.map { it::class })
        val connection = copied.elements[1] as Connector
        assertEquals(ConnectorEnd.Bound(copied.elements[0].id, ConnectionPort.RIGHT), connection.start)
        assertEquals(ConnectorEnd.Bound(copied.elements[2].id, ConnectionPort.LEFT), connection.end)
        assertEquals(line(page).start, line(copied, connection).start)
        assertEquals(line(page).end, line(copied, connection).end)
        assertTrue(copied.elements.none { it.id in page.elements.map { original -> original.id } })
        session.undo()
        assertEquals(listOf(page), session.board.pages)
        session.redo()
        assertEquals(copied, session.page)
    }

    @Test fun partialClipboardCopiesNeverBindToAnOriginalIdOnAnotherPage() {
        val clipboard = ObjectClipboard()
        clipboard.copy(page, setOf(first.id, edge.id))
        val copies = clipboard.paste()
        assertEquals(listOf(Note::class, Connector::class), copies.map { it::class })
        val unrelated = second.copy(bounds = Bounds(-500f, -500f, -400f, -400f))
        val destination = Page(elements = listOf(unrelated) + copies)
        val copied = copies.last() as Connector
        assertEquals(ConnectorEnd.Bound(copies.first().id, ConnectionPort.RIGHT), copied.start)
        assertEquals(ConnectorEnd.Free(Point(224f, 174f)), copied.end)
        assertEquals(Point(124f, 74f), line(destination, copied).start)
        assertEquals(Point(224f, 174f), line(destination, copied).end)
    }

    @Test fun aConnectionCannotBeGroupedOrTransformedWithoutItsBoundTargets() {
        val session = session()
        assertNull(session.group(setOf(first.id, edge.id)))
        assertFalse(session.transformBy(edge.id, Point(20f, 30f), 2f))
        assertFalse(session.rotate(setOf(edge.id), 90.0))
        assertFalse(session.align(setOf(first.id, edge.id), ObjectAlignment.LEFT))
        assertEquals(page, session.page)
        assertFalse(session.canUndo)
    }

    @Test fun rotatingTheWholeGraphPreservesPortReferencesAndUsesOneUndo() {
        val session = session()
        val turn = AffineTransform2D.rotation(90.0, Bounds(0f, 0f, 300f, 200f).center)
        assertTrue(session.rotate(page.elements.map { it.id }.toSet(), 90.0))
        assertEquals(edge, session.page.elements[1])
        assertEquals(turn.map(line(page).start), line(session.page).start)
        assertEquals(turn.map(line(page).end), line(session.page).end)
        session.undo()
        assertEquals(page.elements, session.page.elements)
        assertFalse(session.canUndo)
    }

    @Test fun freeConnectionsCanMoveIndependentlyWithTheirStyleUnchanged() {
        val free = edge.copy(start = ConnectorEnd.Free(Point(0f, 0f)), end = ConnectorEnd.Free(Point(100f, 100f)))
        val session = session(page.copy(elements = listOf(free)))
        assertTrue(session.transformBy(free.id, Point(20f, 30f), 1f))
        val moved = session.page.elements.single() as Connector
        assertEquals(ConnectorEnd.Free(Point(20f, 30f)), moved.start)
        assertEquals(ConnectorEnd.Free(Point(120f, 130f)), moved.end)
        assertEquals(free.style, moved.style)
        session.undo()
        assertEquals(free, session.page.elements.single())
    }

    @Test fun invalidTargetsAndBindingsToOtherConnectionsAreRejectedBeforeAPageCanEscape() {
        assertThrows(IllegalArgumentException::class.java) { page.copy(elements = listOf(first, edge)) }
        val toEdge = edge.copy(id = "other-edge", start = ConnectorEnd.Bound(edge.id, ConnectionPort.CENTER))
        assertThrows(IllegalArgumentException::class.java) { page.copy(elements = page.elements + toEdge) }
        val stroke = Stroke(id = first.id, points = listOf(Point(0f, 0f)), style = InkStyle())
        assertThrows(IllegalArgumentException::class.java) { page.copy(elements = listOf(stroke, edge, second)) }
        val session = session()
        val revision = session.revision
        assertThrows(IllegalArgumentException::class.java) {
            session.replace(edge.copy(end = ConnectorEnd.Bound("missing", ConnectionPort.LEFT)))
        }
        assertEquals(page.elements, session.page.elements)
        assertEquals(revision, session.revision)
        assertFalse(session.canUndo)
    }

    @Test fun editsOnAConnectedPageCannotDropTheirOwnUndoWhenTheBudgetIsTooSmall() {
        val session = session(budget = 100L)
        assertThrows(DocumentFailure::class.java) { session.delete(setOf(first.id)) }
        assertThrows(DocumentFailure::class.java) { session.transformBy(first.id, Point(20f, 0f), 1f) }
        assertEquals(page.elements, session.page.elements)
        assertEquals(0L, session.revision)
        assertFalse(session.canUndo)
    }
}
