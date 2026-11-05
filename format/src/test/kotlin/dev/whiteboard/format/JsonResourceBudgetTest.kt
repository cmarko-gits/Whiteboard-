package dev.whiteboard.format

import dev.whiteboard.core.cloud.DocumentDelta
import dev.whiteboard.core.cloud.ObjectMutation
import dev.whiteboard.core.cloud.PageMutation
import dev.whiteboard.core.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JsonResourceBudgetTest {
    private fun limited(action: () -> Unit) {
        val failure = assertThrows(DocumentFailure::class.java, action)
        assertEquals(DocumentError.DOCUMENT_LIMIT, failure.error)
    }

    @Test fun theOriginalMillionObjectPayloadIsRejectedInABoundedChildJvm() {
        val runtime =
            File(System.getProperty("java.home"), "bin/${if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"}")
        val classpath = listOf(
            JsonAllocationBudgetProbe::class.java,
            DocumentCodec::class.java,
            DocumentFailure::class.java,
            JSONObject::class.java,
            Unit::class.java
        ).map { File(it.protectionDomain.codeSource.location.toURI()).absolutePath }.distinct().joinToString(File.pathSeparator)
        val child = ProcessBuilder(runtime.absolutePath, "-Xms16m", "-Xmx64m", "-cp", classpath, JsonAllocationBudgetProbe::class.java.name)
            .redirectErrorStream(true).start()
        try {
            assertTrue("Allocation probe did not finish within its process deadline", child.waitFor(30, TimeUnit.SECONDS))
            val output = child.inputStream.bufferedReader().use { it.readText() }
            assertEquals(output, 0, child.exitValue())
            assertTrue(output, output.contains("DOCUMENT_LIMIT before full JSON parsing"))
        } finally {
            if (child.isAlive) child.destroyForcibly()
            child.inputStream.close()
        }
    }

    @Test fun wideUntrustedContainersAreRejectedBeforeMissingSchemaFieldsAreRead() {
        val source = "{\"version\":5,\"padding\":[" + List(500) { "{}" }.joinToString(",") + "]}"
        val codec = DocumentCodec(allocationBudget = 8192)
        limited { codec.decode(source) }
        limited { codec.decodePageJson(source) }
        limited { codec.decodeElementJson(source) }
        limited { DocumentDeltaCodec(allocationBudget = 8192).decode(source) }
    }

    @Test fun editableArchiveReadersPreserveTheDocumentLimitFailure() {
        val source = "{\"version\":5,\"padding\":[" + List(500) { "{}" }.joinToString(",") + "]}"
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { archive ->
            archive.putNextEntry(ZipEntry("whiteboard-version.txt"))
            archive.write("2".toByteArray())
            archive.closeEntry()
            archive.putNextEntry(ZipEntry("boards/0.json"))
            archive.write(source.toByteArray())
            archive.closeEntry()
        }
        limited { BoardArchive(DocumentCodec(8192)).readContents(ByteArrayInputStream(output.toByteArray())) }
    }

    @Test fun sourceStringsAndNumericRepresentationsConsumeTheBudgetBeforePlatformParsing() {
        limited { JsonSafety.check(" ".repeat(5000) + "null", 32, 8192) }
        limited { JsonSafety.check("{\"s\":\"" + "x".repeat(100) + "\"}", 32, 500) }
        limited { JsonSafety.check("{\"n\":" + "1".repeat(100) + "}", 32, 500) }
        JsonSafety.check("{\"n\":null}", 32, 500)
    }

    @Test fun exactRemainingBudgetIsAcceptedAndLargeConfiguredBudgetsCannotOverflow() {
        JsonSafety.check("null", 32, 40)
        limited { JsonSafety.check("null", 32, 39) }
        JsonSafety.check("{\"a\":[1,2,3]}", 32, Long.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java) { DocumentCodec(0) }
        assertThrows(IllegalArgumentException::class.java) { DocumentDeltaCodec(allocationBudget = -1) }
    }

    @Test fun encodersDoNotReturnADocumentTheirOwnReadersWouldReject() {
        val note = Note(bounds = Bounds(0f, 0f, 200f, 200f), text = "x".repeat(16_000))
        val page = Page(elements = listOf(note))
        val board = Board(name = "Budget parity", pages = listOf(page))
        val standard = DocumentCodec()
        val bounded = DocumentCodec(16_384)
        limited { bounded.encode(board) }
        limited { bounded.decode(standard.encode(board)) }
        limited { bounded.encodePageJson(page) }
        limited { bounded.decodePageJson(standard.encodePageJson(page)) }
        limited { bounded.encodeElementJson(note) }
        limited { bounded.decodeElementJson(standard.encodeElementJson(note)) }
        val small = board.withPage(page.copy(elements = listOf(note.copy(text = "Small"))))
        assertEquals(small, bounded.decode(bounded.encode(small)))
    }

    @Test fun deltaEnvelopesApplyTheSameBudgetWhenWritingAndReading() {
        val note = Note(bounds = Bounds(0f, 0f, 200f, 200f), text = "x".repeat(16_000))
        val delta = DocumentDelta(pages = listOf(PageMutation(id = "page", objects = listOf(ObjectMutation(note.id, null, note)))))
        val normal = DocumentDeltaCodec()
        val bounded = DocumentDeltaCodec(allocationBudget = 16_384)
        limited { bounded.encode(delta) }
        limited { bounded.decode(normal.encode(delta)) }
        assertEquals(delta, normal.decode(normal.encode(delta)))
    }

    @Test fun theExistingTenThousandNodeGraphStillRoundTripsWithTheProductionBudget() {
        val nodes = List(10_000) { index ->
            val x = (index % 100) * 160f
            val y = (index / 100) * 140f
            Shape(id = "node-$index", bounds = Bounds(x, y, x + 100f, y + 80f), kind = Tool.RECTANGLE, style = InkStyle())
        }
        val edges = List(nodes.size - 1) { index ->
            Connector(
                id = "edge-$index",
                start = ConnectorEnd.Bound(nodes[index].id, ConnectionPort.RIGHT),
                end = ConnectorEnd.Bound(nodes[index + 1].id, ConnectionPort.LEFT)
            )
        }
        val board = Board(name = "Large graph", pages = listOf(Page(elements = nodes + edges)))
        val codec = DocumentCodec()
        val restored = codec.decode(codec.encode(board))
        assertEquals(board, restored)
        assertEquals(board.activePage.geometry.resolve(edges.last()), restored.activePage.geometry.resolve(edges.last()))
    }
}
