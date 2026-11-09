package dev.whiteboard.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Debug
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.whiteboard.app.workspace.LocalMediaStore
import dev.whiteboard.app.workspace.WorkspaceViewModel
import dev.whiteboard.canvas.WhiteboardView
import dev.whiteboard.core.model.*
import dev.whiteboard.data.*
import java.io.File
import kotlinx.coroutines.runBlocking
import leakcanary.AppWatcher
import leakcanary.LeakAssertions
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import shark.HprofHeapGraph.Companion.openHeapGraph

@RunWith(AndroidJUnit4::class)
class MemoryQualificationTest {
    private fun canvas(view: View): WhiteboardView? {
        if (view is WhiteboardView) return view
        if (view is ViewGroup) repeat(view.childCount) { canvas(view.getChildAt(it))?.let { return it } }
        return null
    }

    @Test fun destroyedEditorsAreCollectedAfterRepeatedActivityAndMediaCycles(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.databaseBuilder(context, WhiteboardDatabase::class.java, "whiteboard.db")
            .addMigrations(DatabaseMigrations.FROM_1_TO_2, DatabaseMigrations.FROM_2_TO_3, DatabaseMigrations.FROM_3_TO_4).build()
        val repository = LocalBoardRepository(database)
        val media = LocalMediaStore(context)
        val elements = List(8) { index ->
            MediaElement(bounds = Bounds(index * 60f, 100f, index * 60f + 120f, 260f), assetId = "${newId()}.png")
        }
        val board = Board(name = "Memory ${newId()}", pages = listOf(Page(elements = elements)))
        val samples = mutableListOf<Long>()
        val heap = File(context.cacheDir, "qualification-memory.hprof")
        fun watchEditor(activity: MainActivity) {
            val editor = checkNotNull(canvas(activity.window.decorView)) { "The real editor View is missing" }
            AppWatcher.objectWatcher.expectWeaklyReachable(editor, "Editor view must be released after its Activity closes")
            AppWatcher.objectWatcher.expectWeaklyReachable(
                ViewModelProvider(activity)[WorkspaceViewModel::class.java],
                "Editor ViewModel must be released after final Activity destruction"
            )
        }
        try {
            elements.forEach { element ->
                val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                try {
                    media.file(element.assetId).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally {
                    bitmap.recycle()
                }
            }
            repository.save(board)
            repeat(6) {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    awaitTestLibrary(device)
                    openTestBoard(device, board.name)
                    checkNotNull(device.wait(Until.findObject(By.descContains("Drawing canvas")), 10_000))
                    instrumentation.waitForIdleSync()
                    scenario.onActivity(::watchEditor)
                    scenario.recreate()
                    checkNotNull(device.wait(Until.findObject(By.descContains("Drawing canvas")), 10_000))
                    instrumentation.waitForIdleSync()
                    scenario.onActivity(::watchEditor)
                    samples.add(Debug.getPss())
                    checkNotNull(device.findObject(By.desc("Back to boards"))).click()
                    checkNotNull(device.wait(Until.findObject(By.desc("New board")), 10_000))
                }
                instrumentation.waitForIdleSync()
            }
            LeakAssertions.assertNoLeaks("Six actual editor/media/recreate/close cycles")
            Debug.dumpHprofData(heap.absolutePath)
            assertTrue("Heap dump must stay within the QA storage budget", heap.length() <= 128L * 1024 * 1024)
            heap.openHeapGraph().use { graph ->
                val remaining = graph.findClassByName(WhiteboardView::class.java.name)?.instances?.count() ?: 0
                val report = JSONObject().put("cycles", 6).put("watchedEditorViews", 12).put("actualEditorUi", true)
                    .put("leakCanaryAssertion", "passed").put("remainingCanvasInstances", remaining)
                    .put("heapDumpBytes", heap.length()).put("heapObjects", graph.objectCount)
                    .put("pssKbPerCycle", org.json.JSONArray(samples)).put("pssKbAfterGc", Debug.getPss())
                    .put("api", android.os.Build.VERSION.SDK_INT)
                File(context.getExternalFilesDir(null), "qualification-memory.json").writeText(report.toString(2))
                assertEquals("Closed editor Views remain in the heap", 0, remaining)
            }
        } catch (failure: Throwable) {
            CaptureUiFailure().snapshot("memory-before-cleanup")
            throw failure
        } finally {
            heap.delete()
            repository.deletePermanently(board.id)
            elements.forEach { media.file(it.assetId).delete() }
            database.close()
        }
    }
}
