package dev.whiteboard.data.cloud

import com.sun.net.httpserver.HttpServer
import dev.whiteboard.core.cloud.CloudError
import dev.whiteboard.core.cloud.CloudFailure
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CloudHttpTest {
    @Test fun cleartextRequiresExplicitLoopbackAndCredentialsCannotBeInOrigin() {
        invalid { CloudHttp("http://127.0.0.1:8080") }
        invalid { CloudHttp("http://example.com", true) }
        invalid { CloudHttp("https://user:password@example.com") }
        invalid { CloudHttp("https://example.com/api") }
        invalid { CloudHttp("https://example.com?token=secret") }
        assertFalse(CloudHttp("").available)
        assertTrue(CloudHttp("https://example.com").available)
    }

    @Test fun redirectsNeverForwardAuthorization(): Unit = runBlocking {
        val received = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/source") { exchange ->
            exchange.responseHeaders.add("Location", "/v1/redirected")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/v1/redirected") { exchange ->
            received.incrementAndGet()
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            val client = CloudHttp("http://127.0.0.1:${server.address.port}", true)
            cloudFailure(CloudError.SERVER) { client.request("GET", "/v1/source", access = "never-forward-this") }
            assertEquals(0, received.get())
        } finally {
            server.stop(0)
        }
    }

    @Test fun responseBoundAndStableErrorsHideServerContent(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/large") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write(ByteArray(70 * 1024)) }
        }
        server.createContext("/v1/failed") { exchange ->
            val bytes = "{\"error\":\"SQL password=do-not-expose\"}".toByteArray()
            exchange.sendResponseHeaders(500, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/v1/code") { exchange ->
            val bytes = "{\"error\":\"invalid_or_expired_code\"}".toByteArray()
            exchange.sendResponseHeaders(400, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = CloudHttp("http://127.0.0.1:${server.address.port}", true)
            cloudFailure(CloudError.SERVER) { client.request("GET", "/v1/large") }
            cloudFailure(CloudError.SERVER) { client.request("GET", "/v1/failed") }
            cloudFailure(CloudError.CODE) { client.request("GET", "/v1/code") }
        } finally {
            server.stop(0)
        }
    }

    @Test fun malformedUtf8IsRejectedRatherThanReplaced(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/invalid") { exchange ->
            val bytes = byteArrayOf(123, 34, 120, 34, 58, 34, 0xc3.toByte(), 0x28, 34, 125)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            cloudFailure(CloudError.SERVER) {
                CloudHttp("http://127.0.0.1:${server.address.port}", true).request("GET", "/v1/invalid")
            }
        } finally {
            server.stop(0)
        }
    }

    private fun invalid(action: () -> Unit) {
        try {
            action()
            fail("Expected invalid origin")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun cancellationStopsARequestWhileItsResponseBodyIsStillStreaming(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/stalled") { exchange ->
            try {
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write('{'.code)
                exchange.responseBody.flush()
                started.complete(Unit)
                release.await(10, TimeUnit.SECONDS)
            } finally {
                exchange.close()
            }
        }
        server.start()
        try {
            val request = async { CloudHttp("http://127.0.0.1:${server.address.port}", true).request("GET", "/v1/stalled") }
            withTimeout(5000) { started.await() }
            withTimeout(2000) { request.cancelAndJoin() }
            assertTrue(request.isCancelled)
        } finally {
            release.countDown()
            server.stop(0)
        }
    }
}

internal suspend fun cloudFailure(expected: CloudError, action: suspend () -> Unit) {
    try {
        action()
        fail("Expected $expected")
    } catch (error: CloudFailure) {
        assertEquals(expected, error.error)
        assertEquals(expected.name, error.message)
    }
}
