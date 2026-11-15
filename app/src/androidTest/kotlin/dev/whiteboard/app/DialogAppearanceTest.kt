package dev.whiteboard.app

import android.view.View
import android.view.Window
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.core.model.ThemeMode
import dev.whiteboard.ui.InputAlertDialog
import dev.whiteboard.ui.InputDialog
import dev.whiteboard.ui.WhiteboardTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DialogAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun plainDialogOwnsSystemBarContrastAcrossThemeAndFontChanges() = assertOwnWindow(alert = false)

    @Test fun alertDialogOwnsSystemBarContrastAcrossThemeAndFontChanges() = assertOwnWindow(alert = true)

    private fun assertOwnWindow(alert: Boolean) {
        var theme by mutableStateOf(ThemeMode.LIGHT)
        var font by mutableFloatStateOf(1f)
        var dialogView: View? = null
        var originalWindow: Window? = null
        compose.setContent {
            WhiteboardTheme(theme) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, font)) {
                    val body: @Composable () -> Unit = {
                        val view = LocalView.current
                        SideEffect { dialogView = view }
                        Text("Dialog appearance")
                    }
                    if (alert) {
                        InputAlertDialog(
                            onDismissRequest = {},
                            title = { Text("Theme") },
                            text = { body() },
                            confirmButton = {},
                            dismissButton = {}
                        )
                    } else {
                        InputDialog(dismiss = {}) { body() }
                    }
                }
            }
        }
        for ((mode, scale) in listOf(ThemeMode.LIGHT to 1f, ThemeMode.DARK to 2f, ThemeMode.LIGHT to 2f, ThemeMode.DARK to 1f)) {
            compose.runOnIdle {
                theme = mode
                font = scale
            }
            compose.waitForIdle()
            compose.runOnIdle {
                val view = checkNotNull(dialogView)
                val provider = checkNotNull(view.parent as? DialogWindowProvider) { "Expected the actual separate dialog Window" }
                val window = provider.window
                if (originalWindow == null) {
                    originalWindow = window
                } else {
                    assertSame("The test must retain its dialog across changes", originalWindow, window)
                }
                val controller = WindowCompat.getInsetsController(window, view)
                assertEquals("Status icons must follow the dialog theme", mode == ThemeMode.LIGHT, controller.isAppearanceLightStatusBars)
                assertEquals(
                    "Navigation icons must follow the dialog theme",
                    mode == ThemeMode.LIGHT,
                    controller.isAppearanceLightNavigationBars
                )
            }
        }
    }
}
