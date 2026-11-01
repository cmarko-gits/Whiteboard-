package dev.whiteboard.core.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Maps local coordinates to document coordinates without depending on Android's Matrix. */
data class AffineTransform2D(
    val xx: Double = 1.0,
    val xy: Double = 0.0,
    val tx: Double = 0.0,
    val yx: Double = 0.0,
    val yy: Double = 1.0,
    val ty: Double = 0.0
) {
    private val determinant = xx * yy - xy * yx

    init {
        require(listOf(xx, xy, yx, yy).all { it.isFinite() && abs(it) <= MAX_LINEAR })
        require(listOf(tx, ty).all { it.isFinite() && abs(it) <= MAX_TRANSLATION })
        require(determinant.isFinite() && determinant != 0.0) { "Transform must be invertible" }
        // Bound both directions: a nearly singular matrix is unsafe for hit-testing.
        require(
            listOf(yy / determinant, -xy / determinant, -yx / determinant, xx / determinant).all {
                it.isFinite() && abs(it) <= MAX_LINEAR
            }
        ) { "Inverse transform exceeds the numerical budget" }
        require(
            listOf((xy * ty - yy * tx) / determinant, (yx * tx - xx * ty) / determinant).all {
                it.isFinite() && abs(it) <= MAX_TRANSLATION
            }
        ) { "Inverse translation exceeds the numerical budget" }
    }

    fun map(point: Point) = Point(
        (xx * point.x + xy * point.y + tx).toFloat(),
        (yx * point.x + yy * point.y + ty).toFloat()
    )

    /** Maps an input point back to local coordinates without allocating another matrix per hit-test. */
    fun unmap(point: Point): Point {
        val x = point.x - tx
        val y = point.y - ty
        return Point(((yy * x - xy * y) / determinant).toFloat(), ((xx * y - yx * x) / determinant).toFloat())
    }

    /** Axis-aligned extent for culling/selection, not a substitute for inverse hit-testing. */
    fun map(bounds: Bounds): Bounds = Bounds.of(
        listOf(
            map(Point(bounds.left, bounds.top)),
            map(Point(bounds.right, bounds.top)),
            map(Point(bounds.right, bounds.bottom)),
            map(Point(bounds.left, bounds.bottom))
        )
    )

    /** Applies this transform first, followed by [next]. Composition remains in Double precision. */
    fun then(next: AffineTransform2D) = AffineTransform2D(
        xx = next.xx * xx + next.xy * yx,
        xy = next.xx * xy + next.xy * yy,
        tx = next.xx * tx + next.xy * ty + next.tx,
        yx = next.yx * xx + next.yy * yx,
        yy = next.yx * xy + next.yy * yy,
        ty = next.yx * tx + next.yy * ty + next.ty
    )

    fun inverse() = AffineTransform2D(
        xx = yy / determinant,
        xy = -xy / determinant,
        tx = (xy * ty - yy * tx) / determinant,
        yx = -yx / determinant,
        yy = xx / determinant,
        ty = (yx * tx - xx * ty) / determinant
    )

    companion object {
        private const val MAX_LINEAR = 1_000_000.0
        private const val MAX_TRANSLATION = 1_000_000_000_000_000.0
        val IDENTITY = AffineTransform2D()

        fun translation(dx: Double, dy: Double) = AffineTransform2D(tx = dx, ty = dy)

        fun rotation(degrees: Double, pivot: Point = Point(0f, 0f)): AffineTransform2D {
            require(degrees.isFinite())
            val angle = ((degrees % 360.0) + 360.0) % 360.0
            if (angle == 0.0) return IDENTITY
            // Preserve exact quarter turns instead of introducing cosine round-off.
            val (c, s) = when (angle) {
                90.0 -> 0.0 to 1.0
                180.0 -> -1.0 to 0.0
                270.0 -> 0.0 to -1.0
                else -> {
                    val radians = Math.toRadians(angle)
                    cos(radians) to sin(radians)
                }
            }
            return around(pivot, AffineTransform2D(xx = c, xy = -s, yx = s, yy = c))
        }

        fun scale(x: Double, y: Double, pivot: Point = Point(0f, 0f)) = around(pivot, AffineTransform2D(xx = x, yy = y))

        private fun around(pivot: Point, linear: AffineTransform2D) = AffineTransform2D(
            xx = linear.xx,
            xy = linear.xy,
            tx = pivot.x - linear.xx * pivot.x - linear.xy * pivot.y,
            yx = linear.yx,
            yy = linear.yy,
            ty = pivot.y - linear.yx * pivot.x - linear.yy * pivot.y
        )
    }
}
