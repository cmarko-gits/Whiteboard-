package dev.whiteboard.format

import dev.whiteboard.core.model.Board
import dev.whiteboard.core.model.MediaElement
import dev.whiteboard.core.model.referencedAssets
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** An editable backup. Entries are parsed in memory; archive paths are never extracted to disk. */
data class ArchiveContents(val boards: List<Board>, val assets: Map<String, ByteArray>)

class BoardArchive(private val codec: DocumentCodec = DocumentCodec()) {
    fun write(boards: List<Board>, output: OutputStream, assetSource: ((String) -> InputStream?)? = null) {
        require(boards.size <= MAX_BOARDS)
        var total = 1L
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("whiteboard-version.txt"))
            zip.write("2".toByteArray())
            zip.closeEntry()
            boards.forEachIndexed { index, board ->
                val bytes = codec.encode(board).toByteArray(Charsets.UTF_8)
                require(bytes.size <= MAX_ENTRY_BYTES) { "Board is too large for this backup format" }
                total += bytes.size
                require(total <= MAX_TOTAL_BYTES) { "Backup is too large" }
                zip.putNextEntry(ZipEntry("boards/$index.json"))
                zip.write(bytes)
                zip.closeEntry()
            }
            val assets = boards.flatMap { it.referencedAssets() }.toSet()
            require(assets.size <= 5000)
            assets.sorted().forEach { id ->
                val input = requireNotNull(assetSource?.invoke(id)) { "Missing backup attachment" }
                zip.putNextEntry(ZipEntry("assets/$id"))
                input.use {
                    var size = 0
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = it.read(buffer)
                        if (count < 0) break
                        size += count
                        total += count
                        require(size <= MAX_ENTRY_BYTES && total <= MAX_TOTAL_BYTES) { "Backup is too large" }
                        zip.write(buffer, 0, count)
                    }
                }
                zip.closeEntry()
            }
        }
    }
    fun read(input: InputStream): List<Board> = readContents(input).boards
    fun readContents(input: InputStream): ArchiveContents {
        val boards = mutableListOf<Board>()
        val assets = mutableMapOf<String, ByteArray>()
        val names = mutableSetOf<String>()
        var version: String? = null
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(names.add(entry.name)) { "Duplicate archive entry" }
                require(names.size <= MAX_BOARDS + 5001) { "Too many archive entries" }
                val isAsset = entry.name.startsWith("assets/") && MediaElement.ASSET_NAME.matches(entry.name.removePrefix("assets/"))
                require(entry.name == "whiteboard-version.txt" || entry.name.matches(Regex("boards/[0-9]+\\.json")) || isAsset) {
                    "Unsupported archive entry"
                }
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val count = zip.read(chunk)
                    if (count < 0) break
                    require(buffer.size() + count <= MAX_ENTRY_BYTES) { "Archive entry is too large" }
                    buffer.write(chunk, 0, count)
                }
                val bytes = buffer.toByteArray()
                total += bytes.size
                require(total <= MAX_TOTAL_BYTES) { "Archive is too large" }
                if (isAsset) {
                    assets[entry.name.removePrefix("assets/")] = bytes
                } else if (entry.name == "whiteboard-version.txt") {
                    version = bytes.toString(Charsets.UTF_8)
                } else {
                    require(boards.size < MAX_BOARDS)
                    boards.add(codec.decode(bytes.toString(Charsets.UTF_8)))
                }
                zip.closeEntry()
            }
        }
        require(version in setOf("1", "2")) { "Unsupported or missing backup version" }
        require(boards.map { it.id }.distinct().size == boards.size) { "Duplicate boards in backup" }
        require(boards.flatMap { it.referencedAssets() }.toSet() == assets.keys) { "Missing or unreferenced backup attachments" }
        return ArchiveContents(boards, assets)
    }
    companion object {
        const val MAX_BOARDS = 1000
        const val MAX_ENTRY_BYTES =
            16 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
    }
}
