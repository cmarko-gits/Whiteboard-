package dev.whiteboard.app

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.app.ui.LanguagePicker
import dev.whiteboard.core.model.AppLanguage
import dev.whiteboard.core.model.ThemeMode
import dev.whiteboard.ui.WhiteboardTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguagePickerAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun languageChoicesHaveSingleRadioTargetsAndRemainUsableAtDoubleFontScale() {
        var selected by mutableStateOf<AppLanguage?>(AppLanguage.ENGLISH)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                WhiteboardTheme(ThemeMode.DARK) { LanguagePicker(selected) { selected = it } }
            }
        }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertCountEquals(3)
        compose.onNodeWithText("English").assertIsSelected()
        compose.onNodeWithText("Srpski (latinica)").assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick().assertIsSelected()
        compose.onNodeWithText("English").assertIsNotSelected()
    }
}
