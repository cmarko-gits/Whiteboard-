package dev.whiteboard.format

import dev.whiteboard.core.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class BoardArchiveTest {
    @Test fun `compressed input cannot exceed one entry budget`() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("assets/${newId()}.png"))
            val chunk = ByteArray(8192)
            repeat(BoardArchive.MAX_ENTRY_BYTES / chunk.size) { zip.write(chunk) }
            zip.write(0)
        }
        assertTrue(output.size() < 100_000)
        val error = assertThrows(IllegalArgumentException::class.java) {
            BoardArchive().readContents(ByteArrayInputStream(output.toByteArray()))
        }
        assertEquals("Archive entry is too large", error.message)
    }

    @Test fun `total decompression budget applies while reading the last entry`() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("whiteboard-version.txt"))
            zip.write('2'.code)
            val chunk = ByteArray(8192)
            repeat(4) {
                zip.putNextEntry(ZipEntry("assets/${newId()}.png"))
                repeat(BoardArchive.MAX_ENTRY_BYTES / chunk.size) { zip.write(chunk) }
            }
        }
        assertTrue(output.size() < 100_000)
        val error = assertThrows(IllegalArgumentException::class.java) {
            BoardArchive().readContents(ByteArrayInputStream(output.toByteArray()))
        }
        assertEquals("Archive is too large", error.message)
    }

    @Test fun `overlong numeric board paths are rejected before document parsing`() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use {
            it.putNextEntry(ZipEntry("boards/${"1".repeat(60_000)}.json"))
            it.write("{}".toByteArray())
        }
        val error = assertThrows(IllegalArgumentException::class.java) {
            BoardArchive().readContents(ByteArrayInputStream(output.toByteArray()))
        }
        assertEquals("Unsupported archive entry", error.message)
    }

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
