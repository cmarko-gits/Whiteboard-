package dev.whiteboard.app

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.canvas.PageExporter
import dev.whiteboard.core.model.*
import dev.whiteboard.data.LocalBoardRepository
import dev.whiteboard.data.WhiteboardDatabase
import dev.whiteboard.format.DocumentCodec
import java.io.ByteArrayOutputStream
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** A reproducible persistence/export workload, not a GPU frame-rate or real-device benchmark. */
@RunWith(AndroidJUnit4::class)
class LargeDocumentTest {
    @Test fun largeEditableDocumentSurvivesStorageAndExport(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.databaseBuilder(context, WhiteboardDatabase::class.java, "benchmark-${newId()}.db").build()
        val repository = LocalBoardRepository(database)
        val elements = List(1000) { index ->
            val x = (index % 40) * 70f
            val y = (index / 40) * 70f
            Stroke(points = List(100) { sample -> Point(x + sample * 0.5f, y + sin(sample * 0.1f) * 20f) }, style = InkStyle())
        } + List(200) { index ->
            Note(
                bounds = Bounds(index % 20 * 140f, index / 20 * 160f, index % 20 * 140f + 120f, index / 20 * 160f + 140f),
                text = "Note $index · Ћирилица"
            )
        }
        val board = Board(name = "Performance fixture", pages = listOf(Page(elements = elements)))
        try {
            var start = SystemClock.elapsedRealtime()
            repository.save(board)
            val saveMs = SystemClock.elapsedRealtime() - start
            start = SystemClock.elapsedRealtime()
            val loaded = repository.load(board.id)
            val loadMs = SystemClock.elapsedRealtime() - start
            assertEquals(board, loaded)
            start = SystemClock.elapsedRealtime()
            val output = ByteArrayOutputStream()
            PageExporter().png(loaded.activePage, output)
            val exportMs = SystemClock.elapsedRealtime() - start
            assertTrue(output.size() > 1000)
            val result = JSONObject().put("objects", elements.size).put("points", 100_000)
                .put("jsonBytes", DocumentCodec().encode(board).toByteArray().size)
                .put("saveMs", saveMs).put("loadMs", loadMs).put("pngMs", exportMs)
                .put("pssKbAfterExport", Debug.getPss()).put("nativeHeapBytes", Debug.getNativeHeapAllocatedSize())
            Log.i("WhiteboardBenchmark", result.toString())
            Unit
        } finally {
            val path = checkNotNull(database.openHelper.writableDatabase.path)
            database.close()
            context.deleteDatabase(java.io.File(path).name)
        }
    }
}
