package dev.whiteboard.app.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.whiteboard.canvas.CanvasEvent
import dev.whiteboard.core.concurrency.AppDispatchers
import dev.whiteboard.core.concurrency.DefaultAppDispatchers
import dev.whiteboard.core.editor.DocumentSession
import dev.whiteboard.core.editor.LayerStep
import dev.whiteboard.core.editor.ObjectAlignment
import dev.whiteboard.core.editor.ObjectClipboard
import dev.whiteboard.core.editor.ObjectRotation
import dev.whiteboard.core.editor.SelectionAlignment
import dev.whiteboard.core.editor.SmartShapeRecognizer
import dev.whiteboard.core.model.*
import dev.whiteboard.core.model.ThemeMode
import dev.whiteboard.core.model.UserSettings
import dev.whiteboard.core.usecase.DocumentTransfers
import dev.whiteboard.core.usecase.EditorPersistence
import dev.whiteboard.core.usecase.ManagePreferences
import dev.whiteboard.ui.R
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class EditorUi(
    val boardId: String,
    val name: String,
    val page: Page,
    val pages: List<Page> = listOf(page),
    val dirty: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val canPaste: Boolean = false,
    val tool: Tool = Tool.PEN,
    val ink: InkStyle = InkStyle(),
    val saving: Boolean = false,
    val initialFit: Boolean = false
) {
    val selectedId get() = selectedIds.singleOrNull()
}
data class PendingRecovery(val saved: Board, val draft: Board, val query: String? = null)
data class PdfImportRequest(val boardId: String, val pages: List<Page>)
data class UiNotice(val resource: Int, val count: Int? = null, val retry: Boolean = false, val id: String = newId())
data class WorkspaceUi(
    val editor: EditorUi? = null,
    val loading: Boolean = false,
    val settings: UserSettings = UserSettings(),
    val settingsLoaded: Boolean = false,
    val recovery: PendingRecovery? = null,
    val textRequest: CanvasEvent.EditText? = null,
    val pdfImport: PdfImportRequest? = null,
    val message: UiNotice? = null,
    val exporting: Boolean = false,
    val rotating: Boolean = false,
    val rotationSequence: Long = 0,
    val aligning: Boolean = false,
    val alignmentSequence: Long = 0,
    val connectionRequest: ConnectionRequest? = null,
    val connectionBusy: Boolean = false,
    val connectionError: Int? = null
)

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val persistence: EditorPersistence,
    private val preferences: ManagePreferences,
    private val files: DocumentTransfers,
    private val dispatchers: AppDispatchers = DefaultAppDispatchers
) : ViewModel() {
    private val mutable = MutableStateFlow(WorkspaceUi())
    val state = mutable.asStateFlow()
    private var session: DocumentSession? = null
    private val clipboard = ObjectClipboard()
    private val clipboardRetentionId = newId()
    private val retentionId = newId()
    private var connectionGeneration = 0L
    private var recoveryJob: Job? = null
    private var brushJob: Job? = null
    private data class FailedOperation(val boardId: String?, val action: suspend () -> Unit)
    private var failedOperation: FailedOperation? = null
    init {
        viewModelScope.launch {
            preferences.values.catch {
                report(it)
            }.collect { settings -> mutable.update { it.copy(settings = settings, settingsLoaded = true) } }
        }
    }

    fun create(
        name: String,
        folderId: String? = null,
        template: BoardTemplate = BoardTemplate.BLANK,
        content: TemplateContent = TemplateContent.ENGLISH
    ) = work {
        mutable.update { it.copy(loading = true) }
        val board = persistence.create(name, folderId, template, content)
        activate(board)
        if (board.activePage.elements.isNotEmpty()) mutable.update { it.copy(editor = it.editor?.copy(initialFit = true)) }
    }
    fun open(id: String, query: String? = null) = work {
        mutable.update { it.copy(loading = true) }
        val opened = persistence.open(id)
        val board = opened.saved
        val draft = opened.recovery
        if (draft != null) {
            mutable.update { it.copy(loading = false, recovery = PendingRecovery(board, draft, query)) }
        } else {
            activate(board, query = query)
        }
    }
    fun createPrivateCopy(source: Board, name: String) = work {
        mutable.update { it.copy(loading = true) }
        val copy = persistence.createPrivateCopy(source, name)
        activate(copy)
        mutable.update { it.copy(editor = it.editor?.copy(initialFit = true)) }
    }
    fun recover(useDraft: Boolean) = work {
        val pending = mutable.value.recovery ?: return@work
        if (!useDraft) persistence.discardDraft(pending.saved.id)
        activate(if (useDraft) pending.draft else pending.saved, recovered = useDraft, query = pending.query)
        mutable.update { it.copy(recovery = null) }
    }
    private suspend fun activate(board: Board, recovered: Boolean = false, query: String? = null) {
        connectionGeneration++
        val match = board.firstTextMatch(query)
        var opened = if (match == null) board else board.copy(activePageId = match.pageId)
        match?.element?.let { target ->
            val bounds = opened.activePage.geometry.selectionBounds(target)
            val view = Viewport().pan(120f - bounds.left, 160f - bounds.top)
            opened = opened.withPage(opened.activePage.copy(viewport = view))
        }
        session = DocumentSession(opened).also { if (recovered) it.markRecovered() }
        persistence.markOpened(board.id)
        val settings = mutable.value.settings
        mutable.update {
            it.copy(
                loading = false,
                connectionRequest = null,
                connectionError = null,
                editor = EditorUi(
                    board.id,
                    board.name,
                    opened.activePage,
                    pages = opened.pages,
                    selectedIds = setOfNotNull(match?.element?.id),
                    canPaste = clipboard.available,
                    tool = if (match?.element != null) Tool.SELECT else settings.lastTool,
                    ink = settings.lastInk
                )
            )
        }
        publish()
    }

    fun canvas(event: CanvasEvent, source: EditorUi? = null) {
        val current = session ?: return
        if (source != null && !sourceMatches(current, source)) return
        try {
            when (event) {
                is CanvasEvent.Add -> {
                    val element = event.element
                    current.add(element)
                    if (element is Connector) {
                        mutable.update {
                            it.copy(editor = it.editor?.copy(selectedIds = setOf(element.id)))
                        }
                    }
                    if (mutable.value.settings.smartShapes && element is Stroke) {
                        SmartShapeRecognizer.recognize(element)?.let { current.replace(it) }
                    }
                    changed()
                }
                is CanvasEvent.Replace -> {
                    current.replaceAll(event.elements)
                    changed()
                }
                is CanvasEvent.TransformBy -> {
                    try {
                        if (current.transformBy(event.id, event.translation, event.factor)) changed()
                    } catch (error: IllegalArgumentException) {
                        report(error)
                    }
                }
                is CanvasEvent.Delete -> {
                    current.delete(event.ids)
                    changed()
                }
                is CanvasEvent.EraseStrokes -> {
                    current.eraseStrokes(event.ids)
                    changed()
                }
                is CanvasEvent.EraseParts -> {
                    try {
                        if (current.eraseParts(event.cuts)) changed()
                    } catch (error: DocumentFailure) {
                        report(error)
                    } catch (error: IllegalArgumentException) {
                        report(error)
                    }
                }
                is CanvasEvent.Rejected -> report(DocumentFailure(event.error))
                CanvasEvent.ConnectionTargetMissing -> mutable.update { it.copy(message = UiNotice(R.string.connection_target_hint)) }
                is CanvasEvent.Select -> {
                    val ids = event.ids.intersect(current.page.elements.map { it.id }.toSet())
                    mutable.update {
                        it.copy(editor = it.editor?.copy(selectedIds = ids, tool = if (ids.isNotEmpty()) Tool.SELECT else it.editor.tool))
                    }
                }
                is CanvasEvent.Fill -> {
                    current.fill(event.position, event.color)
                    changed()
                }
                is CanvasEvent.EditText -> {
                    val existing = event.existing?.let { previous ->
                        current.page.elements.firstOrNull { it.id == previous.id } as? Element
                    }
                    if (event.existing != null && existing == null) return
                    val content = existing?.content()
                    if (existing != null && (if (event.note) content !is Note else content !is TextElement)) return
                    mutable.update { it.copy(textRequest = event.copy(existing = existing)) }
                }
                is CanvasEvent.Camera -> {
                    current.updateViewport(event.viewport)
                    if (mutable.value.editor?.initialFit == true) mutable.update { it.copy(editor = it.editor?.copy(initialFit = false)) }
                }
            }
        } catch (error: DocumentFailure) {
            report(error)
        } catch (error: IllegalArgumentException) {
            report(error)
        }
    }
    fun tool(tool: Tool) {
        mutable.update { state ->
            state.copy(
                editor = state.editor?.let { editor ->
                    val style = when (tool) {
                        Tool.HIGHLIGHTER -> editor.ink.copy(color = 0xFFFFD54F.toInt(), width = 22f, opacity = 0.35f)
                        Tool.MARKER -> editor.ink.copy(width = 10f, opacity = 1f)
                        Tool.PEN -> editor.ink.copy(width = 3f, opacity = 1f)
                        else -> editor.ink
                    }
                    editor.copy(tool = tool, ink = style, selectedIds = if (tool == Tool.SELECT) editor.selectedIds else emptySet())
                }
            )
        }
        rememberBrush()
    }
    fun ink(style: InkStyle) {
        mutable.update { it.copy(editor = it.editor?.copy(ink = style)) }
        rememberBrush()
    }
    private fun rememberBrush() {
        brushJob?.cancel()
        brushJob = work {
            delay(250)
            mutable.value.editor?.let { preferences.lastBrush(it.tool, it.ink) }
        }
    }
    fun preset(preset: BrushPreset) {
        mutable.update { it.copy(editor = it.editor?.copy(tool = preset.tool, ink = preset.ink, selectedIds = emptySet())) }
        rememberBrush()
    }
    fun savePreset(name: String) = work { mutable.value.editor?.let { preferences.addBrush(name, it.tool, it.ink) } }
    fun deletePreset(id: String) = work { preferences.deleteBrush(id) }
    fun favoriteColors(colors: List<Int>) = work { preferences.favoriteColors(colors) }
    fun smartShapes(enabled: Boolean) = work { preferences.smartShapes(enabled) }
    fun snap(enabled: Boolean) = work { preferences.snap(enabled) }
    fun eraserMode(mode: EraserMode) = work { preferences.eraserMode(mode) }
    fun undo() {
        session?.undo()
        changed()
    }
    fun redo() {
        session?.redo()
        changed()
    }
    fun selectPage(id: String) {
        session?.selectPage(id)
        dismissConnection()
        clearSelection()
        publish()
    }
    fun addPage() {
        if ((session?.board?.pages?.size ?: 0) >= 1000) return
        session?.addPage()
        clearSelection()
        changed()
    }
    fun duplicatePage(id: String) = work {
        if ((session?.board?.pages?.size ?: 0) >= 1000) return@work
        session?.duplicatePage(id)
        clearSelection()
        changed()
    }
    fun deletePage(id: String) {
        session?.deletePage(id)
        clearSelection()
        changed()
    }
    fun renamePage(id: String, name: String) {
        session?.renamePage(id, name)
        changed()
    }
    fun movePage(id: String, delta: Int) {
        session?.movePage(id, delta)
        changed()
    }
    fun background(pattern: Background, color: Int) {
        session?.background(pattern, color)
        changed()
    }
    fun importImage(uri: String) = work {
        val current = session ?: return@work
        mutable.update { it.copy(exporting = true) }
        try {
            val image = files.importImage(uri)
            if (session === current) {
                val center = current.page.viewport.toDocument(Point(300f, 300f))
                current.add(image.transform(Bounds(center.x, center.y, center.x + image.bounds.width, center.y + image.bounds.height)))
                changed()
            }
        } finally {
            mutable.update { it.copy(exporting = false) }
        }
    }
    fun importPdf(uri: String) = work {
        val current = session ?: return@work
        mutable.update { it.copy(exporting = true) }
        try {
            val pages = files.importPdf(uri)
            if (session === current) {
                mutable.update { it.copy(pdfImport = PdfImportRequest(current.board.id, pages)) }
            }
        } finally {
            mutable.update { it.copy(exporting = false) }
        }
    }
    fun applyPdfRange(first: Int, last: Int) = work {
        val pending = mutable.value.pdfImport ?: return@work
        val current = session ?: return@work
        require(current.board.id == pending.boardId && first >= 1 && last >= first && last <= pending.pages.size)
        current.addPages(pending.pages.subList(first - 1, last))
        mutable.update { it.copy(pdfImport = null) }
        clearSelection()
        changed()
    }
    fun dismissPdf() = work {
        val pending = mutable.value.pdfImport ?: return@work
        mutable.update { it.copy(pdfImport = null) }
        files.deleteAssets(pending.pages.flatMap { it.elements }.filterIsInstance<MediaElement>().map { it.assetId }.toSet())
    }
    fun rotateSelected(degrees: Double = 90.0) = work {
        if (mutable.value.rotating || mutable.value.aligning) return@work
        val current = session ?: return@work
        val original = current.page
        val ids = mutable.value.editor?.selectedIds.orEmpty()
        val budget = current.editBudgetBytes
        mutable.update { it.copy(rotating = true) }
        try {
            val plan = withContext(dispatchers.computation) {
                val calculation = currentCoroutineContext()
                ObjectRotation.plan(original, ids, degrees, budget) { calculation.ensureActive() }
            }
            if (session === current && current.applySelectionEdit(plan)) {
                changed()
            } else if (plan.replacements.isNotEmpty() && mutable.value.editor?.boardId == current.board.id) {
                mutable.update { it.copy(message = UiNotice(R.string.rotation_changed)) }
            }
        } finally {
            mutable.update { it.copy(rotating = false, rotationSequence = it.rotationSequence + 1) }
        }
    }
    fun alignSelected(alignment: ObjectAlignment) = work {
        if (mutable.value.rotating || mutable.value.aligning) return@work
        val current = session ?: return@work
        val original = current.page
        val ids = mutable.value.editor?.selectedIds.orEmpty()
        val budget = current.editBudgetBytes
        mutable.update { it.copy(aligning = true) }
        try {
            val plan = withContext(dispatchers.computation) {
                val calculation = currentCoroutineContext()
                SelectionAlignment.plan(original, ids, alignment, budget) { calculation.ensureActive() }
            }
            if (session === current && current.applySelectionEdit(plan)) {
                changed()
            } else if (plan.replacements.isNotEmpty() && mutable.value.editor?.boardId == current.board.id) {
                mutable.update { it.copy(message = UiNotice(R.string.alignment_changed)) }
            }
        } finally {
            mutable.update { it.copy(aligning = false, alignmentSequence = it.alignmentSequence + 1) }
        }
    }
    fun cropSelected(crop: Bounds) {
        val current = session ?: return
        val element = current.page.elements.firstOrNull { it.id == mutable.value.editor?.selectedId } ?: return
        val media = element.content() as? MediaElement ?: return
        current.replace(element.withContent(media.cropped(crop)))
        changed()
    }
    private fun clearSelection() {
        mutable.update { it.copy(editor = it.editor?.copy(selectedIds = emptySet())) }
    }
    fun currentViewport(pageId: String): Viewport? = session?.board?.pages?.firstOrNull { it.id == pageId }?.viewport
    fun deleteSelected() {
        val ids = mutable.value.editor?.selectedIds ?: return
        session?.delete(ids)
        clearSelection()
        changed()
    }
    fun duplicateSelected() = work {
        val current = session ?: return@work
        val temporary = ObjectClipboard()
        temporary.copy(current.page, mutable.value.editor?.selectedIds.orEmpty())
        val copies = temporary.paste()
        current.addAll(copies)
        mutable.update { it.copy(editor = it.editor?.copy(selectedIds = copies.map { copy -> copy.id }.toSet())) }
        changed()
    }
    fun selectedElements(): List<PageObject> {
        val ids = mutable.value.editor?.selectedIds.orEmpty()
        return session?.page?.elements?.filter { it.id in ids }.orEmpty()
    }
    fun selectAll() {
        mutable.update {
            it.copy(
                editor = it.editor?.copy(
                    tool = Tool.SELECT,
                    selectedIds = session?.page?.elements?.map { e ->
                        e.id
                    }?.toSet().orEmpty()
                )
            )
        }
    }
    fun copySelected(cut: Boolean = false) = work {
        val current = session ?: return@work
        val elements = selectedElements()
        if (elements.isEmpty()) return@work
        clipboard.copy(current.page, elements.map { it.id }.toSet())
        files.protectSession(clipboardRetentionId, clipboard.assetIds)
        mutable.update { it.copy(editor = it.editor?.copy(canPaste = true)) }
        if (cut) deleteSelected()
    }
    fun paste() = work {
        val current = session ?: return@work
        val copies = clipboard.paste()
        current.addAll(copies)
        mutable.update { it.copy(editor = it.editor?.copy(tool = Tool.SELECT, selectedIds = copies.map { copy -> copy.id }.toSet())) }
        changed()
    }
    fun reorderSelected(front: Boolean) = work {
        if (session?.reorder(mutable.value.editor?.selectedIds.orEmpty(), front) == true) changed()
    }
    fun stepSelectedLayer(step: LayerStep) = work {
        if (session?.stepLayer(mutable.value.editor?.selectedIds.orEmpty(), step) == true) changed()
    }
    fun groupSelected() {
        val group = session?.group(mutable.value.editor?.selectedIds.orEmpty()) ?: return
        mutable.update { it.copy(editor = it.editor?.copy(selectedIds = setOf(group.id))) }
        changed()
    }
    fun ungroupSelected() {
        val ids = session?.ungroup(mutable.value.editor?.selectedIds.orEmpty()) ?: return
        mutable.update { it.copy(editor = it.editor?.copy(selectedIds = ids)) }
        changed()
    }
    fun applySelectedStyle(ink: InkStyle, fill: Int?) = work {
        session?.replaceAll(
            selectedElements().map { element ->
                element.mapLeaves { child ->
                    when (child) {
                        is Stroke -> child.copy(style = ink)
                        is Line -> child.copy(style = ink)
                        is Connector -> child.copy(style = ink)
                        is Shape -> child.copy(style = ink, fill = fill)
                        else -> child
                    }
                }
            }
        )
        changed()
    }

    fun showConnection(id: String? = null, position: Point? = null, source: EditorUi? = null) = work {
        if (mutable.value.connectionBusy || mutable.value.connectionRequest != null) return@work
        val current = session ?: return@work
        val original = current.page
        if (source != null && !sourceMatches(current, source)) {
            mutable.update { it.copy(message = UiNotice(R.string.connection_changed)) }
            return@work
        }
        val existing = id?.let { value -> original.elements.firstOrNull { it.id == value } as? Connector }
        if (id != null && existing == null) return@work
        val selected = mutable.value.editor?.selectedIds.orEmpty()
        val boardId = current.board.id
        val generation = ++connectionGeneration
        val ink = existing?.style ?: mutable.value.editor?.ink ?: InkStyle()
        mutable.update { it.copy(connectionBusy = true, connectionError = null) }
        try {
            val request = withContext(dispatchers.computation) {
                val calculation = currentCoroutineContext()
                val targets = original.geometry.connectionTargets { calculation.ensureActive() }
                val candidates = (targets.filter { it.id in selected } + targets).distinctBy { it.id }
                val center = position ?: original.viewport.toDocument(Point(240f, 240f))
                val startTarget = candidates.firstOrNull()
                val endTarget = candidates.firstOrNull { it.id != startTarget?.id }
                val start = existing?.start ?: startTarget?.let {
                    ConnectorEnd.Bound(it.id, original.geometry.closestConnectionPort(it.id, endTarget?.bounds?.center ?: center))
                } ?: ConnectorEnd.Free(center)
                fun point(end: ConnectorEnd): Point = when (end) {
                    is ConnectorEnd.Free -> end.point
                    is ConnectorEnd.Bound -> original.geometry.connectionPorts().point(end.targetId, end.port)
                }
                val startPoint = point(start)
                val end = existing?.end ?: endTarget?.let {
                    ConnectorEnd.Bound(it.id, original.geometry.closestConnectionPort(it.id, startPoint))
                } ?: ConnectorEnd.Free(Point(startPoint.x + 160f, startPoint.y))
                calculation.ensureActive()
                ConnectionRequest(boardId, original, existing, targets, start, end, startPoint, point(end), ink, existing?.arrow ?: true)
            }
            if (generation != connectionGeneration) return@work
            if (session === current && current.page.id == original.id && current.page.elements === original.elements) {
                mutable.update { it.copy(connectionRequest = request) }
            } else {
                mutable.update { it.copy(message = UiNotice(R.string.connection_changed)) }
            }
        } finally {
            mutable.update { it.copy(connectionBusy = false) }
        }
    }

    fun dismissConnection() {
        connectionGeneration++
        mutable.update { it.copy(connectionRequest = null, connectionError = null) }
    }

    fun applyConnection(start: ConnectorEnd, end: ConnectorEnd, arrow: Boolean) = work {
        if (mutable.value.connectionBusy) return@work
        val request = mutable.value.connectionRequest ?: return@work
        val current = session ?: return@work
        mutable.update { it.copy(connectionBusy = true, connectionError = null) }
        try {
            val definition = withContext(dispatchers.computation) {
                val calculation = currentCoroutineContext()
                calculation.ensureActive()
                val result = Connector(request.existing?.id ?: request.id, start, end, request.style, arrow)
                val objects = if (request.existing == null) {
                    request.page.elements + result
                } else {
                    request.page.elements.map {
                        if (it.id == result.id) result else it
                    }
                }
                request.page.copy(elements = objects).contentBounds
                calculation.ensureActive()
                result
            }
            if (mutable.value.connectionRequest?.id != request.id) return@work
            if (session !== current || current.board.id != request.boardId || current.page.id != request.page.id ||
                current.page.elements !== request.page.elements
            ) {
                dismissConnection()
                mutable.update { it.copy(message = UiNotice(R.string.connection_changed)) }
                return@work
            }
            if (request.existing == null) current.add(definition) else current.replace(definition)
            mutable.update { it.copy(editor = it.editor?.copy(tool = Tool.SELECT, selectedIds = setOf(definition.id))) }
            dismissConnection()
            changed()
        } catch (error: DocumentFailure) {
            mutable.update {
                it.copy(
                    connectionError = if (error.error ==
                        DocumentError.EDIT_LIMIT
                    ) {
                        R.string.edit_limit
                    } else {
                        R.string.operation_failed
                    }
                )
            }
        } catch (_: IllegalArgumentException) {
            mutable.update { it.copy(connectionError = R.string.connection_invalid) }
        } finally {
            mutable.update { it.copy(connectionBusy = false) }
        }
    }

    fun detachConnection(id: String, source: EditorUi? = null) = work {
        val current = session ?: return@work
        if (source != null && !sourceMatches(current, source)) return@work
        val definition = current.page.elements.firstOrNull { it.id == id } as? Connector ?: return@work
        val resolved = current.page.geometry.resolve(definition) as Line
        current.replace(definition.copy(start = ConnectorEnd.Free(resolved.start), end = ConnectorEnd.Free(resolved.end)))
        changed()
    }

    private fun sourceMatches(current: DocumentSession, source: EditorUi): Boolean =
        current.board.id == source.boardId && current.page.id == source.page.id && current.page.elements === source.page.elements
    fun applyText(text: String, size: Float, bold: Boolean, italic: Boolean, alignment: TextAlignment, color: Int) {
        val request = mutable.value.textRequest ?: return
        if (text.isNotBlank()) {
            val existing = request.existing?.let { previous -> session?.page?.elements?.firstOrNull { it.id == previous.id } }
            if (request.existing != null && existing == null) {
                dismissText()
                return
            }
            val content = (existing as? Element)?.content()
            if (existing != null && (if (request.note) content !is Note else content !is TextElement)) {
                dismissText()
                return
            }
            val b =
                content?.bounds
                    ?: Bounds(
                        request.position.x,
                        request.position.y,
                        request.position.x + if (request.note) 240f else 320f,
                        request.position.y + if (request.note) 220f else 180f
                    )
            val element = if (request.note) {
                Note(existing?.id ?: newId(), b, text, color, size)
            } else {
                TextElement(existing?.id ?: newId(), b, text, color, size, bold, italic, alignment)
            }
            if (existing == null) session?.add(element) else session?.replace(existing.withContent(element))
            changed()
        }
        dismissText()
    }
    fun dismissText() {
        mutable.update { it.copy(textRequest = null) }
    }
    private fun changed() {
        publish()
        scheduleRecovery()
    }
    private fun publish() {
        val current = session ?: return
        files.protectSession(retentionId, current.retainedAssetIds)
        mutable.update { state ->
            state.copy(
                editor = state.editor?.copy(
                    page = current.page,
                    pages = current.board.pages,
                    dirty = current.dirty,
                    canUndo = current.canUndo,
                    canRedo = current.canRedo,
                    selectedIds = state.editor.selectedIds.intersect(current.page.elements.map { it.id }.toSet())
                )
            )
        }
    }
    private fun scheduleRecovery() {
        recoveryJob?.cancel()
        recoveryJob = viewModelScope.launch {
            delay(700)
            try {
                flushRecoveryNow()
                if (mutable.value.settings.autosave && session?.dirty == true) save()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                report(error)
            }
        }
    }
    private suspend fun flushRecoveryNow() {
        session?.let { persistence.flush(it) }
    }
    fun flushRecovery() = work(retryable = true) { flushRecoveryNow() }

    fun save(closeAfter: Boolean = false) = work(retryable = true) {
        if (mutable.value.editor?.saving == true) return@work
        val current = session ?: return@work
        mutable.update { it.copy(editor = it.editor?.copy(saving = true)) }
        var committed = false
        try {
            val saved = persistence.save(current)
            committed = true
            publish()
            files.thumbnail(saved.board)
        } finally {
            mutable.update { it.copy(editor = it.editor?.copy(saving = false)) }
            if (committed && current.dirty && mutable.value.settings.autosave) scheduleRecovery()
        }
        if (closeAfter && !current.dirty) leave()
    }
    fun leave(discard: Boolean = false) = work {
        if (mutable.value.editor?.saving == true) return@work
        recoveryJob?.cancelAndJoin()
        val current = session ?: return@work
        if (current.dirty && !discard) return@work
        persistence.discardDraft(current.board.id)
        if (session !== current) return@work
        session = null
        dismissConnection()
        files.releaseSession(retentionId)
        mutable.update { it.copy(editor = null, textRequest = null) }
    }
    fun theme(mode: ThemeMode) = work { preferences.theme(mode) }
    fun fingerDrawing(enabled: Boolean) = work { preferences.fingerDrawing(enabled) }
    fun finishOnboarding() = work { preferences.finishOnboarding() }
    fun autosave(enabled: Boolean) = work {
        preferences.autosave(enabled)
        if (enabled && session?.dirty == true) save()
    }
    fun dismissMessage(id: String? = null) {
        mutable.update { if (id == null || it.message?.id == id) it.copy(message = null) else it }
    }
    fun retryFailedOperation() {
        val failed = failedOperation ?: return
        failedOperation = null
        dismissMessage()
        if (failed.boardId != mutable.value.editor?.boardId) return
        work(retryable = true, block = failed.action)
    }
    fun cameraUnavailable() {
        mutable.update { it.copy(message = UiNotice(R.string.camera_unavailable)) }
    }
    fun backup(uri: String) = work(retryable = true) {
        mutable.update { it.copy(exporting = true) }
        try {
            val count = files.backup(uri)
            mutable.update { it.copy(message = UiNotice(R.string.backup_exported, count)) }
        } finally {
            mutable.update { it.copy(exporting = false) }
        }
    }
    fun restoreBackup(uri: String) = work {
        mutable.update { it.copy(loading = true) }
        try {
            val count = files.restore(uri)
            mutable.update { it.copy(message = UiNotice(R.string.backup_imported, count)) }
        } finally {
            mutable.update { it.copy(loading = false) }
        }
    }

    fun exportPng(uri: String) = work(retryable = true) {
        val page = session?.page ?: return@work
        mutable.update { it.copy(exporting = true) }
        try {
            files.exportPng(uri, page)
            mutable.update { it.copy(message = UiNotice(R.string.png_exported)) }
        } finally {
            mutable.update { it.copy(exporting = false) }
        }
    }
    fun exportJpeg(uri: String) = work(retryable = true) {
        val page = session?.page ?: return@work
        mutable.update { it.copy(exporting = true) }
        try {
            files.exportJpeg(uri, page)
            mutable.update { it.copy(message = UiNotice(R.string.file_exported)) }
        } finally {
            mutable.update { it.copy(exporting = false) }
        }
    }
    fun exportPdf(uri: String) = work(retryable = true) {
        val board = session?.board ?: return@work
        mutable.update { it.copy(exporting = true) }
        try {
            files.exportPdf(uri, board)
            mutable.update { it.copy(message = UiNotice(R.string.file_exported)) }
        } finally {
            mutable.update { it.copy(exporting = false) }
        }
    }
    fun sharePng(onReady: (File) -> Unit) = work {
        val page = session?.page ?: return@work
        mutable.update { it.copy(exporting = true) }
        try {
            val file = files.sharePng(page)
            onReady(file)
        } finally {
            mutable.update { it.copy(exporting = false) }
        }
    }
    private fun report(error: Throwable, retry: Boolean = false) {
        val resource = when (error) {
            is UnsupportedDocumentVersion -> R.string.newer_document
            is DocumentFailure -> when (error.error) {
                DocumentError.STORAGE_FULL -> R.string.storage_full
                DocumentError.STORAGE_UNAVAILABLE -> R.string.storage_unavailable
                DocumentError.PERMISSION_DENIED -> R.string.document_permission_denied
                DocumentError.INVALID_DOCUMENT -> R.string.invalid_document
                DocumentError.EDIT_LIMIT -> R.string.edit_limit
                DocumentError.DOCUMENT_LIMIT -> R.string.document_limit
            }
            is IOException -> R.string.storage_unavailable
            is SecurityException -> R.string.document_permission_denied
            else -> R.string.operation_failed
        }
        mutable.update {
            it.copy(
                loading = false,
                message = UiNotice(resource, retry = retry)
            )
        }
    }
    override fun onCleared() {
        files.releaseSession(retentionId)
        files.releaseSession(clipboardRetentionId)
        super.onCleared()
    }
    private fun work(retryable: Boolean = false, block: suspend () -> Unit): Job = viewModelScope.launch {
        val boardId = mutable.value.editor?.boardId
        if (retryable) {
            failedOperation = null
            mutable.update { if (it.message?.retry == true) it.copy(message = null) else it }
        }
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val canRetry = retryable && error !is IllegalArgumentException && error !is SecurityException &&
                (error !is DocumentFailure || error.error in setOf(DocumentError.STORAGE_FULL, DocumentError.STORAGE_UNAVAILABLE))
            failedOperation = if (canRetry) FailedOperation(boardId, block) else null
            report(error, retry = canRetry)
        }
    }
}
