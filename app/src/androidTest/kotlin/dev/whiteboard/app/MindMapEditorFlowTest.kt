package dev.whiteboard.app

import android.content.Context
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import dev.whiteboard.canvas.PageExporter
import dev.whiteboard.core.model.*
import dev.whiteboard.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MindMapEditorFlowTest {
    @get:Rule val capture = CaptureUiFailure()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val database = Room.databaseBuilder(context, WhiteboardDatabase::class.java, "whiteboard.db")
        .addMigrations(DatabaseMigrations.FROM_1_TO_2, DatabaseMigrations.FROM_2_TO_3, DatabaseMigrations.FROM_3_TO_4).build()
    private val repository = LocalBoardRepository(database)
    private val owned = mutableListOf<String>()
    private var completed = false
    private fun text(value: String) = checkNotNull(device.wait(Until.findObject(By.text(value)), 10_000)) { value }
    private fun control(value: String) = checkNotNull(device.wait(Until.findObject(By.desc(value).enabled(true)), 10_000)) { value }
    private fun node(value: String) = checkNotNull(
        device.wait(Until.findObject(By.textContains(value).clazz("android.widget.TextView").pkg(context.packageName)), 10_000)
    ) { value }
    private fun click(value: String) {
        text(value)
        clickTestDialogText(device, value)
    }
    private fun mapActions() {
        control("More actions").click()
        click("Mind maps")
        text("Done")
        device.waitForIdle()
    }
    private suspend fun awaitKeyboardShown() = withTimeout(10_000) {
        val shown = Regex("\\bmInputShown=true\\b")
        while (!shown.containsMatchIn(device.executeShellCommand("dumpsys input_method"))) delay(50)
    }
    private fun submitTitle(value: String) {
        val field = testDialogField(device, "Node title")
        field.text = value
        click("Confirm")
        control("More actions")
        awaitTestKeyboardHidden(device)
    }

    @After fun cleanup(): Unit = runBlocking {
        try {
            if (!completed) capture.snapshot("mind-map-node-editor-before-cleanup")
        } finally {
            try {
                device.setOrientationNatural()
                device.unfreezeRotation()
                launchTestLibrary(device)
                owned.forEach { repository.deletePermanently(it) }
            } finally {
                database.close()
            }
        }
    }
    private suspend fun save(board: Board, predicate: (Page) -> Boolean): Page {
        text("Unsaved changes")
        control("Save").click()
        text("Saved on this device")
        return withTimeout(10_000) {
            var page = repository.load(board.id).activePage
            while (!predicate(page)) {
                delay(50)
                page = repository.load(board.id).activePage
            }
            page
        }
    }

    @Test fun localizedSemanticTemplateSupportsNodeEditsReparentHiddenBranchMovementAndSavedReopen(): Unit = runBlocking {
        val board = TemplateFactory.create("Node actions ${newId()}", BoardTemplate.MIND_MAP)
        val source = board.activePage
        val tree = source.mindMaps.single()
        val root = source.elements.first { it.id == tree.rootId } as Note
        val people = source.elements.filterIsInstance<Note>().single { it.text == "People" }
        owned += board.id
        repository.save(board)
        launchTestLibrary(device)
        openTestBoard(device, board.name)
        mapActions()
        testDialogField(device, "Find a node").click()
        testDialogField(device, "Find a node").text = "Central idea"
        awaitKeyboardShown()
        device.setOrientationLeft()
        device.waitForIdle()
        assertEquals("Central idea", testDialogField(device, "Find a node").text)
        testDialogField(device, "Find a node").click()
        awaitKeyboardShown()
        click("Done")
        awaitTestKeyboardHidden(device)
        device.setOrientationNatural()
        device.waitForIdle()
        node("Central idea").click()
        click("Add child")
        submitTitle("Native child")
        val withChild = save(board) { it.mindMaps.single().nodes.size == 6 }
        val child = withChild.elements.filterIsInstance<Note>().single { it.text == "Native child" }
        assertEquals(root.id, withChild.mindMaps.single().node(child.id).parentId)
        assertTrue(source.elements.all { old -> withChild.elements.first { it.id == old.id } == old })
        mapActions()
        click("Add sibling")
        submitTitle("Native sibling")
        val withSibling = save(board) { it.mindMaps.single().nodes.size == 7 }
        val sibling = withSibling.elements.filterIsInstance<Note>().single { it.text == "Native sibling" }
        mapActions()
        click("Edit node")
        testDialogField(device, "Node title").text = "Edited sibling"
        device.setOrientationLeft()
        device.waitForIdle()
        assertEquals("Edited sibling", testDialogField(device, "Node title").text)
        click("Confirm")
        control("More actions")
        awaitTestKeyboardHidden(device)
        save(board) { it.elements.filterIsInstance<Note>().any { note -> note.id == sibling.id && note.text == "Edited sibling" } }
        device.setOrientationNatural()
        device.waitForIdle()
        mapActions()
        click("Change parent")
        node("People").click()
        val reparented = save(board) { it.mindMaps.single().node(sibling.id).parentId == people.id }
        val managed = reparented.elements.first { it.id == reparented.mindMaps.single().node(sibling.id).connectionId } as Connector
        assertEquals(people.id, (managed.start as ConnectorEnd.Bound).targetId)
        assertEquals(sibling.bounds, (reparented.elements.first { it.id == sibling.id } as Note).bounds)
        control("Undo").click()
        save(board) { it.mindMaps.single().node(sibling.id).parentId == root.id }
        control("Redo").click()
        save(board) { it.mindMaps.single().node(sibling.id).parentId == people.id }
        mapActions()
        click("Mind maps")
        node("People").click()
        click("Collapse branch")
        val hidden = save(board) { it.mindMaps.single().node(people.id).collapsed }
        assertFalse(hidden.mindMapIndex!!.isVisible(sibling.id))
        mapActions()
        click("Move whole branch")
        testDialogField(device, "Horizontal movement").text = "-120"
        testDialogField(device, "Vertical movement").text = "80"
        testDialogField(device, "Horizontal movement").click()
        awaitKeyboardShown()
        device.setOrientationLeft()
        device.waitForIdle()
        assertEquals("-120", testDialogField(device, "Horizontal movement").text)
        assertEquals("80", testDialogField(device, "Vertical movement").text)
        capture.snapshot("mind-map-branch-move-input")
        click("Confirm")
        control("More actions")
        awaitTestKeyboardHidden(device)
        device.setOrientationNatural()
        device.waitForIdle()
        val moved = save(board) {
            it.geometry.selectionBounds(it.elements.first { objectValue -> objectValue.id == people.id }).center ==
                people.bounds.center + Point(-120f, 80f)
        }
        val movedSibling = moved.elements.first { it.id == sibling.id } as Note
        assertEquals(sibling.bounds.center + Point(-120f, 80f), movedSibling.bounds.center)
        assertEquals(hidden.mindMaps, moved.mindMaps)
        assertEquals(root, moved.elements.first { it.id == root.id })
        mapActions()
        click("Expand branch")
        val expanded = save(board) { !it.mindMaps.single().node(people.id).collapsed }
        assertTrue(expanded.mindMapIndex!!.isVisible(sibling.id))
        mapActions()
        click("Select map")
        text("Saved on this device")
        withTimeout(10_000) {
            while (control("Note: Edited sibling").visibleBounds.bottom > control("Zoom out").visibleBounds.top) delay(50)
        }
        capture.snapshot("mind-map-node-editor-saved")
        control("Back to boards").click()
        openTestBoard(device, board.name)
        text("Saved on this device")
        val reopened = repository.load(board.id).activePage
        assertEquals(expanded.elements, reopened.elements)
        assertEquals(expanded.mindMaps, reopened.mindMaps)
        completed = true
    }

    @Test fun blankBoardCanCancelCreationThenCreateAColoredEditableRootAndExportItsActualColor(): Unit = runBlocking {
        val board = Board(name = "Create map ${newId()}")
        owned += board.id
        repository.save(board)
        launchTestLibrary(device)
        openTestBoard(device, board.name)
        mapActions()
        click("New mind map")
        testDialogField(device, "Node title").text = "Cancelled"
        click("Cancel")
        assertTrue(repository.load(board.id).activePage.mindMaps.isEmpty())
        click("New mind map")
        testDialogField(device, "Node title").text = "Native map"
        clickTestDialogDescription("Color picker")
        testDialogField(device, "HEX color").text = "336699"
        click("Apply")
        click("Confirm")
        control("More actions")
        awaitTestKeyboardHidden(device)
        val page = save(board) { it.mindMaps.size == 1 }
        val root = page.elements.single() as Note
        assertEquals("Native map", root.text)
        assertEquals(Color.rgb(51, 102, 153), root.color)
        assertEquals(root.id, page.mindMaps.single().rootId)
        val image = PageExporter().bitmap(page)
        try {
            val frame = checkNotNull(page.visibleContentBounds).expand(32f)
            val x = ((root.bounds.right - 24f - frame.left) * image.width / frame.width).toInt()
            val y = ((root.bounds.bottom - 24f - frame.top) * image.height / frame.height).toInt()
            assertEquals(root.color, image.getPixel(x, y))
        } finally {
            image.recycle()
        }
        control("Undo").click()
        save(board) { it.elements.isEmpty() && it.mindMaps.isEmpty() }
        control("Redo").click()
        save(board) { it.elements == page.elements && it.mindMaps == page.mindMaps }
        capture.snapshot("mind-map-colored-root")
        completed = true
    }
}
