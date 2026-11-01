package dev.whiteboard.app.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.whiteboard.core.model.*
import dev.whiteboard.core.usecase.ManageLibrary
import dev.whiteboard.ui.R
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class LibraryUi(
    val boards: List<BoardSummary> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val searchQuery: String = "",
    val searchMatches: Set<String> = emptySet(),
    val loading: Boolean = true,
    val message: UiNotice? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(private val library: ManageLibrary) : ViewModel() {
    private val mutable = MutableStateFlow(LibraryUi())
    val state = mutable.asStateFlow()
    private val query = MutableStateFlow("")
    private val reload = MutableStateFlow(0L)
    init {
        viewModelScope.launch {
            library.prepare()
            reload.flatMapLatest {
                library.observe(query).catch { failure ->
                    if (failure is CancellationException) throw failure
                    mutable.update { it.copy(loading = false, message = UiNotice(R.string.storage_unavailable, retry = true)) }
                }
            }.collect { snapshot ->
                mutable.update {
                    it.copy(
                        boards = snapshot.boards,
                        folders = snapshot.folders,
                        searchQuery = snapshot.query,
                        searchMatches = snapshot.matches,
                        loading = false,
                        message = if (snapshot.error == null) it.message else UiNotice(R.string.storage_unavailable, retry = true)
                    )
                }
            }
        }
    }
    fun retry() {
        mutable.update { it.copy(loading = true, message = null) }
        reload.value++
    }
    fun search(value: String) {
        query.value = value.trim().take(512)
    }
    suspend fun preview(reference: String) = library.preview(reference)
    fun rename(id: String, name: String) = work { library.rename(id, name) }
    fun favorite(board: BoardSummary) = work { library.favorite(board) }
    fun trash(id: String) = work { library.trash(id) }
    fun restore(id: String) = work { library.restore(id) }
    fun permanentlyDelete(id: String) = work { library.permanentlyDelete(id) }
    fun createFolder(name: String, parent: String?) = work { library.createFolder(name, parent) }
    fun renameFolder(id: String, name: String) = work { library.renameFolder(id, name) }
    fun moveFolder(id: String, parent: String?) = work { library.moveFolder(id, parent) }
    fun deleteFolder(id: String) = work { library.deleteFolder(id) }
    fun assignBoard(board: String, folder: String?) = work { library.assignBoard(board, folder) }
    fun dismissMessage(id: String? = null) {
        mutable.update { if (id == null || it.message?.id == id) it.copy(message = null) else it }
    }
    private fun work(action: suspend () -> Unit) = viewModelScope.launch {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutable.update { it.copy(message = UiNotice(R.string.operation_failed)) }
        }
    }
}
