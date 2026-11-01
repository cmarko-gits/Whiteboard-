package dev.whiteboard.core.testing

import dev.whiteboard.core.model.*
import dev.whiteboard.core.repository.DocumentStorage
import java.io.File

class FakeDocumentStorage : DocumentStorage {
    val retained = linkedMapOf<String, Set<String>>()
    val thumbnails = mutableListOf<Board>()
    var export: suspend () -> Unit = {}
    override fun protectSession(id: String, assets: Set<String>) {
        retained[id] = assets
    }
    override fun releaseSession(id: String) {
        retained.remove(id)
    }
    override suspend fun pruneUnused() = Unit
    override suspend fun deletePreview(id: String) = Unit
    override suspend fun importImage(source: String): MediaElement = error("No image fixture configured")
    override suspend fun importPdf(source: String): List<Page> = error("No PDF fixture configured")
    override suspend fun deleteAssets(ids: Set<String>) = Unit
    override suspend fun exportPng(destination: String, page: Page) = export()
    override suspend fun exportJpeg(destination: String, page: Page) = export()
    override suspend fun exportPdf(destination: String, board: Board) = export()
    override suspend fun sharePng(page: Page): File {
        export()
        return File("fixture.png")
    }
    override suspend fun backup(destination: String): Int {
        export()
        return 0
    }
    override suspend fun restore(source: String): Int {
        export()
        return 0
    }
    override suspend fun thumbnail(board: Board) {
        thumbnails += board
    }
}
