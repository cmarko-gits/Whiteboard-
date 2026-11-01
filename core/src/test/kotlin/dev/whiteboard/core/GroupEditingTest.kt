package dev.whiteboard.core

import dev.whiteboard.core.editor.DocumentSession
import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class GroupEditingTest {
    @Test fun eraserUnwrapsLastGroupMemberAndUndoRestoresWholeGroup() {
        val stroke = Stroke(points = listOf(Point(0f, 0f), Point(100f, 100f)), style = InkStyle())
        val note = note(200f)
        val group = ElementGroup(children = listOf(stroke, note))
        val session = DocumentSession(Board(name = "Eraser", pages = listOf(Page(elements = listOf(group)))))
        session.eraseStrokes(setOf(stroke.id))
        assertEquals(listOf(note), session.page.elements)
        session.undo()
        assertEquals(listOf(group), session.page.elements)
        session.redo()
        assertEquals(listOf(note), session.page.elements)
    }
    private fun note(x: Float) = Note(bounds = Bounds(x, 0f, x + 100f, 100f), text = "Note")

    @Test fun groupingAndUngroupingPreserveIdsOrderAndUndo() {
        val original = List(4) { note(it * 150f) }
        val session = DocumentSession(Board(name = "Groups", pages = listOf(Page(elements = original))))
        val group = requireNotNull(session.group(setOf(original[0].id, original[2].id)))
        assertEquals(listOf(original[0], original[2]), group.children)
        assertEquals(listOf(original[1], group, original[3]), session.page.elements)
        session.undo()
        assertEquals(original, session.page.elements)
        session.redo()
        val ids = session.ungroup(setOf(group.id))
        assertEquals(group.children.map { it.id }.toSet(), ids)
        assertEquals(listOf(original[1], original[0], original[2], original[3]), session.page.elements)
        session.undo()
        assertEquals(group, session.page.elements[1])
    }

    @Test fun mergingGroupsRemainsFlatAndDuplicatesEveryIdentity() {
        val group = ElementGroup(children = listOf(note(0f), note(200f)))
        val extra = note(400f)
        val session = DocumentSession(Board(name = "Merged", pages = listOf(Page(elements = listOf(group, extra)))))
        val merged = requireNotNull(session.group(setOf(group.id, extra.id)))
        assertEquals(3, merged.children.size)
        assertTrue(merged.children.none { it is ElementGroup })
        val copy = merged.duplicate()
        assertNotEquals(merged.id, copy.id)
        assertTrue(merged.children.map { it.id }.intersect(copy.children.map { it.id }.toSet()).isEmpty())
    }

    @Test fun groupTransformsAndDeletedMediaRemainAvailableForUndo() {
        val image = MediaElement(bounds = Bounds(200f, 0f, 300f, 100f), assetId = "${newId()}.png")
        val group = ElementGroup(children = listOf(note(0f), image))
        val moved = group.transform(Bounds(50f, 50f, 650f, 250f))
        assertEquals(Bounds(50f, 50f, 250f, 250f), moved.children.first().bounds)
        val session = DocumentSession(Board(name = "Assets", pages = listOf(Page(elements = listOf(group)))))
        session.delete(setOf(group.id))
        assertTrue(image.assetId in session.retainedAssetIds)
        session.undo()
        assertEquals(group, session.page.elements.single())
    }
}
