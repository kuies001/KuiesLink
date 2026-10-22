package tw.kuies.link.update

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Streams the APK into app-private storage, then checks the byte count and SHA-256 before the file
 * is handed to the verifier. Redirects are followed manually so every hop stays on GitHub hosts.
 */
class ApkDownloader(
    private val client: OkHttpClient,
    private val trust: UpdateTrust,
    private val directory: File,
) {
    suspend fun download(
        update: AvailableUpdate,
        onProgress: (Int) -> Unit,
    ): PrepareResult = withContext(Dispatchers.IO) {
        if (!trust.isAllowedAssetUrl(update.apkUrl)) {
            return@withContext PrepareResult.Failed(UpdateFailure.ASSET_MISMATCH)
        }
        directory.mkdirs()
        val partial = File(directory, "${update.apkAssetName}.part")
        val target = File(directory, update.apkAssetName)
        partial.delete()
        target.delete()
        try {
            downloadTo(update, partial, target, onProgress)
        } catch (cancelled: CancellationException) {
            partial.delete()
            throw cancelled
        } catch (_: SocketTimeoutException) {
            partial.delete()
            PrepareResult.Failed(UpdateFailure.TIMEOUT)
        } catch (_: IOException) {
            partial.delete()
            PrepareResult.Failed(UpdateFailure.DOWNLOAD_FAILED)
        } catch (_: SecurityException) {
            partial.delete()
            PrepareResult.Failed(UpdateFailure.STORAGE)
        }
    }

    private suspend fun downloadTo(
        update: AvailableUpdate,
        partial: File,
        target: File,
        onProgress: (Int) -> Unit,
    ): PrepareResult {
        var url = update.apkUrl
        var redirects = 0
        while (true) {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "KuiesLink-Android")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    val location = response.header("Location")
                        ?: return PrepareResult.Failed(UpdateFailure.DOWNLOAD_FAILED)
                    val next = response.request.url.resolve(location)
                        ?: return PrepareResult.Failed(UpdateFailure.DOWNLOAD_FAILED)
                    if (!trust.isAllowedRedirectUrl(next.toString())) {
                        return PrepareResult.Failed(UpdateFailure.ASSET_MISMATCH)
                    }
                    if (++redirects > MAX_REDIRECTS) {
                        return PrepareResult.Failed(UpdateFailure.DOWNLOAD_FAILED)
                    }
                    url = next.toString()
                    return@use
                }
                if (!response.isSuccessful) {
                    return PrepareResult.Failed(UpdateFailure.DOWNLOAD_FAILED)
                }
                val body = response.body
                    ?: return PrepareResult.Failed(UpdateFailure.DOWNLOAD_FAILED)
                if (body.contentLength() > UpdateContract.MAX_APK_BYTES) {
                    return PrepareResult.Failed(UpdateFailure.APK_TOO_LARGE)
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            total += read
                            if (total > UpdateContract.MAX_APK_BYTES) {
                                return PrepareResult.Failed(UpdateFailure.APK_TOO_LARGE)
                            }
                            val percent =
                                ((total * 100) / update.apkSizeBytes).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
                if (total != update.apkSizeBytes) {
                    partial.delete()
                    return PrepareResult.Failed(UpdateFailure.SIZE_MISMATCH)
                }
                val actual = digest.digest().toHex()
                if (!actual.equals(update.sha256, ignoreCase = true)) {
                    partial.delete()
                    return PrepareResult.Failed(UpdateFailure.HASH_MISMATCH)
                }
                if (!partial.renameTo(target)) {
                    partial.delete()
                    return PrepareResult.Failed(UpdateFailure.STORAGE)
                }
                onProgress(100)
                return PrepareResult.Prepared(target)
            }
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val MAX_REDIRECTS = 4
        const val BUFFER_BYTES = 64 * 1024
    }
}
