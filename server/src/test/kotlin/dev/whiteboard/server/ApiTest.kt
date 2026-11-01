package dev.whiteboard.server

import dev.whiteboard.core.cloud.DeltaEngine
import dev.whiteboard.core.model.Board
import dev.whiteboard.format.DocumentCodec
import dev.whiteboard.format.DocumentDeltaCodec
import dev.whiteboard.format.ElementDigest
import dev.whiteboard.server.http.whiteboardApi
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ApiTest {
    @Test fun operationHttpRoundTripAndResumeAreIdempotent(): Unit = ServerFixture().use { f ->
        val account = f.account("alice@example.com")
        val created = f.boards.create(account.accessToken, Board(name = "Initial"))
        val delta = DeltaEngine(ElementDigest()).between(created.document, created.document.copy(name = "From network"))
        val operationId = java.util.UUID.randomUUID().toString()
        val request = JSONObject().put("operationId", operationId).put("baseRevision", 0)
            .put("delta", JSONObject(DocumentDeltaCodec().encode(delta))).toString()
        testApplication {
            application { whiteboardApi(f.auth, f.boards) }
            repeat(2) {
                val response = client.post("/v1/boards/${created.document.id}/operations") {
                    bearerAuth(account.accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(1L, JSONObject(response.bodyAsText()).getLong("revision"))
            }
            val changes = client.get("/v1/boards/${created.document.id}/changes?after=0") { bearerAuth(account.accessToken) }
            assertEquals(HttpStatusCode.OK, changes.status)
            val page = JSONObject(changes.bodyAsText())
            assertEquals(1, page.getJSONArray("operations").length())
            assertEquals(operationId, page.getJSONArray("operations").getJSONObject(0).getString("operationId"))
            assertEquals("From network", f.boards.get(account.accessToken, created.document.id).document.name)
        }
    }

    @Test fun httpRegistrationVerificationSessionAndBoardRoundTrip(): Unit = ServerFixture().use { f ->
        testApplication {
            application { whiteboardApi(f.auth, f.boards) }
            val registration = client.post("/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(JSONObject().put("email", "alice@example.com").put("password", ServerFixture.PASSWORD).toString())
            }
            assertEquals(HttpStatusCode.Accepted, registration.status)
            assertFalse(registration.bodyAsText().contains(f.mail.code("alice@example.com")))
            val verification = client.post("/v1/auth/verify") {
                contentType(ContentType.Application.Json)
                setBody(JSONObject().put("code", f.mail.code("alice@example.com")).toString())
            }
            assertEquals(HttpStatusCode.Accepted, verification.status)
            val login = client.post("/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(JSONObject().put("email", "alice@example.com").put("password", ServerFixture.PASSWORD).toString())
            }
            assertEquals(HttpStatusCode.OK, login.status)
            assertEquals("no-store", login.headers[HttpHeaders.CacheControl])
            val access = JSONObject(login.bodyAsText()).getString("accessToken")
            val created = client.post("/v1/boards") {
                bearerAuth(access)
                contentType(ContentType.Application.Json)
                setBody(
                    JSONObject().put("operationId", java.util.UUID.randomUUID().toString())
                        .put("document", JSONObject(DocumentCodec().encode(Board(name = "HTTP board")))).toString()
                )
            }
            assertEquals(HttpStatusCode.Created, created.status)
            val document = JSONObject(created.bodyAsText()).getJSONObject("document")
            val id = document.getString("id")
            val loaded = client.get("/v1/boards/$id") { bearerAuth(access) }
            assertEquals("HTTP board", JSONObject(loaded.bodyAsText()).getJSONObject("document").getString("name"))
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/boards/$id").status)
            val logout = client.post("/v1/auth/logout") { bearerAuth(access) }
            assertEquals(HttpStatusCode.Accepted, logout.status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/account") { bearerAuth(access) }.status)
        }
    }

    @Test fun invalidJsonOversizedUnknownLengthAndDeepNestingAreRejected(): Unit = ServerFixture().use { f ->
        testApplication {
            application { whiteboardApi(f.auth, f.boards) }
            val wrongType = client.post("/v1/auth/register") {
                setBody("{}")
                contentType(ContentType.Text.Plain)
            }
            assertEquals(HttpStatusCode.UnsupportedMediaType, wrongType.status)
            val invalid = client.post("/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("{broken")
            }
            assertEquals(HttpStatusCode.BadRequest, invalid.status)
            assertEquals("invalid_request", JSONObject(invalid.bodyAsText()).getString("error"))
            val tooLarge = client.post("/v1/auth/register") {
                setBody(object : OutgoingContent.WriteChannelContent() {
                    override val contentType = ContentType.Application.Json
                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        channel.writeFully(ByteArray(6000) { 'a'.code.toByte() })
                    }
                })
            }
            assertEquals(HttpStatusCode.PayloadTooLarge, tooLarge.status)
            val nested = client.post("/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("{\"a\":".repeat(100) + "{}" + "}".repeat(100))
            }
            assertEquals(HttpStatusCode.BadRequest, nested.status)
            val compressed = client.post("/v1/auth/register") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.ContentEncoding, "gzip")
                setBody("{}")
            }
            assertEquals(HttpStatusCode.UnsupportedMediaType, compressed.status)
            val hugeNumber = client.post("/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(
                    "{\"email\":\"malformed@example.com\",\"password\":\"disposable test passphrase\",\"huge\":" + "9".repeat(1000) + "}"
                )
            }
            assertEquals(HttpStatusCode.BadRequest, hugeNumber.status)
            assertTrue(f.mail.items.isEmpty())
        }
    }
}
