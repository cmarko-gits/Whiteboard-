package dev.whiteboard.app

import android.view.View
import android.view.Window
import android.view.WindowManager
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
import dev.whiteboard.ui.WhiteboardAlertDialog
import dev.whiteboard.ui.WhiteboardTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DialogAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun plainDialogOwnsSystemBarContrastAcrossThemeAndFontChanges() = assertOwnWindow(Kind.INPUT)

    @Test fun alertDialogOwnsSystemBarContrastAcrossThemeAndFontChanges() = assertOwnWindow(Kind.INPUT_ALERT)

    @Test fun standardAlertOwnsSystemBarContrastAcrossThemeAndFontChanges() = assertOwnWindow(Kind.ALERT)

    private enum class Kind { INPUT, INPUT_ALERT, ALERT }

    private fun assertOwnWindow(kind: Kind) {
        var theme by mutableStateOf(ThemeMode.LIGHT)
        var font by mutableFloatStateOf(1f)
        var dialogView: View? = null
        var initialBarFlags = 0
        var previousWindow: Window? = null
        var previousFont: Float? = null
        compose.setContent {
            WhiteboardTheme(theme) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, font)) {
                    val body: @Composable () -> Unit = {
                        val view = LocalView.current
                        val window = checkNotNull(view.parent as? DialogWindowProvider).window
                        // Capture before appearance SideEffects run, including on a new
                        // density-owned Window. Icon changes must not alter layout flags.
                        val originalFlags = remember(view) { window.attributes.flags and barWindowFlags }
                        SideEffect {
                            dialogView = view
                            initialBarFlags = originalFlags
                        }
                        Text("Dialog appearance")
                    }
                    when (kind) {
                        Kind.INPUT_ALERT -> InputAlertDialog(
                            onDismissRequest = {},
                            title = { Text("Theme") },
                            text = { body() },
                            confirmButton = {},
                            dismissButton = {}
                        )
                        Kind.ALERT -> WhiteboardAlertDialog(
                            onDismissRequest = {},
                            title = { Text("Theme") },
                            text = { body() },
                            confirmButton = {},
                            dismissButton = {}
                        )
                        Kind.INPUT -> InputDialog(dismiss = {}) { body() }
                    }
                }
            }
        }
        val changes = listOf(
            ThemeMode.LIGHT to 1f,
            ThemeMode.DARK to 1f,
            ThemeMode.LIGHT to 1f,
            ThemeMode.LIGHT to 2f,
            ThemeMode.DARK to 2f,
            ThemeMode.LIGHT to 2f,
            ThemeMode.DARK to 1f
        )
        for ((mode, scale) in changes) {
            compose.runOnIdle {
                theme = mode
                font = scale
            }
            compose.waitForIdle()
            compose.runOnIdle {
                val view = checkNotNull(dialogView)
                val provider = checkNotNull(view.parent as? DialogWindowProvider) { "Expected the actual separate dialog Window" }
                val window = provider.window
                assertEquals(
                    "Appearance must preserve the dialog's bar/layout flags",
                    initialBarFlags,
                    window.attributes.flags and barWindowFlags
                )
                if (previousFont == scale) {
                    assertSame("A theme-only change must update the retained dialog", previousWindow, window)
                }
                // Compose remembers DialogWrapper with density as a key. Font changes
                // may replace its Window; that new Window must get the right flags too.
                previousWindow = window
                previousFont = scale
                val controller = WindowCompat.getInsetsController(window, view)
                assertFalse("Status icons must stay light over the dialog's dimmed owner", controller.isAppearanceLightStatusBars)
                assertFalse("Navigation icons must stay light over the dialog's dimmed owner", controller.isAppearanceLightNavigationBars)
            }
        }
    }

    private companion object {
        @Suppress("DEPRECATION") // These flags still determine legacy floating-dialog insets.
        val barWindowFlags = WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION or
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
    }
}
