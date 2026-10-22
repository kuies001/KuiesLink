package tw.kuies.link.update

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkDownloaderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = TestHttpServer()
    private val trust = localTrust(server.port)
    private val apkBytes = ByteArray(20_000) { index -> (index % 251).toByte() }
    private val apkPath = "/v0.3.4-test/KuiesLink-v0.3.4-test.apk"
    private val apkUrl = "http://127.0.0.1:${server.port}$apkPath"
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun downloadsAndVerifiesHash() = runTest {
        server.bytes(apkPath, apkBytes)
        val progress = mutableListOf<Int>()

        val result = downloader().download(update(), progress::add)

        assertTrue(result is PrepareResult.Prepared)
        val apk = (result as PrepareResult.Prepared).apk
        assertTrue(apk.isFile)
        assertTrue(apkBytes.contentEquals(apk.readBytes()))
        assertEquals(100, progress.last())
    }

    @Test
    fun hashMismatchDeletesTheDownload() = runTest {
        server.bytes(apkPath, apkBytes)

        val result = downloader().download(update(sha = "0".repeat(64))) {}

        assertEquals(PrepareResult.Failed(UpdateFailure.HASH_MISMATCH), result)
        assertNoFilesLeft()
    }

    @Test
    fun sizeMismatchDeletesTheDownload() = runTest {
        server.bytes(apkPath, apkBytes)

        val result = downloader().download(update(size = apkBytes.size + 1L)) {}

        assertEquals(PrepareResult.Failed(UpdateFailure.SIZE_MISMATCH), result)
        assertNoFilesLeft()
    }

    @Test
    fun httpErrorIsReported() = runTest {
        server.status(apkPath, 500)

        val result = downloader().download(update()) {}

        assertEquals(PrepareResult.Failed(UpdateFailure.DOWNLOAD_FAILED), result)
        assertNoFilesLeft()
    }

    @Test
    fun redirectOutsideGitHubIsRejected() = runTest {
        server.redirect(apkPath, "https://evil.example/KuiesLink.apk")

        val result = downloader().download(update()) {}

        assertEquals(PrepareResult.Failed(UpdateFailure.ASSET_MISMATCH), result)
        assertNoFilesLeft()
    }

    @Test
    fun timeoutIsReported() = runTest {
        server.handle(apkPath) { Thread.sleep(1_500) }
        val shortTimeoutClient = client.newBuilder()
            .readTimeout(200, TimeUnit.MILLISECONDS)
            .callTimeout(500, TimeUnit.MILLISECONDS)
            .build()

        val result = downloader(shortTimeoutClient).download(update()) {}

        assertEquals(PrepareResult.Failed(UpdateFailure.TIMEOUT), result)
        assertNoFilesLeft()
    }

    @Test
    fun interruptedDownloadFailsWithoutLeavingFiles() = runTest {
        server.handle(apkPath) { exchange ->
            exchange.sendResponseHeaders(200, 1_000)
            exchange.responseBody.use { body ->
                body.write(ByteArray(10))
                body.flush()
            }
        }

        val result = downloader().download(update()) {}

        assertTrue(result is PrepareResult.Failed)
        val failure = (result as PrepareResult.Failed).failure
        assertTrue(
            "unexpected failure: $failure",
            failure == UpdateFailure.DOWNLOAD_FAILED ||
                failure == UpdateFailure.SIZE_MISMATCH ||
                failure == UpdateFailure.TIMEOUT,
        )
        assertNoFilesLeft()
    }

    @Test
    fun arbitraryInitialUrlIsRejected() = runTest {
        val result = downloader().download(
            update(url = "https://evil.example/KuiesLink-v0.3.4-test.apk"),
        ) {}

        assertEquals(PrepareResult.Failed(UpdateFailure.ASSET_MISMATCH), result)
        assertNoFilesLeft()
    }

    private fun downloader(client: OkHttpClient = this.client) = ApkDownloader(
        client = client,
        trust = trust,
        directory = updateDir(),
    )

    private fun updateDir(): File =
        temporaryFolder.root.resolve("updates").apply { mkdirs() }

    private fun update(
        url: String = apkUrl,
        sha: String = sha256(apkBytes),
        size: Long = apkBytes.size.toLong(),
    ) = AvailableUpdate(
        tagName = "v0.3.4-test",
        versionCode = 11,
        versionName = "0.3.4-test",
        changelog = "",
        apkAssetName = "KuiesLink-v0.3.4-test.apk",
        apkUrl = url,
        apkSizeBytes = size,
        sha256 = sha,
    )

    private fun assertNoFilesLeft() {
        val files = updateDir().listFiles().orEmpty()
        assertFalse(files.any { it.name.endsWith(".apk") || it.name.endsWith(".part") })
    }
}
