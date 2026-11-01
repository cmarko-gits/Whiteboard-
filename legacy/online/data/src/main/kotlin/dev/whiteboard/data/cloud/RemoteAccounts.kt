package dev.whiteboard.data.cloud

import android.content.Context
import dev.whiteboard.core.cloud.AccountGateway
import dev.whiteboard.core.cloud.CloudAccount
import dev.whiteboard.core.cloud.CloudError
import dev.whiteboard.core.cloud.CloudFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class RemoteAccounts internal constructor(private val http: CloudHttp, private val vault: SessionStorage) : AccountGateway {
    constructor(context: Context, http: CloudHttp) : this(http, SessionVault(context.applicationContext, http.origin))
    private val mutex = Mutex()
    private var session: CloudSession? = null
    private var identityEpoch = 0L
    private val current = MutableStateFlow<CloudAccount?>(null)
    override val account = current.asStateFlow()
    override val available get() = http.available

    override suspend fun restore() = mutex.withLock {
        if (!available) return@withLock
        session = withContext(Dispatchers.IO) { vault.load() }
        current.value = session?.account
        if (session == null) return@withLock
        val access = accessToken()
        val response = try {
            http.request("GET", "/v1/account", access = access)
        } catch (error: CloudFailure) {
            if (error.error == CloudError.CREDENTIALS) clear()
            throw error
        }
        val restored = CloudAccount(response.getString("id"), response.getString("email"))
        if (restored.id != session?.account?.id) {
            clear()
            throw CloudFailure(CloudError.CREDENTIALS)
        }
        save(checkNotNull(session).copy(account = restored))
    }

    override suspend fun register(email: String, password: String) {
        http.request("POST", "/v1/auth/register", JSONObject().put("email", email).put("password", password))
    }
    override suspend fun verify(code: String) {
        http.request("POST", "/v1/auth/verify", JSONObject().put("code", code.trim()))
    }
    override suspend fun resendVerification(email: String) {
        http.request("POST", "/v1/auth/resend-verification", JSONObject().put("email", email))
    }
    override suspend fun requestReset(email: String) {
        http.request("POST", "/v1/auth/forgot-password", JSONObject().put("email", email))
    }
    override suspend fun reset(code: String, password: String) {
        http.request("POST", "/v1/auth/reset-password", JSONObject().put("code", code.trim()).put("password", password))
    }

    override suspend fun login(email: String, password: String) = mutex.withLock {
        // Explicit logout is required before switching identities; local documents are never reassigned by logging in.
        if (session != null) throw CloudFailure(CloudError.REQUEST)
        val tokens = http.request("POST", "/v1/auth/login", JSONObject().put("email", email).put("password", password))
        val access = tokens.getString("accessToken")
        val account = http.request("GET", "/v1/account", access = access)
        save(
            CloudSession(
                CloudAccount(account.getString("id"), account.getString("email")),
                access,
                tokens.getString("refreshToken"),
                tokens.getLong("accessExpiresAt")
            )
        )
    }

    override suspend fun logout(): Boolean = mutex.withLock {
        val revoked = try {
            http.request("POST", "/v1/auth/logout", access = accessToken())
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: CloudFailure) {
            false
        }
        clear()
        revoked
    }

    override suspend fun delete(password: String) = mutex.withLock {
        http.request("DELETE", "/v1/account", JSONObject().put("password", password), accessToken())
        clear()
    }

    internal suspend fun authorized(
        accountId: String,
        method: String,
        path: String,
        body: JSONObject? = null,
        maxResponseBytes: Int = 64 * 1024
    ): JSONObject = authorized(accountId, method, path, body, maxResponseBytes) { it }

    internal suspend fun <T> authorized(
        accountId: String,
        method: String,
        path: String,
        body: JSONObject? = null,
        maxResponseBytes: Int = 64 * 1024,
        transform: suspend (JSONObject) -> T
    ): T {
        val lease = mutex.withLock {
            if (session?.account?.id != accountId) throw CloudFailure(CloudError.CREDENTIALS)
            val token = accessToken()
            RequestLease(accountId, identityEpoch, token)
        }
        val response = try {
            http.request(method, path, body, lease.token, maxResponseBytes)
        } catch (error: CloudFailure) {
            mutex.withLock {
                requireCurrent(lease)
                // An old response must never revoke a replacement identity or a newly rotated token.
                if (error.error == CloudError.CREDENTIALS && session?.access == lease.token) clear()
            }
            throw error
        }
        val result = transform(response)
        mutex.withLock { requireCurrent(lease) }
        return result
    }

    private fun requireCurrent(lease: RequestLease) {
        if (identityEpoch != lease.epoch || session?.account?.id != lease.accountId) throw CloudFailure(CloudError.CREDENTIALS)
    }

    private data class RequestLease(val accountId: String, val epoch: Long, val token: String)

    private suspend fun accessToken(): String {
        val value = session ?: throw CloudFailure(CloudError.CREDENTIALS)
        if (value.expires > System.currentTimeMillis() + 30_000) return value.access
        val response = try {
            http.request("POST", "/v1/auth/refresh", JSONObject().put("refreshToken", value.refresh))
        } catch (error: CloudFailure) {
            if (error.error == CloudError.CREDENTIALS) clear()
            throw error
        }
        val renewed = value.copy(
            access = response.getString("accessToken"),
            refresh = response.getString("refreshToken"),
            expires = response.getLong("accessExpiresAt")
        )
        save(renewed)
        return renewed.access
    }

    private suspend fun save(value: CloudSession) {
        withContext(Dispatchers.IO) { vault.save(value) }
        if (session?.account?.id != value.account.id) identityEpoch++
        session = value
        current.value = value.account
    }
    private suspend fun clear() {
        withContext(Dispatchers.IO) { vault.clear() }
        identityEpoch++
        session = null
        current.value = null
    }
}
