package dev.whiteboard.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.app.workspace.DocumentFiles
import dev.whiteboard.app.workspace.LocalMediaStore
import dev.whiteboard.canvas.PageExporter
import dev.whiteboard.core.editor.DocumentSession
import dev.whiteboard.core.model.*
import dev.whiteboard.data.LocalBoardRepository
import dev.whiteboard.data.WhiteboardDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, WhiteboardDatabase::class.java).build()
    private val repository = LocalBoardRepository(database)
    private val media = LocalMediaStore(context)
    private val files = DocumentFiles(context, repository, media)
    private val temporary = mutableListOf<File>()
    private val imported = mutableSetOf<String>()
    private fun temp(extension: String): File = File.createTempFile("media-test-", extension, context.cacheDir).also { temporary.add(it) }

    @After fun cleanup() = runBlocking {
        repository.savedBoards().flatMap { it.referencedAssets() }.forEach { imported.add(it) }
        imported.forEach { media.file(it).delete() }
        temporary.forEach { it.delete() }
        database.close()
    }

    @Test fun previewReadsAreBoundedAndRestrictedToThePrivateThumbnailDirectory() = runBlocking {
        val directory = File(context.filesDir, "thumbnails").apply { mkdirs() }
        val preview = File.createTempFile("preview-test-", ".png", directory).also { temporary.add(it) }
        val bytes = byteArrayOf(1, 2, 3)
        preview.writeBytes(bytes)
        assertArrayEquals(bytes, files.readPreview(preview.absolutePath))
        val privateFile = temp(".png").apply { writeBytes(bytes) }
        assertNull(files.readPreview(privateFile.absolutePath))
        assertNull(files.readPreview(File(directory, "../${privateFile.name}").path))
        preview.outputStream().use { output ->
            val block = ByteArray(8192)
            repeat(257) { output.write(block) }
        }
        assertNull(files.readPreview(preview.absolutePath))
    }

    @Test fun imageImportAndEditableBackupRestorePreservePixels() = runBlocking {
        val input = temp(".png")
        val fixture = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        input.outputStream().use { fixture.compress(Bitmap.CompressFormat.PNG, 100, it) }
        fixture.recycle()
        val element = media.importImage(Uri.fromFile(input)).also { imported.add(it.assetId) }
        val group = ElementGroup(children = listOf(element, TextElement(bounds = Bounds(0f, 0f, 1f, 1f), text = "")))
        val board = Board(name = "Image backup", pages = listOf(Page(elements = listOf(group))))
        repository.save(board)
        val backup = temp(".zip")
        assertEquals(1, files.backup(Uri.fromFile(backup)))
        assertEquals(1, files.restore(Uri.fromFile(backup)))
        val restored = repository.savedBoards().first { it.id != board.id }
        assertTrue(restored.activePage.elements.single() is ElementGroup)
        val restoredImage = restored.activePage.elements.flatMap { it.leaves() }.filterIsInstance<MediaElement>().single()
        assertNotEquals(element.assetId, restoredImage.assetId)
        assertTrue(media.file(element.assetId).isFile)
        val bitmap = PageExporter(media).bitmap(restored.activePage)
        try {
            assertEquals(Color.RED, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun pdfImportAndAnnotatedExportPreservePageCount() = runBlocking {
        val input = temp(".pdf")
        val fixture = PdfDocument()
        try {
            repeat(2) { index ->
                val page = fixture.startPage(PdfDocument.PageInfo.Builder(300, 400, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawRect(30f, 30f, 270f, 370f, Paint().apply { color = Color.RED })
                fixture.finishPage(page)
            }
            input.outputStream().use { fixture.writeTo(it) }
        } finally {
            fixture.close()
        }
        val pages = media.importPdf(Uri.fromFile(input))
        imported.addAll(pages.flatMap { it.elements }.filterIsInstance<MediaElement>().map { it.assetId })
        assertEquals(2, pages.size)
        val annotation = Stroke(points = listOf(Point(40f, 60f), Point(200f, 250f)), style = InkStyle(color = Color.BLUE, width = 12f))
        val board = Board(
            name = "PDF",
            pages = pages.mapIndexed { index, page ->
                if (index ==
                    0
                ) {
                    page.copy(elements = page.elements + annotation)
                } else {
                    page
                }
            }
        )
        val output = temp(".pdf")
        files.exportPdf(Uri.fromFile(output), board)
        ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { exported ->
                assertEquals(2, exported.pageCount)
                exported.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    try {
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        var bluePixels = 0
                        for (y in 0 until bitmap.height step 2) {
                            for (x in 0 until bitmap.width step 2) {
                                val pixel = bitmap.getPixel(x, y)
                                if (Color.blue(pixel) > 180 && Color.red(pixel) < 80) bluePixels++
                            }
                        }
                        assertTrue("Expected the blue annotation in the exported PDF", bluePixels > 20)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    @Test fun rotatedMediaKeepsItsFrameAndPixelsThroughBackupRestoreAndPdfExport() = runBlocking {
        val input = temp(".png")
        val fixture = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        try {
            fixture.eraseColor(Color.RED)
            input.outputStream().use { assertTrue(fixture.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            fixture.recycle()
        }
        val image = media.importImage(Uri.fromFile(input)).also { imported.add(it.assetId) }
        val session = DocumentSession(Board(name = "Rotated backup", pages = listOf(Page(elements = listOf(image)))))
        session.rotate(setOf(image.id), 37.0)
        val original = session.board
        val placed = original.activePage.elements.single() as AffineElement
        repository.save(original)
        val backup = temp(".zip")
        assertEquals(1, files.backup(Uri.fromFile(backup)))
        assertEquals(1, files.restore(Uri.fromFile(backup)))
        val restored = repository.savedBoards().single { it.id != original.id }
        val restoredImage = restored.activePage.elements.single() as AffineElement
        assertEquals(placed.placement, restoredImage.placement)
        assertEquals(placed.source.bounds, restoredImage.source.bounds)
        assertNotEquals(image.assetId, (restoredImage.source as MediaElement).assetId)
        assertTrue(media.file((restoredImage.source as MediaElement).assetId).isFile)
        val output = temp(".pdf")
        files.exportPdf(Uri.fromFile(output), restored)
        ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(1, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    try {
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        assertEquals(Color.RED, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }
}
