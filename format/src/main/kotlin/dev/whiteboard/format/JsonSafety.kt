package dev.whiteboard.format

import dev.whiteboard.core.model.DocumentError
import dev.whiteboard.core.model.DocumentFailure

/** Strict, bounded preflight before recursive platform parsing. No JSON objects or numeric big integers are allocated here. */
object JsonSafety {
    // Accounting policy for temporary input/tree storage, not an exact platform heap prediction.
    const val DEFAULT_ALLOCATION_BUDGET = 80L * 1024 * 1024

    fun defaultAllocationBudget(): Long = allocationBudgetForHeap(Runtime.getRuntime().maxMemory())

    internal fun allocationBudgetForHeap(maximumHeapBytes: Long): Long {
        require(maximumHeapBytes > 0)
        return minOf(DEFAULT_ALLOCATION_BUDGET, (maximumHeapBytes / 2).coerceAtLeast(1))
    }

    fun check(source: String, maximumDepth: Int = 32) {
        check(source, maximumDepth, defaultAllocationBudget())
    }

    internal fun check(source: String, maximumDepth: Int, allocationBudget: Long) {
        require(maximumDepth in 1..64)
        require(allocationBudget > 0)
        Parser(source, maximumDepth, allocationBudget).check()
    }

    private class Parser(private val source: String, private val maximumDepth: Int, private var remainingBytes: Long) {
        private var position = 0
        private fun charge(bytes: Long) {
            // Subtraction after the comparison avoids overflowing an accumulated estimate.
            if (bytes > remainingBytes) throw DocumentFailure(DocumentError.DOCUMENT_LIMIT)
            remainingBytes -= bytes
        }
        fun check() {
            charge(source.length.toLong() * 2)
            value(0)
            whitespace()
            require(position == source.length) { "Trailing JSON content" }
        }
        private fun value(depth: Int) {
            charge(32)
            whitespace()
            require(position < source.length) { "Incomplete JSON" }
            when (source[position]) {
                '{' -> {
                    charge(96)
                    require(depth < maximumDepth) { "JSON nesting limit exceeded" }
                    position++
                    whitespace()
                    if (take('}')) return
                    while (true) {
                        charge(64) // Object entry, excluding its separately charged key/value.
                        string()
                        whitespace()
                        require(take(':')) { "Missing JSON colon" }
                        value(depth + 1)
                        whitespace()
                        if (take('}')) return
                        require(take(',')) { "Missing JSON comma" }
                        whitespace()
                    }
                }
                '[' -> {
                    charge(64)
                    require(depth < maximumDepth) { "JSON nesting limit exceeded" }
                    position++
                    whitespace()
                    if (take(']')) return
                    while (true) {
                        value(depth + 1)
                        whitespace()
                        if (take(']')) return
                        require(take(',')) { "Missing JSON comma" }
                    }
                }
                '"' -> string()
                't' -> literal("true")
                'f' -> literal("false")
                'n' -> literal("null")
                '-', in '0'..'9' -> number()
                else -> throw IllegalArgumentException("Invalid JSON token")
            }
        }
        private fun string() {
            require(take('"')) { "JSON strings require double quotes" }
            charge(48)
            while (position < source.length) {
                when (val character = source[position++]) {
                    '"' -> return
                    '\\' -> {
                        require(position < source.length)
                        when (source[position++]) {
                            '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                            'u' -> repeat(4) {
                                require(position < source.length && source[position++] in "0123456789abcdefABCDEF") {
                                    "Invalid JSON escape"
                                }
                            }
                            else -> throw IllegalArgumentException("Invalid JSON escape")
                        }
                        charge(2)
                    }
                    else -> {
                        require(character.code >= 0x20) { "Unescaped JSON control character" }
                        charge(2)
                    }
                }
            }
            throw IllegalArgumentException("Incomplete JSON string")
        }
        private fun number() {
            val start = position
            take('-')
            if (!take('0')) {
                require(position < source.length && source[position] in '1'..'9') { "Invalid JSON number" }
                digits()
            }
            if (take('.')) digits()
            if (take('e') || take('E')) {
                if (!take('+')) take('-')
                val exponentStart = position
                digits()
                var exponent = 0
                for (index in exponentStart until position) {
                    exponent = exponent * 10 + (source[index] - '0')
                    require(exponent <= 400) { "JSON exponent limit exceeded" }
                }
            }
            require(position - start <= 128) { "JSON numeric literal is too large" }
            charge(48L + (position - start) * 2L)
        }
        private fun digits() {
            val start = position
            while (position < source.length && source[position] in '0'..'9') position++
            require(position > start) { "Missing JSON digits" }
        }
        private fun literal(value: String) {
            require(source.regionMatches(position, value, 0, value.length)) { "Invalid JSON literal" }
            position += value.length
        }
        private fun whitespace() {
            while (position < source.length && source[position] in " \r\n\t") position++
        }
        private fun take(character: Char): Boolean {
            if (position >= source.length || source[position] != character) return false
            position++
            return true
        }
    }
}
