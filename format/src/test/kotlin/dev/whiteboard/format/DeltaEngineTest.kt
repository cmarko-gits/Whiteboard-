package dev.whiteboard.format

import dev.whiteboard.core.cloud.*
import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class DeltaEngineTest {
    @Test fun conflictOnSecondObjectLeavesFirstObjectUnchanged() {
        val batch = engine.between(board, edited(first.copy(text = "Mine first"), second.copy(text = "Mine second")))
        val remote = edited(first, second.copy(text = "Remote second"))
        conflict(DeltaConflictReason.OBJECT_MODIFIED) { engine.apply(remote, batch) }
        assertEquals(listOf(first, second.copy(text = "Remote second")), remote.activePage.elements)
    }

    @Test fun digestMatchesPortableVersionOneFixture() {
        val fixture = Note(id = "digest-fixture", bounds = Bounds(1f, 2f, 30f, 40f), text = "Ž Ћ 🖊", color = 7, fontSize = 22f)
        assertEquals("6d0685e8d3692d3502e0c944f1d1a3cb28ee4306e70b30ed998e3473d4022ea2", ElementDigest().of(fixture))
    }

    private val engine = DeltaEngine(ElementDigest())
    private val first = Note(id = "first", bounds = Bounds(0f, 0f, 100f, 100f), text = "One")
    private val second = Note(id = "second", bounds = Bounds(200f, 0f, 300f, 100f), text = "Two")
    private val page = Page(id = "page", elements = listOf(first, second))
    private val board = Board(id = "board", name = "Ideas", pages = listOf(page))
    private fun edited(vararg elements: Element) = board.withPage(page.copy(elements = elements.toList()))

    @Test fun independentOfflineEditsConvergeInEitherArrivalOrder() {
        val a = engine.between(board, edited(first.copy(text = "Alice"), second))
        val b = engine.between(board, edited(first, second.copy(text = "Bob")))
        val ab = engine.apply(engine.apply(board, a).board, b).board
        val ba = engine.apply(engine.apply(board, b).board, a).board
        assertEquals(ab, ba)
        assertEquals(listOf("Alice", "Bob"), ab.activePage.elements.filterIsInstance<Note>().map { it.text })
    }

    @Test fun sameObjectConflictPreservesRemoteAndLeavesOriginalUnchanged() {
        val a = engine.between(board, edited(first.copy(text = "Alice"), second))
        val b = engine.between(board, edited(first.copy(text = "Bob"), second))
        val remote = engine.apply(board, a).board
        conflict(DeltaConflictReason.OBJECT_MODIFIED) { engine.apply(remote, b) }
        assertEquals("Alice", (remote.activePage.elements.first() as Note).text)
        assertEquals("One", (board.activePage.elements.first() as Note).text)
    }

    @Test fun deleteWinsAgainstUpdateAndLateEditCannotResurrectObject() {
        val edit = engine.between(board, edited(first.copy(text = "Changed"), second))
        val delete = engine.between(board, edited(second))
        val changed = engine.apply(board, edit)
        val deleted = engine.apply(changed.board, delete, changed.tombstones)
        assertEquals(listOf(second), deleted.board.activePage.elements)
        conflict(DeltaConflictReason.OBJECT_DELETED) { engine.apply(deleted.board, edit, deleted.tombstones) }
        val restoreOldId = engine.between(deleted.board, edited(first, second))
        conflict(DeltaConflictReason.ID_REUSED) { engine.apply(deleted.board, restoreOldId, deleted.tombstones) }
        val restoreCopy = engine.between(deleted.board, edited(first.duplicate(), second))
        assertEquals(2, engine.apply(deleted.board, restoreCopy, deleted.tombstones).board.activePage.elements.size)
    }

    @Test fun groupsAndUngroupingPreserveLiveChildIdentities() {
        val group = ElementGroup(id = "group", children = listOf(first, second))
        val grouped = engine.apply(board, engine.between(board, edited(group)))
        assertEquals(listOf(group), grouped.board.activePage.elements)
        assertFalse(grouped.tombstones.objects[page.id].orEmpty().contains(first.id))
        val ungrouped = engine.apply(grouped.board, engine.between(grouped.board, board), grouped.tombstones)
        assertEquals(board, ungrouped.board)
        assertTrue(ungrouped.tombstones.objects[page.id].orEmpty().contains(group.id))
    }

    @Test fun groupingAfterConcurrentChildDeletionIsAtomicConflict() {
        val group = ElementGroup(id = "group", children = listOf(first, second))
        val grouped = engine.between(board, edited(group))
        val deleted = engine.apply(board, engine.between(board, edited(second)))
        conflict(DeltaConflictReason.OBJECT_DELETED) { engine.apply(deleted.board, grouped, deleted.tombstones) }
        assertEquals(listOf(second), deleted.board.activePage.elements)
    }

    @Test fun pageDeletionCannotBeReversedByStaleMetadataOrReusedPageId() {
        val extra = Page(id = "extra")
        val initial = board.copy(pages = listOf(page, extra))
        val renamed = engine.between(initial, initial.withPage(page.copy(name = "Alice")))
        val result = engine.apply(initial, engine.between(initial, initial.copy(pages = listOf(extra), activePageId = extra.id)))
        conflict(DeltaConflictReason.PAGE_DELETED) { engine.apply(result.board, renamed, result.tombstones) }
        conflict(DeltaConflictReason.ID_REUSED) { engine.apply(result.board, engine.between(result.board, initial), result.tombstones) }
        conflict(DeltaConflictReason.LAST_PAGE) {
            engine.apply(board, DocumentDelta(pages = listOf(PageMutation(page.id, deleted = true))))
        }
    }

    @Test fun cameraAndLibraryChangesProduceNoSharedOperation() {
        val personal = board.copy(favorite = true, folderId = "folder", trashedAt = 10, updatedAt = 100)
            .withPage(page.copy(viewport = Viewport(Point(300f, 200f), 2f)))
        assertTrue(engine.between(board, personal).empty)
    }

    @Test fun reorderRetainsConcurrentUnknownObjectsAndDoesNotResurrectDeletedOnes() {
        val reorder = engine.between(board, edited(second, first))
        val added = first.copy(id = "remote", text = "Remote")
        val current = edited(first, second, added)
        val result = engine.apply(current, reorder)
        assertEquals(listOf(second.id, first.id, added.id), result.board.activePage.elements.map { it.id })
        val removal = engine.apply(board, engine.between(board, edited(second)))
        val reordered = engine.apply(removal.board, reorder, removal.tombstones)
        assertEquals(listOf(second), reordered.board.activePage.elements)
    }

    @Test fun undoOfOwnEditCannotOverwriteAnotherUsersLaterEdit() {
        val mine = edited(first.copy(text = "Mine"), second)
        val undo = engine.between(mine, board)
        val later = edited(first.copy(text = "Remote afterwards"), second)
        conflict(DeltaConflictReason.OBJECT_MODIFIED) { engine.apply(later, undo) }
    }

    @Test fun elementFingerprintIsStableAfterDocumentRoundTripIncludingUnicodeAndGroups() {
        val group = ElementGroup(id = "group", children = listOf(first.copy(text = "Đirilično Ћирилично 🖊"), second))
        val source = edited(group)
        val decoded = DocumentCodec().decode(DocumentCodec().encode(source))
        val digest = ElementDigest()
        assertEquals(digest.of(group), digest.of(decoded.activePage.elements.single()))
        assertNotEquals(digest.of(first), digest.of(first.copy(text = "different")))
    }

    private fun conflict(reason: DeltaConflictReason, action: () -> Unit) {
        try {
            action()
            fail("Expected $reason")
        } catch (error: DeltaConflict) {
            assertEquals(reason, error.reason)
        }
    }
}
