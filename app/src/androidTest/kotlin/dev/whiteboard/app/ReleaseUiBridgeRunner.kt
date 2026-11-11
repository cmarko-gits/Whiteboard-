package dev.whiteboard.app

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64

/** External test client only: operates on the installed R8 APK without adding hooks to it. */
class ReleaseUiBridgeRunner : Instrumentation() {
    private var request = Bundle()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        request = Bundle(arguments ?: Bundle())
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            val target = checkNotNull(request.getString("wb_package"))
            require(target == "dev.whiteboard.app" || target == "dev.whiteboard.app.debug")
            val mode = checkNotNull(request.getString("wb_mode"))
            require(mode == "text" || mode == "description")
            val encoded = checkNotNull(request.getString("wb_label64"))
            require(encoded.length <= 1024)
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            require(bytes.size in 1..512)
            val label = bytes.toString(Charsets.UTF_8)
            val automation = checkNotNull(uiAutomation) { "UI automation did not connect" }
            val deadline = SystemClock.uptimeMillis() + 10_000
            while (!activateConnectedUiControl(automation, target, label, mode == "description")) {
                check(SystemClock.uptimeMillis() < deadline) { "Connected control unavailable in $target: $mode/$label" }
                SystemClock.sleep(50)
            }
            // A successful semantic action is never replayed.
            result.putString("whiteboard_clicked64", encoded)
            result.putString("whiteboard_package", target)
            finish(Activity.RESULT_OK, result)
        } catch (error: Exception) {
            result.putString("whiteboard_error", error.stackTraceToString().take(8192))
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
