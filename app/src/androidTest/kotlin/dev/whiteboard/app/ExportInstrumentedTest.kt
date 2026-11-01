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
