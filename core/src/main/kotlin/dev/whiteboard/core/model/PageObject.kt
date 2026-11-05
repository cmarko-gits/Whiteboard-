package dev.whiteboard.core.model

/**
 * Identity shared by local elements and page-dependent connections. Only Element has standalone
 * bounds/transform operations; connection geometry must be resolved against its owning page.
 */
sealed interface PageObject {
    val id: String
}
