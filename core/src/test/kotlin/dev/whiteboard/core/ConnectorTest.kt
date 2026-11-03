package dev.whiteboard.core

import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ConnectorTest {
    private val start = ConnectorEnd.Bound("first", ConnectionPort.RIGHT)
    private val end = ConnectorEnd.Bound("second", ConnectionPort.LEFT)

    @Test fun aConnectionRetainsExplicitPortsStyleAndIdentity() {
        val style = InkStyle(color = 0xFF123456.toInt(), width = 17f, opacity = 0.4f)
        val connector = Connector("edge", start, end, style, arrow = false)
        assertEquals(start, connector.start)
        assertEquals(end, connector.end)
        assertEquals("edge", connector.id)
        assertEquals(style, connector.style)
        assertFalse(connector.arrow)
    }

    @Test fun eitherEndOrBothEndsCanRemainFreeAfterDetachment() {
        val free = ConnectorEnd.Free(Point(12f, -20f))
        assertEquals(free, Connector(start = free, end = end).start)
        assertEquals(free, Connector(start = start, end = free).end)
        assertEquals(free, Connector(start = free, end = free).start)
    }

    @Test fun sameObjectLoopsAreRejectedEvenWhenPortsDiffer() {
        ConnectionPort.entries.forEach { port ->
            assertThrows(IllegalArgumentException::class.java) {
                Connector(start = start, end = ConnectorEnd.Bound("first", port))
            }
        }
    }

    @Test fun neitherEndCanReferToItsOwnConnectorIdentity() {
        assertThrows(IllegalArgumentException::class.java) { Connector("first", start, end) }
        assertThrows(IllegalArgumentException::class.java) { Connector("second", start, end) }
    }

    @Test fun invalidObjectIdentitiesAreRejectedAtConstructionAndCopy() {
        listOf("", " ", "\t\n", "x".repeat(129)).forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { Connector(id, start, end) }
            assertThrows(IllegalArgumentException::class.java) { ConnectorEnd.Bound(id, ConnectionPort.CENTER) }
        }
        val connector = Connector(start = start, end = end)
        assertThrows(IllegalArgumentException::class.java) { connector.copy(id = "first") }
        assertThrows(IllegalArgumentException::class.java) { connector.copy(end = start) }
        assertEquals(128, Connector("x".repeat(128), start, end).id.length)
    }

    @Test fun freePositionsRespectTheDocumentCoordinateLimit() {
        assertEquals(Point(1_000_000_000f, -1_000_000_000f), ConnectorEnd.Free(Point(1_000_000_000f, -1_000_000_000f)).point)
        listOf(Point(Float.MAX_VALUE, 0f), Point(0f, -Float.MAX_VALUE)).forEach { point ->
            assertThrows(IllegalArgumentException::class.java) { ConnectorEnd.Free(point) }
        }
        assertThrows(IllegalArgumentException::class.java) { ConnectorEnd.Free(Point(Float.NaN, 0f)) }
        assertThrows(IllegalArgumentException::class.java) { ConnectorEnd.Free(Point(0f, Float.POSITIVE_INFINITY)) }
    }

    @Test fun editingAConnectionDoesNotMutateItsPreviousValue() {
        val original = Connector(start = start, end = end)
        val edited = original.copy(end = ConnectorEnd.Free(Point(8f, 9f)), arrow = false, style = InkStyle(width = 7f))
        assertEquals(original.id, edited.id)
        assertEquals(end, original.end)
        assertTrue(original.arrow)
        assertEquals(3f, original.style.width, 0f)
        assertFalse(edited.arrow)
    }
}
