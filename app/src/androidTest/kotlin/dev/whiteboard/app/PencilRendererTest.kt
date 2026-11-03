package dev.whiteboard.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.canvas.BoardRenderer
import dev.whiteboard.canvas.PageExporter
import dev.whiteboard.core.model.*
import dev.whiteboard.format.DocumentCodec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PencilRendererTest {
    private val points = listOf(Point(20f, 32f), Point(240f, 32f))
    private val style = InkStyle(color = Color.BLUE, width = 18f)

    private fun image() = Bitmap.createBitmap(260, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

    @Test fun pencilHasVisibleGrainWhileTheSameOpaquePenRemainsUniform() {
        val pencil = image()
        val pen = image()
        try {
            BoardRenderer().drawElement(Canvas(pencil), Stroke(points = points, style = style, tool = Tool.PENCIL))
            BoardRenderer().drawElement(Canvas(pen), Stroke(points = points, style = style))
            val grain = (40..220).map { pencil.getPixel(it, 32) }
            assertTrue(grain.toSet().size >= 8)
            assertTrue(grain.none { it == Color.WHITE })
            assertTrue(grain.any { it != Color.BLUE })
            assertTrue((40..220).all { pen.getPixel(it, 32) == Color.BLUE })
        } finally {
            pencil.recycle()
            pen.recycle()
        }
    }

    @Test fun liveAndCommittedPencilMatchAndDoNotLeakTextureOrDoubleColorAlphaIntoOtherTools() {
        val live = image()
        val committed = image()
        try {
            val translucent = InkStyle(color = 0x80FF0000.toInt(), width = 14f, opacity = 0.6f)
            val stroke = Stroke(points = points, style = translucent, tool = Tool.PENCIL)
            val path = Path().apply {
                moveTo(20f, 32f)
                lineTo(240f, 32f)
            }
            val renderer = BoardRenderer()
            renderer.drawLiveStroke(Canvas(live), path, points.first(), false, translucent, Tool.PENCIL)
            renderer.drawElement(Canvas(committed), stroke)
            assertTrue(live.sameAs(committed))
            // Color alpha 0.5 and opacity 0.6 should permit ~0.3 coverage at the darkest grains.
            assertTrue((40..220).minOf { Color.green(live.getPixel(it, 32)) } < 210)
            renderer.drawElement(Canvas(committed), Stroke(points = listOf(Point(20f, 72f), Point(240f, 72f)), style = style))
            assertEquals(Color.BLUE, committed.getPixel(100, 72))
        } finally {
            live.recycle()
            committed.recycle()
        }
    }

    @Test fun savedPencilExportsTheSameDeterministicTextureWithoutAnyNewDocumentFields() {
        val stroke = Stroke(points = points, style = style, tool = Tool.PENCIL)
        val board = Board(name = "Pencil", pages = listOf(Page(background = Background.PLAIN, elements = listOf(stroke))))
        val codec = DocumentCodec()
        val reopened = codec.decode(codec.encode(board))
        assertEquals(board, reopened)
        val before = PageExporter().bitmap(board.activePage, maxDimension = 512)
        val after = PageExporter().bitmap(reopened.activePage, maxDimension = 512)
        try {
            assertTrue(before.sameAs(after))
            val bounds = checkNotNull(board.activePage.contentBounds).expand(32f)
            val scale = before.width / bounds.width
            val y = ((32f - bounds.top) * scale).toInt()
            val colors = (40..220).map { x -> before.getPixel(((x - bounds.left) * scale).toInt(), y) }.toSet()
            assertTrue(colors.size >= 8)
        } finally {
            before.recycle()
            after.recycle()
        }
    }
}
