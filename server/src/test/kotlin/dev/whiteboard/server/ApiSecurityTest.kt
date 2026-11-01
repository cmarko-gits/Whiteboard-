package dev.whiteboard.server

import dev.whiteboard.core.model.Board
import dev.whiteboard.format.DocumentCodec
import dev.whiteboard.server.http.whiteboardApi
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ApiSecurityTest {
    @Test fun revisionCoercionCannotCommitOrMutateDocuments(): Unit = ServerFixture().use { fixture ->
        val owner = fixture.account("owner@example.com")
        val created = fixture.boards.create(owner.accessToken, Board(name = "Original"), UUID.randomUUID().toString())
        testApplication {
            application { whiteboardApi(fixture.auth, fixture.boards) }
            for (value in listOf("\"0\"", "0.5", "0.0", "-1", "9223372036854775808", "true", "null")) {
                val response = client.put("/v1/boards/${created.document.id}") {
                    bearerAuth(owner.accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(
                        "{\"operationId\":\"${UUID.randomUUID()}\",\"expectedRevision\":$value,\"document\":" +
                            DocumentCodec().encode(created.document.copy(name = "Must not change")) + "}"
                    )
                }
                assertEquals(value, HttpStatusCode.BadRequest, response.status)
                assertEquals("invalid_request", JSONObject(response.bodyAsText()).getString("error"))
            }
            assertEquals("Original", fixture.boards.get(owner.accessToken, created.document.id).document.name)
            assertEquals(0L, fixture.boards.get(owner.accessToken, created.document.id).revision)
        }
    }

    @Test fun unknownIdentityFieldsAndDuplicateKeysAreRejectedWithoutSendingMail(): Unit = ServerFixture().use { fixture ->
        testApplication {
            application { whiteboardApi(fixture.auth, fixture.boards) }
            for (body in listOf(
                """{"email":"user@example.com","password":"test-only strong passphrase","role":"OWNER"}""",
                """{"email":"first@example.com","email":"second@example.com","password":"test-only strong passphrase"}"""
            )) {
                val response = client.post("/v1/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
            assertTrue(fixture.mail.items.isEmpty())
        }
    }

    @Test fun securityHeadersCoverFailuresAndUnknownRoutes(): Unit = ServerFixture().use { fixture ->
        testApplication {
            application { whiteboardApi(fixture.auth, fixture.boards) }
            for (path in listOf("/health", "/v1/account", "/does-not-exist")) {
                val response = client.get(path)
                assertEquals(path, "no-store", response.headers[HttpHeaders.CacheControl])
                assertEquals("nosniff", response.headers["X-Content-Type-Options"])
                assertEquals("DENY", response.headers["X-Frame-Options"])
            }
        }
    }

    @Test fun ambiguousAuthorizationAndCrossAccountAccessAreDenied(): Unit = ServerFixture().use { fixture ->
        val owner = fixture.account("owner@example.com")
        val outsider = fixture.account("outsider@example.com")
        val board = fixture.boards.create(owner.accessToken, Board(name = "Private"), UUID.randomUUID().toString())
        testApplication {
            application { whiteboardApi(fixture.auth, fixture.boards) }
            val ambiguous = client.get("/v1/account") {
                header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
                header(HttpHeaders.Authorization, "Bearer ${outsider.accessToken}")
            }
            assertEquals(HttpStatusCode.Unauthorized, ambiguous.status)
            for (method in listOf(HttpMethod.Get, HttpMethod.Delete)) {
                val response = client.request("/v1/boards/${board.document.id}") {
                    this.method = method
                    bearerAuth(outsider.accessToken)
                }
                assertTrue(response.status in listOf(HttpStatusCode.Forbidden, HttpStatusCode.NotFound))
                assertFalse(response.bodyAsText().contains("Private"))
            }
            assertEquals("Private", fixture.boards.get(owner.accessToken, board.document.id).document.name)
        }
    }
}
