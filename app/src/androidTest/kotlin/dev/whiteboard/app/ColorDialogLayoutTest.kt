package dev.whiteboard.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.core.model.ThemeMode
import dev.whiteboard.ui.AdaptiveDialogContent
import dev.whiteboard.ui.AdaptiveDialogSurface
import dev.whiteboard.ui.WhiteboardTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorDialogLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactViewportKeepsTheHexFieldAndBothActionsVisible() {
        compose.setContent {
            WhiteboardTheme(ThemeMode.LIGHT) {
                Box(Modifier.requiredSize(400.dp, 82.dp)) {
                    AdaptiveDialogSurface { compact, _ ->
                        AdaptiveDialogContent(
                            compact = compact,
                            input = {
                                OutlinedTextField(
                                    value = "65B7F2",
                                    onValueChange = {},
                                    label = { Text("HEX color") },
                                    singleLine = true,
                                    modifier = Modifier.testTag("compact-hex")
                                )
                            },
                            actions = {
                                TextButton(onClick = {}) { Text("Cancel") }
                                TextButton(onClick = {}) { Text("Apply") }
                            },
                            body = { Box(Modifier.height(164.dp)) }
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("compact-hex").assertIsDisplayed().assertHeightIsAtLeast(56.dp).assertTextContains("65B7F2")
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Apply").assertIsDisplayed()
    }
}
