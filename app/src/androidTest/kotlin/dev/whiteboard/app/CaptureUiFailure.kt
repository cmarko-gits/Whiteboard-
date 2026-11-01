package dev.whiteboard.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Keeps the original failure while preserving the disposable emulator UI for diagnosis. */
class CaptureUiFailure : TestWatcher() {
    override fun failed(error: Throwable, description: Description) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val name = description.methodName.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val directory = context.getExternalFilesDir(null) ?: return
        runCatching { device.dumpWindowHierarchy(File(directory, "failure-$name.xml")) }
        runCatching { device.takeScreenshot(File(directory, "failure-$name.png")) }
    }
}
