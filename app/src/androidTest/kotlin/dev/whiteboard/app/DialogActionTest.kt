package dev.whiteboard.app

import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DialogActionTest {
    @get:Rule val capture = CaptureUiFailure()

    @Test fun aVisibleDisabledControlIsConfirmedExactlyOnceAfterItsPendingStateBecomesEnabled() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrument)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var button: Button
            var confirmations = 0
            var confirmationsBeforeEnabled = -1
            scenario.onActivity { activity ->
                button = Button(activity).apply {
                    setAllCaps(false)
                    text = "Apply"
                    isEnabled = false
                    setOnClickListener { confirmations++ }
                }
                activity.setContentView(button)
            }
            instrument.waitForIdleSync()
            try {
                assertFalse(checkNotNull(device.wait(Until.findObject(By.text("Apply")), 5_000)).isEnabled)
                assertFalse(testDialogActionEnabled("Apply"))
                scenario.onActivity {
                    button.postDelayed({
                        confirmationsBeforeEnabled = confirmations
                        button.isEnabled = true
                    }, 350)
                }
                clickTestDialogText(device, "Apply")
                assertTrue(testDialogActionEnabled("Apply"))
                scenario.onActivity {
                    assertEquals(0, confirmationsBeforeEnabled)
                    assertEquals(1, confirmations)
                }
            } catch (error: Throwable) {
                capture.snapshot("dialog-action-before-cleanup")
                throw error
            }
        }
    }
}
