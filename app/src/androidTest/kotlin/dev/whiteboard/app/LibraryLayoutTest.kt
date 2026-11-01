package dev.whiteboard.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dev.whiteboard.core.model.Board
import dev.whiteboard.core.model.newId
import dev.whiteboard.data.DatabaseMigrations
import dev.whiteboard.data.LocalBoardRepository
import dev.whiteboard.data.WhiteboardDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryLayoutTest {
    @get:Rule val capture = CaptureUiFailure()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val database = Room.databaseBuilder(context, WhiteboardDatabase::class.java, "whiteboard.db")
        .addMigrations(DatabaseMigrations.FROM_1_TO_2, DatabaseMigrations.FROM_2_TO_3, DatabaseMigrations.FROM_3_TO_4).build()
    private val repository = LocalBoardRepository(database)
    private val owned = mutableListOf<String>()
    private val originalFontScale = device.executeShellCommand("settings get system font_scale").trim().toFloatOrNull()
    private var fontChanged = false
    private var completed = false
    private var caseName = "normal"

    @After fun cleanup(): Unit = runBlocking {
        if (!completed) capture.snapshot("library-$caseName-before-cleanup")
        if (fontChanged) {
            device.executeShellCommand(
                originalFontScale?.let { "settings put system font_scale $it" } ?: "settings delete system font_scale"
            )
        }
        owned.forEach { repository.deletePermanently(it) }
        database.close()
    }

    @Test fun longBoardNamesKeepReadableSpaceAndAnAccessibleOpenTarget() = verifyLayout(doubleFont = false)

    @Test fun doubleFontKeepsReadableNamesAndAnAccessibleOpenTarget() = verifyLayout(doubleFont = true)

    private fun visibleTitle(name: String): UiObject2 {
        repeat(6) {
            device.findObject(By.text(name).clazz("android.widget.TextView"))?.let {
                if (it.visibleBounds.height() > 0) return it
            }
            val grid = checkNotNull(device.wait(Until.findObject(By.desc("Search results").scrollable(true)), 5_000))
            grid.scroll(Direction.DOWN, 0.8f)
            device.waitForIdle()
        }
        error("Board title could not be reached")
    }

    private fun verifyLayout(doubleFont: Boolean): Unit = runBlocking {
        caseName = if (doubleFont) "double-font" else "normal"
        val prefix = "Layout ${newId()}"
        val board = Board(name = "$prefix long board name")
        owned += board.id
        repository.save(board)
        if (doubleFont) {
            fontChanged = true
            device.executeShellCommand("settings put system font_scale 2.0")
        }
        launchTestLibrary(device)
        searchTestLibrary(device, prefix)
        device.waitForIdle()
        if (doubleFont) {
            var actualScale = 0f
            val deadline = android.os.SystemClock.uptimeMillis() + 5_000
            do {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    actualScale = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .firstOrNull { it is MainActivity }?.resources?.configuration?.fontScale ?: 0f
                }
                if (kotlin.math.abs(actualScale - 2f) < 0.01f) break
                android.os.SystemClock.sleep(50)
            } while (android.os.SystemClock.uptimeMillis() < deadline)
            assertEquals("Double font did not reach the active activity", 2f, actualScale, 0.01f)
        }
        val density = context.resources.displayMetrics.density
        val title = visibleTitle(board.name)
        assertTrue("Board title was squeezed beside the preview and menu", title.visibleBounds.width() >= 80 * density - 1)
        var target = title
        while (!target.isClickable) target = checkNotNull(target.parent) { "Board has no open action" }
        assertTrue("Open target is shorter than 48 dp", target.visibleBounds.height() >= 48 * density - 1)
        device.executeShellCommand("screencap -p /sdcard/whiteboard-library-layout.png")
        completed = true
    }
}
