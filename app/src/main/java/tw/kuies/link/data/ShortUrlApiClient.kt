package tw.kuies.link.data

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Strict-mode client. HTTPS only; only a debug build may point at a localhost HTTP endpoint
 * (used with `adb reverse` for end-to-end verification).
 */
class ShortUrlApiClient(
    baseUrl: String,
    allowLocalhostHttp: Boolean = false,
    private val client: OkHttpClient = defaultClient(),
) {
    private val endpoint: String = buildEndpoint(baseUrl, allowLocalhostHttp)

    suspend fun shorten(url: String): ShortenResult = suspendCancellableCoroutine { continuation ->
        val payload = buildJsonObject {
            put("url", url)
            put("clean_tracking", true)
            put("mode", "strict")
            put("fast_response", true)
        }.toString()

        val request = try {
            Request.Builder()
                .url(endpoint)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        } catch (error: IllegalArgumentException) {
            continuation.resume(ShortenResult.Failure(ShortenError.INVALID_REQUEST))
            return@suspendCancellableCoroutine
        }

        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (!continuation.isActive) return
                continuation.resume(mapFailure(error))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { result ->
                    val body = result.body?.string().orEmpty()
                    val parsed = if (body.length > MAX_RESPONSE_BYTES) {
                        ShortenResult.Failure(ShortenError.INVALID_RESPONSE, result.code)
                    } else {
                        ShortenResponseParser.parse(result.code, body)
                    }
                    if (continuation.isActive) continuation.resume(parsed)
                }
            }
        })
    }

    private fun mapFailure(error: IOException): ShortenResult = when (error) {
        is SocketTimeoutException -> ShortenResult.Failure(ShortenError.TIMEOUT)
        is SSLException -> ShortenResult.Failure(ShortenError.TLS)
        is UnknownHostException, is ConnectException -> ShortenResult.Failure(ShortenError.NETWORK)
        else -> ShortenResult.Failure(ShortenError.NETWORK)
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 64 * 1024
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun buildEndpoint(baseUrl: String, allowLocalhostHttp: Boolean): String {
            val normalized = baseUrl.trim().trimEnd('/')
            val parsed = normalized.toHttpUrlOrNull()
            require(parsed != null) { "Invalid shortener base URL" }
            if (parsed.scheme != "https") {
                val isLocalhost = parsed.host == "127.0.0.1" || parsed.host == "localhost"
                require(allowLocalhostHttp && isLocalhost) { "Only HTTPS endpoints are allowed" }
            }
            return "$normalized/api/public/shorten"
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
