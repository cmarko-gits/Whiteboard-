package dev.whiteboard.core.model

class UnsupportedDocumentVersion(val version: Int) : IllegalArgumentException("Unsupported document version: $version")
