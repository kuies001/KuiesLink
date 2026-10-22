package tw.kuies.link.update

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateApiTest {

    private val server = TestHttpServer()
    private val trust = localTrust(server.port)
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun stableReleaseJsonIsParsed() = runTest {
        server.text(RELEASE_PATH, RELEASE_JSON)

        val result = UpdateApi(trust, client).fetchLatestRelease()

        assertTrue(result is ApiResult.Success)
        assertEquals("v0.3.4-test", (result as ApiResult.Success).value.tagName)
    }

    @Test
    fun missingReleaseIsReported() = runTest {
        server.status(RELEASE_PATH, 404)

        val result = UpdateApi(trust, client).fetchLatestRelease()

        assertEquals(ApiResult.Failure(UpdateFailure.RELEASE_NOT_FOUND), result)
    }

    @Test
    fun serverErrorIsReported() = runTest {
        server.status(RELEASE_PATH, 500)

        val result = UpdateApi(trust, client).fetchLatestRelease()

        assertEquals(ApiResult.Failure(UpdateFailure.NETWORK), result)
    }

    @Test
    fun rateLimitIsReported() = runTest {
        server.status(RELEASE_PATH, 403, mapOf("X-RateLimit-Remaining" to "0"))

        val result = UpdateApi(trust, client).fetchLatestRelease()

        assertEquals(ApiResult.Failure(UpdateFailure.RATE_LIMITED), result)
    }

    @Test
    fun malformedReleaseJsonIsRejected() = runTest {
        server.text(RELEASE_PATH, "{not-json")

        val result = UpdateApi(trust, client).fetchLatestRelease()

        assertEquals(ApiResult.Failure(UpdateFailure.MANIFEST_INVALID), result)
    }

    @Test
    fun timeoutIsReported() = runTest {
        server.handle(RELEASE_PATH) { Thread.sleep(1_500) }
        val shortTimeoutClient = client.newBuilder()
            .readTimeout(200, TimeUnit.MILLISECONDS)
            .callTimeout(500, TimeUnit.MILLISECONDS)
            .build()

        val result = UpdateApi(trust, shortTimeoutClient).fetchLatestRelease()

        assertEquals(ApiResult.Failure(UpdateFailure.TIMEOUT), result)
    }

    @Test
    fun manifestUrlOutsideTheRepositoryIsRejectedWithoutNetwork() = runTest {
        val result = UpdateApi(trust, client).fetchManifest("https://evil.example/update.json")

        assertEquals(ApiResult.Failure(UpdateFailure.ASSET_MISMATCH), result)
    }

    private companion object {
        const val RELEASE_PATH = "/repos/kuies001/KuiesLink/releases/latest"
        const val RELEASE_JSON =
            """{"tag_name":"v0.3.4-test","draft":false,"prerelease":false,"assets":[]}"""
    }
}
