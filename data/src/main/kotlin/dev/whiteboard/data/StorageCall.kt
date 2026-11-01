package dev.whiteboard.data

import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteFullException
import dev.whiteboard.core.model.DocumentError
import dev.whiteboard.core.model.DocumentFailure
import java.io.IOException
import kotlinx.coroutines.*

internal suspend fun <T> storageCall(dispatcher: CoroutineDispatcher, action: suspend () -> T): T = withContext(dispatcher) {
    try {
        action()
    } catch (
        cancelled: CancellationException
    ) {
        throw cancelled
    } catch (
        error: SQLiteFullException
    ) {
        throw DocumentFailure(DocumentError.STORAGE_FULL, error)
    } catch (
        error: SQLiteDatabaseCorruptException
    ) {
        throw DocumentFailure(DocumentError.INVALID_DOCUMENT, error)
    } catch (
        error: SQLiteCantOpenDatabaseException
    ) {
        throw DocumentFailure(DocumentError.STORAGE_UNAVAILABLE, error)
    } catch (
        error: SecurityException
    ) {
        throw DocumentFailure(DocumentError.PERMISSION_DENIED, error)
    } catch (
        error: IOException
    ) {
        throw DocumentFailure(DocumentError.STORAGE_UNAVAILABLE, error)
    }
}
