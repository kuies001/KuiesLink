package tw.kuies.link.update

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Reads the public GitHub release metadata; no token is ever sent from the app. */
class UpdateApi(
    private val trust: UpdateTrust,
    private val client: OkHttpClient = apiClient(),
) {
    // GitHub serves release assets via a 302 to release-assets.githubusercontent.com.
    private val manifestClient: OkHttpClient by lazy {
        client.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun fetchLatestRelease(): ApiResult<GitHubRelease> =
        when (val response = getText(trust.latestReleaseUrl, MAX_RELEASE_BYTES)) {
            is ApiResult.Failure -> response
            is ApiResult.Success -> {
                val release = UpdateJson.parseRelease(response.value)
                    ?: return ApiResult.Failure(UpdateFailure.MANIFEST_INVALID)
                ApiResult.Success(release)
            }
        }

    suspend fun fetchManifest(url: String): ApiResult<String> {
        if (!trust.isAllowedAssetUrl(url)) {
            return ApiResult.Failure(UpdateFailure.ASSET_MISMATCH)
        }
        return getText(
            url = url,
            maxBytes = UpdateContract.MAX_MANIFEST_BYTES,
            httpClient = manifestClient,
            validateFinalUrl = true,
        )
    }

    private suspend fun getText(
        url: String,
        maxBytes: Long,
        httpClient: OkHttpClient = client,
        validateFinalUrl: Boolean = false,
    ): ApiResult<String> =
        suspendCancellableCoroutine { continuation ->
            val request = try {
                Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "KuiesLink-Android")
                    .build()
            } catch (_: IllegalArgumentException) {
                continuation.resume(ApiResult.Failure(UpdateFailure.NETWORK))
                return@suspendCancellableCoroutine
            }

            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) {
                        continuation.resume(ApiResult.Failure(mapFailure(error)))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { result ->
                        if (!continuation.isActive) return
                        if (validateFinalUrl &&
                            !trust.isAllowedRedirectUrl(result.request.url.toString())
                        ) {
                            continuation.resume(ApiResult.Failure(UpdateFailure.ASSET_MISMATCH))
                            return
                        }
                        if (!result.isSuccessful) {
                            continuation.resume(
                                ApiResult.Failure(httpFailure(result.code, result.header("X-RateLimit-Remaining"))),
                            )
                            return
                        }
                        val declared = result.header("Content-Length")?.toLongOrNull()
                        if (declared != null && declared > maxBytes) {
                            continuation.resume(ApiResult.Failure(UpdateFailure.MANIFEST_INVALID))
                            return
                        }
                        val body = result.peekBody(maxBytes + 1).string()
                        if (body.toByteArray().size > maxBytes) {
                            continuation.resume(ApiResult.Failure(UpdateFailure.MANIFEST_INVALID))
                        } else {
                            continuation.resume(ApiResult.Success(body))
                        }
                    }
                }
            })
        }

    private fun mapFailure(error: IOException): UpdateFailure = when (error) {
        is SocketTimeoutException -> UpdateFailure.TIMEOUT
        is SSLException -> UpdateFailure.NETWORK
        is UnknownHostException, is ConnectException -> UpdateFailure.NETWORK
        else -> UpdateFailure.NETWORK
    }

    private fun httpFailure(code: Int, rateLimitRemaining: String?): UpdateFailure = when {
        code == 404 -> UpdateFailure.RELEASE_NOT_FOUND
        code == 403 && rateLimitRemaining == "0" -> UpdateFailure.RATE_LIMITED
        code == 429 -> UpdateFailure.RATE_LIMITED
        else -> UpdateFailure.NETWORK
    }

    companion object {
        private const val MAX_RELEASE_BYTES = 512L * 1024

        fun apiClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            // The check is a read-only GET; retrying a stale pooled connection is safe.
            .retryOnConnectionFailure(true)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        fun downloadClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // ApkDownloader follows redirects itself so every hop is checked against the allowlist.
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
