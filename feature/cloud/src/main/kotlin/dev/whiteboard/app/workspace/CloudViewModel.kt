package dev.whiteboard.app.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.whiteboard.core.cloud.*
import dev.whiteboard.core.model.Board
import dev.whiteboard.core.repository.BoardRepository
import dev.whiteboard.ui.R
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class CloudUi(
    val accountId: String? = null,
    val remote: List<CloudBoardSummary> = emptyList(),
    val cached: List<CachedCloudBoard> = emptyList(),
    val pending: List<PendingPublication> = emptyList(),
    val next: String? = null,
    val busy: Boolean = false,
    val message: Int? = null,
    val error: Boolean = false,
    val preview: SyncedDocument? = null,
    val offlinePreview: Boolean = false,
    val background: SyncWorkStatus = SyncWorkStatus(SyncWorkState.IDLE)
)

@HiltViewModel
class CloudViewModel @Inject constructor(
    private val accounts: AccountGateway,
    private val boards: BoardGateway,
    private val local: BoardRepository,
    private val store: SyncStore,
    private val publications: PublicationStore,
    private val publisher: PublicationCoordinator,
    private val synchronization: SyncCoordinator,
    private val scheduler: SyncScheduler
) : ViewModel() {
    val account = accounts.account
    val available get() = accounts.available
    private val mutable = MutableStateFlow(CloudUi())
    val state = mutable.asStateFlow()
    private var actionJob: Job? = null

    init {
        viewModelScope.launch {
            account.collectLatest { identity ->
                actionJob?.cancelAndJoin()
                mutable.value = CloudUi(accountId = identity?.id)
                if (identity != null) {
                    combine(store.observeBoards(identity.id), publications.observePending(identity.id), scheduler.observe(identity.id)) {
                            cached,
                            pending,
                            background
                        ->
                        Triple(cached, pending, background)
                    }
                        .catch { update(identity.id) { it.copy(message = R.string.operation_failed, error = true) } }
                        .collect { (cached, pending, background) ->
                            update(identity.id) {
                                it.copy(cached = cached, pending = pending, background = background)
                            }
                        }
                }
            }
        }
    }

    fun refresh(more: Boolean = false) = work { id ->
        val after = if (more) mutable.value.next ?: return@work else null
        val page = boards.list(id, after)
        update(id) {
            it.copy(remote = if (more) (it.remote + page.items).distinctBy { item -> item.id } else page.items, next = page.next)
        }
    }

    fun publish(sourceId: String) = work { id ->
        val source = local.load(sourceId)
        val result = publisher.publish(id, source)
        update(id) { it.copy(preview = result, offlinePreview = false, message = R.string.cloud_published) }
    }

    fun retry(sourceId: String) = work { id ->
        val result = publisher.retry(id, sourceId)
        update(id) { it.copy(preview = result, offlinePreview = false, message = R.string.cloud_published) }
    }

    fun open(boardId: String) = work { id ->
        var offline = false
        val result = try {
            synchronization.reconcile(id, boardId)
        } catch (error: CloudFailure) {
            if (error.status == 409) {
                val cached = store.load(id, boardId) ?: throw error
                update(id) { it.copy(preview = cached, offlinePreview = false, message = R.string.cloud_conflict, error = true) }
                return@work
            }
            if (error.error != CloudError.OFFLINE) throw error
            offline = true
            store.load(id, boardId) ?: throw error
        }
        update(id) { it.copy(preview = result, offlinePreview = offline) }
    }

    fun closePreview() {
        mutable.update { it.copy(preview = null, offlinePreview = false) }
    }
    fun retryBackground() {
        account.value?.id?.let(scheduler::enqueue)
    }
    fun selectedCopy(): Board? = mutable.value.takeIf { it.accountId == account.value?.id }?.preview?.working
    fun dismissMessage() {
        mutable.update { it.copy(message = null) }
    }

    private fun update(id: String, action: (CloudUi) -> CloudUi) {
        mutable.update { if (it.accountId == id && account.value?.id == id) action(it) else it }
    }

    private fun work(action: suspend (String) -> Unit) {
        val id = account.value?.id ?: return
        if (mutable.value.accountId != id || mutable.value.busy) return
        update(id) { it.copy(busy = true, message = null, error = false) }
        actionJob = viewModelScope.launch {
            try {
                action(id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                update(id) { it.copy(message = message(error), error = true) }
            } finally {
                update(id) { it.copy(busy = false) }
            }
        }
    }

    private fun message(error: Exception): Int = when (error) {
        is PublicationFailure -> when (error.error) {
            PublicationError.MEDIA_UNAVAILABLE -> R.string.cloud_media_unavailable
            PublicationError.TOO_LARGE -> R.string.cloud_too_large
            PublicationError.QUEUE_FULL -> R.string.cloud_queue_full
            else -> R.string.operation_failed
        }
        is CloudFailure -> when {
            error.status == 404 || error.status == 403 -> R.string.cloud_access_unavailable
            error.status == 409 -> R.string.cloud_conflict
            error.status == 413 -> R.string.cloud_too_large
            error.error == CloudError.OFFLINE -> R.string.account_offline
            error.error == CloudError.CREDENTIALS -> R.string.account_credentials_error
            error.error == CloudError.LIMIT -> R.string.account_limit_error
            error.error == CloudError.UNAVAILABLE -> R.string.account_unavailable
            else -> R.string.account_server_error
        }
        is SyncFailure -> R.string.cloud_conflict
        else -> R.string.operation_failed
    }
}
