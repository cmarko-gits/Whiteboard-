package dev.whiteboard.data.cloud

import org.json.JSONObject

internal object CloudPayloads {
    const val MAX_REQUEST_BYTES = 8 * 1024 * 1024

    fun creation(id: String, document: String): JSONObject = JSONObject().put("operationId", id).put("document", JSONObject(document))

    fun operation(id: String, baseRevision: Long, encodedDelta: String): JSONObject =
        JSONObject().put("operationId", id).put("baseRevision", baseRevision).put("delta", JSONObject(encodedDelta))

    fun bytes(json: JSONObject): Int = json.toString().toByteArray(Charsets.UTF_8).size
}
