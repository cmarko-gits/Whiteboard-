package dev.whiteboard.format

import dev.whiteboard.core.model.DocumentError
import dev.whiteboard.core.model.DocumentFailure
import kotlin.system.exitProcess

/** Runs only in the separate 64 MiB regression JVM, never in the Android app. */
object JsonAllocationBudgetProbe {
    @JvmStatic fun main(args: Array<String>) {
        val source = buildString(3_000_025) {
            append("{\"version\":5,\"padding\":[")
            repeat(1_000_000) { index ->
                if (index != 0) append(',')
                append("{}")
            }
            append("]}")
        }
        try {
            DocumentCodec().decode(source)
            error("Expected a document limit")
        } catch (failure: DocumentFailure) {
            check(failure.error == DocumentError.DOCUMENT_LIMIT)
            println("DOCUMENT_LIMIT before full JSON parsing")
        } catch (_: OutOfMemoryError) {
            println("OutOfMemoryError")
            exitProcess(3)
        }
    }
}
