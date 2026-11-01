package dev.whiteboard.format

import dev.whiteboard.core.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class BoardArchiveTest {
    @Test fun `backup retains editable document structure`() {
        val boards = listOf(Board(name = "One"), Board(name = "Two"))
        val output = ByteArrayOutputStream()
        BoardArchive().write(boards, output)
        assertEquals(boards, BoardArchive().read(ByteArrayInputStream(output.toByteArray())))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsafe archive entries are rejected`() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use {
            it.putNextEntry(ZipEntry("../../outside.json"))
            it.write("{}".toByteArray())
        }
        BoardArchive().read(ByteArrayInputStream(output.toByteArray()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `backup version is required`() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).close()
        BoardArchive().read(ByteArrayInputStream(output.toByteArray()))
    }

    @Test fun `shared attachments are stored once and preserved`() {
        val id = "${newId()}.png"
        val page = Page(elements = listOf(MediaElement(bounds = Bounds(0f, 0f, 10f, 10f), assetId = id)))
        val board = Board(name = "Image", pages = listOf(page))
        val output = ByteArrayOutputStream()
        val data = byteArrayOf(1, 2, 3)
        BoardArchive().write(listOf(board), output) { ByteArrayInputStream(data) }
        val restored = BoardArchive().readContents(ByteArrayInputStream(output.toByteArray()))
        assertEquals(listOf(board), restored.boards)
        assertArrayEquals(data, restored.assets.getValue(id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing attachment prevents incomplete backup`() {
        val page = Page(elements = listOf(MediaElement(bounds = Bounds(0f, 0f, 10f, 10f), assetId = "${newId()}.png")))
        BoardArchive().write(listOf(Board(name = "Missing", pages = listOf(page))), ByteArrayOutputStream())
    }
}
