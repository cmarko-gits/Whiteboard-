package dev.whiteboard.core

import dev.whiteboard.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class PageGeometryTest {
    private val lower = Shape(id = "lower", bounds = Bounds(10f, 10f, 110f, 110f), kind = Tool.RECTANGLE, style = InkStyle())
    private val upper = Note(id = "upper", bounds = Bounds(60f, 60f, 160f, 160f), text = "Unchanged")
    private val page = Page(elements = listOf(lower, upper))

    @Test fun theTopmostActualContourWinsPickingAndOrderIsUnchanged() {
        assertSame(upper, page.geometry.topmostAt(Point(80f, 80f), 0f))
        assertSame(lower, page.geometry.topmostAt(Point(20f, 20f), 0f))
        assertNull(page.geometry.topmostAt(Point(300f, 300f), 0f))
        val diamond = lower.copy(kind = Tool.DIAMOND)
        val geometry = Page(elements = listOf(diamond)).geometry
        assertNull(geometry.topmostAt(Point(11f, 11f), 0f))
        assertSame(diamond, geometry.topmostAt(Point(60f, 60f), 0f))
    }

    @Test fun previewReplacesGeometryInTheOriginalLayerWithoutChangingTheDocument() {
        val moved = lower.copy(bounds = Bounds(40f, 40f, 140f, 140f))
        val preview = page.geometry.preview(listOf(moved))
        assertEquals(listOf(moved, upper), preview.page.elements)
        assertSame(upper, preview.topmostAt(Point(80f, 80f), 0f))
        assertEquals(listOf(lower, upper), page.elements)
        assertSame(lower, page.geometry.topmostAt(Point(20f, 20f), 0f))
        assertNull(preview.topmostAt(Point(20f, 20f), 0f))
    }

    @Test fun previewConnectionsUseTheirPreviewTargetAndCancelKeepsTheOriginalPort() {
        val edge = Connector(
            start = ConnectorEnd.Bound(lower.id, ConnectionPort.RIGHT),
            end = ConnectorEnd.Bound(upper.id, ConnectionPort.LEFT)
        )
        val moved = lower.copy(bounds = Bounds(210f, 10f, 310f, 110f))
        val original = PageConnectionGeometry(page, listOf(edge))
        val preview = PageConnectionGeometry(page.geometry.preview(listOf(moved)).page, listOf(edge))
        assertEquals(Point(310f, 60f), preview.line(edge.id).start)
        assertEquals(Point(110f, 60f), original.line(edge.id).start)
        assertEquals(edge.end, ConnectorEnd.Bound(upper.id, ConnectionPort.LEFT))
    }

    @Test fun viewportDoesNotChangeWorldBoundsOrPicking() {
        val zoomed = page.copy(viewport = Viewport(offset = Point(200f, -400f), scale = 7f))
        assertEquals(page.geometry.contentBounds, zoomed.geometry.contentBounds)
        assertSame(upper, zoomed.geometry.topmostAt(Point(80f, 80f), 0f))
        assertEquals(lower.renderBounds, zoomed.geometry.renderBounds(lower))
        assertEquals(lower.selectionBounds, zoomed.geometry.selectionBounds(lower))
    }

    @Test fun previewRejectsDuplicateUnknownAndCollidingChildIdentities() {
        assertThrows(IllegalArgumentException::class.java) { page.geometry.preview(listOf(lower, lower)) }
        assertThrows(IllegalArgumentException::class.java) { page.geometry.preview(listOf(lower.copy(id = "missing"))) }
        val child = lower.copy(id = "child")
        val group = ElementGroup(id = lower.id, children = listOf(child, upper.copy()))
        assertThrows(IllegalArgumentException::class.java) { page.geometry.preview(listOf(group)) }
        assertEquals(listOf(lower, upper), page.elements)
    }

    @Test fun invalidPreviewInkIsRejectedBeforeTheSnapshotCanBeUsed() {
        val invalid = lower.copy(bounds = Bounds(999_999_488f, 0f, 1_000_000_000f, 100f), style = InkStyle(width = 200f))
        assertThrows(IllegalArgumentException::class.java) {
            page.geometry.preview(listOf(invalid))
        }
        assertEquals(listOf(lower, upper), page.elements)
    }

    @Test fun cancellingPreviewPreparationDoesNotReplaceTheOriginalObjects() {
        var visits = 0
        assertThrows(CancellationException::class.java) {
            page.geometry.preview(listOf(lower.copy(bounds = Bounds(200f, 200f, 300f, 300f)))) {
                if (++visits == 2) throw CancellationException()
            }
        }
        assertSame(lower, page.elements.first())
        assertEquals(2, visits)
    }

    @Test fun emptyAndIdentityPreviewsReuseGeometryAndEmptyPagesHaveNoContentBounds() {
        assertSame(page.geometry, page.geometry.preview(emptyList()))
        assertSame(page.geometry, page.geometry.preview(listOf(lower)))
        assertNull(Page().contentBounds)
        assertEquals(page.contentBounds, page.geometry.contentBounds)
    }

    @Test fun invalidPickingTolerancesAreRejectedEvenWhenThePageIsEmpty() {
        listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { tolerance ->
            assertThrows(IllegalArgumentException::class.java) { Page().geometry.topmostAt(Point(0f, 0f), tolerance) }
            assertThrows(IllegalArgumentException::class.java) { page.geometry.hitTest(upper, Point(70f, 70f), tolerance) }
        }
    }
}
