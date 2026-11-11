package dev.whiteboard.app.releaseclient

import android.os.Bundle
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.whiteboard.app.clickConnectedUiControl
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in external UI client, excluded from the regular native test source set and release APK. */
@RunWith(AndroidJUnit4::class)
class ReleaseUiActionTest {
    @Test fun activateRequestedControl() {
        val request = InstrumentationRegistry.getArguments()
        val target = checkNotNull(request.getString("wb_package"))
        require(target == "dev.whiteboard.app" || target == "dev.whiteboard.app.debug")
        val mode = checkNotNull(request.getString("wb_mode"))
        require(mode == "text" || mode == "description")
        val encoded = checkNotNull(request.getString("wb_label64"))
        require(encoded.length <= 1024)
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        require(bytes.size in 1..512)
        val label = bytes.toString(Charsets.UTF_8)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        clickConnectedUiControl(checkNotNull(instrumentation.uiAutomation), target, label, mode == "description")
        instrumentation.sendStatus(
            0,
            Bundle().apply {
                putString("whiteboard_clicked64", encoded)
                putString("whiteboard_package", target)
            }
        )
    }
}
