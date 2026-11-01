package dev.whiteboard.core

import dev.whiteboard.core.editor.DocumentSession
import dev.whiteboard.core.editor.FillEngine
import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class FillEngineTest {
    private val color = 0xFF1FAAE5.toInt()
    private fun shape(kind: Tool = Tool.RECTANGLE) = Shape(
        bounds = Bounds(10f, 10f, 110f, 110f),
        kind = kind,
        style = InkStyle(color = 0xFF123456.toInt(), width = 8f, opacity = 0.4f)
    )
    private fun session(vararg elements: Element) =
        DocumentSession(Board(name = "Fill", pages = listOf(Page(elements = elements.toList()))))

    @Test fun fillChangesOnlyTopmostSurfaceAndUndoRestoresOriginal() {
        val bottom = shape()
        val top = shape(Tool.ELLIPSE)
        val session = session(bottom, top)
        session.fill(Point(60f, 60f), color)
        assertEquals(listOf(bottom, top.copy(fill = color)), session.page.elements)
        assertTrue(session.dirty)
        session.undo()
        assertEquals(listOf(bottom, top), session.page.elements)
        assertFalse(session.dirty)
        session.redo()
        assertEquals(top.copy(fill = color), session.page.elements.last())
    }

    @Test fun sameFillDoesNotCreateHistoryOrDirtyState() {
        val original = shape().copy(fill = color)
        val session = session(original)
        session.fill(Point(60f, 60f), color)
        assertFalse(session.dirty)
        assertFalse(session.canUndo)
    }

    @Test fun backgroundFillPreservesPatternPagesAndObjects() {
        val original = shape()
        val second = Page(name = "Other")
        val board = Board(name = "Pages", pages = listOf(Page(elements = listOf(original)), second))
        val session = DocumentSession(board)
        session.fill(Point(-200f, -100f), color)
        assertEquals(color, session.page.backgroundColor)
        assertEquals(board.activePage.background, session.page.background)
        assertEquals(listOf(original), session.page.elements)
        assertEquals(second, session.board.pages.last())
        session.undo()
        assertEquals(board.pages, session.board.pages)
    }

    @Test fun emptyCornersOfCurvedAndPolygonShapesAreNotFilled() {
        for (kind in listOf(Tool.ELLIPSE, Tool.CIRCLE, Tool.DIAMOND, Tool.TRIANGLE)) {
            val page = Page(elements = listOf(shape(kind)))
            assertEquals("$kind corner", FillEngine.Target.Background, FillEngine.target(page, Point(12f, 12f)))
            assertTrue("$kind center", FillEngine.target(page, Point(60f, 60f)) is FillEngine.Target.Surface)
        }
    }

    @Test fun groupedFillKeepsOtherChildrenAndGroupIdentity() {
        val first = shape()
        val second = shape().copy(bounds = Bounds(200f, 200f, 300f, 300f))
        val group = ElementGroup(children = listOf(first, second))
        val session = session(group)
        session.fill(Point(60f, 60f), color)
        assertEquals(group.copy(children = listOf(first.copy(fill = color), second)), session.page.elements.single())
        session.undo()
        assertEquals(group, session.page.elements.single())
    }

    @Test fun textAndOpenStrokesBlockAccidentalBackgroundChanges() {
        val stroke = Stroke(points = listOf(Point(10f, 10f), Point(100f, 100f)), style = InkStyle())
        val text = TextElement(bounds = Bounds(200f, 200f, 300f, 300f), text = "Keep")
        val session = session(stroke, text)
        session.fill(Point(50f, 50f), color)
        session.fill(Point(250f, 250f), color)
        assertFalse(session.dirty)
        assertFalse(session.canUndo)
    }

    @Test fun noteColorChangesWithoutChangingTextOrGeometry() {
        val note = Note(bounds = Bounds(-100f, -100f, 100f, 100f), text = "Keep note")
        val session = session(note)
        session.fill(Point(0f, 0f), color)
        assertEquals(note.copy(color = color), session.page.elements.single())
    }
}
