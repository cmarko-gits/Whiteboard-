package dev.whiteboard.server.boards

import dev.whiteboard.core.model.*
import dev.whiteboard.format.DocumentCodec
import dev.whiteboard.server.ServiceFailure
import dev.whiteboard.server.invalidRequest
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

internal object CloudDocuments {
    val codec = DocumentCodec()
    fun shared(source: Board, id: String): Board = source.copy(
        id = id,
        favorite = false,
        folderId = null,
        trashedAt = null,
        activePageId = source.pages.first().id,
        pages = source.pages.map { it.copy(viewport = Viewport()) }
    )
    fun payload(board: Board): String {
        board.pages.forEach { page ->
            require(page.id.length in 1..128)
            page.elements.forEach { element ->
                require(element.id.length in 1..128)
                element.leaves().forEach { require(it.id.length in 1..128) }
            }
        }
        if (board.pages.any { page -> page.elements.any { it.leaves().any { leaf -> leaf is MediaElement } } }) {
            throw ServiceFailure(400, "attachments_not_supported_yet")
        }
        return codec.encode(board).also { if (bytes(it) > MAX_DOCUMENT_BYTES) throw ServiceFailure(413, "document_too_large") }
    }
    fun bytes(source: String): Long = source.toByteArray(Charsets.UTF_8).size.toLong()
    fun fingerprint(kind: String, revision: Long, source: String): String {
        // Preserve the original snapshot receipt hash across the schema upgrade.
        val value = if (kind == "snapshot") "$revision\n$source" else "$kind\n$revision\n$source"
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))
    }
    fun operationId(value: String) {
        if (!runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) invalidRequest()
    }
    const val MAX_DOCUMENT_BYTES = 8L * 1024 * 1024
}
