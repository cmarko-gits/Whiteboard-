package dev.whiteboard.core

import dev.whiteboard.core.model.AppLanguage
import org.junit.Assert.*
import org.junit.Test

class AppLanguageTest {
    @Test fun supportedRegionalAndOrderedLocaleListsReflectTheirFirstLanguage() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(""))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags("en-GB"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags("EN-us,sr-Latn"))
        assertEquals(AppLanguage.SERBIAN_LATIN, AppLanguage.fromTags("sr-Latn-RS,en"))
        assertEquals(AppLanguage.SERBIAN_LATIN, AppLanguage.fromTags("sr-latn"))
    }

    @Test fun anUnsupportedExternalChoiceIsNeverMisrepresentedAsASupportedOrSystemChoice() {
        listOf("ar", "de,en", "sr-Cyrl", "enough", "sr-Latnish", "not a tag").forEach { assertNull(AppLanguage.fromTags(it)) }
    }

    @Test fun eachSelectableChoiceCanBeReadBackWithoutChangingItsMeaning() {
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.fromTags(it.tag)) }
    }
}
