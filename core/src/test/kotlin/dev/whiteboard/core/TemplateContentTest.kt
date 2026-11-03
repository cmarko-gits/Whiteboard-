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
            english.elements.zip(localized.elements).forEach { (before, after) ->
                val normalized = when (after) {
                    is TextElement -> after.copy(id = before.id, text = (before as TextElement).text)
                    is Note -> after.copy(id = before.id, text = (before as Note).text)
                    is Line -> after.copy(id = before.id)
                    else -> error("Unexpected template object")
                }
                assertEquals(before, normalized)
            }
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
