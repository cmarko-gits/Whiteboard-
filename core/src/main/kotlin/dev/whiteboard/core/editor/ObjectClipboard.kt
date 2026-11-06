package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*

/** Private application clipboard. Immutable objects remain editable; no system clipboard access. */
class ObjectClipboard {
    private class State(val source: Page, val pasteCount: Int = 0)
    private var state = State(Page())
    val available get() = state.source.elements.isNotEmpty()
    val assetIds get() = state.source.elements.referencedAssets()
    fun copy(elements: List<PageObject>) {
        copy(PageFragment(elements))
    }
    fun copy(page: Page, ids: Set<String>) {
        copy(MindMapCopies.branches(page, ids))
    }
    fun copy(fragment: PageFragment) {
        state = State(fragment.page)
    }
    fun paste(): List<PageObject> {
        require(state.source.mindMaps.isEmpty()) { "A semantic clipboard requires pasteFragment" }
        return pasteFragment().objects
    }
    fun pasteFragment(): PageFragment {
        val plan = pasteRequest().prepare()
        check(accept(plan))
        return plan.fragment
    }

    /** Capture on the owner thread; preparation reads only this immutable request on a worker. */
    fun pasteRequest(): ClipboardPasteRequest = ClipboardPasteRequest(state.source, state.pasteCount, state)
    fun isCurrent(plan: ClipboardPastePlan): Boolean = state === plan.request.identity
    fun accept(plan: ClipboardPastePlan): Boolean {
        if (!isCurrent(plan)) return false
        state = State(state.source, plan.nextCount)
        return true
    }
}

class ClipboardPasteRequest internal constructor(internal val source: Page, private val pasteCount: Int, internal val identity: Any) {
    fun prepare(checkpoint: () -> Unit = {}): ClipboardPastePlan {
        checkpoint()
        if (source.elements.isEmpty()) return ClipboardPastePlan(this, PageFragment(emptyList()), pasteCount)
        val nextCount = pasteCount % 20 + 1
        val offset = 24f * nextCount
        val copied = MindMapCopies.branches(
            source,
            source.elements.map { it.id }.toSet(),
            Point(offset, offset),
            checkpoint = checkpoint
        )
        return ClipboardPastePlan(this, copied, nextCount)
    }
}

class ClipboardPastePlan internal constructor(
    internal val request: ClipboardPasteRequest,
    val fragment: PageFragment,
    internal val nextCount: Int
)
