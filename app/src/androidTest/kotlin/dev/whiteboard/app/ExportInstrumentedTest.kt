package dev.whiteboard.app

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.canvas.PageExporter
import dev.whiteboard.core.model.*
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportInstrumentedTest {
    @Test fun noteTextRemainsVisibleAcrossDarkGrayLightAndTransparentFills() {
        // Sample the actual exporter bitmap, excluding page margins and rounded note edges.
        val cases = listOf(
            Triple(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
            Triple(0xFF777777.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
            Triple(0xFFF4E6A3.toInt(), 0xFFFFFFFF.toInt(), 0xFF30352D.toInt()),
            Triple(0x00000000, 0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
            Triple(0x00000000, 0xFFFFFFFF.toInt(), 0xFF30352D.toInt())
        )
        cases.forEach { (fill, background, foreground) ->
            val note = Note(bounds = Bounds(0f, 0f, 240f, 128f), text = "MMMM", fontSize = 40f, color = fill)
            val page = Page(background = Background.PLAIN, backgroundColor = background, elements = listOf(note))
            val bitmap = PageExporter().bitmap(page)
            try {
                var glyphPixels = 0
                for (y in 96 until 220) {
                    for (x in 96 until 480) {
                        if (bitmap.getPixel(x, y) == foreground) glyphPixels++
                    }
                }
                assertTrue("Missing readable glyphs for fill ${fill.toUInt().toString(16)}", glyphPixels > 40)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun exportRendersObjectsAtNegativeCoordinates() {
        val page = Page(
            background = Background.PLAIN,
            backgroundColor = 0xFFFFFFFF.toInt(),
            elements = listOf(
                Shape(bounds = Bounds(-200f, -200f, -100f, -100f), kind = Tool.RECTANGLE, style = InkStyle(), fill = 0xFFFF0000.toInt())
            )
        )
        val output = ByteArrayOutputStream()
        PageExporter().png(page, output)
        val bytes = output.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull(bitmap)
        assertEquals(0xFFFF0000.toInt(), bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
        assertEquals(0xFFFFFFFF.toInt(), bitmap.getPixel(0, 0))
        bitmap.recycle()
    }
}
