package dev.whiteboard.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.core.model.ThemeMode
import dev.whiteboard.ui.SettingsSwitch
import dev.whiteboard.ui.WhiteboardTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun clickingTheLabelChangesOneNamedSwitch() {
        var enabled by mutableStateOf(false)
        compose.setContent {
            WhiteboardTheme(ThemeMode.LIGHT) { SettingsSwitch("Draw with finger", enabled, { enabled = it }) }
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        val control = compose.onNodeWithText("Draw with finger")
        control.assertIsOff().assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        // Hit the label area, away from the switch graphic on the right.
        control.performTouchInput { click(Offset(8f, center.y)) }.assertIsOn()
    }

    @Test fun aLongLabelAtDoubleFontScaleRemainsOneAccessibleTarget() {
        val label = "Automatically save completed edits"
        var enabled by mutableStateOf(true)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                WhiteboardTheme(ThemeMode.DARK) { SettingsSwitch(label, enabled, { enabled = it }) }
            }
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onNodeWithText(label).assertIsOn().assertHeightIsAtLeast(48.dp).performClick().assertIsOff()
    }
}
