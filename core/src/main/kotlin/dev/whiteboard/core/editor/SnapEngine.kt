package dev.whiteboard.core.editor

import dev.whiteboard.core.model.Bounds
import kotlin.math.abs

data class SnapResult(val bounds: Bounds, val guideX: Float?, val guideY: Float?)

object SnapEngine {
    fun snap(bounds: Bounds, candidates: List<Bounds>, tolerance: Float): SnapResult {
        var dx = 0f
        var dy = 0f
        var bestX = tolerance
        var bestY = tolerance
        var guideX: Float? = null
        var guideY: Float? = null
        val xs = floatArrayOf(bounds.left, bounds.center.x, bounds.right)
        val ys = floatArrayOf(bounds.top, bounds.center.y, bounds.bottom)
        candidates.forEach { other ->
            for (target in floatArrayOf(other.left, other.center.x, other.right)) {
                for (source in xs) {
                    val distance = target - source
                    if (abs(distance) < bestX) {
                        bestX = abs(distance)
                        dx = distance
                        guideX = target
                    }
                }
            }
            for (target in floatArrayOf(other.top, other.center.y, other.bottom)) {
                for (source in ys) {
                    val distance = target - source
                    if (abs(distance) < bestY) {
                        bestY = abs(distance)
                        dy = distance
                        guideY = target
                    }
                }
            }
        }
        return SnapResult(Bounds(bounds.left + dx, bounds.top + dy, bounds.right + dx, bounds.bottom + dy), guideX, guideY)
    }
}
