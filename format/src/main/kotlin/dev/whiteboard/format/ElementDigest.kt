package dev.whiteboard.format

import dev.whiteboard.core.cloud.ElementFingerprint
import dev.whiteboard.core.model.PageObject
import java.io.DataOutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Version 1: sorted JSON keys, typed length-prefixed values and normalized decimal numbers, independent of map order. */
class ElementDigest(private val codec: DocumentCodec = DocumentCodec()) : ElementFingerprint {
    override fun of(element: PageObject): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DataOutputStream(DigestOutputStream(Sink, digest)).use {
            it.writeInt(1)
            write(JSONObject(codec.encodeElementJson(element)), it)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun write(value: Any?, output: DataOutputStream) {
        when {
            value == null || value === JSONObject.NULL -> output.writeByte(0)
            value is JSONObject -> {
                output.writeByte(1)
                val keys = value.keys().asSequence().toList().sorted()
                output.writeInt(keys.size)
                keys.forEach {
                    text(it, output)
                    write(value.get(it), output)
                }
            }
            value is JSONArray -> {
                output.writeByte(2)
                output.writeInt(value.length())
                for (index in 0 until value.length()) write(value.get(index), output)
            }
            value is String -> {
                output.writeByte(3)
                text(value, output)
            }
            value is Number -> {
                output.writeByte(4)
                text(value.toString().toBigDecimal().stripTrailingZeros().toPlainString(), output)
            }
            value is Boolean -> {
                output.writeByte(5)
                output.writeBoolean(value)
            }
            else -> error("Unsupported canonical value")
        }
    }
    private fun text(value: String, output: DataOutputStream) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        output.writeInt(bytes.size)
        output.write(bytes)
    }
    private object Sink : java.io.OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
    }
}
