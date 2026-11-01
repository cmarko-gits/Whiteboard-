package dev.whiteboard.core.repository

import dev.whiteboard.core.model.*
import java.io.File

/** Sources and destinations are opaque URI strings. Only platform adapters interpret them. */
interface DocumentStorage {
    fun protectSession(id: String, assets: Set<String>)
    fun releaseSession(id: String)
    suspend fun pruneUnused()
    suspend fun deletePreview(id: String)
    suspend fun readPreview(reference: String): ByteArray? = null
    suspend fun importImage(source: String): MediaElement
    suspend fun importPdf(source: String): List<Page>
    suspend fun deleteAssets(ids: Set<String>)
    suspend fun exportPng(destination: String, page: Page)
    suspend fun exportJpeg(destination: String, page: Page)
    suspend fun exportPdf(destination: String, board: Board)
    suspend fun sharePng(page: Page): File
    suspend fun backup(destination: String): Int
    suspend fun restore(source: String): Int
    suspend fun thumbnail(board: Board)
}
