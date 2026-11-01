package dev.whiteboard.core

import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class GeometryTest {
    @Test fun `coordinate transforms round trip with pan and zoom`() {
        val view = Viewport(Point(-250f, 100f), 2.5f)
        val p = Point(-100f, 200f)
        assertEquals(p, view.toDocument(view.toScreen(p)))
    }

    @Test fun `zoom preserves document coordinate under anchor`() {
        val view = Viewport(Point(50f, -20f), 1.2f)
        val anchor = Point(320f, 450f)
        val before = view.toDocument(anchor)
        val after = view.zoom(1.8f, anchor).toDocument(anchor)
        assertEquals(before.x, after.x, 0.001f)
        assertEquals(before.y, after.y, 0.001f)
    }

    @Test fun `zoom is clamped`() {
        assertEquals(Viewport.MAX_SCALE, Viewport().zoom(100f, Point(0f, 0f)).scale)
        assertEquals(Viewport.MIN_SCALE, Viewport().zoom(0.001f, Point(0f, 0f)).scale)
    }

    @Test fun `hit test respects distance from a diagonal stroke`() {
        val stroke = Stroke(points = listOf(Point(0f, 0f), Point(100f, 100f)), style = InkStyle(width = 2f))
        assertTrue(stroke.hitTest(Point(50f, 51f), 3f))
        assertFalse(stroke.hitTest(Point(10f, 90f), 3f))
    }

    @Test fun `fit contains content including negative coordinates`() {
        val bounds = Bounds(-1000f, -500f, 1000f, 500f)
        val view = Viewport.fit(bounds, 1080f, 1920f)
        assertTrue(view.toScreen(Point(bounds.left, bounds.top)).x >= 63f)
        assertTrue(view.toScreen(Point(bounds.right, bounds.bottom)).x <= 1017f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid geometry`() {
        Point(Float.NaN, 1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects duplicate object ids`() {
        val item = Note(bounds = Bounds(0f, 0f, 100f, 100f), text = "Test")
        Page(elements = listOf(item, item))
    }
}
