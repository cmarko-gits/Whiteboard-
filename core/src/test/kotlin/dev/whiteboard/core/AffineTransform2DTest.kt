package dev.whiteboard.core

import dev.whiteboard.core.model.AffineTransform2D
import dev.whiteboard.core.model.Bounds
import dev.whiteboard.core.model.Point
import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class AffineTransform2DTest {
    private fun assertPoint(expected: Point, actual: Point, delta: Float = 0.001f) {
        assertEquals(expected.x, actual.x, delta)
        assertEquals(expected.y, actual.y, delta)
    }

    @Test fun identityAndFullTurnsLeavePointsAndBoundsUnchanged() {
        val bounds = Bounds(-10f, 20f, 70f, 90f)
        listOf(0.0, 360.0, -360.0, 720.0).forEach { angle ->
            val transform = AffineTransform2D.rotation(angle)
            assertEquals(AffineTransform2D.IDENTITY, transform)
            assertEquals(bounds, transform.map(bounds))
            assertEquals(bounds.center, transform.map(bounds.center))
        }
    }

    @Test fun quarterTurnsAreExactAndKeepThePivotFixed() {
        val pivot = Point(100f, 200f)
        val point = Point(130f, 210f)
        listOf(90.0 to Point(90f, 230f), 180.0 to Point(70f, 190f), 270.0 to Point(110f, 170f)).forEach { (angle, expected) ->
            val transform = AffineTransform2D.rotation(angle, pivot)
            assertEquals(expected, transform.map(point))
            assertEquals(pivot, transform.map(pivot))
        }
        assertEquals(AffineTransform2D.rotation(270.0, pivot), AffineTransform2D.rotation(-90.0, pivot))
    }

    @Test fun anArbitraryAnglePreservesDistanceToTheSharedPivot() {
        val pivot = Point(10f, 20f)
        val transform = AffineTransform2D.rotation(45.0, pivot)
        assertPoint(Point(10f + sqrt(50f), 20f + sqrt(50f)), transform.map(Point(20f, 20f)))
        assertPoint(pivot, transform.map(pivot))
    }

    @Test fun compositionOrderMatchesAUserRotatingThenMovingTheObject() {
        val rotate = AffineTransform2D.rotation(90.0)
        val move = AffineTransform2D.translation(100.0, 200.0)
        assertEquals(Point(100f, 210f), rotate.then(move).map(Point(10f, 0f)))
        assertEquals(Point(-200f, 110f), move.then(rotate).map(Point(10f, 0f)))
    }

    @Test fun rotationFollowedByNonuniformScalingRetainsTheExactAffineResult() {
        val transform = AffineTransform2D.rotation(45.0).then(AffineTransform2D.scale(2.0, 3.0))
        val result = transform.map(Point(10f, 0f))
        assertPoint(Point(sqrt(200f), sqrt(450f)), result)
        assertPoint(Point(10f, 0f), transform.inverse().map(result))
    }

    @Test fun inverseHitTestingRejectsEmptyCornersOfTheRotatedBoundingBox() {
        val local = Bounds(-50f, -10f, 50f, 10f)
        val transform = AffineTransform2D.rotation(45.0)
        val extent = transform.map(local)
        val corner = Point(extent.left + 1, extent.top + 1)
        assertTrue(extent.contains(corner))
        assertFalse(local.contains(transform.inverse().map(corner)))
        assertFalse(local.contains(transform.unmap(corner)))
        assertTrue(local.contains(transform.inverse().map(transform.map(Point(20f, 5f)))))
    }

    @Test fun mappedBoundsIncludeAllFourCorners() {
        val transform = AffineTransform2D.rotation(90.0, Point(10f, 20f))
        assertEquals(Bounds(-10f, 10f, 30f, 30f), transform.map(Bounds(0f, 0f, 20f, 40f)))
    }

    @Test fun aReflectionRemainsInvertible() {
        val transform = AffineTransform2D.scale(-2.0, 3.0, Point(10f, 20f))
        assertEquals(Point(4f, 32f), transform.map(Point(13f, 24f)))
        assertPoint(Point(13f, 24f), transform.inverse().map(Point(4f, 32f)))
    }

    @Test fun repeatedSmallTurnsKeepTheMatrixInDoublePrecision() {
        var transform = AffineTransform2D.IDENTITY
        repeat(360) { transform = transform.then(AffineTransform2D.rotation(1.0)) }
        assertEquals(1.0, transform.xx, 1e-12)
        assertEquals(0.0, transform.xy, 1e-12)
        assertPoint(Point(200_000f, -300_000f), transform.map(Point(200_000f, -300_000f)))
    }

    @Test fun seededTransformsRoundTripPointsWithinFloatStoragePrecision() {
        val random = Random(731)
        repeat(500) {
            val pivot = Point(random.nextFloat() * 1000f, random.nextFloat() * 1000f)
            val point = Point(random.nextFloat() * 1000f, random.nextFloat() * 1000f)
            val transform = AffineTransform2D.rotation(random.nextDouble(-720.0, 720.0), pivot)
                .then(AffineTransform2D.scale(random.nextDouble(0.1, 10.0), random.nextDouble(0.1, 10.0), pivot))
                .then(AffineTransform2D.translation(random.nextDouble(-1000.0, 1000.0), random.nextDouble(-1000.0, 1000.0)))
            assertPoint(point, transform.inverse().map(transform.map(point)), 0.01f)
            assertPoint(point, transform.unmap(transform.map(point)), 0.01f)
        }
    }

    @Test fun invalidSingularAndNumericallyUnsafeMatricesAreRejected() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e20).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { AffineTransform2D(xx = invalid) }
            assertThrows(IllegalArgumentException::class.java) { AffineTransform2D(tx = invalid) }
        }
        assertThrows(IllegalArgumentException::class.java) { AffineTransform2D(xx = 1.0, xy = 1.0, yx = 1.0, yy = 1.0) }
        assertThrows(IllegalArgumentException::class.java) { AffineTransform2D.scale(0.0, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { AffineTransform2D.scale(1e-12, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { AffineTransform2D.rotation(Double.NaN) }
    }

    @Test fun geometryBeyondTheExistingDocumentBoundsIsRejected() {
        val transform = AffineTransform2D.translation(1_000_000_000.0, 0.0)
        assertThrows(IllegalArgumentException::class.java) { transform.map(Bounds(100f, 0f, 1000f, 100f)) }
        val pivot = Point(900_000_000f, 900_000_000f)
        assertPoint(pivot, AffineTransform2D.rotation(45.0, pivot).map(pivot), 1f)
    }
}
