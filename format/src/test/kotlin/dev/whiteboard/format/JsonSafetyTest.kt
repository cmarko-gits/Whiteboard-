package dev.whiteboard.format

import dev.whiteboard.core.model.*
import org.junit.Assert.fail
import org.junit.Test

class JsonSafetyTest {
    @Test fun bracesEscapedQuotesAndBackslashesInsideTextDoNotCountAsNesting() {
        val note = Note(bounds = Bounds(0f, 0f, 20f, 20f), text = "[{ \"quoted\" \\ }]] Ć Ћ 🖊\n".repeat(500))
        val board = Board(name = "Escapes", pages = listOf(Page(elements = listOf(note))))
        val codec = DocumentCodec()
        org.junit.Assert.assertEquals(board, codec.decode(codec.encode(board)))
    }

    @Test fun excessiveNestingIsRejectedBeforeRecursiveParsing() {
        val malicious = "{\"a\":".repeat(10_000) + "{}" + "}".repeat(10_000)
        rejected { DocumentCodec().decode(malicious) }
        rejected { DocumentCodec().decodeElementJson(malicious) }
        rejected { DocumentDeltaCodec().decode(malicious) }
    }

    @Test fun incompleteStringsUnescapedControlsAndUnbalancedContainersAreRejected() {
        listOf("{\"text\":\"unfinished", "{\"text\":\"new\nline\"}", "}{}", "{[]").forEach { value -> rejected { JsonSafety.check(value) } }
    }

    @Test fun permissiveParserSyntaxCannotHideContainersFromDepthCheck() {
        listOf("{'a':'\"','deep':[]}", "{/* \" */ \"deep\": []}", "{a:1}", "{\"a\":1,}").forEach { value ->
            rejected { JsonSafety.check(value) }
        }
    }

    @Test fun hugeNumericLiteralsAndExponentsAreBoundedBeforeBigDecimalParsing() {
        rejected { JsonSafety.check("{\"version\":" + "9".repeat(10_000) + "}") }
        rejected { JsonSafety.check("{\"version\":1e999999999}") }
        JsonSafety.check("{\"a\":[-0, 1.25e+30, 1e-30, true, false, null]}")
    }

    private fun rejected(action: () -> Unit) {
        try {
            action()
            fail("Expected bounded JSON rejection")
        } catch (_: IllegalArgumentException) { }
    }
}
