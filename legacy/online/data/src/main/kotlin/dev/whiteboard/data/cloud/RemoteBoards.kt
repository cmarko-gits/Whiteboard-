package dev.whiteboard.data.cloud

import dev.whiteboard.core.cloud.*
import dev.whiteboard.core.model.Board
import dev.whiteboard.format.DocumentCodec
import dev.whiteboard.format.DocumentDeltaCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** Typed document transport. Retry identifiers and revisions come from the durable caller, never from this adapter. */
class RemoteBoards(private val accounts: RemoteAccounts) : BoardGateway {
    private val documents = DocumentCodec()
    private val deltas = DocumentDeltaCodec()

    override suspend fun list(accountId: String, after: String?): CloudBoardPage {
        after?.let(::identifier)
        return request(accountId, "GET", "/v1/boards" + (after?.let { "?after=$it" } ?: "")) { response ->
            val array = response.getJSONArray("items")
            require(array.length() <= 100)
            val items = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                CloudBoardSummary(identifier(item.getString("id")), item.getString("name"), revision(item, "revision"), role(item))
            }
            require(items.zipWithNext().all { (a, b) -> a.id < b.id } && items.all { after == null || it.id > after })
            val next = if (response.isNull("next")) null else identifier(response.getString("next"))
            require(next == null || (items.size == 100 && next == items.last().id))
            CloudBoardPage(items, next)
        }
    }

    override suspend fun create(accountId: String, operationId: String, document: Board): CloudBoardSnapshot {
        identifier(operationId)
        val body = encode { CloudPayloads.creation(operationId, documents.encode(document)) }
        return request(accountId, "POST", "/v1/boards", body, document = true) { response ->
            snapshot(response).also { require(it.role == CloudBoardRole.OWNER) }
        }
    }

    override suspend fun load(accountId: String, boardId: String): CloudBoardSnapshot =
        request(accountId, "GET", path(boardId), document = true) { response ->
            snapshot(response).also { require(it.document.id == boardId) }
        }

    override suspend fun submit(accountId: String, boardId: String, operationId: String, baseRevision: Long, delta: DocumentDelta): Long {
        identifier(operationId)
        require(baseRevision >= 0 && baseRevision < Long.MAX_VALUE && !delta.empty)
        val body = encode {
            CloudPayloads.operation(operationId, baseRevision, deltas.encode(delta))
        }
        return request(accountId, "POST", "${path(boardId)}/operations", body) { response ->
            revision(response, "revision").also { require(it > baseRevision) }
        }
    }

    override suspend fun changes(accountId: String, boardId: String, after: Long): CloudChanges {
        require(after >= 0)
        return request(
            accountId,
            "GET",
            "${path(boardId)}/changes?after=$after",
            document = true
        ) { response ->
            val next = revision(response, "nextRevision")
            val current = revision(response, "currentRevision")
            require(next in after..current)
            val array = response.getJSONArray("operations")
            require(array.length() <= 100)
            var cursor = after
            val operations = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val at = revision(item, "revision")
                require(cursor < Long.MAX_VALUE && at == cursor + 1)
                cursor = at
                CloudOperation(
                    identifier(item.getString("operationId")),
                    at,
                    if (item.isNull("actorId")) null else identifier(item.getString("actorId")),
                    revision(item, "createdAt"),
                    deltas.decode(item.getJSONObject("delta").toString()).also { require(!it.empty) }
                )
            }
            require(cursor == next && (operations.isNotEmpty() || next == current))
            require(operations.map { it.id }.toSet().size == operations.size)
            CloudChanges(operations, next, current)
        }
    }

    override suspend fun delete(accountId: String, boardId: String) {
        accounts.authorized(accountId, "DELETE", path(boardId))
    }

    private fun snapshot(json: JSONObject): CloudBoardSnapshot = CloudBoardSnapshot(
        documents.decode(json.getJSONObject("document").toString()).also { identifier(it.id) },
        revision(json, "revision"),
        role(json)
    )

    private fun path(id: String) = "/v1/boards/${identifier(id)}"
    private fun identifier(value: String): String {
        require(value.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")))
        return value
    }
    private fun role(json: JSONObject) = CloudBoardRole.valueOf(json.getString("role"))
    private fun revision(json: JSONObject, key: String): Long {
        val value = json.get(key)
        require(value is Number && value.toString().matches(Regex("0|[1-9][0-9]{0,18}")))
        return value.toString().toLong()
    }

    private suspend fun encode(action: () -> JSONObject): JSONObject = withContext(Dispatchers.Default) {
        action().also {
            if (CloudPayloads.bytes(it) > CloudPayloads.MAX_REQUEST_BYTES) throw CloudFailure(CloudError.REQUEST, 413)
        }
    }
    private suspend fun <T> request(
        accountId: String,
        method: String,
        path: String,
        body: JSONObject? = null,
        document: Boolean = false,
        read: (JSONObject) -> T
    ): T = accounts.authorized(accountId, method, path, body, if (document) CloudHttp.MAX_DOCUMENT_RESPONSE else 64 * 1024) {
        decode { read(it) }
    }
    private suspend fun <T> decode(action: () -> T): T = withContext(Dispatchers.Default) {
        try {
            action()
        } catch (_: JSONException) {
            throw CloudFailure(CloudError.SERVER)
        } catch (_: IllegalArgumentException) {
            throw CloudFailure(CloudError.SERVER)
        }
    }
}
