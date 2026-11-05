package dev.whiteboard.core

import dev.whiteboard.core.editor.ConnectionTargetRemoval
import dev.whiteboard.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ConnectionTargetRemovalTest {
    private val first = Note(id = "first", bounds = Bounds(0f, 0f, 100f, 100f), text = "A")
    private val second = Note(id = "second", bounds = Bounds(200f, 100f, 300f, 200f), text = "B")
    private val page = Page(id = "page", elements = listOf(first, second))
    private val edge = Connector(
        id = "edge",
        start = ConnectorEnd.Bound(first.id, ConnectionPort.RIGHT),
        end = ConnectorEnd.Bound(second.id, ConnectionPort.LEFT)
    )

    @Test fun removingOneTargetDetachesOnlyItsEndAtTheLastWorldPosition() {
        val after = page.copy(elements = listOf(second))
        val changes = ConnectionTargetRemoval.plan(page, after, listOf(edge))
        assertEquals(edge, changes.single().before)
        assertEquals(ConnectorEnd.Free(Point(100f, 50f)), changes.single().after.start)
        assertEquals(edge.end, changes.single().after.end)
        assertEquals(edge.style, changes.single().after.style)
        val resolved = PageConnectionGeometry(after, listOf(changes.single().after)).line(edge.id)
        assertEquals(Point(100f, 50f), resolved.start)
        assertEquals(Point(200f, 150f), resolved.end)
        assertEquals(listOf(first, second), page.elements)
    }

    @Test fun removingBothTargetsKeepsTheWholeConnectionAsAFreeLine() {
        val changes = ConnectionTargetRemoval.plan(page, page.copy(elements = emptyList()), listOf(edge))
        assertEquals(ConnectorEnd.Free(Point(100f, 50f)), changes.single().after.start)
        assertEquals(ConnectorEnd.Free(Point(200f, 150f)), changes.single().after.end)
        assertEquals(edge.id, changes.single().after.id)
        assertEquals(edge.arrow, changes.single().after.arrow)
    }

    @Test fun movingATargetOrEditingItsContentDoesNotDetachItsStableIdentity() {
        val moved = second.copy(bounds = Bounds(500f, 600f, 700f, 800f), text = "Updated")
        val after = page.copy(elements = listOf(first, moved), viewport = Viewport(scale = 4f))
        assertTrue(ConnectionTargetRemoval.plan(page, after, listOf(edge)).isEmpty())
        assertEquals(Point(500f, 700f), PageConnectionGeometry(after, listOf(edge)).line(edge.id).end)
        assertEquals(ConnectorEnd.Bound("second", ConnectionPort.LEFT), edge.end)
    }

    @Test fun ungroupingDetachesTheRemovedGroupFrameButKeepsStableChildPorts() {
        val group = ElementGroup(id = "group", children = page.elements)
        val grouped = page.copy(elements = listOf(group))
        val toGroup = edge.copy(id = "group-edge", start = ConnectorEnd.Bound(group.id, ConnectionPort.BOTTOM))
        val changes = ConnectionTargetRemoval.plan(grouped, page, listOf(edge, toGroup))
        assertEquals(1, changes.size)
        assertEquals(toGroup, changes.single().before)
        assertEquals(ConnectorEnd.Free(Point(150f, 200f)), changes.single().after.start)
        assertEquals(toGroup.end, changes.single().after.end)
        assertEquals(Point(100f, 50f), PageConnectionGeometry(page, listOf(edge)).line(edge.id).start)
    }

    @Test fun collapsingAGroupAfterErasingItsStrokeChildDetachesOnlyTheGroupPort() {
        val stroke = Stroke(id = "stroke", points = listOf(Point(150f, 50f)), style = InkStyle())
        val group = ElementGroup(id = "group", children = listOf(first, stroke))
        val grouped = page.copy(elements = listOf(group, second))
        val toGroup = edge.copy(start = ConnectorEnd.Bound(group.id, ConnectionPort.RIGHT))
        val previous = PageConnectionGeometry(grouped, listOf(toGroup)).line(edge.id).start
        val changes = ConnectionTargetRemoval.plan(grouped, page, listOf(toGroup))
        assertEquals(ConnectorEnd.Free(previous), changes.single().after.start)
        assertEquals(toGroup.end, changes.single().after.end)
    }

    @Test fun reusingATargetIdForAnUnsupportedKindCannotLeaveADanglingBinding() {
        val line = Line(id = first.id, start = Point(0f, 0f), end = Point(100f, 100f), style = InkStyle())
        val changes = ConnectionTargetRemoval.plan(page, page.copy(elements = listOf(line, second)), listOf(edge))
        assertEquals(ConnectorEnd.Free(Point(100f, 50f)), changes.single().after.start)
    }

    @Test fun existingDanglingReferencesAndCrossPageEditsAreRejectedRatherThanRepaired() {
        val missing = edge.copy(start = ConnectorEnd.Bound("missing", ConnectionPort.TOP))
        assertThrows(IllegalArgumentException::class.java) { ConnectionTargetRemoval.plan(page, page, listOf(missing)) }
        assertThrows(IllegalArgumentException::class.java) { ConnectionTargetRemoval.plan(page, page.copy(id = "other"), listOf(edge)) }
        assertEquals(ConnectorEnd.Bound("first", ConnectionPort.RIGHT), edge.start)
    }

    @Test fun cancelledPlanningCannotPublishPartialChangesOrModifyEitherPage() {
        val edges = List(100) { edge.copy(id = "edge-$it") }
        val after = page.copy(elements = emptyList())
        var visits = 0
        assertThrows(CancellationException::class.java) {
            ConnectionTargetRemoval.plan(page, after, edges) { if (++visits == 117) throw CancellationException() }
        }
        assertEquals(117, visits)
        assertEquals(listOf(first, second), page.elements)
        assertTrue(after.elements.isEmpty())
        assertTrue(edges.all { it.start is ConnectorEnd.Bound && it.end is ConnectorEnd.Bound })
    }

    @Test fun deletedConnectionsAreExcludedAndFreeEndsNeedNoTargetChange() {
        assertTrue(ConnectionTargetRemoval.plan(page, page.copy(elements = emptyList()), emptyList()).isEmpty())
        val free = edge.copy(start = ConnectorEnd.Free(Point(-10f, -20f)), end = ConnectorEnd.Free(Point(30f, 40f)))
        assertTrue(ConnectionTargetRemoval.plan(page, page.copy(elements = emptyList()), listOf(free)).isEmpty())
    }

    @Test fun unsafeRemainingGeometryRejectsTheWholePlan() {
        val unsafe = Shape(
            id = second.id,
            bounds = Bounds(999_999_488f, 0f, 1_000_000_000f, 100f),
            kind = Tool.RECTANGLE,
            style = InkStyle(width = 200f)
        )
        assertThrows(IllegalArgumentException::class.java) {
            ConnectionTargetRemoval.plan(page, page.copy(elements = listOf(unsafe)), listOf(edge))
        }
        assertEquals(listOf(first, second), page.elements)
    }
}
