package dev.whiteboard.core.usecase

import dev.whiteboard.core.model.*
import dev.whiteboard.core.repository.DocumentStorage
import java.net.URI

/** Explicit user-selected document transfers. Remote URLs and arbitrary filesystem paths are not inputs. */
class DocumentTransfers(private val storage: DocumentStorage) {
    private fun selectedUri(value: String): String {
        val uri = runCatching { URI(value) }.getOrNull()
        if (value.length > 8192 || uri?.scheme != "content" || uri.rawAuthority.isNullOrEmpty()) {
            throw DocumentFailure(DocumentError.PERMISSION_DENIED)
        }
        return value
    }
    fun protectSession(id: String, assets: Set<String>) = storage.protectSession(id, assets)
    fun releaseSession(id: String) = storage.releaseSession(id)
    suspend fun pruneUnused() = storage.pruneUnused()
    suspend fun importImage(source: String) = storage.importImage(selectedUri(source))
    suspend fun importPdf(source: String) = storage.importPdf(selectedUri(source))
    suspend fun deleteAssets(ids: Set<String>) = storage.deleteAssets(ids)
    suspend fun exportPng(destination: String, page: Page) = storage.exportPng(selectedUri(destination), page)
    suspend fun exportJpeg(destination: String, page: Page) = storage.exportJpeg(selectedUri(destination), page)
    suspend fun exportPdf(destination: String, board: Board) = storage.exportPdf(selectedUri(destination), board)
    suspend fun sharePng(page: Page) = storage.sharePng(page)
    suspend fun backup(destination: String) = storage.backup(selectedUri(destination))
    suspend fun restore(source: String) = storage.restore(selectedUri(source))
    suspend fun thumbnail(board: Board) = storage.thumbnail(board)
}
