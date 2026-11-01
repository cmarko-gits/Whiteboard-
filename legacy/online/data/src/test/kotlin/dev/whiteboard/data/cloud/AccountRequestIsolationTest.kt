package dev.whiteboard.data.cloud

import com.sun.net.httpserver.HttpServer
import dev.whiteboard.core.cloud.CloudAccount
import dev.whiteboard.core.cloud.CloudError
import dev.whiteboard.core.cloud.CloudFailure
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AccountRequestIsolationTest {
    @Test fun delayedSuccessCannotCrossSignOutEvenWhenSameIdentitySignsBackIn() = delayedResponse(200, "account-a")

    @Test fun delayedUnauthorizedResponseCannotSignOutReplacementIdentity() = delayedResponse(401, "account-b")

    private fun delayedResponse(status: Int, replacement: String): Unit = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reads = AtomicInteger()
        val pool = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = pool
        server.createContext("/v1/") { exchange ->
            val path = exchange.requestURI.path
            val body = exchange.requestBody.use { it.readBytes().toString(Charsets.UTF_8) }
            val payload = when (path) {
                "/v1/auth/login" -> {
                    val id = JSONObject(body).getString("email").substringBefore('@')
                    """{"accessToken":"$id","refreshToken":"refresh","accessExpiresAt":${System.currentTimeMillis() + 900_000}}"""
                }
                "/v1/account" -> {
                    val id = exchange.requestHeaders.getFirst("Authorization").removePrefix("Bearer ")
                    """{"id":"$id","email":"$id@example.com"}"""
                }
                "/v1/boards" -> {
                    reads.incrementAndGet()
                    started.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    if (status == 200) """{"items":[],"next":null}""" else """{"error":"invalid_credentials"}"""
                }
                else -> """{"accepted":true}"""
            }.toByteArray()
            exchange.sendResponseHeaders(if (path == "/v1/boards") status else 200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        try {
            val vault = object : SessionStorage {
                var value: CloudSession? =
                    CloudSession(CloudAccount("account-a", "account-a@example.com"), "account-a", "refresh", Long.MAX_VALUE)
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
            cloudFailure(CloudError.CREDENTIALS) { accounts.authorized("unrelated-account", "GET", "/v1/boards") }
            assertEquals(0, reads.get())
            val pending = async { runCatching { accounts.authorized("account-a", "GET", "/v1/boards") } }
            assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
            assertTrue(accounts.logout())
            accounts.login("$replacement@example.com", "test passphrase")
            release.countDown()
            val failure = pending.await().exceptionOrNull() as CloudFailure
            assertEquals(CloudError.CREDENTIALS, failure.error)
            assertEquals(replacement, accounts.account.value?.id)
            assertEquals(replacement, vault.value?.account?.id)
        } finally {
            release.countDown()
            server.stop(0)
            pool.shutdownNow()
        }
    }
}
