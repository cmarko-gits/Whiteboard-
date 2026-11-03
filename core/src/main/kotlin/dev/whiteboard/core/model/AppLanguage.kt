package dev.whiteboard.core.model

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    SERBIAN_LATIN("sr-Latn");

    companion object {
        /** Unknown external locale settings must not be displayed as a selected supported language. */
        fun fromTags(tags: String): AppLanguage? {
            val first = tags.substringBefore(',')
            return when {
                first.isEmpty() -> SYSTEM
                first.equals("en", ignoreCase = true) || first.startsWith("en-", ignoreCase = true) -> ENGLISH
                first.equals("sr-Latn", ignoreCase = true) || first.startsWith("sr-Latn-", ignoreCase = true) -> SERBIAN_LATIN
                else -> null
            }
        }
    }
}
