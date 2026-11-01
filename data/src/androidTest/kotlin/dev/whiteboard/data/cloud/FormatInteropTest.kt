package dev.whiteboard.data.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.core.cloud.DeltaEngine
import dev.whiteboard.core.model.*
import dev.whiteboard.format.DocumentCodec
import dev.whiteboard.format.DocumentDeltaCodec
import dev.whiteboard.format.ElementDigest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormatInteropTest {
    @Test fun portableCodecUsesAndroidJsonApiAndMatchesJvmDigest() {
        val bounds = Bounds(1.25f, 2.5f, 30.75f, 40.5f)
        val elements = listOf(
            Stroke(points = listOf(Point(0.1f, 0.2f), Point(20.3f, 30.4f)), style = InkStyle(width = 2.4f, opacity = 0.7f)),
            Shape(bounds = bounds, kind = Tool.ELLIPSE, style = InkStyle(width = 1.3f)),
            Line(start = Point(0.1f, 1.2f), end = Point(30f, 40f), style = InkStyle(), arrow = true),
            TextElement(bounds = bounds, text = "Tekst Ћирилично 🖊", fontSize = 22.5f),
            MediaElement(bounds = bounds, assetId = "00000000-0000-0000-0000-000000000000.png"),
            ElementGroup(children = listOf(Note(bounds = bounds, text = "One", fontSize = 21.5f), Note(bounds = bounds, text = "Two")))
        )
        val page = Page(elements = elements, viewport = Viewport(Point(0.3f, -0.7f), 1.7f))
        val board = Board(name = "Portable format", pages = listOf(page))
        val codec = DocumentCodec()
        assertEquals(board, codec.decode(codec.encode(board)))
        elements.forEach { assertEquals(it, codec.decodeElementJson(codec.encodeElementJson(it))) }
        val fixture = Note(id = "digest-fixture", bounds = Bounds(1f, 2f, 30f, 40f), text = "Ž Ћ 🖊", color = 7, fontSize = 22f)
        assertEquals("6d0685e8d3692d3502e0c944f1d1a3cb28ee4306e70b30ed998e3473d4022ea2", ElementDigest().of(fixture))
        val engine = DeltaEngine(ElementDigest())
        val delta = engine.between(board, board.copy(name = "Shared change"))
        val operations = DocumentDeltaCodec()
        assertEquals(delta, operations.decode(operations.encode(delta)))
    }
}
