package dev.whiteboard.core.model

data class Folder(val id: String = newId(), val name: String, val parentId: String? = null) {
    init {
        require(name.isNotBlank() && name.length <= 120)
        require(id != parentId)
    }
}

class FolderTree(folders: List<Folder>) {
    private val byId = folders.associateBy { it.id }
    fun path(id: String?): String {
        val names = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        var current = id
        while (current != null && visited.add(current)) {
            val folder = byId[current] ?: break
            names.add(folder.name)
            current = folder.parentId
        }
        return names.asReversed().joinToString(" / ")
    }
    fun canMove(id: String, parent: String?): Boolean {
        if (id !in byId || parent != null && parent !in byId) return false
        val visited = mutableSetOf<String>()
        var current = parent
        while (current != null) {
            if (current == id || !visited.add(current)) return false
            current = byId[current]?.parentId
        }
        return true
    }
}

fun Board.searchableText(): String = buildString {
    append(name)
    pages.forEach { page ->
        append('\n')
        append(page.name)
        page.elements.flatMap { it.leaves() }.forEach { element ->
            when (element) {
                is TextElement -> {
                    append('\n')
                    append(element.text)
                }
                is Note -> {
                    append('\n')
                    append(element.text)
                }
                else -> Unit
            }
        }
    }
}
