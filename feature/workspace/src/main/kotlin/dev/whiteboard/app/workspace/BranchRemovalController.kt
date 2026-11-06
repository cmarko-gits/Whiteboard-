package dev.whiteboard.app.workspace

import dev.whiteboard.core.concurrency.AppDispatchers
import dev.whiteboard.core.editor.*
import dev.whiteboard.core.model.*
import dev.whiteboard.ui.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class BranchRemovalRequest internal constructor(
    val id: String = newId(),
    val boardId: String,
    val pageId: String,
    val cut: Boolean,
    val nodeCount: Int,
    val objectCount: Int,
    internal val owner: DocumentSession,
    internal val plan: MindMapEditPlan,
    internal val copied: PageFragment?
)

data class BranchRemovalUi(val request: BranchRemovalRequest? = null, val busy: Boolean = false)

/** Owns preparation/confirmation lifetime; a confirmation never recomputes a different selection. */
class BranchRemovalController internal constructor(
    private val scope: CoroutineScope,
    private val dispatchers: AppDispatchers,
    private val session: () -> DocumentSession?,
    private val commit: (DocumentSession, MindMapEditPlan, PageFragment?) -> Unit,
    private val notice: (Int) -> Unit,
    private val report: (Throwable) -> Unit
) {
    private val mutable = MutableStateFlow(BranchRemovalUi())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var preparing: Job? = null

    fun request(ids: Set<String>, cut: Boolean = false): Job {
        dismiss()
        val sequence = generation
        val current = session()
        val source = current?.page
        val selected = ids.toSet()
        return scope.launch {
            if (current == null || source == null) return@launch
            mutable.value = BranchRemovalUi(busy = true)
            try {
                val prepared = withContext(dispatchers.computation) {
                    val calculation = currentCoroutineContext()
                    val checkpoint = { calculation.ensureActive() }
                    val plan =
                        MindMapEdits.deleteSelection(source, selected, current.editBudgetBytes, checkpoint) ?: return@withContext null
                    val copied = if (cut) {
                        MindMapCopies.branches(
                            source,
                            selected,
                            byteBudget = current.editBudgetBytes,
                            checkpoint = checkpoint
                        )
                    } else {
                        null
                    }
                    calculation.ensureActive()
                    BranchRemovalRequest(
                        boardId = current.board.id,
                        pageId = source.id,
                        cut = cut,
                        nodeCount = plan.removedNodeCount,
                        objectCount = plan.removedObjectCount,
                        owner = current,
                        plan = plan,
                        copied = copied
                    )
                }
                if (sequence != generation || prepared == null) return@launch
                if (session() === current && current.page.sharesSceneWith(source)) {
                    mutable.value = BranchRemovalUi(prepared)
                } else if (session()?.board?.id == current.board.id) {
                    notice(R.string.branch_changed)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (sequence == generation) report(failure)
            } finally {
                if (sequence == generation) mutable.update { it.copy(busy = false) }
            }
        }.also { preparing = it }
    }

    fun confirm(id: String): Job = scope.launch {
        val request = mutable.value.request?.takeIf { it.id == id } ?: return@launch
        if (mutable.value.busy) return@launch
        val current = session()
        if (current == null || current !== request.owner || !current.page.sharesSceneWith(request.plan.original)) {
            dismiss()
            notice(R.string.branch_changed)
            return@launch
        }
        try {
            commit(current, request.plan, request.copied)
            dismiss()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            report(failure)
        }
    }

    fun dismiss() {
        generation++
        preparing?.cancel()
        preparing = null
        mutable.value = BranchRemovalUi()
    }
}
