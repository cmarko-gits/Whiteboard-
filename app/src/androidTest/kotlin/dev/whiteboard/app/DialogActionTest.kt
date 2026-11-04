package dev.whiteboard.app

import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DialogActionTest {
    @Test fun aVisibleDisabledControlIsConfirmedExactlyOnceAfterItsPendingStateBecomesEnabled() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrument)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var button: Button
            var confirmations = 0
            var confirmationsBeforeEnabled = -1
            scenario.onActivity { activity ->
                button = Button(activity).apply {
                    text = "Apply"
                    isEnabled = false
                    setOnClickListener { confirmations++ }
                }
                activity.setContentView(button)
            }
            instrument.waitForIdleSync()
            scenario.onActivity {
                button.postDelayed({
                    confirmationsBeforeEnabled = confirmations
                    button.isEnabled = true
                }, 350)
            }
            clickTestDialogText(device, "Apply")
            scenario.onActivity {
                assertEquals(0, confirmationsBeforeEnabled)
                assertEquals(1, confirmations)
            }
        }
    }
}
