package dev.whiteboard.data.cloud

import dev.whiteboard.core.cloud.CloudError
import dev.whiteboard.core.cloud.CloudFailure
import dev.whiteboard.core.concurrency.AppDispatchers
import dev.whiteboard.core.concurrency.DefaultAppDispatchers
import dev.whiteboard.format.JsonSafety
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** One cancellable transport. Redirects, cookies and transparent POST retries never carry credentials elsewhere. */
class CloudHttp(baseUrl: String, allowLoopbackHttp: Boolean = false, private val dispatchers: AppDispatchers = DefaultAppDispatchers) {
    val origin = baseUrl.trimEnd('/')
    val available = origin.isNotEmpty()

    init {
        if (available) {
            val uri = URI(origin)
            require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path.isNullOrEmpty())
            require(uri.scheme == "https" || (allowLoopbackHttp && uri.scheme == "http" && uri.host in setOf("127.0.0.1", "localhost")))
        }
    }

    suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        access: String? = null,
        maxResponseBytes: Int = 64 * 1024
    ): JSONObject {
        if (!available) throw CloudFailure(CloudError.UNAVAILABLE)
        require(maxResponseBytes in 1..MAX_DOCUMENT_RESPONSE)
        require(path.startsWith("/v1/") && !path.startsWith("//") && !path.contains("..") && !path.contains('\\'))
        require(URI(path).fragment == null)
        val requestBody = withContext(dispatchers.computation) {
            if (body != null || method in setOf("POST", "PUT", "PATCH")) {
                (body?.toString() ?: "{}").toByteArray(Charsets.UTF_8).toRequestBody(JSON)
            } else {
                null
            }
        }
        val request = Request.Builder().url(origin + path).method(method, requestBody)
            .header("Accept", "application/json").header("Accept-Encoding", "identity")
            .apply { access?.let { header("Authorization", "Bearer $it") } }.build()
        val response = withContext(dispatchers.io) { exchange(request, maxResponseBytes) }
        return withContext(dispatchers.computation) {
            val source = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(response.bytes)).toString()
            } catch (_: java.nio.charset.CharacterCodingException) {
                throw CloudFailure(CloudError.SERVER)
            }
            if (response.status !in 200..299) {
                val code = runCatching {
                    JsonSafety.check(source)
                    JSONObject(source).optString("error")
                }.getOrDefault("")
                throw CloudFailure(
                    when {
                        response.status == 401 -> CloudError.CREDENTIALS
                        code == "invalid_or_expired_code" -> CloudError.CODE
                        response.status == 429 -> CloudError.LIMIT
                        response.status in 400..499 -> CloudError.REQUEST
                        else -> CloudError.SERVER
                    },
                    response.status,
                    code.takeIf { it.matches(Regex("[a-z_]{1,64}")) }
                )
            }
            runCatching {
                JsonSafety.check(source)
                JSONObject(source)
            }.getOrElse { throw CloudFailure(CloudError.SERVER) }
        }
    }

    private data class WireResponse(val status: Int, val bytes: ByteArray)

    private suspend fun exchange(request: Request, maximum: Int): WireResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        // Remains registered until the entire bounded response body has been read, not only its headers.
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWith(
                        Result.failure(
                            if (e is SSLException) {
                                CloudFailure(
                                    CloudError.SERVER,
                                    code = "tls_validation_failed"
                                )
                            } else {
                                CloudFailure(CloudError.OFFLINE)
                            }
                        )
                    )
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (it.header("Content-Encoding")?.equals("identity", ignoreCase = true) ==
                            false
                        ) {
                            throw CloudFailure(CloudError.SERVER)
                        }
                        val limit = if (it.code in 200..299) maximum else 64 * 1024
                        val body = it.body
                        if (body.contentLength() > limit) throw CloudFailure(CloudError.SERVER)
                        val output = ByteArrayOutputStream()
                        body.byteStream().use { stream ->
                            val buffer = ByteArray(4096)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count == -1) break
                                if (output.size() + count > limit) throw CloudFailure(CloudError.SERVER)
                                output.write(buffer, 0, count)
                            }
                        }
                        WireResponse(it.code, output.toByteArray())
                    }
                }.recoverCatching { error ->
                    if (error is IOException) throw CloudFailure(CloudError.OFFLINE)
                    throw error
                }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }

    companion object {
        internal const val MAX_DOCUMENT_RESPONSE = 12 * 1024 * 1024
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val client by lazy {
            OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                .cookieJar(CookieJar.NO_COOKIES).cache(null).callTimeout(30, TimeUnit.SECONDS).connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS).build()
        }
    }
}
