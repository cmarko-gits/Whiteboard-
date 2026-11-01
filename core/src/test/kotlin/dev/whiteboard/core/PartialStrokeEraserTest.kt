package dev.whiteboard.core

import dev.whiteboard.core.editor.PartialStrokeEraser
import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class PartialStrokeEraserTest {
    private val stroke = Stroke(points = listOf(Point(0f, 0f), Point(100f, 0f)), style = InkStyle(width = 2f), tool = Tool.PENCIL)

    @Test fun aStationaryCutSplitsASparseSegmentAndKeepsStyleAndIdentity() {
        val pieces = PartialStrokeEraser.cut(stroke, Point(50f, 0f), Point(50f, 0f), 9f)
        assertEquals(2, pieces.size)
        assertEquals(listOf(Point(0f, 0f), Point(40f, 0f)), pieces[0].points)
        assertEquals(listOf(Point(60f, 0f), Point(100f, 0f)), pieces[1].points)
        assertEquals(stroke.id, pieces.first().id)
        assertEquals(2, pieces.map { it.id }.distinct().size)
        assertTrue(pieces.all { it.style == stroke.style && it.tool == Tool.PENCIL })
    }

    @Test fun aFastSweepCutsEvenWhenNeitherEraserSampleTouchesTheStroke() {
        val pieces = PartialStrokeEraser.cut(stroke, Point(50f, -100f), Point(50f, 100f), 9f)
        assertEquals(listOf(40f, 60f), listOf(pieces[0].points.last().x, pieces[1].points.first().x))
    }

    @Test fun parallelSweepIncludesTheRoundCapsAtBothEnds() {
        val pieces = PartialStrokeEraser.cut(stroke, Point(30f, 0f), Point(70f, 0f), 9f)
        assertEquals(Point(20f, 0f), pieces.first().points.last())
        assertEquals(Point(80f, 0f), pieces.last().points.first())
    }

    @Test fun removingEitherEndDoesNotJoinAcrossTheErasedArea() {
        val left = PartialStrokeEraser.cut(stroke, Point(0f, 0f), Point(20f, 0f), 9f).single()
        val right = PartialStrokeEraser.cut(stroke, Point(80f, 0f), Point(100f, 0f), 9f).single()
        assertEquals(listOf(Point(30f, 0f), Point(100f, 0f)), left.points)
        assertEquals(listOf(Point(0f, 0f), Point(70f, 0f)), right.points)
    }

    @Test fun aMissReturnsTheOriginalObjectAndTangencyDoesNotMakeAFalseGap() {
        assertSame(stroke, PartialStrokeEraser.cut(stroke, Point(50f, 50f), Point(50f, 60f), 9f).single())
        assertSame(stroke, PartialStrokeEraser.cut(stroke, Point(50f, 10f), Point(50f, 10f), 9f).single())
    }

    @Test fun dotAndRepeatedPointsCanBeFullyErasedWithoutEmptyStrokeObjects() {
        listOf(listOf(Point(50f, 0f)), List(5) { Point(50f, 0f) }).forEach { points ->
            assertTrue(PartialStrokeEraser.cut(stroke.copy(points = points), Point(50f, 0f), Point(50f, 0f), 1f).isEmpty())
        }
        assertTrue(PartialStrokeEraser.cut(stroke, Point(0f, 0f), Point(100f, 0f), 1f).isEmpty())
    }

    @Test fun longSegmentsUseStableCircleIntersections() {
        val long = stroke.copy(points = listOf(Point(-1_000_000f, 0f), Point(1_000_000f, 0f)))
        val pieces = PartialStrokeEraser.cut(long, Point(0f, 0f), Point(0f, 0f), 9f)
        assertEquals(-10f, pieces.first().points.last().x, 0.001f)
        assertEquals(10f, pieces.last().points.first().x, 0.001f)
    }

    @Test fun reversingTheSweepProducesTheSameGeometry() {
        val first = Point(35f, -20f)
        val last = Point(65f, 20f)
        val forward = PartialStrokeEraser.cut(stroke, first, last, 9f)
        val reverse = PartialStrokeEraser.cut(stroke, last, first, 9f)
        assertEquals(forward.map { it.points }, reverse.map { it.points })
    }

    @Test fun aCutAcrossAPolylineVertexDoesNotReconnectTheTwoSides() {
        val corner = stroke.copy(points = listOf(Point(-100f, 0f), Point(0f, 100f), Point(100f, 0f)))
        val pieces = PartialStrokeEraser.cut(corner, Point(0f, 100f), Point(0f, 100f), 9f)
        assertEquals(2, pieces.size)
        assertEquals(-7.071068f, pieces.first().points.last().x, 0.0001f)
        assertEquals(7.071068f, pieces.last().points.first().x, 0.0001f)
        assertTrue(pieces.flatMap { it.points }.none { it == Point(0f, 100f) })
    }

    @Test fun excessiveFragmentationIsRejectedBeforeCreatingThousandsOfStrokeIds() {
        val zigzag = stroke.copy(points = List(PartialStrokeEraser.MAX_FRAGMENTS + 3) { Point(if (it % 2 == 0) -20f else 20f, 0f) })
        val error = assertThrows(DocumentFailure::class.java) {
            PartialStrokeEraser.cut(zigzag, Point(0f, 0f), Point(0f, 0f), 9f)
        }
        assertEquals(DocumentError.EDIT_LIMIT, error.error)
    }

    @Test fun lateCutsPreserveEarlierVerticesAndTheOutsidePartOfTheBoundarySegment() {
        val sampled = stroke.copy(points = listOf(0f, 25f, 50f, 75f, 100f).map { Point(it, 0f) })
        val pieces = PartialStrokeEraser.cut(sampled, Point(75f, 0f), Point(75f, 0f), 9f)
        assertEquals(listOf(0f, 25f, 50f, 65f), pieces.first().points.map { it.x })
        assertEquals(listOf(85f, 100f), pieces.last().points.map { it.x })
        val boundary = stroke.copy(points = listOf(Point(0f, 0f), Point(40f, 0f), Point(100f, 0f)))
        assertEquals(
            listOf(Point(0f, 0f), Point(40f, 0f)),
            PartialStrokeEraser.cut(boundary, Point(50f, 0f), Point(50f, 0f), 9f).first().points
        )
    }

    @Test fun seededPolylinesRetainOutsideSamplesAndRemoveInsideSamplesWithoutBridges() {
        val random = kotlin.random.Random(42)
        repeat(100) {
            fun point() = Point(random.nextInt(-200, 201).toFloat(), random.nextInt(-200, 201).toFloat())
            val original = stroke.copy(points = List(20) { point() })
            val from = point()
            val to = point()
            val radius = random.nextInt(1, 21).toFloat()
            val pieces = PartialStrokeEraser.cut(original, from, to, radius)
            fun distance(point: Point): Float = pieces.minOfOrNull { piece ->
                if (piece.points.size == 1) {
                    distanceToSegment(point, piece.points.single(), piece.points.single())
                } else {
                    piece.points.zipWithNext().minOf { (a, b) -> distanceToSegment(point, a, b) }
                }
            } ?: Float.POSITIVE_INFINITY
            original.points.zipWithNext().forEach { (a, b) ->
                repeat(41) { index ->
                    val ratio = index / 40f
                    val sample = Point(a.x + (b.x - a.x) * ratio, a.y + (b.y - a.y) * ratio)
                    val maskDistance = distanceToSegment(sample, from, to) - radius - original.style.width / 2
                    if (maskDistance > 0.01f) assertTrue("An outside point was lost", distance(sample) < 0.002f)
                    if (maskDistance < -0.01f) assertTrue("A bridge remains inside the erased area", distance(sample) > 0.001f)
                }
            }
        }
    }

    @Test fun invalidRadiusIsRejected() {
        listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { radius ->
            assertThrows(IllegalArgumentException::class.java) { PartialStrokeEraser.cut(stroke, Point(0f, 0f), Point(0f, 0f), radius) }
        }
    }
}
