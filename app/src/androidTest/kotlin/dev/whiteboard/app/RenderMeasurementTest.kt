package dev.whiteboard.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.whiteboard.canvas.WhiteboardView
import dev.whiteboard.core.model.*
import kotlin.math.ceil
import kotlin.math.sin
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Repeatable software draw/culling/cache measurement. Does not measure GPU frames or stylus latency. */
@RunWith(AndroidJUnit4::class)
class RenderMeasurementTest {
    @Test fun recordsWarmRenderDistributionWithoutMutatingTheDocument() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val page = Page(
            elements = List(1000) { index ->
                val x = index % 40 * 70f
                val y = index / 40 * 70f
                Stroke(points = List(100) { sample -> Point(x + sample * 0.5f, y + sin(sample * 0.1f) * 20f) }, style = InkStyle())
            }
        )
        val instrument = InstrumentationRegistry.getInstrumentation()
        val output = Bitmap.createBitmap(1080, 1200, Bitmap.Config.ARGB_8888)
        try {
            instrument.runOnMainSync {
                val canvas = Canvas(output)
                for ((name, viewport) in listOf("visible" to Viewport(), "culled" to Viewport(Point(-100_000f, -100_000f)))) {
                    val view = WhiteboardView(context)
                    view.layout(0, 0, output.width, output.height)
                    view.update(page, Tool.PEN, InkStyle(), emptySet(), viewport)
                    repeat(5) { view.draw(canvas) }
                    val samples = List(30) {
                        val started = SystemClock.elapsedRealtimeNanos()
                        view.draw(canvas)
                        (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
                    }.sorted()
                    fun percentile(p: Double) = samples[(ceil(samples.size * p).toInt() - 1).coerceIn(samples.indices)]
                    val result = JSONObject().put("scenario", name).put("renderer", "software-canvas")
                        .put("model", Build.MODEL).put("api", Build.VERSION.SDK_INT).put("samples", samples.size)
                        .put("warmups", 5).put("objects", page.elements.size).put("points", 100_000)
                        .put("width", output.width).put("height", output.height)
                        .put("p50Ms", percentile(0.50)).put("p95Ms", percentile(0.95)).put("p99Ms", percentile(0.99))
                    Log.i("WhiteboardRender", result.toString())
                }
                assertEquals(1000, page.elements.size)
                assertEquals(100_000, page.elements.sumOf { (it as Stroke).points.size })
            }
        } finally {
            output.recycle()
        }
    }
}
