package dev.whiteboard.format

import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class DocumentCodecTest {
    private val codec = DocumentCodec()

    @Test(expected = IllegalArgumentException::class)
    fun `unsafe point geometry is rejected before a document reaches the canvas`() {
        val source = checkNotNull(javaClass.getResource("/documents/version-1.json")).readText()
        val root = org.json.JSONObject(source)
        root.getJSONArray("pages").getJSONObject(0).getJSONArray("objects").getJSONObject(0)
            .getJSONArray("points").getJSONArray(0).put(0, 1e38)
        codec.decode(root.toString())
    }

    @Test fun `historical fixtures upgrade without losing content or geometry`() {
        (1..2).forEach { version ->
            val source = checkNotNull(javaClass.getResource("/documents/version-$version.json")).readText()
            val legacy = codec.decode(source)
            assertEquals(2, legacy.activePage.elements.size)
            assertEquals(legacy, codec.decode(codec.encode(legacy)))
            if (version == 1) {
                assertEquals("Ћирилица / Đaci", legacy.activePage.elements.filterIsInstance<TextElement>().single().text)
                assertEquals(Viewport(Point(-100f, 200f), 1.5f), legacy.activePage.viewport)
            } else {
                assertEquals(90, legacy.activePage.elements.filterIsInstance<MediaElement>().single().rotation)
            }
        }
    }

    @Test fun `all object types round trip exactly`() {
        val page = Page(
            elements = listOf(
                Stroke(points = listOf(Point(-20f, 3f), Point(100f, 40f)), style = InkStyle(opacity = 0.35f), tool = Tool.HIGHLIGHTER),
                Shape(bounds = Bounds(10f, 20f, 80f, 90f), kind = Tool.DIAMOND, style = InkStyle()),
                Line(start = Point(100f, 100f), end = Point(-50f, -80f), style = InkStyle(), arrow = true),
                TextElement(
                    bounds = Bounds(0f, 0f, 100f, 100f),
                    text = "Ćirilica / Ћирилица\nHello",
                    bold = true,
                    alignment = TextAlignment.CENTER
                ),
                Note(bounds = Bounds(200f, 200f, 400f, 400f), text = "Plan")
            ),
            viewport = Viewport(Point(-100f, 400f), 1.5f)
        )
        val board = Board(name = "Arhitektura", pages = listOf(page), favorite = true)
        assertEquals(board, codec.decode(codec.encode(board)))
    }

    @Test(expected = UnsupportedDocumentVersion::class)
    fun `future formats are rejected instead of silently rewritten`() {
        codec.decode("{\"version\":999}")
    }

    @Test fun `multiple pages and nullable metadata survive`() {
        val first = Page()
        val second = Page(name = "Second")
        val board = Board(name = "Test", pages = listOf(first, second), activePageId = second.id, folderId = "folder", trashedAt = 1234L)
        assertEquals(board, codec.decode(codec.encode(board)))
    }

    @Test fun `media crop and rotation survive serialization`() {
        val page = Page(
            elements = listOf(
                MediaElement(
                    bounds = Bounds(0f, 0f, 300f, 400f),
                    assetId = "${newId()}.pdf",
                    pdfPage = 2,
                    rotation = 90,
                    crop = Bounds(0.1f, 0.2f, 0.9f, 0.8f)
                )
            )
        )
        val board = Board(name = "PDF", pages = listOf(page))
        assertEquals(board, codec.decode(codec.encode(board)))
    }

    @Test fun `version one documents remain readable`() {
        val board = Board(name = "Legacy")
        (1..2).forEach { version ->
            val legacy = org.json.JSONObject(codec.encode(board)).put("version", version).toString()
            assertEquals(board, codec.decode(legacy))
        }
    }

    @Test fun `group children keep text media and identity through backup encoding`() {
        val group = ElementGroup(
            children = listOf(
                Note(bounds = Bounds(10f, 20f, 210f, 220f), text = "Grouped Ћирилица"),
                MediaElement(bounds = Bounds(300f, 20f, 500f, 220f), assetId = "${newId()}.png")
            )
        )
        val board = Board(name = "Grouped", pages = listOf(Page(elements = listOf(group))))
        assertEquals(board, codec.decode(codec.encode(board)))
        assertTrue(board.searchableText().contains("Ћирилица"))
        assertEquals(group.id, board.firstTextMatch("ћирилица")?.element?.id)
        assertEquals(1, board.referencedAssets().size)
    }
}
