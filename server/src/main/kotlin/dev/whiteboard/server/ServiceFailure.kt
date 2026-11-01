package dev.whiteboard.server

/** Only this stable code, never exception/database text, is sent to an API client. */
class ServiceFailure(val status: Int, val code: String) : RuntimeException(code)

fun invalidRequest(): Nothing = throw ServiceFailure(400, "invalid_request")
fun unauthenticated(): Nothing = throw ServiceFailure(401, "invalid_credentials")
fun notFound(): Nothing = throw ServiceFailure(404, "not_found")
