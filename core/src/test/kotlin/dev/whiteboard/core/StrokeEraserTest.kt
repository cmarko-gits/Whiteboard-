package dev.whiteboard.core

import dev.whiteboard.core.editor.StrokeEraser
import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class StrokeEraserTest {
    @Test fun fastGestureCrossesStrokeEvenWhenNeitherSampleTouchesIt() {
        val stroke = Stroke(points = listOf(Point(50f, -50f), Point(50f, 50f)), style = InkStyle(width = 1f))
        assertTrue(StrokeEraser.intersects(stroke, Point(0f, 0f), Point(100f, 0f), 2f))
        assertFalse(StrokeEraser.intersects(stroke, Point(0f, 100f), Point(100f, 100f), 2f))
    }

    @Test fun pointStrokeAndStationaryEraserIncludeStrokeWidth() {
        val stroke = Stroke(points = listOf(Point(10f, 10f)), style = InkStyle(width = 10f))
        assertTrue(StrokeEraser.intersects(stroke, Point(18f, 10f), Point(18f, 10f), 3f))
        assertFalse(StrokeEraser.intersects(stroke, Point(19f, 10f), Point(19f, 10f), 3f))
        assertTrue(StrokeEraser.intersects(stroke, Point(0f, 10f), Point(100f, 10f), 0f))
    }

    @Test fun parallelAndCollinearSegmentsRespectDistanceAndEndpoints() {
        val stroke = Stroke(points = listOf(Point(0f, 0f), Point(100f, 0f)), style = InkStyle(width = 2f))
        assertTrue(StrokeEraser.intersects(stroke, Point(20f, 5f), Point(60f, 5f), 4f))
        assertFalse(StrokeEraser.intersects(stroke, Point(20f, 6f), Point(60f, 6f), 4f))
        assertTrue(StrokeEraser.intersects(stroke, Point(100f, 0f), Point(120f, 0f), 0f))
        assertFalse(StrokeEraser.intersects(stroke, Point(110f, 0f), Point(120f, 0f), 1f))
    }
}
