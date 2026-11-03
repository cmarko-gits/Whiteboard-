package dev.whiteboard.app

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.app.ui.localizedTemplateContent
import dev.whiteboard.canvas.PageExporter
import dev.whiteboard.core.model.*
import dev.whiteboard.format.DocumentCodec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemplateLocalizationTest {
    private fun content(tag: String): TemplateContent {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
        return localizedTemplateContent(context.createConfigurationContext(configuration).resources)
    }

    @Test fun englishAndSerbianTemplatesUseActualResourcesAndRoundtripAndExportWithoutRelabeling() {
        val english = content("en")
        TemplateLabel.entries.forEach { assertEquals(it.english, english[it]) }
        val serbian = content("sr-Latn")
        assertEquals("Stranica 1", serbian[TemplateLabel.PAGE_ONE])
        assertEquals("Ključni pojmovi", serbian[TemplateLabel.KEY_CONCEPTS])
        assertEquals("Mogućnosti", serbian[TemplateLabel.POSSIBILITIES])
        assertEquals("Završeno", serbian[TemplateLabel.DONE])
        val codec = DocumentCodec()
        listOf(english, serbian).forEach { content ->
            BoardTemplate.entries.forEach { template ->
                val board = TemplateFactory.create("Moj plan đaka", template, content)
                val decoded = codec.decode(codec.encode(board))
                assertEquals(board, decoded)
                val bitmap = PageExporter().bitmap(decoded.activePage, maxDimension = 512)
                try {
                    assertTrue(bitmap.width in 1..512)
                    assertTrue(bitmap.height in 1..512)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test fun anUnsupportedLanguageFallsBackToTheCompleteEnglishSeedContent() {
        val fallback = content("de")
        TemplateLabel.entries.forEach { assertEquals(it.english, fallback[it]) }
    }
}
