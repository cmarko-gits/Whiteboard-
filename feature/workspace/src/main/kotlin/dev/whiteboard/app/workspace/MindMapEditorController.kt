package dev.whiteboard.app.workspace

import dev.whiteboard.core.concurrency.AppDispatchers
import dev.whiteboard.core.editor.*
import dev.whiteboard.core.model.*
import dev.whiteboard.ui.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MindMapEditorUi(
    val request: MindMapEditorRequest? = null,
    val busy: Boolean = false,
    val error: Int? = null,
    val focusId: String? = null,
    val focusBounds: Bounds? = null,
    val focusSequence: Long = 0
)

/** Workers use immutable pages; the owning session is checked again before publication. */
class MindMapEditorController internal constructor(
    private val scope: CoroutineScope,
    private val dispatchers: AppDispatchers,
    private val session: () -> DocumentSession?,
    private val applied: (Set<String>, Boolean) -> Unit,
    private val notice: (Int) -> Unit,
    private val report: (Throwable) -> Unit
) {
    private val mutable = MutableStateFlow(MindMapEditorUi())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var active: Job? = null

    fun open(nodeId: String? = null, position: Point? = null): Job {
        dismiss()
        val sequence = generation
        val current = session()
        val source = current?.page
        val boardId = current?.board?.id
        return scope.launch {
            if (current == null || source == null || boardId == null) return@launch
            mutable.update { it.copy(busy = true) }
            try {
                val request = withContext(dispatchers.computation) {
                    val calculation = currentCoroutineContext()
                    MindMapEditorRequest.prepare(
                        current,
                        source,
                        boardId,
                        position ?: source.viewport.toDocument(Point(240f, 240f)),
                        nodeId
                    ) {
                        calculation.ensureActive()
                    }
                }
                if (sequence != generation) return@launch
                if (isCurrent(request)) mutable.update { it.copy(request = request) } else notice(R.string.mind_map_changed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (sequence == generation) report(failure)
            } finally {
                if (sequence == generation) mutable.update { it.copy(busy = false) }
            }
        }.also { active = it }
    }

    fun apply(action: MindMapAction): Job = scope.launch {
        val request = mutable.value.request ?: return@launch
        if (mutable.value.busy) return@launch
        active = currentCoroutineContext().job
        val sequence = generation
        mutable.update { it.copy(busy = true, error = null) }
        try {
            val result = withContext(dispatchers.computation) {
                val calculation = currentCoroutineContext()
                val checkpoint = { calculation.ensureActive() }
                prepare(request, action, checkpoint)
            }
            if (sequence != generation || mutable.value.request?.id != request.id) return@launch
            if (!isCurrent(request)) {
                clear(cancel = false, clearFocus = false)
                notice(R.string.mind_map_changed)
                return@launch
            }
            val edited = result.plan?.let { request.owner.applyMindMapEdit(it) } ?: false
            applied(result.selection, edited)
            clear(cancel = false, clearFocus = false)
            mutable.update { it.copy(focusId = result.focus, focusBounds = result.bounds, focusSequence = it.focusSequence + 1) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: DocumentFailure) {
            if (sequence ==
                generation
            ) {
                mutable.update {
                    it.copy(
                        error = if (failure.error ==
                            DocumentError.EDIT_LIMIT
                        ) {
                            R.string.edit_limit
                        } else {
                            R.string.operation_failed
                        }
                    )
                }
            }
        } catch (_: IllegalArgumentException) {
            if (sequence == generation) mutable.update { it.copy(error = R.string.mind_map_invalid) }
        } finally {
            if (sequence == generation) mutable.update { it.copy(busy = false) }
        }
    }

    private data class Prepared(val plan: MindMapEditPlan?, val selection: Set<String>, val focus: String?, val bounds: Bounds? = null)
    private fun prepare(request: MindMapEditorRequest, action: MindMapAction, checkpoint: () -> Unit): Prepared {
        checkpoint()
        val page = request.page
        val budget = request.owner.editBudgetBytes
        fun title(value: String) {
            require(value.isNotBlank() && value.length <= 20_000)
        }
        fun node(id: String): MindMapRow = requireNotNull(request.byId[id]) { "Unknown requested node" }
        if (action is MindMapAction.SelectMap) {
            val row = node(action.nodeId)
            val index = checkNotNull(page.mindMapIndex)
            val tree = index.tree(row.mapId)
            val objects = page.visibleElements.filter {
                checkpoint()
                index.ownerOfObject(it.id)?.id == tree.id
            }
            val ids = objects.mapTo(HashSet()) { it.id }
            val bounds = objects.fold<PageObject, Bounds?>(null) { frame, element ->
                checkpoint()
                val area = page.geometry.renderBounds(element)
                frame?.union(area) ?: area
            }
            return Prepared(null, ids, tree.rootId, bounds)
        }
        val plan = when (action) {
            is MindMapAction.Create -> {
                title(action.title)
                MindMapEdits.create(page, request.position, action.title, action.color, budget, checkpoint = checkpoint)
            }
            is MindMapAction.Child -> {
                node(action.parentId)
                title(action.title)
                MindMapEdits.addChild(page, action.parentId, action.title, action.color, byteBudget = budget, checkpoint = checkpoint)
            }
            is MindMapAction.Sibling -> {
                node(action.nodeId)
                title(action.title)
                MindMapEdits.addSibling(page, action.nodeId, action.title, action.color, byteBudget = budget, checkpoint = checkpoint)
            }
            is MindMapAction.Edit -> {
                node(action.nodeId)
                title(action.title)
                MindMapEdits.edit(page, action.nodeId, action.title, action.color, budget, checkpoint)
            }
            is MindMapAction.Collapse -> {
                node(action.nodeId)
                MindMapEdits.collapse(page, action.nodeId, action.collapsed, budget, checkpoint)
            }
            is MindMapAction.Focus -> {
                node(action.nodeId)
                MindMapEdits.reveal(page, action.nodeId, budget, checkpoint)
            }
            is MindMapAction.Reparent -> {
                node(action.nodeId)
                node(action.parentId)
                MindMapEdits.reparent(page, action.nodeId, action.parentId, budget, checkpoint)
            }
            is MindMapAction.Move -> {
                node(action.nodeId)
                MindMapEdits.moveBranch(page, action.nodeId, action.delta, budget, checkpoint)
            }
            is MindMapAction.SelectMap -> error("Handled above")
        }
        val fallback = when (action) {
            is MindMapAction.Edit -> action.nodeId
            is MindMapAction.Collapse -> action.nodeId
            is MindMapAction.Focus -> action.nodeId
            is MindMapAction.Reparent -> action.nodeId
            is MindMapAction.Move -> action.nodeId
            else -> null
        }
        val focus = plan?.focusId ?: fallback?.let { id ->
            val index = checkNotNull(page.mindMapIndex)
            checkNotNull(index.ownerOfObject(id)).pathTo(id, checkpoint).last { index.isVisible(it.id) }.id
        }
        return Prepared(plan, setOfNotNull(focus), focus)
    }

    internal fun isCurrent(request: MindMapEditorRequest): Boolean =
        session() === request.owner && request.owner.page.sharesSceneWith(request.page)
    fun dismiss(clearFocus: Boolean = false) = clear(cancel = true, clearFocus = clearFocus)
    private fun clear(cancel: Boolean, clearFocus: Boolean) {
        generation++
        if (cancel) active?.cancel()
        active = null
        mutable.update {
            it.copy(
                request = null,
                busy = false,
                error = null,
                focusId = if (clearFocus) null else it.focusId,
                focusBounds = if (clearFocus) null else it.focusBounds
            )
        }
    }
}
