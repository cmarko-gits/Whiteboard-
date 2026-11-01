package dev.whiteboard.data.cloud

import com.sun.net.httpserver.HttpServer
import dev.whiteboard.core.cloud.*
import dev.whiteboard.core.model.*
import dev.whiteboard.format.DocumentCodec
import dev.whiteboard.format.DocumentDeltaCodec
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RemoteBoardsTest {
    private val accountId = "11111111-1111-1111-1111-111111111111"
    private val boardId = "22222222-2222-2222-2222-222222222222"
    private val operationId = "33333333-3333-3333-3333-333333333333"

    @Test fun documentResponsesUseLargerBoundAndKeepUnicode(): Unit = runBlocking {
        fixture { remote, reply ->
            val document = Board(
                id = boardId,
                name = "Large Unicode board",
                pages = listOf(Page(elements = listOf(Note(bounds = Bounds(0f, 0f, 300f, 300f), text = "Ћирилица 🖊".repeat(10_000)))))
            )
            reply.source = JSONObject().put("document", JSONObject(DocumentCodec().encode(document))).put("revision", 4)
                .put("role", "VIEWER").toString()
            val loaded = remote.load(accountId, boardId)
            assertEquals(4L, loaded.revision)
            assertEquals(CloudBoardRole.VIEWER, loaded.role)
            assertEquals(document.activePage.elements, loaded.document.activePage.elements)
        }
    }

    @Test fun replayRejectsGapsFractionsAndNonAdvancingCursor(): Unit = runBlocking {
        fixture { remote, reply ->
            val delta = DocumentDelta(name = ValueChange("Before", "After"))
            fun page(revision: Number, next: Long, current: Long) = JSONObject()
                .put("nextRevision", next).put("currentRevision", current).put(
                    "operations",
                    JSONArray().put(
                        JSONObject().put("operationId", operationId).put("revision", revision).put("actorId", JSONObject.NULL)
                            .put("createdAt", 123).put("delta", JSONObject(DocumentDeltaCodec().encode(delta)))
                    )
                ).toString()
            for (source in listOf(page(2, 2, 2), page(1.5, 1, 1), page(1, 0, 1))) {
                reply.source = source
                cloudFailure(CloudError.SERVER) { remote.changes(accountId, boardId, 0) }
            }
            reply.source = page(1, 1, 2)
            val valid = remote.changes(accountId, boardId, 0)
            assertEquals(1L, valid.nextRevision)
            assertEquals(2L, valid.currentRevision)
            assertNull(valid.operations.single().actorId)
            assertEquals(delta, valid.operations.single().delta)
        }
    }

    @Test fun snapshotIdentityAndListingCursorAreValidated(): Unit = runBlocking {
        fixture { remote, reply ->
            reply.source = JSONObject().put("document", JSONObject(DocumentCodec().encode(Board(name = "Other board"))))
                .put("revision", 0).put("role", "OWNER").toString()
            cloudFailure(CloudError.SERVER) { remote.load(accountId, boardId) }
            reply.source = """{"items":[],"next":"$boardId"}"""
            cloudFailure(CloudError.SERVER) { remote.list(accountId) }
            reply.source = """{"items":[{"id":"$boardId","name":"Board","revision":0,"role":"ADMIN"}],"next":null}"""
            cloudFailure(CloudError.SERVER) { remote.list(accountId) }
        }
    }

    @Test fun conflictKeepsStableCodeAndDoesNotRevokeIdentity(): Unit = runBlocking {
        fixture { remote, reply ->
            reply.status = 409
            reply.source = """{"error":"conflict_object_modified"}"""
            try {
                remote.submit(accountId, boardId, operationId, 0, DocumentDelta(name = ValueChange("a", "b")))
                fail("Expected conflict")
            } catch (error: CloudFailure) {
                assertEquals(409, error.status)
                assertEquals("conflict_object_modified", error.code)
            }
            reply.status = 200
            reply.source = """{"items":[],"next":null}"""
            assertTrue(remote.list(accountId).items.isEmpty())
        }
    }

    private class Reply(var status: Int = 200, var source: String = "{}")

    private suspend fun fixture(action: suspend (RemoteBoards, Reply) -> Unit) {
        val reply = Reply()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/") { exchange ->
            assertEquals("Bearer access", exchange.requestHeaders.getFirst("Authorization"))
            exchange.requestBody.use { it.readBytes() }
            val account = exchange.requestURI.path == "/v1/account"
            val bytes = (if (account) """{"id":"$accountId","email":"test@example.com"}""" else reply.source).toByteArray()
            exchange.sendResponseHeaders(if (account) 200 else reply.status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val vault = object : SessionStorage {
                private var value: CloudSession? =
                    CloudSession(CloudAccount(accountId, "test@example.com"), "access", "refresh", Long.MAX_VALUE)
                override fun load() = value
                override fun save(session: CloudSession) {
                    value = session
                }
                override fun clear() {
                    value = null
                }
            }
            val accounts = RemoteAccounts(CloudHttp("http://127.0.0.1:${server.address.port}", true), vault)
            accounts.restore()
            action(RemoteBoards(accounts), reply)
        } finally {
            server.stop(0)
        }
    }
}
