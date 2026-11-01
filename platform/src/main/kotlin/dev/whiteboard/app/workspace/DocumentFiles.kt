package dev.whiteboard.app.workspace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.whiteboard.canvas.BoardRenderer
import dev.whiteboard.canvas.PageExporter
import dev.whiteboard.core.concurrency.AppDispatchers
import dev.whiteboard.core.concurrency.DefaultAppDispatchers
import dev.whiteboard.core.model.*
import dev.whiteboard.core.repository.BoardRepository
import dev.whiteboard.core.repository.DocumentStorage
import dev.whiteboard.format.BoardArchive
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Platform file handling stays outside the editor's state and document mutation logic. */
@Singleton
class DocumentFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BoardRepository,
    val media: LocalMediaStore,
    private val dispatchers: AppDispatchers = DefaultAppDispatchers
) : DocumentStorage {
    private val exporter = PageExporter(media)
    private val sessions = ConcurrentHashMap<String, Set<String>>()
    override fun protectSession(id: String, assets: Set<String>) {
        sessions[id] = assets
    }
    override fun releaseSession(id: String) {
        sessions.remove(id)
    }
    override suspend fun pruneUnused() = withContext(dispatchers.io) {
        val retained = repository.retainedAssetIds() + sessions.values.flatten()
        media.prune(retained)
    }
    override suspend fun deletePreview(id: String) = withContext(dispatchers.io) {
        if (id.matches(Regex("[a-f0-9-]{36}"))) {
            File(context.filesDir, "thumbnails").listFiles()?.filter { it.name.startsWith("$id-") }?.forEach { it.delete() }
        }
    }
    override suspend fun exportPng(destination: String, page: Page) = exportPng(Uri.parse(destination), page)
    override suspend fun exportJpeg(destination: String, page: Page) = exportJpeg(Uri.parse(destination), page)
    override suspend fun exportPdf(destination: String, board: Board) = exportPdf(Uri.parse(destination), board)
    override suspend fun backup(destination: String): Int = backup(Uri.parse(destination))
    override suspend fun restore(source: String): Int = restore(Uri.parse(source))
    override suspend fun importImage(source: String): MediaElement = media.importImage(Uri.parse(source))
    override suspend fun importPdf(source: String): List<Page> = media.importPdf(Uri.parse(source))
    override suspend fun deleteAssets(ids: Set<String>) = withContext(dispatchers.io) {
        ids.forEach { media.file(it).delete() }
    }

    suspend fun exportPng(uri: Uri, page: Page) = withContext(dispatchers.io) {
        context.contentResolver.openOutputStream(uri)?.use { exporter.png(page, it) }
            ?: error("Cannot open export destination")
    }

    override suspend fun sharePng(page: Page): File = withContext(dispatchers.io) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        File.createTempFile("whiteboard-", ".png", dir).also { file -> file.outputStream().use { exporter.png(page, it) } }
    }

    suspend fun backup(uri: Uri): Int = withContext(dispatchers.io) {
        val saved = repository.savedBoards()
        context.contentResolver.openOutputStream(uri)?.use { output ->
            BoardArchive().write(saved, output) { id -> media.file(id).takeIf { it.isFile }?.inputStream() }
        }
            ?: error("Cannot open backup destination")
        saved.size
    }

    suspend fun restore(uri: Uri): Int = withContext(dispatchers.io) {
        val contents = context.contentResolver.openInputStream(uri)?.use { BoardArchive().readContents(it) }
            ?: error("Cannot read backup")
        val mapping = contents.assets.keys.associateWith { "${newId()}.${it.substringAfterLast('.')}" }
        var committed = false
        try {
            mapping.forEach { (old, renamed) ->
                media.file(renamed).outputStream().use { it.write(contents.assets.getValue(old)) }
                media.validate(renamed)
            }
            val boards = contents.boards.map { board ->
                board.copy(
                    pages = board.pages.map { page ->
                        page.copy(
                            elements = page.elements.map { element ->
                                element.mapLeaves { if (it is MediaElement) it.copy(assetId = mapping.getValue(it.assetId)) else it }
                            }
                        )
                    }
                )
            }
            withContext(NonCancellable) {
                repository.importBoards(boards)
                committed = true
            }
            boards.size
        } catch (error: Exception) {
            if (!committed) mapping.values.forEach { media.file(it).delete() }
            throw error
        }
    }

    suspend fun exportJpeg(uri: Uri, page: Page) = withContext(dispatchers.io) {
        val bitmap = exporter.bitmap(page)
        try {
            context.contentResolver.openOutputStream(uri)?.use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
                ?: error("Cannot open export destination")
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun exportPdf(uri: Uri, board: Board) = withContext(dispatchers.io) {
        val document = PdfDocument()
        try {
            board.pages.forEachIndexed { index, source ->
                val bounds = (source.contentBounds ?: Bounds(0f, 0f, 595f, 842f)).expand(24f)
                val landscape = bounds.width > bounds.height
                val width = if (landscape) 842 else 595
                val height = if (landscape) 595 else 842
                val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                val scale = minOf(width / bounds.width, height / bounds.height)
                try {
                    page.canvas.save()
                    page.canvas.translate((width - bounds.width * scale) / 2, (height - bounds.height * scale) / 2)
                    page.canvas.scale(scale, scale)
                    page.canvas.translate(-bounds.left, -bounds.top)
                    BoardRenderer(media, strictAssets = true).drawPage(page.canvas, source, bounds, scale)
                    page.canvas.restore()
                } finally {
                    document.finishPage(page)
                }
            }
            context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) } ?: error("Cannot open PDF destination")
        } finally {
            document.close()
        }
    }

    override suspend fun readPreview(reference: String): ByteArray? = withContext(dispatchers.io) {
        val root = File(context.filesDir, "thumbnails").canonicalFile
        val file = File(reference).canonicalFile
        if (file.parentFile != root || !file.isFile || file.extension != "png" || file.length() !in 1..(2L * 1024 * 1024)) {
            return@withContext null
        }
        file.inputStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > 2 * 1024 * 1024) return@withContext null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    override suspend fun thumbnail(board: Board): Unit = withContext(dispatchers.io) {
        // A preview failure must not turn a successful document save into a failed save.
        try {
            val dir = File(context.filesDir, "thumbnails").apply { mkdirs() }
            val file = File(dir, "${board.id}-${newId()}.png")
            val bitmap = exporter.bitmap(board.activePage, 512)
            try {
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
            repository.saveThumbnail(board.id, file.absolutePath)
            dir.listFiles()?.filter { it.name.startsWith("${board.id}-") && it != file }?.forEach { it.delete() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The next explicit Save regenerates the disposable preview.
        }
    }
}
