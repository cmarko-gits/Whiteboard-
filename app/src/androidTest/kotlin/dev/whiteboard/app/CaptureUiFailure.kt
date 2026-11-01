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
        snapshot("failure-${description.methodName}")
    }

    fun snapshot(label: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val name = label.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = output?.let(::File) ?: context.getExternalFilesDir(null) ?: return
        directory.mkdirs()
        runCatching { device.dumpWindowHierarchy(File(directory, "$name.xml")) }
        runCatching { device.takeScreenshot(File(directory, "$name.png")) }
    }
}
