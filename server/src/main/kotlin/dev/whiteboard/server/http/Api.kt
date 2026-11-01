package dev.whiteboard.server.http

import dev.whiteboard.format.DocumentCodec
import dev.whiteboard.format.DocumentDeltaCodec
import dev.whiteboard.format.JsonSafety
import dev.whiteboard.server.ServiceFailure
import dev.whiteboard.server.auth.AuthService
import dev.whiteboard.server.auth.SessionTokens
import dev.whiteboard.server.boards.BoardOperations
import dev.whiteboard.server.boards.BoardRole
import dev.whiteboard.server.boards.BoardService
import dev.whiteboard.server.boards.CloudBoard
import dev.whiteboard.server.invalidRequest
import dev.whiteboard.server.unauthenticated
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.sql.SQLException
import java.util.concurrent.Semaphore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONParserConfiguration

fun Application.whiteboardApi(auth: AuthService, boards: BoardService, limits: RateLimiter = RateLimiter()) {
    install(
        createApplicationPlugin("ApiSecurityHeaders") {
            onCall { call ->
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.response.header("X-Content-Type-Options", "nosniff")
                call.response.header("X-Frame-Options", "DENY")
            }
        }
    )
    val requests = Semaphore(32)
    val documentRequests = Semaphore(2)
    val logger = log
    val operations = BoardOperations(auth)
    install(StatusPages) {
        exception<ServiceFailure> { call, error ->
            if (error.status == 429) call.response.header(HttpHeaders.RetryAfter, "60")
            call.json(JSONObject().put("error", error.code), error.status)
        }
        exception<JSONException> { call, _ -> call.json(JSONObject().put("error", "invalid_request"), 400) }
        exception<IllegalArgumentException> { call, _ -> call.json(JSONObject().put("error", "invalid_request"), 400) }
        exception<SQLException> { call, error ->
            val transient = error.sqlState in setOf("40001", "40P01", "55P03", "57014") || error.sqlState?.startsWith("08") == true
            logger.error("Database request failed with SQL state {}", error.sqlState)
            if (transient) call.response.header(HttpHeaders.RetryAfter, "1")
            call.json(JSONObject().put("error", if (transient) "database_busy" else "internal_error"), if (transient) 503 else 500)
        }
        exception<Throwable> { call, error ->
            if (error is CancellationException) throw error
            // Avoid leaking tokens, passwords, SQL values or document content through exception messages.
            logger.error("API request failed: {}", error.javaClass.simpleName)
            call.json(JSONObject().put("error", "internal_error"), 500)
        }
    }
    suspend fun RoutingContext.api(document: Boolean = false, action: suspend ApplicationCall.() -> Unit) {
        if (!requests.tryAcquire()) throw ServiceFailure(503, "server_busy")
        val hasDocumentSlot = !document || documentRequests.tryAcquire()
        try {
            if (!hasDocumentSlot) throw ServiceFailure(503, "server_busy")
            limits.check("api:${call.request.origin.remoteHost}", 240)
            call.action()
        } finally {
            if (document && hasDocumentSlot) documentRequests.release()
            requests.release()
        }
    }
    fun ApplicationCall.authLimit() = limits.check("auth:${request.origin.remoteHost}", 20)
    fun ApplicationCall.mailLimit(email: String) = limits.check("mail:${email.trim().lowercase()}", 2)
    routing {
        get("/health") { api { json(JSONObject().put("status", "ok")) } }
        post("/v1/auth/register") {
            api {
                authLimit()
                val body = body("email", "password")
                val email = body.getString("email")
                mailLimit(email)
                withContext(Dispatchers.IO) { auth.register(email, body.getString("password")) }
                accepted()
            }
        }
        post("/v1/auth/verify") {
            api {
                authLimit()
                val body = body("code")
                withContext(Dispatchers.IO) { auth.verify(body.getString("code")) }
                accepted()
            }
        }
        post("/v1/auth/resend-verification") {
            api {
                authLimit()
                val email = body("email").getString("email")
                mailLimit(email)
                withContext(Dispatchers.IO) { auth.requestAction(email, "verify") }
                accepted()
            }
        }
        post("/v1/auth/forgot-password") {
            api {
                authLimit()
                val email = body("email").getString("email")
                mailLimit(email)
                withContext(Dispatchers.IO) { auth.requestAction(email, "reset") }
                accepted()
            }
        }
        post("/v1/auth/reset-password") {
            api {
                authLimit()
                val body = body("code", "password")
                withContext(Dispatchers.IO) { auth.reset(body.getString("code"), body.getString("password")) }
                accepted()
            }
        }
        post("/v1/auth/login") {
            api {
                authLimit()
                val body = body("email", "password")
                limits.check("login:${body.getString("email").trim().lowercase()}", 10)
                val session = withContext(Dispatchers.IO) { auth.login(body.getString("email"), body.getString("password")) }
                json(session.json())
            }
        }
        post("/v1/auth/refresh") {
            api {
                authLimit()
                val body = body("refreshToken")
                json(withContext(Dispatchers.IO) { auth.refresh(body.getString("refreshToken")) }.json())
            }
        }
        post("/v1/auth/logout") {
            api {
                val token = bearer()
                withContext(Dispatchers.IO) { auth.logout(token) }
                accepted()
            }
        }
        get("/v1/account") {
            api {
                val token = bearer()
                val account = withContext(Dispatchers.IO) { auth.authenticate(token) }
                json(JSONObject().put("id", account.id).put("email", account.email))
            }
        }
        delete("/v1/account") {
            api {
                authLimit()
                val token = bearer()
                val password = body("password").getString("password")
                withContext(Dispatchers.IO) { auth.deleteAccount(token, password) }
                accepted()
            }
        }
        get("/v1/boards") {
            api {
                val token = bearer()
                val after = request.queryParameters["after"] ?: ""
                val list = withContext(Dispatchers.IO) { boards.list(token, after) }
                json(
                    JSONObject().put(
                        "items",
                        JSONArray(
                            list.map {
                                JSONObject().put("id", it.id).put("name", it.name).put("revision", it.revision).put("role", it.role.name)
                            }
                        )
                    ).put("next", if (list.size == 100) list.last().id else JSONObject.NULL)
                )
            }
        }
        post("/v1/boards") {
            api(document = true) {
                val token = bearer()
                withContext(Dispatchers.IO) { auth.authenticate(token) }
                val request = body("operationId", "document", limit = DOCUMENT_LIMIT)
                val board = withContext(Dispatchers.IO) {
                    boards.create(
                        token,
                        DocumentCodec().decode(request.getJSONObject("document").toString()),
                        request.getString("operationId")
                    )
                }
                json(board.json(), 201)
            }
        }
        get("/v1/boards/{id}") {
            api(document = true) {
                val token = bearer()
                val id = boardId()
                json(withContext(Dispatchers.IO) { boards.get(token, id) }.json())
            }
        }
        post("/v1/boards/{id}/operations") {
            api(document = true) {
                val token = bearer()
                withContext(Dispatchers.IO) { auth.authenticate(token) }
                val id = boardId()
                val body = body("operationId", "baseRevision", "delta", limit = DOCUMENT_LIMIT)
                val revision = withContext(Dispatchers.IO) {
                    operations.submit(
                        token,
                        id,
                        body.getString("operationId"),
                        body.revision("baseRevision"),
                        DocumentDeltaCodec().decode(body.getJSONObject("delta").toString())
                    )
                }
                json(JSONObject().put("revision", revision))
            }
        }
        get("/v1/boards/{id}/changes") {
            api(document = true) {
                val token = bearer()
                val id = boardId()
                val after = request.queryParameters["after"]?.toLongOrNull() ?: invalidRequest()
                val page = withContext(Dispatchers.IO) { operations.changes(token, id, after) }
                json(
                    JSONObject().put("nextRevision", page.nextRevision).put("currentRevision", page.currentRevision).put(
                        "operations",
                        JSONArray(
                            page.operations.map {
                                JSONObject().put("operationId", it.operationId).put("revision", it.revision)
                                    .put(
                                        "actorId",
                                        it.actorId ?: JSONObject.NULL
                                    ).put("createdAt", it.createdAt).put("delta", JSONObject(it.delta))
                            }
                        )
                    )
                )
            }
        }
        put("/v1/boards/{id}") {
            api(document = true) {
                val token = bearer()
                withContext(Dispatchers.IO) { auth.authenticate(token) }
                val id = boardId()
                val body = body("operationId", "expectedRevision", "document", limit = DOCUMENT_LIMIT)
                val revision = withContext(Dispatchers.IO) {
                    boards.commit(
                        token,
                        id,
                        body.getString("operationId"),
                        body.revision("expectedRevision"),
                        DocumentCodec().decode(body.getJSONObject("document").toString())
                    )
                }
                json(JSONObject().put("revision", revision))
            }
        }
        delete("/v1/boards/{id}") {
            api {
                val token = bearer()
                val id = boardId()
                withContext(Dispatchers.IO) { boards.delete(token, id) }
                accepted()
            }
        }
        post("/v1/boards/{id}/invites") {
            api {
                val token = bearer()
                val id = boardId()
                val body = body("email", "role")
                val code =
                    withContext(Dispatchers.IO) {
                        boards.invite(token, id, body.getString("email"), BoardRole.valueOf(body.getString("role")))
                    }
                json(JSONObject().put("code", code), 201)
            }
        }
        delete("/v1/boards/{id}/invites") {
            api {
                val token = bearer()
                val id = boardId()
                withContext(Dispatchers.IO) { boards.revokeInvites(token, id) }
                accepted()
            }
        }
        post("/v1/invites/accept") {
            api {
                val token = bearer()
                val code = body("code").getString("code")
                json(JSONObject().put("boardId", withContext(Dispatchers.IO) { boards.accept(token, code) }))
            }
        }
        put("/v1/boards/{id}/members/{member}") {
            api {
                val token = bearer()
                val id = boardId()
                val member = parameters["member"] ?: invalidRequest()
                val role = BoardRole.valueOf(body("role").getString("role"))
                withContext(Dispatchers.IO) { boards.changeMember(token, id, member, role) }
                accepted()
            }
        }
        delete("/v1/boards/{id}/members/{member}") {
            api {
                val token = bearer()
                val id = boardId()
                val member = parameters["member"] ?: invalidRequest()
                withContext(Dispatchers.IO) { boards.changeMember(token, id, member, null) }
                accepted()
            }
        }
    }
}

private const val DOCUMENT_LIMIT = 8 * 1024 * 1024

private suspend fun ApplicationCall.body(vararg fields: String, limit: Int = 4096): JSONObject {
    if (!request.contentType().match(ContentType.Application.Json)) throw ServiceFailure(415, "json_required")
    if (request.headers[HttpHeaders.ContentEncoding]?.let { it != "identity" } == true) throw ServiceFailure(415, "encoding_not_supported")
    val declared = request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (declared != null && (declared < 0 || declared > limit)) throw ServiceFailure(413, "request_too_large")
    val channel = receiveChannel()
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    withTimeout(30_000) {
        while (true) {
            val count = channel.readAvailable(buffer)
            if (count == -1) break
            if (output.size() + count > limit) throw ServiceFailure(413, "request_too_large")
            output.write(buffer, 0, count)
        }
    }
    val source = runCatching {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(output.toByteArray())).toString()
    }.getOrElse { invalidRequest() }
    return withContext(Dispatchers.Default) {
        JsonSafety.check(source)
        JSONObject(source, JSONParserConfiguration().withMaxNestingDepth(32)).also {
            if (it.keySet() != fields.toSet()) invalidRequest()
        }
    }
}

private fun ApplicationCall.bearer(): String {
    val value = request.headers.getAll(HttpHeaders.Authorization)?.singleOrNull() ?: unauthenticated()
    if (!value.startsWith("Bearer ") || value.length != 50) unauthenticated()
    return value.removePrefix("Bearer ")
}
private fun JSONObject.revision(name: String): Long {
    val value = get(name)
    if (value !is Number || !value.toString().matches(Regex("0|[1-9][0-9]{0,18}"))) invalidRequest()
    return value.toString().toLongOrNull() ?: invalidRequest()
}
private fun ApplicationCall.boardId(): String = parameters["id"]?.takeIf { it.length == 36 } ?: invalidRequest()
private suspend fun ApplicationCall.accepted() = json(JSONObject().put("accepted", true), 202)
private suspend fun ApplicationCall.json(body: JSONObject, status: Int = 200) =
    respondText(withContext(Dispatchers.Default) { body.toString() }, ContentType.Application.Json, HttpStatusCode.fromValue(status))
private fun SessionTokens.json() =
    JSONObject().put("accessToken", accessToken).put("refreshToken", refreshToken).put("accessExpiresAt", accessExpiresAt)
private suspend fun CloudBoard.json() = withContext(Dispatchers.Default) {
    JSONObject().put("document", JSONObject(DocumentCodec().encode(document))).put("revision", revision).put("role", role.name)
}
