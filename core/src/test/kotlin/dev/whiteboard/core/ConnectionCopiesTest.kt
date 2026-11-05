package dev.whiteboard.core

import dev.whiteboard.core.editor.ConnectionCopies
import dev.whiteboard.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ConnectionCopiesTest {
    private val first = Note(id = "first", bounds = Bounds(0f, 0f, 100f, 100f), text = "A")
    private val second = Shape(id = "second", bounds = Bounds(200f, 100f, 300f, 200f), kind = Tool.DIAMOND, style = InkStyle())
    private val page = Page(elements = listOf(first, second))
    private val edge = Connector(
        id = "edge",
        start = ConnectorEnd.Bound(first.id, ConnectionPort.RIGHT),
        end = ConnectorEnd.Bound(second.id, ConnectionPort.TOP),
        style = InkStyle(width = 7f, opacity = 0.4f)
    )

    @Test fun copyingBothTargetsRebindsBothEndsAndPreservesStyleAndGeometry() {
        val copy = ConnectionCopies.duplicate(page, listOf(edge), setOf("first", "second", "edge"), Point(24f, 48f))
        val copied = copy.connections.single()
        assertEquals(ConnectorEnd.Bound(copy.identities.getValue("first"), ConnectionPort.RIGHT), copied.start)
        assertEquals(ConnectorEnd.Bound(copy.identities.getValue("second"), ConnectionPort.TOP), copied.end)
        assertEquals(edge.style, copied.style)
        assertEquals(edge.arrow, copied.arrow)
        val resolved = PageConnectionGeometry(Page(elements = copy.elements), copy.connections).line(copied.id)
        assertEquals(Point(124f, 98f), resolved.start)
        assertEquals(Point(274f, 148f), resolved.end)
        assertTrue(copy.identities.values.none { it in setOf("first", "second", "edge") })
        assertEquals(listOf(first, second), page.elements)
    }

    @Test fun anUncopiedTargetBecomesAFreeEndAtItsLastWorldPointPlusOffset() {
        val copy = ConnectionCopies.duplicate(page, listOf(edge), setOf("first", "edge"), Point(24f, 24f))
        val copied = copy.connections.single()
        assertEquals(ConnectorEnd.Bound(copy.identities.getValue("first"), ConnectionPort.RIGHT), copied.start)
        assertEquals(ConnectorEnd.Free(Point(274f, 124f)), copied.end)
        assertFalse(copy.identities.containsKey("second"))
        val other = second.copy(bounds = Bounds(-500f, -500f, -400f, -400f))
        val destination = Page(elements = copy.elements + other)
        assertEquals(Point(274f, 124f), PageConnectionGeometry(destination, copy.connections).line(copied.id).end)
    }

    @Test fun copyingOnlyTheConnectionDetachesBothEndsWithoutMovingItsSourceTargets() {
        val copy = ConnectionCopies.duplicate(page, listOf(edge), setOf("edge"), Point(-10f, 20f))
        assertTrue(copy.elements.isEmpty())
        assertEquals(setOf("edge"), copy.identities.keys)
        val copied = copy.connections.single()
        assertEquals(ConnectorEnd.Free(Point(90f, 70f)), copied.start)
        assertEquals(ConnectorEnd.Free(Point(240f, 120f)), copied.end)
        assertEquals(first, page.elements.first())
    }

    @Test fun freeEndsStayFreeAndAreTranslatedExactlyOnce() {
        val mixed = edge.copy(end = ConnectorEnd.Free(Point(450f, 500f)))
        val copy = ConnectionCopies.duplicate(page, listOf(mixed), setOf("first", "edge"), Point(30f, 40f))
        assertEquals(ConnectorEnd.Free(Point(480f, 540f)), copy.connections.single().end)
        val free = mixed.copy(start = ConnectorEnd.Free(Point(-30f, -40f)))
        val freeCopy = ConnectionCopies.duplicate(page, listOf(free), setOf("edge"))
        assertEquals(free.copy(id = freeCopy.identities.getValue("edge")), freeCopy.connections.single())
    }

    @Test fun groupAndChildPortsBothUseTheFullChildIdentityMap() {
        val group = ElementGroup(id = "group", children = page.elements.map { it as Element })
        val grouped = page.copy(elements = listOf(group))
        val toGroup = edge.copy(id = "group-edge", start = ConnectorEnd.Bound(group.id, ConnectionPort.BOTTOM))
        val copy = ConnectionCopies.duplicate(grouped, listOf(edge, toGroup), setOf("group", "edge", "group-edge"))
        assertEquals(setOf("group", "first", "second", "edge", "group-edge"), copy.identities.keys)
        assertEquals(ConnectorEnd.Bound(copy.identities.getValue("first"), ConnectionPort.RIGHT), copy.connections.first().start)
        assertEquals(ConnectorEnd.Bound(copy.identities.getValue("group"), ConnectionPort.BOTTOM), copy.connections.last().start)
        val geometry = PageConnectionGeometry(Page(elements = copy.elements), copy.connections)
        assertEquals(Point(150f, 200f), geometry.line(copy.connections.last().id).start)
    }

    @Test fun rotatedChildAndUncopiedGroupFrameHaveTheCorrectIndependentPolicies() {
        val posed = AffineElement.fromDocument(second, AffineTransform2D.rotation(37.0))
        val group = ElementGroup(id = "group", children = listOf(first, posed))
        val grouped = page.copy(elements = listOf(group))
        val copy = ConnectionCopies.duplicate(grouped, listOf(edge), setOf("group", "edge"), Point(40f, 60f))
        val before = PageConnectionGeometry(grouped, listOf(edge)).line(edge.id)
        val after = PageConnectionGeometry(Page(elements = copy.elements), copy.connections).line(copy.connections.single().id)
        assertEquals(before.start.x + 40f, after.start.x, 0.001f)
        assertEquals(before.end.x + 40f, after.end.x, 0.001f)
        assertEquals(before.end.y + 60f, after.end.y, 0.001f)
        val detached = ConnectionCopies.duplicate(grouped, listOf(edge), setOf("edge"), Point(40f, 60f))
        assertEquals(ConnectorEnd.Free(before.end + Point(40f, 60f)), detached.connections.single().end)
    }

    @Test fun sourceIdsIncludingUnselectedObjectsAndEdgesCannotBeAllocatedToCopies() {
        listOf("first", "second", "edge", "", " ", "x".repeat(129)).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) {
                ConnectionCopies.duplicate(page, listOf(edge), setOf("first"), identity = { bad })
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ConnectionCopies.duplicate(page, listOf(edge), setOf("first", "edge"), identity = { "same-new-id" })
        }
    }

    @Test fun unknownSelectionAndMalformedGraphReferencesAreRejectedBeforeReturningACopy() {
        assertThrows(IllegalArgumentException::class.java) { ConnectionCopies.duplicate(page, listOf(edge), setOf("missing")) }
        val grouped = page.copy(elements = listOf(ElementGroup(children = page.elements.map { it as Element })))
        assertThrows(IllegalArgumentException::class.java) { ConnectionCopies.duplicate(grouped, listOf(edge), setOf("first")) }
        val dangling = edge.copy(end = ConnectorEnd.Bound("other-page-object", ConnectionPort.LEFT))
        assertThrows(IllegalArgumentException::class.java) { ConnectionCopies.duplicate(page, listOf(dangling), setOf("edge")) }
        assertThrows(IllegalArgumentException::class.java) { ConnectionCopies.duplicate(page, listOf(edge, edge), emptySet()) }
    }

    @Test fun cancelledCopyAndUnsafeDetachedEndCannotPublishAPartialGraph() {
        var allocations = 0
        assertThrows(CancellationException::class.java) {
            ConnectionCopies.duplicate(page, listOf(edge), setOf("first", "second", "edge"), identity = {
                if (++allocations == 3) throw CancellationException()
                "new-$allocations"
            })
        }
        val nearLimit = edge.copy(start = ConnectorEnd.Free(Point(999_999_872f, 0f)))
        assertThrows(IllegalArgumentException::class.java) {
            ConnectionCopies.duplicate(page, listOf(nearLimit), setOf("edge"), Point(256f, 0f))
        }
        assertEquals(listOf(first, second), page.elements)
        assertEquals("edge", edge.id)
    }

    @Test fun emptySelectionAndCopyingTargetsWithoutEdgesAreExplicitAndIndependent() {
        val empty = ConnectionCopies.duplicate(page, listOf(edge), emptySet())
        assertTrue(empty.elements.isEmpty() && empty.connections.isEmpty() && empty.identities.isEmpty())
        val firstCopy = ConnectionCopies.duplicate(page, listOf(edge), setOf("first", "second"))
        val nextCopy = ConnectionCopies.duplicate(page, listOf(edge), setOf("first", "second"))
        assertTrue(firstCopy.connections.isEmpty())
        assertEquals(2, firstCopy.elements.size)
        assertTrue(firstCopy.identities.values.toSet().intersect(nextCopy.identities.values.toSet()).isEmpty())
    }
}
