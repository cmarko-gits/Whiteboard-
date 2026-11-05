package dev.whiteboard.core

import dev.whiteboard.core.editor.DocumentSession
import dev.whiteboard.core.editor.ObjectClipboard
import dev.whiteboard.core.editor.ObjectCopies
import dev.whiteboard.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ObjectCopiesTest {
    private val note = Note(id = "note", bounds = Bounds(10f, 20f, 110f, 120f), text = "Keep")
    private val media = MediaElement(
        id = "media",
        bounds = Bounds(200f, 20f, 400f, 120f),
        assetId = "${newId()}.png",
        rotation = 90,
        crop = Bounds(0.1f, 0.2f, 0.8f, 0.9f)
    )
    private val posed = AffineElement.fromDocument(media, AffineTransform2D.rotation(13.5))
    private val group = ElementGroup(id = "group", children = listOf(note, posed))

    @Test fun allTopLevelAndChildIdsAreMappedBeforeCopiesCanReferToThem() {
        val line = Line(id = "line", start = Point(5f, 6f), end = Point(70f, 80f), style = InkStyle(), arrow = true)
        var next = 0
        val result = ObjectCopies.duplicate(listOf(group, line), identity = { "copied-${next++}" })
        assertEquals(mapOf("group" to "copied-0", "note" to "copied-1", "media" to "copied-2", "line" to "copied-3"), result.identities)
        val copied = result.elements.first() as ElementGroup
        assertEquals("copied-0", copied.id)
        assertEquals(listOf("copied-1", "copied-2"), copied.children.map { it.id })
        val copiedPose = copied.children.last() as AffineElement
        assertEquals("copied-2", copiedPose.source.id)
        assertEquals(posed.placement, copiedPose.placement)
        assertEquals(media.crop, (copiedPose.source as MediaElement).crop)
        assertEquals(setOf(media.assetId), result.elements.referencedAssets())
        assertEquals(line.copy(id = "copied-3"), result.elements.last())
        assertEquals(listOf(note, posed), group.children)
    }

    @Test fun translationPreservesEveryLocalKindAndTheExactStrokeSamples() {
        val style = InkStyle(0xFF123456.toInt(), 4f, 0.6f)
        val stroke = Stroke(id = "stroke", points = listOf(Point(0.1f, 0.2f), Point(10.3f, 20.4f)), style = style, tool = Tool.PENCIL)
        val shape = Shape(id = "shape", bounds = note.bounds, kind = Tool.TRIANGLE, style = style, fill = 0xFFAA5500.toInt())
        val line = Line(id = "line", start = Point(2f, 3f), end = Point(20f, 30f), style = style, arrow = true)
        val text = TextElement(id = "text", bounds = note.bounds, text = "Text", bold = true, italic = true)
        val source = listOf(stroke, shape, line, text, media.copy(id = "standalone"), group)
        val result = ObjectCopies.duplicate(source, Point(24f, 48f), identity = { newId() })
        val copiedStroke = result.elements[0] as Stroke
        assertEquals(stroke.points.map { it + Point(24f, 48f) }, copiedStroke.points)
        assertEquals(stroke.tool, copiedStroke.tool)
        assertEquals(style, copiedStroke.style)
        assertEquals(shape.copy(id = result.identities.getValue(shape.id), bounds = Bounds(34f, 68f, 134f, 168f)), result.elements[1])
        assertEquals(
            line.copy(id = result.identities.getValue(line.id), start = Point(26f, 51f), end = Point(44f, 78f)),
            result.elements[2]
        )
        assertEquals(text.copy(id = result.identities.getValue(text.id), bounds = Bounds(34f, 68f, 134f, 168f)), result.elements[3])
        val copiedMedia = result.elements[4] as MediaElement
        assertEquals(media.rotation, copiedMedia.rotation)
        assertEquals(media.crop, copiedMedia.crop)
        assertEquals(Bounds(224f, 68f, 424f, 168f), copiedMedia.bounds)
        val copiedGroup = result.elements[5] as ElementGroup
        val copiedPose = copiedGroup.children.last() as AffineElement
        assertEquals(posed.placement.then(AffineTransform2D.translation(24.0, 48.0)), copiedPose.placement)
        assertEquals(note.text, (copiedGroup.children.first() as Note).text)
        assertEquals(Point(0.1f, 0.2f), stroke.points.first())
    }

    @Test fun zeroOffsetPreservesDegenerateFramesAndDoesNotNormalizeDocumentGeometry() {
        val zero = note.copy(bounds = Bounds(1_000_000_000f, 0f, 1_000_000_000f, 0f))
        val copied = ObjectCopies.duplicate(listOf(zero), identity = { "new" }).elements.single() as Note
        assertEquals(zero.copy(id = "new"), copied)
        val moved = ObjectCopies.duplicate(listOf(zero), Point(-100f, 50f), identity = { "moved" }).elements.single()
        assertEquals(Bounds(zero.bounds.left - 100f, 50f, zero.bounds.right - 100f, 50f), moved.bounds)
    }

    @Test fun identityCollisionsCannotSilentlyKeepAnOriginalOrOverwriteAGroupChild() {
        listOf("group", "note", "media", "", " ", "x".repeat(129)).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { ObjectCopies.duplicate(listOf(group), identity = { bad }) }
        }
        assertThrows(IllegalArgumentException::class.java) { ObjectCopies.duplicate(listOf(group), identity = { "same-new-id" }) }
        assertEquals(listOf("note", "media"), group.children.map { it.id })
    }

    @Test fun duplicateSourceIdsAndTheCombinedLeafLimitAreRejectedBeforeCopying() {
        assertThrows(IllegalArgumentException::class.java) { ObjectCopies.duplicate(listOf(note, note)) }
        assertThrows(IllegalArgumentException::class.java) { ObjectCopies.duplicate(listOf(group, note)) }
        val large = ElementGroup(children = List(Page.MAX_ELEMENTS) { note.copy(id = "child-$it") })
        assertThrows(IllegalArgumentException::class.java) { ObjectCopies.duplicate(listOf(large, note)) }
        assertThrows(IllegalArgumentException::class.java) { ObjectCopies.duplicate(List(Page.MAX_ELEMENTS + 1) { note }) }
    }

    @Test fun cancellingIdentityAllocationLeavesTheDocumentAndHistoryUntouched() {
        val session = DocumentSession(Board(name = "Original", pages = listOf(Page(elements = listOf(group)))))
        var allocated = 0
        assertThrows(CancellationException::class.java) {
            ObjectCopies.duplicate(session.page.elements, identity = {
                if (++allocated == 2) throw CancellationException()
                "new-$allocated"
            })
        }
        assertEquals(2, allocated)
        assertEquals(listOf(group), session.page.elements)
        assertEquals(0L, session.revision)
        assertFalse(session.canUndo)
    }

    @Test fun translationOfALongStrokeCanBeCancelledBetweenSampleChunks() {
        val stroke = Stroke(id = "long", points = List(50_000) { Point(it.toFloat(), 0f) }, style = InkStyle())
        var visits = 0
        assertThrows(CancellationException::class.java) {
            ObjectCopies.duplicate(listOf(stroke), Point(24f, 24f), checkpoint = {
                if (++visits == 17) throw CancellationException()
            })
        }
        assertEquals(17, visits)
        assertEquals(Point(0f, 0f), stroke.points.first())
        assertEquals(Point(49_999f, 0f), stroke.points.last())
    }

    @Test fun unsafeSourceOrTranslatedInkCannotBePublished() {
        val source = note.copy(bounds = Bounds(999_999_936f, 0f, 1_000_000_000f, 100f))
        assertThrows(IllegalArgumentException::class.java) { ObjectCopies.duplicate(listOf(source), Point(128f, 0f)) }
        val invalid = Shape(id = "unsafe", bounds = source.bounds, kind = Tool.RECTANGLE, style = InkStyle(width = 200f))
        assertThrows(IllegalArgumentException::class.java) { ObjectCopies.duplicate(listOf(invalid), Point(-1000f, 0f)) }
        assertEquals(1_000_000_000f, source.bounds.right)
    }

    @Test fun pageDuplicationUsesTheSameChildIdentityPolicyAndOneUndoRestoresTheSource() {
        val original = Page(elements = listOf(group))
        val session = DocumentSession(Board(name = "Pages", pages = listOf(original)))
        session.duplicatePage(original.id)
        val copied = session.page.elements.single() as ElementGroup
        assertTrue((listOf(copied.id) + copied.children.map { it.id }).none { it in setOf("group", "note", "media") })
        assertEquals(setOf(media.assetId), session.page.elements.referencedAssets())
        assertEquals(group.bounds, copied.bounds)
        session.undo()
        assertEquals(listOf(original), session.board.pages)
        assertFalse(session.dirty)
        session.redo()
        assertEquals(copied, session.page.elements.single())
    }

    @Test fun clipboardPreservesItsOffsetCycleAndNeverReusesChildIdentities() {
        val clipboard = ObjectClipboard()
        clipboard.copy(listOf(group))
        val allocated = HashSet<String>()
        repeat(21) { index ->
            val copied = clipboard.paste().single() as ElementGroup
            (listOf(copied.id) + copied.children.map { it.id }).forEach { assertTrue(allocated.add(it)) }
            val offset = 24f * (index % 20 + 1)
            assertEquals(note.bounds.left + offset, copied.children.first().bounds.left)
            assertEquals(setOf(media.assetId), clipboard.assetIds)
        }
        clipboard.copy(emptyList())
        assertFalse(clipboard.available)
        assertTrue(clipboard.paste().isEmpty())
    }
}
