package dev.whiteboard.data.cloud

import com.sun.net.httpserver.HttpServer
import dev.whiteboard.core.cloud.CloudAccount
import dev.whiteboard.core.cloud.CloudError
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RemoteAccountsTest {
    private class MemoryVault(var value: CloudSession? = null) : SessionStorage {
        override fun load() = value
        override fun save(session: CloudSession) {
            value = session
        }
        override fun clear() {
            value = null
        }
    }

    @Test fun concurrentRestoreRotatesOnlyOnceAndRevokedSessionIsCleared(): Unit = runBlocking {
        val rotations = AtomicInteger()
        var revoked = false
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/auth/refresh") { exchange ->
            rotations.incrementAndGet()
            val bytes = JSONObject().put("accessToken", "new-access").put("refreshToken", "new-refresh")
                .put("accessExpiresAt", System.currentTimeMillis() + 900_000).toString().toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/v1/account") { exchange ->
            val source = if (revoked) """{"error":"invalid_credentials"}""" else """{"id":"account-a","email":"alice@example.com"}"""
            val bytes = source.toByteArray()
            exchange.sendResponseHeaders(if (revoked) 401 else 200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val vault = MemoryVault(CloudSession(CloudAccount("account-a", "alice@example.com"), "expired", "refresh", 0))
            val accounts = RemoteAccounts(CloudHttp("http://127.0.0.1:${server.address.port}", true), vault)
            coroutineScope { listOf(async { accounts.restore() }, async { accounts.restore() }).forEach { it.await() } }
            assertEquals(1, rotations.get())
            assertEquals("new-refresh", vault.value?.refresh)
            assertEquals("alice@example.com", accounts.account.value?.email)
            revoked = true
            cloudFailure(CloudError.CREDENTIALS) { accounts.restore() }
            assertNull(vault.value)
            assertNull(accounts.account.value)
        } finally {
            server.stop(0)
        }
    }

    @Test fun offlineSignOutStillClearsDeviceCredentials(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/account") { exchange ->
            val bytes = """{"id":"account-a","email":"alice@example.com"}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val vault = MemoryVault(
            CloudSession(
                CloudAccount("account-a", "alice@example.com"),
                "access",
                "refresh",
                System.currentTimeMillis() + 900_000
            )
        )
        val accounts = RemoteAccounts(CloudHttp("http://127.0.0.1:${server.address.port}", true), vault)
        try {
            accounts.restore()
        } finally {
            server.stop(0)
        }
        assertFalse(accounts.logout())
        assertNull(vault.value)
        assertNull(accounts.account.value)
    }
}
