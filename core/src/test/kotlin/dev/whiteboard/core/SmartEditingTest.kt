package dev.whiteboard.core

import dev.whiteboard.core.editor.*
import dev.whiteboard.core.model.*
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class SmartEditingTest {
    @Test fun `closed ellipse is recognized and retains style and identity`() {
        val points = (0..60).map {
            val angle =
                it * 2 * PI / 60
            Point((100 + 70 * cos(angle)).toFloat(), (100 + 50 * sin(angle)).toFloat())
        }
        val stroke = Stroke(points = points, style = InkStyle(width = 4f))
        val shape = requireNotNull(SmartShapeRecognizer.recognize(stroke))
        assertEquals(Tool.ELLIPSE, shape.kind)
        assertEquals(stroke.id, shape.id)
        assertEquals(stroke.style, shape.style)
    }

    @Test fun `open curves are left untouched`() {
        val stroke = Stroke(points = List(40) { Point(it * 4f, sin(it / 4f) * 20) }, style = InkStyle())
        assertNull(SmartShapeRecognizer.recognize(stroke))
    }

    @Test fun `snapping chooses nearest edge and respects tolerance`() {
        val bounds = Bounds(103f, 200f, 153f, 250f)
        val other = listOf(Bounds(0f, 0f, 100f, 100f))
        assertEquals(100f, SnapEngine.snap(bounds, other, 8f).bounds.left)
        assertEquals(bounds, SnapEngine.snap(bounds, other, 2f).bounds)
    }

    @Test fun `deleted media stays protected while undo or redo can restore it`() {
        val session = DocumentSession(Board(name = "Retention"))
        val media = MediaElement(bounds = Bounds(0f, 0f, 200f, 200f), assetId = "${newId()}.png")
        session.add(media)
        session.delete(setOf(media.id))
        assertTrue(media.assetId in session.retainedAssetIds)
        session.undo()
        session.undo()
        assertTrue(media.assetId in session.retainedAssetIds)
    }
}
