package dev.whiteboard.app

import android.os.Build
import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.whiteboard.core.editor.ConnectionCopies
import dev.whiteboard.core.editor.DocumentSession
import dev.whiteboard.core.model.*
import dev.whiteboard.format.DocumentCodec
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Algorithm measurements on the instrumentation worker, not a UI frame-time benchmark. */
@RunWith(AndroidJUnit4::class)
class ConnectionScaleTest {
    @Test fun connectedGraphsPreserveGeometryHistoryAndCopiesAtThreeScales() {
        val reports = JSONArray()
        for (count in listOf(100, 1000, 10_000)) {
            val nodes = List(count) { index ->
                val x = (index % 100) * 160f
                val y = (index / 100) * 140f
                Shape(id = "node-$index", bounds = Bounds(x, y, x + 100, y + 80), kind = Tool.RECTANGLE, style = InkStyle())
            }
            val edges = List(count - 1) { index ->
                Connector(
                    id = "edge-$index",
                    start = ConnectorEnd.Bound(nodes[index].id, ConnectionPort.RIGHT),
                    end = ConnectorEnd.Bound(nodes[index + 1].id, ConnectionPort.LEFT)
                )
            }
            val page = Page(elements = nodes + edges)
            val board = Board(name = "Connection profile $count", pages = listOf(page))
            val timings = JSONObject()
            val allocations = JSONObject()
            fun allocatedBytes(): Long = checkNotNull(Debug.getRuntimeStat("art.gc.bytes-allocated").toLongOrNull())
            fun measure(name: String, iterations: Int = 5, block: () -> Unit) {
                val allocated = ArrayList<Long>(iterations)
                val samples = List(iterations) {
                    val before = allocatedBytes()
                    val started = SystemClock.elapsedRealtimeNanos()
                    block()
                    val elapsed = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
                    allocated.add((allocatedBytes() - before).also { assertTrue("Monotonic ART allocation counter", it >= 0L) })
                    elapsed
                }.sorted()
                val sortedAllocations = allocated.sorted()
                allocations.put(
                    name,
                    JSONObject().put("samplesBytes", JSONArray(allocated))
                        .put("medianBytes", sortedAllocations[sortedAllocations.size / 2]).put("maxBytes", sortedAllocations.last())
                )
                timings.put(
                    name,
                    JSONObject().put(
                        "samplesMs",
                        JSONArray(samples)
                    ).put("medianMs", samples[samples.size / 2]).put("maxMs", samples.last())
                )
            }
            measure("coldResolve", 1) {
                assertNotNull(page.contentBounds)
                assertEquals(nodes.first().bounds.right, (page.geometry.resolve(edges.first()) as Line).start.x, 0f)
            }
            val firstLine = page.geometry.resolve(edges.first()) as Line
            val moved = nodes.first().copy(bounds = Bounds(0f, 30f, 100f, 110f))
            measure("metadata100") {
                repeat(100) { index ->
                    val metadata = page.copy(name = "Viewport $index", viewport = Viewport())
                    assertSame(firstLine, metadata.geometry.resolve(edges.first()))
                }
            }
            measure("targetPreview") {
                val preview = page.geometry.preview(listOf(moved))
                assertEquals(firstLine.start + Point(0f, 30f), (preview.resolve(edges.first()) as Line).start)
                assertEquals(firstLine.start, (page.geometry.resolve(edges.first()) as Line).start)
            }
            val session = DocumentSession(board)
            measure("moveUndoRedo") {
                session.replace(moved)
                assertEquals(firstLine.start + Point(0f, 30f), (session.page.geometry.resolve(edges.first()) as Line).start)
                session.undo()
                assertEquals(firstLine.start, (session.page.geometry.resolve(edges.first()) as Line).start)
                session.redo()
                session.undo()
            }
            measure("copyGraph", 3) {
                val copy = ConnectionCopies.duplicate(page, edges, page.elements.map { it.id }.toSet(), Point(24f, 24f))
                assertEquals(page.elements.size, copy.objects.size)
                val copiedPage = Page(elements = copy.objects)
                val line = copiedPage.geometry.resolve(copy.connections.first()) as Line
                assertEquals(firstLine.start + Point(24f, 24f), line.start)
            }
            measure("codecRoundTrip", 3) {
                val codec = DocumentCodec()
                val decoded = codec.decode(codec.encode(board)).activePage
                assertEquals(page.elements, decoded.elements)
                assertEquals(firstLine, decoded.geometry.resolve(edges.first()))
            }
            reports.put(
                JSONObject().put("nodes", count).put("connections", edges.size).put("timings", timings).put("allocations", allocations)
            )
        }
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        if (output != null) {
            val report = JSONObject()
                .put(
                    "scope",
                    "debug emulator algorithm measurements on instrumentation worker; not rendering, UI frame time, physical hardware or release performance"
                )
                .put("api", Build.VERSION.SDK_INT).put("model", Build.MODEL).put("maxHeapBytes", Runtime.getRuntime().maxMemory())
                .put(
                    "allocationScope",
                    "ART process-wide cumulative bytes allocated during each operation; includes counter reads and concurrent work; not retained cache size"
                )
                .put("graphs", reports)
            val file = File(output, "connection-scale-profile.json")
            file.parentFile?.mkdirs()
            file.writeText(report.toString(2))
        }
    }
}
