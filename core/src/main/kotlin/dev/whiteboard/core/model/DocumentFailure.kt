package dev.whiteboard.core.model

enum class DocumentError { STORAGE_FULL, STORAGE_UNAVAILABLE, PERMISSION_DENIED, INVALID_DOCUMENT }

/** Stable domain metadata. Exception messages and document contents never become UI/log text. */
class DocumentFailure(val error: DocumentError, cause: Throwable? = null) : Exception(error.name, cause)
