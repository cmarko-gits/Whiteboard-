package dev.whiteboard.core

import dev.whiteboard.core.model.*
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class PageSnapshotTest {
    private val note = Note(id = "target", bounds = Bounds(10f, 20f, 110f, 120f), text = "Original")
    private val page = Page(id = "page", elements = listOf(note))

    @Test fun metadataChangesReuseValidatedContentAndWorldGeometryButExposeTheCurrentPage() {
        val ports = page.geometry.connectionPorts()
        val bounds = page.contentBounds
        val updated = page.copy(
            name = "Renamed",
            background = Background.GRID,
            backgroundColor = 0xFF000000.toInt(),
            viewport = Viewport(Point(400f, -200f), 3f)
        )
        assertSame(page.content, updated.content)
        assertSame(bounds, updated.geometry.contentBounds)
        assertSame(ports, updated.geometry.connectionPorts())
        assertSame(updated, updated.geometry.page)
        assertEquals("Renamed", updated.geometry.page.name)
        assertEquals(3f, updated.geometry.page.viewport.scale)
        assertTrue(ports.matches(updated))
        assertNotEquals(page, updated)
    }

    @Test fun replacingObjectsInvalidatesPortsWithoutChangingTheSourceOrCancelSnapshot() {
        val ports = page.geometry.connectionPorts()
        val moved = note.copy(bounds = Bounds(210f, 20f, 310f, 120f))
        val preview = page.geometry.preview(listOf(moved))
        val next = preview.connectionPorts()
        assertNotSame(page.content, preview.page.content)
        assertNotSame(ports, next)
        assertFalse(ports.matches(preview.page))
        assertEquals(Point(110f, 70f), ports.point(note.id, ConnectionPort.RIGHT))
        assertEquals(Point(310f, 70f), next.point(note.id, ConnectionPort.RIGHT))
        assertSame(ports, page.geometry.connectionPorts())
        assertSame(note, page.elements.single())
    }

    @Test fun pageIdentityAndNewListsCreateFreshSnapshotsEvenWhenValuesAreEqual() {
        val ports = page.geometry.connectionPorts()
        val another = page.copy(id = "another")
        val equal = page.copy(elements = ArrayList(page.elements))
        assertNotSame(page.content, another.content)
        assertFalse(ports.matches(another))
        assertNotSame(ports, another.geometry.connectionPorts())
        assertEquals(page, equal)
        assertEquals(page.hashCode(), equal.hashCode())
        assertNotSame(page.content, equal.content)
        assertFalse(ports.matches(equal))
        assertSame(page.content, page.copy().content)
    }

    @Test fun copyingAndDestructuringPreserveTheSixPublicValueFields() {
        val explicit = Page(page.id, page.name, page.background, page.backgroundColor, page.elements, page.viewport)
        val (id, name, background, color, elements, viewport) = page
        assertEquals(page, explicit)
        assertEquals(page.hashCode(), explicit.hashCode())
        assertEquals(page.toString(), explicit.toString())
        assertEquals(page.id, id)
        assertEquals(page.name, name)
        assertEquals(page.background, background)
        assertEquals(page.backgroundColor, color)
        assertSame(page.elements, elements)
        assertEquals(page.viewport, viewport)
        assertNotEquals(page, page.copy(backgroundColor = color + 1))
        assertNotEquals(page, "page")
    }

    @Test fun cancelledPreparationPublishesNothingAndACachedIndexStillHonorsCancellation() {
        val many = Page(elements = List(100) { note.copy(id = "note-$it") })
        var visits = 0
        assertThrows(CancellationException::class.java) {
            many.geometry.connectionPorts { if (++visits == 17) throw CancellationException() }
        }
        var retryVisits = 0
        val ports = many.geometry.connectionPorts { retryVisits++ }
        assertEquals(102, retryVisits)
        assertTrue(ports.supports("note-99"))
        assertThrows(CancellationException::class.java) {
            many.copy(name = "Metadata").geometry.connectionPorts { throw CancellationException() }
        }
        assertSame(ports, many.geometry.connectionPorts())
    }

    @Test fun invalidContentCannotEnterTheCacheOrBypassCopyValidation() {
        val invalid = Shape(
            id = "invalid",
            bounds = Bounds(999_999_488f, 0f, 1_000_000_000f, 100f),
            kind = Tool.RECTANGLE,
            style = InkStyle(width = 200f)
        )
        val unsafe = page.copy(elements = listOf(invalid))
        repeat(2) { assertThrows(IllegalArgumentException::class.java) { unsafe.geometry.connectionPorts() } }
        assertThrows(IllegalArgumentException::class.java) { page.copy(elements = listOf(note, note)) }
        val group = ElementGroup(children = listOf(note, note.copy(id = "other")))
        assertThrows(IllegalArgumentException::class.java) { page.copy(elements = listOf(group, note)) }
        assertThrows(IllegalArgumentException::class.java) {
            page.copy(elements = List(Page.MAX_ELEMENTS + 1) { note.copy(id = "note-$it") })
        }
        assertEquals(Point(110f, 70f), page.geometry.connectionPorts().point(note.id, ConnectionPort.RIGHT))
    }

    @Test fun largePagesDoNotRescanPortsForRepeatedViewportOrBackgroundUpdates() {
        val large = Page(elements = List(Page.MAX_ELEMENTS - 1) { note.copy(id = "note-$it") })
        var visits = 0
        val ports = large.geometry.connectionPorts { visits++ }
        assertEquals(Page.MAX_ELEMENTS + 1, visits)
        val bounds = large.contentBounds
        var current = large
        repeat(100) {
            current = current.copy(viewport = Viewport(Point(it.toFloat(), -it.toFloat()), 2f), background = Background.GRID)
            assertSame(large.content, current.content)
            assertSame(bounds, current.contentBounds)
            var cachedVisits = 0
            assertSame(ports, current.geometry.connectionPorts { cachedVisits++ })
            assertEquals(1, cachedVisits)
        }
        assertTrue(ports.supports("note-99998"))
        val edge = Connector(
            start = ConnectorEnd.Bound("note-0", ConnectionPort.RIGHT),
            end = ConnectorEnd.Bound("note-99998", ConnectionPort.LEFT)
        )
        var batchVisits = 0
        val connections = PageConnectionGeometry(current, listOf(edge)) { batchVisits++ }
        assertEquals(4, batchVisits)
        assertEquals(Point(110f, 70f), connections.line(edge.id).start)
        assertEquals(Point(10f, 70f), connections.line(edge.id).end)
    }

    @Test fun concurrentReadersPublishOnlyOneCompleteIndexAcrossMetadataCopies() {
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val futures = (0..1).map { index ->
                pool.submit(
                    Callable {
                        check(start.await(5, TimeUnit.SECONDS))
                        page.copy(name = "Reader $index").geometry.connectionPorts()
                    }
                )
            }
            start.countDown()
            val first = futures.first().get(5, TimeUnit.SECONDS)
            assertSame(first, futures.last().get(5, TimeUnit.SECONDS))
            assertTrue(first.supports(note.id))
            assertEquals(Point(110f, 70f), first.point(note.id, ConnectionPort.RIGHT))
            assertSame(first, page.geometry.connectionPorts())
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
