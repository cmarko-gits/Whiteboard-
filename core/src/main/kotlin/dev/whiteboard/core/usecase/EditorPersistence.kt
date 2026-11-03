package dev.whiteboard.core.usecase

import dev.whiteboard.core.editor.DocumentSession
import dev.whiteboard.core.model.*
import dev.whiteboard.core.repository.BoardRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class OpenedBoard(val saved: Board, val recovery: Board?)
data class SavedCapture(val board: Board, val revision: Long)

/** Serializes Save/recovery/discard while preserving edits made during a suspended write. */
class EditorPersistence(private val repository: BoardRepository) {
    private val mutex = Mutex()

    suspend fun open(id: String): OpenedBoard {
        val saved = repository.load(id)
        val recovery = repository.draft(id)?.copy(
            name = saved.name,
            favorite = saved.favorite,
            folderId = saved.folderId,
            trashedAt = saved.trashedAt
        )?.takeIf { it != saved }
        return OpenedBoard(saved, recovery)
    }

    suspend fun create(
        name: String,
        folderId: String?,
        template: BoardTemplate,
        content: TemplateContent = TemplateContent.ENGLISH
    ): Board = TemplateFactory.create(name.trim().take(120), template, content).copy(folderId = folderId).also { repository.save(it) }

    suspend fun createPrivateCopy(source: Board, name: String): Board {
        val now = System.currentTimeMillis()
        val result = source.copy(
            id = newId(),
            name = name.take(120),
            createdAt = now,
            updatedAt = now,
            favorite = false,
            folderId = null,
            trashedAt = null
        )
        repository.save(result)
        return result
    }

    suspend fun markOpened(id: String) = repository.markOpened(id)

    suspend fun save(session: DocumentSession): SavedCapture = mutex.withLock {
        val capture = SavedCapture(session.board, session.revision)
        repository.save(capture.board)
        session.markSaved(capture.revision)
        flushLocked(session)
        capture
    }

    suspend fun flush(session: DocumentSession) = mutex.withLock { flushLocked(session) }

    suspend fun discardDraft(id: String) = mutex.withLock { repository.discardDraft(id) }

    private suspend fun flushLocked(session: DocumentSession) {
        if (session.dirty) repository.saveDraft(session.board) else repository.discardDraft(session.board.id)
    }
}
