package dev.whiteboard.core

import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class FolderTreeTest {
    private val root = Folder(name = "Work")
    private val child = Folder(name = "Architecture", parentId = root.id)
    private val tree = FolderTree(listOf(root, child))

    @Test fun `cannot move a folder into its descendant`() {
        assertFalse(tree.canMove(root.id, child.id))
    }

    @Test fun `can move a nested folder to root`() {
        assertTrue(tree.canMove(child.id, null))
    }

    @Test fun `paths include ancestors`() {
        assertEquals("Work / Architecture", tree.path(child.id))
    }
}
