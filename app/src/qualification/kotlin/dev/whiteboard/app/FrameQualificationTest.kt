package dev.whiteboard.app

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Window
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.canvas.WhiteboardView
import dev.whiteboard.core.model.*
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.sin
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Choreographer-driven real Window frames; emulator measurements are not physical GPU FPS. */
@RunWith(AndroidJUnit4::class)
class FrameQualificationTest {
    @Test fun recordsWindowFramesAndASampledCpuTraceForALargeCanvas() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val samples = Collections.synchronizedList(mutableListOf<Double>())
        val worker = HandlerThread("Whiteboard-frame-qualification").apply { start() }
        val done = CountDownLatch(1)
        val elements = List(1000) { index ->
            Stroke(
                points = List(100) { sample ->
                    Point(index % 40 * 70f + sample * 0.5f, index / 40 * 70f + sin(sample * 0.1f) * 20f)
                },
                style = InkStyle()
            )
        }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            samples.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0)
        }
        val trace = File(context.getExternalFilesDir(null), "qualification-cpu")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val view = WhiteboardView(activity)
                    view.update(Page(elements = elements), Tool.PAN, InkStyle(), emptySet())
                    activity.setContentView(view)
                    activity.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper))
                    Debug.startMethodTracingSampling(trace.absolutePath, 8 * 1024 * 1024, 1000)
                    Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
                        private var frame = 0
                        override fun doFrame(frameTimeNanos: Long) {
                            if (frame++ >= 90) {
                                done.countDown()
                                return
                            }
                            view.zoomBy(if (frame % 2 == 0) 1.01f else 1 / 1.01f)
                            Choreographer.getInstance().postFrameCallback(this)
                        }
                    })
                }
                assertTrue("The frame workload timed out", done.await(90, TimeUnit.SECONDS))
                scenario.onActivity { activity ->
                    Debug.stopMethodTracing()
                    activity.window.removeOnFrameMetricsAvailableListener(listener)
                }
            }
        } finally {
            Debug.stopMethodTracing()
            worker.quitSafely()
            worker.join(2000)
        }
        val sorted = synchronized(samples) { samples.toList().sorted() }
        assertTrue("Expected actual rendered Window frames", sorted.size >= 20)
        fun percentile(p: Double) = sorted[(ceil(sorted.size * p).toInt() - 1).coerceIn(sorted.indices)]
        val report = JSONObject().put("frames", sorted.size).put("objects", 1000).put("points", 100_000)
            .put("p50Ms", percentile(0.5)).put("p95Ms", percentile(0.95)).put("p99Ms", percentile(0.99))
            .put("sampledCpuTrace", "qualification-cpu.trace").put("api", android.os.Build.VERSION.SDK_INT)
            .put("environment", "emulator; tracing enabled; not physical-device FPS or latency")
        File(context.getExternalFilesDir(null), "qualification-frames.json").writeText(report.toString(2))
    }
}
