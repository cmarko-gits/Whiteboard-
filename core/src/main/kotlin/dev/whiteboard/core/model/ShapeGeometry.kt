package dev.whiteboard.core.model

fun Line.arrowhead(): Pair<Point, Point>? {
    if (!arrow) return null
    val angle = kotlin.math.atan2(end.y - start.y, end.x - start.x)
    val length = maxOf(14f, style.width * 4)
    return Point(end.x - length * kotlin.math.cos(angle - 0.5f), end.y - length * kotlin.math.sin(angle - 0.5f)) to
        Point(end.x - length * kotlin.math.cos(angle + 0.5f), end.y - length * kotlin.math.sin(angle + 0.5f))
}

/** Closed shape geometry shared by picking and bucket fill, before any affine placement. */
fun Shape.containsShape(point: Point, tolerance: Float = 0f): Boolean {
    require(tolerance.isFinite() && tolerance >= 0f)
    if (!bounds.contains(point, tolerance)) return false
    val b = bounds
    if (b.width <= 0f || b.height <= 0f) {
        return tolerance > 0 && distanceToSegment(point, Point(b.left, b.top), Point(b.right, b.bottom)) <= tolerance
    }
    if (kind == Tool.ELLIPSE || kind == Tool.CIRCLE) {
        val x = (point.x.toDouble() - b.center.x) / (b.width / 2.0 + tolerance)
        val y = (point.y.toDouble() - b.center.y) / (b.height / 2.0 + tolerance)
        return x * x + y * y <= 1.0
    }
    val vertices = when (kind) {
        Tool.TRIANGLE -> listOf(Point(b.center.x, b.top), Point(b.right, b.bottom), Point(b.left, b.bottom))
        Tool.DIAMOND -> listOf(Point(b.center.x, b.top), Point(b.right, b.center.y), Point(b.center.x, b.bottom), Point(b.left, b.center.y))
        else -> return b.contains(point, tolerance)
    }
    var positive = false
    var negative = false
    vertices.indices.forEach { index ->
        val a = vertices[index]
        val next = vertices[(index + 1) % vertices.size]
        val cross = (next.x.toDouble() - a.x) * (point.y.toDouble() - a.y) -
            (next.y.toDouble() - a.y) * (point.x.toDouble() - a.x)
        if (cross > 0) positive = true
        if (cross < 0) negative = true
    }
    if (!(positive && negative)) return true
    return tolerance > 0 && vertices.indices.any { index ->
        distanceToSegment(point, vertices[index], vertices[(index + 1) % vertices.size]) <= tolerance
    }
}
