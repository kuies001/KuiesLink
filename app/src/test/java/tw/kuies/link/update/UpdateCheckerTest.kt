package tw.kuies.link.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private val trust = UpdateTrust(
        owner = "kuies001",
        repo = "KuiesLink",
        apiBase = "https://api.github.com",
        assetBase = "https://github.com/kuies001/KuiesLink/releases/download/",
    )
    private val checker = UpdateChecker(
        trust = trust,
        packageName = "tw.kuies.link",
        currentVersionCode = 9,
        deviceSdk = 36,
    )

    @Test
    fun newerVersionCodeIsAvailable() {
        val result = checker.evaluate(release(), manifest())
        assertTrue(result is UpdateCheck.Available)
        val update = (result as UpdateCheck.Available).update
        assertEquals(11L, update.versionCode)
        assertEquals("0.3.4-test", update.versionName)
        assertEquals(ASSET_NAME, update.apkAssetName)
        assertEquals(SHA, update.sha256)
    }

    @Test
    fun sameVersionIsUpToDate() {
        val result = checker.evaluate(release(), manifest(versionCode = 9))
        assertEquals(UpdateCheck.UpToDate, result)
    }

    @Test
    fun olderVersionIsNotADowngrade() {
        val result = checker.evaluate(release(), manifest(versionCode = 8))
        assertEquals(UpdateCheck.UpToDate, result)
    }

    @Test
    fun draftReleaseIsRejected() {
        val result = checker.evaluate(release(draft = true), manifest())
        assertEquals(UpdateCheck.Failed(UpdateFailure.NO_STABLE_RELEASE), result)
    }

    @Test
    fun prereleaseIsRejected() {
        val result = checker.evaluate(release(prerelease = true), manifest())
        assertEquals(UpdateCheck.Failed(UpdateFailure.NO_STABLE_RELEASE), result)
    }

    @Test
    fun malformedManifestIsRejected() {
        val result = checker.evaluate(release(), "{not-json")
        assertEquals(UpdateCheck.Failed(UpdateFailure.MANIFEST_INVALID), result)
    }

    @Test
    fun missingManifestFieldsAreRejected() {
        val result = checker.evaluate(release(), """{"schema":1,"packageName":"tw.kuies.link"}""")
        assertEquals(UpdateCheck.Failed(UpdateFailure.MANIFEST_INVALID), result)
    }

    @Test
    fun wrongPackageNameIsRejected() {
        val result = checker.evaluate(release(), manifest(packageName = "com.evil.app"))
        assertEquals(UpdateCheck.Failed(UpdateFailure.PACKAGE_MISMATCH), result)
    }

    @Test
    fun tagMustMatchManifestVersionName() {
        val result = checker.evaluate(release(tag = "v9.9.9"), manifest())
        assertEquals(UpdateCheck.Failed(UpdateFailure.MANIFEST_INVALID), result)
    }

    @Test
    fun manifestMustListTheConventionalAssetName() {
        val result = checker.evaluate(release(), manifest(apkAsset = "update.apk"))
        assertEquals(UpdateCheck.Failed(UpdateFailure.ASSET_MISMATCH), result)
    }

    @Test
    fun assetMustBelongToThePinnedRepository() {
        val result = checker.evaluate(
            release(apkUrl = "https://evil.example/KuiesLink-v0.3.4-test.apk"),
            manifest(),
        )
        assertEquals(UpdateCheck.Failed(UpdateFailure.ASSET_MISMATCH), result)
    }

    @Test
    fun assetSizeMustMatchTheManifest() {
        val result = checker.evaluate(release(apkSize = 1), manifest())
        assertEquals(UpdateCheck.Failed(UpdateFailure.ASSET_MISMATCH), result)
    }

    @Test
    fun updateJsonAssetIsRequired() {
        val result = checker.evaluate(release(includeManifest = false), manifest())
        assertEquals(UpdateCheck.Failed(UpdateFailure.MANIFEST_MISSING), result)
    }

    @Test
    fun apkAssetIsRequired() {
        val result = checker.evaluate(release(includeApk = false), manifest())
        assertEquals(UpdateCheck.Failed(UpdateFailure.ASSET_MISSING), result)
    }

    @Test
    fun newerMinSdkIsIncompatible() {
        val result = checker.evaluate(release(), manifest(minSdk = 37))
        assertEquals(UpdateCheck.Failed(UpdateFailure.INCOMPATIBLE), result)
    }

    @Test
    fun invalidHashIsRejected() {
        val result = checker.evaluate(release(), manifest(sha = "abc"))
        assertEquals(UpdateCheck.Failed(UpdateFailure.MANIFEST_INVALID), result)
    }

    @Test
    fun schemaMustMatch() {
        val result = checker.evaluate(release(), manifest(schema = 2))
        assertEquals(UpdateCheck.Failed(UpdateFailure.MANIFEST_INVALID), result)
    }

    private fun release(
        tag: String = "v0.3.4-test",
        draft: Boolean = false,
        prerelease: Boolean = false,
        apkSize: Long = APK_SIZE,
        apkUrl: String = apkUrl(),
        includeManifest: Boolean = true,
        includeApk: Boolean = true,
    ): GitHubRelease {
        val assets = buildList {
            if (includeManifest) {
                add(GitHubAsset("update.json", 100, manifestUrl()))
            }
            if (includeApk) {
                add(GitHubAsset(ASSET_NAME, apkSize, apkUrl))
            }
        }
        return GitHubRelease(tag, draft, prerelease, assets)
    }

    private fun manifest(
        schema: Int = 1,
        packageName: String = "tw.kuies.link",
        versionCode: Long = 11,
        minSdk: Int = 28,
        apkAsset: String = ASSET_NAME,
        sha: String = SHA,
    ): String = """
        {
          "schema": $schema,
          "packageName": "$packageName",
          "versionCode": $versionCode,
          "versionName": "0.3.4-test",
          "apkAsset": "$apkAsset",
          "sha256": "$sha",
          "sizeBytes": $APK_SIZE,
          "minSdk": $minSdk,
          "changelog": "測試更新"
        }
    """.trimIndent()

    private fun manifestUrl() =
        "https://github.com/kuies001/KuiesLink/releases/download/v0.3.4-test/update.json"

    private fun apkUrl() =
        "https://github.com/kuies001/KuiesLink/releases/download/v0.3.4-test/$ASSET_NAME"

    private companion object {
        const val ASSET_NAME = "KuiesLink-v0.3.4-test.apk"
        const val APK_SIZE = 12_345L
        val SHA = "a".repeat(64)
    }
}
