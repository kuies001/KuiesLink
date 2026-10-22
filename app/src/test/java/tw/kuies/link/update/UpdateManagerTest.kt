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

class UpdateManagerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = TestHttpServer()
    private val trust = localTrust(server.port)
    private val apkBytes = ByteArray(5_000) { index -> (index % 241).toByte() }
    private val apkPath = "/v0.3.4-test/KuiesLink-v0.3.4-test.apk"
    private val manifestPath = "/v0.3.4-test/update.json"
    private val releasePath = "/repos/kuies001/KuiesLink/releases/latest"
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
    fun checkReturnsTheAvailableUpdate() = runTest {
        serve()

        val result = manager().checkForUpdate()

        assertTrue(result is UpdateCheck.Available)
        assertEquals(11L, (result as UpdateCheck.Available).update.versionCode)
        assertEquals(UpdateContract.apkAssetName("0.3.4-test"), result.update.apkAssetName)
    }

    @Test
    fun checkRejectsAnApkUrlOutsideTheRepository() = runTest {
        serve(apkUrl = "https://evil.example/KuiesLink-v0.3.4-test.apk")

        val result = manager().checkForUpdate()

        assertEquals(UpdateCheck.Failed(UpdateFailure.ASSET_MISMATCH), result)
    }

    @Test
    fun checkRejectsAManifestUrlOutsideTheRepository() = runTest {
        serve(manifestUrl = "https://evil.example/update.json")

        val result = manager().checkForUpdate()

        assertEquals(UpdateCheck.Failed(UpdateFailure.ASSET_MISMATCH), result)
    }

    @Test
    fun downloadAndVerifyPassesForMatchingMetadata() = runTest {
        serve()
        val update = (manager().checkForUpdate() as UpdateCheck.Available).update

        val result = manager().downloadAndVerify(update) {}

        assertTrue(result is PrepareResult.Prepared)
        val apk = (result as PrepareResult.Prepared).apk
        assertTrue(apkBytes.contentEquals(apk.readBytes()))
    }

    @Test
    fun downloadAndVerifyDeletesAnApkWithTheWrongSigner() = runTest {
        serve()
        val update = (manager().checkForUpdate() as UpdateCheck.Available).update

        val result = manager(installedSigner = "other").downloadAndVerify(update) {}

        assertEquals(PrepareResult.Failed(UpdateFailure.SIGNATURE_MISMATCH), result)
        assertNoApkFiles()
    }

    private fun manager(installedSigner: String = "signer"): UpdateManager = UpdateManager(
        trust = trust,
        api = UpdateApi(trust, client),
        checker = UpdateChecker(trust, "tw.kuies.link", 9, 36),
        downloader = ApkDownloader(client, trust, updateDir()),
        verifier = ApkVerifier(
            object : ApkMetadataSource {
                override fun readArchive(apk: File): ApkMetadata =
                    ApkMetadata("tw.kuies.link", 11, setOf("signer"))

                override fun readInstalled(packageName: String): ApkMetadata =
                    ApkMetadata(packageName, 9, setOf(installedSigner))
            },
        ),
        packageName = "tw.kuies.link",
        currentVersionCode = 9,
    )

    private fun serve(
        apkUrl: String = apkUrl(),
        manifestUrl: String = manifestUrl(),
    ) {
        val manifest = manifestJson()
        val release = """
            {
              "tag_name": "v0.3.4-test",
              "draft": false,
              "prerelease": false,
              "assets": [
                {"name":"update.json","size":${manifest.toByteArray().size},
                 "browser_download_url":"$manifestUrl"},
                {"name":"${UpdateContract.apkAssetName("0.3.4-test")}","size":${apkBytes.size},
                 "browser_download_url":"$apkUrl"}
              ]
            }
        """.trimIndent()
        server.text(releasePath, release)
        server.text(manifestPath, manifest)
        server.bytes(apkPath, apkBytes)
    }

    private fun manifestJson(): String = """
        {
          "schema": 1,
          "packageName": "tw.kuies.link",
          "versionCode": 11,
          "versionName": "0.3.4-test",
          "apkAsset": "${UpdateContract.apkAssetName("0.3.4-test")}",
          "sha256": "${sha256(apkBytes)}",
          "sizeBytes": ${apkBytes.size},
          "minSdk": 28,
          "changelog": "測試更新"
        }
    """.trimIndent()

    private fun apkUrl(): String = "http://127.0.0.1:${server.port}$apkPath"

    private fun manifestUrl(): String = "http://127.0.0.1:${server.port}$manifestPath"

    private fun updateDir(): File =
        temporaryFolder.root.resolve("updates").apply { mkdirs() }

    private fun assertNoApkFiles() {
        val files = updateDir().listFiles().orEmpty()
        assertFalse(files.any { it.name.endsWith(".apk") || it.name.endsWith(".part") })
    }
}
