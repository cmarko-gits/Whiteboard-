package dev.whiteboard.app

import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import dev.whiteboard.app.workspace.WorkspaceViewModel
import dev.whiteboard.core.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLanguageFlowTest {
    @get:Rule val capture = CaptureUiFailure()
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrument)
    private fun text(value: String): UiObject2 = checkNotNull(device.wait(Until.findObject(By.text(value)), 10_000)) {
        "Missing language flow text: $value"
    }
    private fun description(value: String): UiObject2 = checkNotNull(device.wait(Until.findObject(By.desc(value)), 10_000)) {
        "Missing language flow control: $value"
    }
    private fun librarySettings(): UiObject2 {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            val target = device.findObject(By.desc("Settings")) ?: device.findObject(By.text("Settings"))
            if (target != null) return target
            SystemClock.sleep(50)
        }
        error("Missing accessible library Settings control")
    }

    private fun englishScenario(block: (ActivityScenario<MainActivity>) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var original = ""
            lateinit var onboarding: Job
            lateinit var initialModel: WorkspaceViewModel
            scenario.onActivity {
                original = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                initialModel = ViewModelProvider(it)[WorkspaceViewModel::class.java]
                onboarding = initialModel.finishOnboarding()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
            }
            try {
                runBlocking {
                    withTimeout(10_000) {
                        onboarding.join()
                        initialModel.state.first { it.settingsLoaded && it.settings.onboarded }
                    }
                }
                instrument.waitForIdleSync()
                check(device.wait(Until.gone(By.text("Start creating")), 10_000))
                testLibraryCreateButton(device)
                block(scenario)
            } finally {
                instrument.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(original)) }
                instrument.waitForIdleSync()
            }
        }
    }

    @Test fun theInAppChoiceSurvivesRecreationAndCanReturnToTheSystemLanguage() = englishScenario { scenario ->
        librarySettings().click()
        text("Srpski (latinica)").click()
        text("Jezik")
        scenario.recreate()
        text("Jezik")
        scenario.onActivity {
            assertEquals("sr-Latn", AppCompatDelegate.getApplicationLocales().toLanguageTags())
            assertEquals("sr", it.resources.configuration.locales[0].language)
        }
        text("Prati sistemski jezik").click()
        text("Language")
        scenario.onActivity { assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty) }
    }

    @Test fun aSerbianTemplateAndUnsavedEditsKeepTheirIdentityAcrossALanguageChange() = englishScenario { scenario ->
        librarySettings().click()
        text("Srpski (latinica)").click()
        text("Jezik")
        text("Gotovo").click()
        testLibraryCreateButton(device, "Nova tabla").click()
        val name = "Locale flow ${SystemClock.uptimeMillis()}"
        val templates = UiScrollable(UiSelector().scrollable(true))
        assertTrue(templates.scrollIntoView(UiSelector().text("Kanban")))
        text("Kanban").click()
        templates.scrollToBeginning(10)
        testDialogField(device, "Naziv").text = name
        text("Potvrdi").click()
        awaitTestKeyboardHidden(device)
        description("Dodatne radnje")
        lateinit var model: WorkspaceViewModel
        lateinit var before: Page
        lateinit var boardId: String
        scenario.onActivity {
            model = ViewModelProvider(it)[WorkspaceViewModel::class.java]
            val editor = checkNotNull(model.state.value.editor)
            assertEquals("Stranica 1", editor.page.name)
            assertEquals("Prati napredak", (editor.page.elements.first() as TextElement).text)
            model.canvas(
                dev.whiteboard.canvas.CanvasEvent.Add(Note(bounds = Bounds(40f, 800f, 300f, 950f), text = "Moja izmena: đak čita"))
            )
            before = checkNotNull(model.state.value.editor).page
            boardId = editor.boardId
        }
        description("Dodatne radnje").click()
        text("Podešavanja").click()
        text("English").click()
        text("Language")
        scenario.onActivity {
            val current = ViewModelProvider(it)[WorkspaceViewModel::class.java]
            assertSame(model, current)
            val editor = checkNotNull(current.state.value.editor)
            assertEquals(boardId, editor.boardId)
            assertEquals(before, editor.page)
            assertTrue(editor.dirty)
        }
        text("Done").click()
        runBlocking { model.save().join() }
        runBlocking { model.open(boardId).join() }
        scenario.onActivity { assertEquals(before.elements, checkNotNull(model.state.value.editor).page.elements) }
    }
}
