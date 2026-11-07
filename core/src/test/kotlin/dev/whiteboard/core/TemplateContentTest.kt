package dev.whiteboard.core

import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class TemplateContentTest {
    private val labels = TemplateLabel.entries.associateWith { "Prevod: ${it.name}" }
    private val translated = TemplateContent(labels)

    @Test fun everyNonemptySeedStringAndTheFirstPageNameUseTheCapturedContent() {
        BoardTemplate.entries.forEach { template ->
            val board = TemplateFactory.create("Korisnikov naziv", template, translated)
            assertEquals("Korisnikov naziv", board.name)
            assertEquals(labels.getValue(TemplateLabel.PAGE_ONE), board.activePage.name)
            board.activePage.elements.forEach { element ->
                val text = when (element) {
                    is Note -> element.text
                    is TextElement -> element.text
                    else -> ""
                }
                if (text.isNotEmpty()) assertTrue("Unlocalized $template: $text", text in labels.values)
            }
        }
    }

    @Test fun changingContentDoesNotChangeTemplateGeometryStylesOrBackgrounds() {
        BoardTemplate.entries.forEach { template ->
            val english = TemplateFactory.create("Plan", template).activePage
            val localized = TemplateFactory.create("Plan", template, translated).activePage
            assertEquals(english.background, localized.background)
            assertEquals(english.backgroundColor, localized.backgroundColor)
            assertEquals(english.elements.size, localized.elements.size)
            val identities = localized.elements.zip(english.elements).associate { (after, before) -> after.id to before.id }
            english.elements.zip(localized.elements).forEach { (before, after) ->
                val normalized = when (after) {
                    is TextElement -> after.copy(id = before.id, text = (before as TextElement).text)
                    is Note -> after.copy(id = before.id, text = (before as Note).text)
                    is Line -> after.copy(id = before.id)
                    is Connector -> {
                        fun remap(end: ConnectorEnd): ConnectorEnd = when (end) {
                            is ConnectorEnd.Bound -> end.copy(targetId = identities.getValue(end.targetId))
                            is ConnectorEnd.Free -> end
                        }
                        after.copy(id = before.id, start = remap(after.start), end = remap(after.end))
                    }
                    else -> error("Unexpected template object")
                }
                assertEquals(before, normalized)
            }
        }
    }

    @Test fun mindMapTemplateCreatesARealLocalizedTreeWithNewIdsAndEditableBoundEdges() {
        val board = TemplateFactory.create("Plan", BoardTemplate.MIND_MAP, translated)
        val page = board.activePage
        val tree = page.mindMaps.single()
        assertEquals(5, tree.nodes.size)
        assertEquals(4, page.elements.filterIsInstance<Connector>().size)
        assertTrue(page.elements.filterIsInstance<Line>().isEmpty())
        val root = page.elements.first { it.id == tree.rootId } as Note
        assertEquals(labels.getValue(TemplateLabel.CENTRAL_IDEA), root.text)
        assertEquals(
            listOf(TemplateLabel.PEOPLE, TemplateLabel.POSSIBILITIES, TemplateLabel.QUESTIONS, TemplateLabel.ACTIONS).map(labels::getValue),
            tree.childrenOf(root.id).map { node -> (page.elements.first { it.id == node.id } as Note).text }
        )
        tree.validateObjects(page)
        assertEquals(page.elements, page.visibleElements)
        val other = TemplateFactory.create("Plan", BoardTemplate.MIND_MAP, translated).activePage
        assertNotEquals(tree.id, other.mindMaps.single().id)
        assertTrue(page.elements.map { it.id }.toSet().intersect(other.elements.map { it.id }.toSet()).isEmpty())
    }

    @Test fun ordinaryNoteLineTemplatesAreNotInferredAsMindMaps() {
        for (template in BoardTemplate.entries.filter { it != BoardTemplate.MIND_MAP }) {
            assertTrue(TemplateFactory.create("Plan", template, translated).activePage.mindMaps.isEmpty())
        }
    }

    @Test fun capturedContentIsCompleteValidatedAndUnaffectedByCallerMapChanges() {
        val mutable = labels.toMutableMap()
        val captured = TemplateContent(mutable)
        mutable.clear()
        assertEquals(labels.getValue(TemplateLabel.QUESTIONS), captured[TemplateLabel.QUESTIONS])
        assertThrows(IllegalArgumentException::class.java) { TemplateContent(labels - TemplateLabel.DATABASE) }
        assertThrows(IllegalArgumentException::class.java) { TemplateContent(labels + (TemplateLabel.DONE to " ")) }
        assertThrows(IllegalArgumentException::class.java) { TemplateContent(labels + (TemplateLabel.PAGE_ONE to "x".repeat(121))) }
    }
}
