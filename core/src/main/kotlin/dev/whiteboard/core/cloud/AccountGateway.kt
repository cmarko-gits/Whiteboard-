package dev.whiteboard.core.cloud

import kotlinx.coroutines.flow.StateFlow

data class CloudAccount(val id: String, val email: String)
enum class CloudError { UNAVAILABLE, OFFLINE, CREDENTIALS, CODE, REQUEST, LIMIT, SERVER, SECURE_STORAGE }
class CloudFailure(val error: CloudError, val status: Int? = null, val code: String? = null) : Exception(error.name)

interface AccountGateway {
    val available: Boolean
    val account: StateFlow<CloudAccount?>
    suspend fun restore()
    suspend fun register(email: String, password: String)
    suspend fun verify(code: String)
    suspend fun resendVerification(email: String)
    suspend fun requestReset(email: String)
    suspend fun reset(code: String, password: String)
    suspend fun login(email: String, password: String)

    /** Always clears this device's credentials. False means server revocation could not be confirmed. */
    suspend fun logout(): Boolean
    suspend fun delete(password: String)
}
