package dev.whiteboard.core

import dev.whiteboard.core.editor.ObjectTranslation
import dev.whiteboard.core.model.*
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class IncrementalPagePreviewTest {
    private val first = Shape(id = "first", bounds = Bounds(40f, 40f, 140f, 140f), kind = Tool.RECTANGLE, style = InkStyle())
    private val second = first.copy(id = "second", bounds = Bounds(260f, 180f, 360f, 280f))
    private val third = first.copy(id = "third", bounds = Bounds(480f, 40f, 580f, 140f))
    private val fourth = first.copy(id = "fourth", bounds = Bounds(700f, 180f, 800f, 280f))
    private val edge =
        Connector(
            id = "edge",
            start = ConnectorEnd.Bound(first.id, ConnectionPort.RIGHT),
            end = ConnectorEnd.Bound(second.id, ConnectionPort.LEFT)
        )
    private val other = edge.copy(
        id = "other",
        start = ConnectorEnd.Bound(third.id, ConnectionPort.RIGHT),
        end = ConnectorEnd.Bound(fourth.id, ConnectionPort.LEFT)
    )
    private val page = Page(elements = listOf(first, edge, second, third, other, fourth))
    private val moved = first.copy(bounds = Bounds(40f, 160f, 140f, 260f))
    private fun warm(source: Page = page): Page = source.also { it.contentBounds }
    private fun point(expected: Point, actual: Point) {
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }

    @Test fun onlyIncidentLinesAreRebuiltWhileTheOriginalAndUnchangedFramesAreShared() {
        warm()
        val original = page.geometry.resolve(edge) as Line
        val untouched = page.geometry.resolve(other)
        val frame = page.geometry.renderBounds(third)
        val preview = page.geometry.preview(listOf(moved))
        point(original.start + Point(0f, 120f), (preview.resolve(edge) as Line).start)
        assertSame(untouched, preview.resolve(other))
        assertSame(frame, preview.renderBounds(third))
        assertSame(original, page.geometry.resolve(edge))
        assertEquals(first, page.elements.first())
        assertEquals(listOf(first.id, edge.id, second.id, third.id, other.id, fourth.id), preview.page.elements.map { it.id })
        assertEquals(preview.resolve(edge).renderBounds, preview.renderBounds(edge))
    }

    @Test fun theExactUnionShrinksWhenTheFormerExtremeTargetMovesInward() {
        val distant = first.copy(bounds = Bounds(-1000f, 40f, -900f, 140f))
        val source = warm(page.copy(elements = listOf(distant, edge, second, third, other, fourth)))
        val preview = source.geometry.preview(listOf(first)).page
        val canonical = page.copy(elements = page.elements.toList())
        assertEquals(canonical.contentBounds, preview.contentBounds)
        assertTrue(checkNotNull(preview.contentBounds).left > checkNotNull(source.contentBounds).left)
    }

    @Test fun frameCachingPreservesForeignLocalGeometryAndRejectsStaleConnectionDefinitions() {
        warm()
        val foreign = first.copy(bounds = Bounds(1000f, 1000f, 1100f, 1100f))
        assertEquals(foreign.renderBounds, page.geometry.renderBounds(foreign))
        val preview = page.geometry.preview(listOf(moved))
        assertEquals(foreign.renderBounds, preview.renderBounds(foreign))
        val stale = edge.copy(arrow = false)
        assertThrows(IllegalArgumentException::class.java) { preview.renderBounds(stale) }
        assertThrows(IllegalArgumentException::class.java) { preview.resolve(stale) }
    }

    @Test fun groupAndChildPortsMoveTogetherWithoutRebuildingAnUnrelatedLine() {
        val group = ElementGroup(id = "group", children = listOf(first, second))
        val groupEdge = edge.copy(
            id = "group-edge",
            start = ConnectorEnd.Bound(group.id, ConnectionPort.RIGHT),
            end = ConnectorEnd.Bound(third.id, ConnectionPort.LEFT)
        )
        val source = warm(page.copy(elements = listOf(group, edge, third, groupEdge, other, fourth)))
        val translated = ObjectTranslation.translate(group, Point(20f, 30f)) {} as ElementGroup
        val preview = source.geometry.preview(listOf(translated))
        point((source.geometry.resolve(edge) as Line).start + Point(20f, 30f), (preview.resolve(edge) as Line).start)
        point((source.geometry.resolve(groupEdge) as Line).start + Point(20f, 30f), (preview.resolve(groupEdge) as Line).start)
        assertSame(source.geometry.resolve(other), preview.resolve(other))
        assertTrue(preview.connectionPorts().matches(preview.page))
    }

    @Test fun structuralChildIdentityChangesCannotReuseValidatedReferenceMembership() {
        val group = ElementGroup(id = "group", children = listOf(first, second))
        val source = warm(Page(elements = listOf(group, edge)))
        val invalid = group.copy(children = listOf(first.copy(id = "different"), second))
        assertThrows(IllegalArgumentException::class.java) { source.geometry.preview(listOf(invalid)) }
        assertSame(group, source.elements.first())
    }

    @Test fun changedConnectionIntentUsesCanonicalValidationAndCannotReadTheOldDefinition() {
        warm()
        val original = page.geometry.resolve(edge)
        val rebound = edge.copy(start = ConnectorEnd.Bound(third.id, ConnectionPort.TOP), arrow = false)
        val preview = page.geometry.preview(listOf(rebound))
        point(third.bounds.center.copy(y = third.bounds.top), (preview.resolve(rebound) as Line).start)
        assertFalse((preview.resolve(rebound) as Line).arrow)
        assertThrows(IllegalArgumentException::class.java) { preview.resolve(edge) }
        assertSame(original, page.geometry.resolve(edge))
    }

    @Test fun aColdInvalidOriginalCanStillBeRepairedWithoutPublishingItsPartialFrames() {
        val invalid = first.copy(bounds = Bounds(999_999_872f, 0f, 999_999_936f, 64f), style = InkStyle(width = 200f))
        val freeEdge = edge.copy(end = ConnectorEnd.Free(Point(200f, 100f)))
        val source = Page(elements = listOf(invalid, freeEdge))
        val repaired = source.geometry.preview(listOf(first))
        assertNotNull(repaired.contentBounds)
        assertThrows(IllegalArgumentException::class.java) { source.contentBounds }
        assertEquals(first.renderBounds, source.geometry.renderBounds(first))
    }

    @Test fun affineBoundaryPortsFollowTranslationThroughTheSameResolver() {
        val rotated = AffineElement.fromDocument(first, AffineTransform2D.rotation(45.0, first.bounds.center))
        val source = warm(page.copy(elements = listOf(rotated, edge, second, third, other, fourth)))
        val translated = ObjectTranslation.translate(rotated, Point(20f, 30f)) {}
        val preview = source.geometry.preview(listOf(translated))
        point((source.geometry.resolve(edge) as Line).start + Point(20f, 30f), (preview.resolve(edge) as Line).start)
        assertEquals(source.copy(elements = listOf(translated, edge, second, third, other, fourth)).contentBounds, preview.contentBounds)
    }

    @Test fun repeatedChainedPreviewsDoNotBuildAnUnboundedResolverStack() {
        var current = warm()
        repeat(2000) { index ->
            val x = 41f + index
            current = current.geometry.preview(listOf(first.copy(bounds = Bounds(x, 40f, x + 100f, 140f)))).page
        }
        point(Point(2140f, 90f), (current.geometry.resolve(edge) as Line).start)
        assertEquals(first, page.elements.first())
    }

    @Test fun cancelledAndConcurrentPreparationCannotMutateTheSharedOriginal() {
        warm()
        val original = page.geometry.resolve(edge)
        val ports = page.geometry.connectionPorts()
        var visits = 0
        assertThrows(CancellationException::class.java) {
            page.geometry.preview(listOf(moved)) { if (++visits == 20) throw CancellationException() }
        }
        assertSame(original, page.geometry.resolve(edge))
        assertSame(ports, page.geometry.connectionPorts())
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = (1..8).map { index ->
                pool.submit<Line> {
                    val translation = Point(0f, index * 25f)
                    val changed = ObjectTranslation.translate(first, translation) {}
                    val preview = page.geometry.preview(listOf(changed))
                    assertSame(page.geometry.resolve(other), preview.resolve(other))
                    preview.resolve(edge) as Line
                }
            }
            futures.forEachIndexed { index, future -> point(Point(140f, 90f + (index + 1) * 25f), future.get(10, TimeUnit.SECONDS).start) }
        } finally {
            pool.shutdownNow()
        }
        assertSame(original, page.geometry.resolve(edge))
    }

    @Test fun cancellationDuringFinalBoundsPreparationLeavesTheSourceReusable() {
        warm()
        val original = page.geometry.resolve(edge)
        var checkpoints = 0
        page.geometry.preview(listOf(moved)) { checkpoints++ }
        var visits = 0
        assertThrows(CancellationException::class.java) {
            page.geometry.preview(listOf(moved)) {
                if (++visits == checkpoints - 4) throw CancellationException()
            }
        }
        assertSame(original, page.geometry.resolve(edge))
        val retry = page.geometry.preview(listOf(moved))
        point(Point(140f, 210f), (retry.resolve(edge) as Line).start)
        assertEquals(page.copy(elements = listOf(moved, edge, second, third, other, fourth)).contentBounds, retry.contentBounds)
    }
}
