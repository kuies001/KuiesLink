package tw.kuies.link.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTrustTest {

    private val trust = UpdateTrust(
        owner = "kuies001",
        repo = "KuiesLink",
        apiBase = "https://api.github.com",
        assetBase = "https://github.com/kuies001/KuiesLink/releases/download/",
    )

    @Test
    fun latestReleaseUrlIsPinnedToTheRepository() {
        assertTrue(
            trust.latestReleaseUrl.endsWith("/repos/kuies001/KuiesLink/releases/latest"),
        )
    }

    @Test
    fun exactReleaseAssetIsAllowed() {
        assertTrue(trust.isAllowedAssetUrl(trust.assetUrl("v0.3.3", "KuiesLink-v0.3.3.apk")))
    }

    @Test
    fun arbitraryHostIsNotAllowed() {
        assertFalse(trust.isAllowedAssetUrl("https://evil.example/KuiesLink-v0.3.3.apk"))
        assertFalse(trust.isAllowedAssetUrl("https://github.com/kuies001/Other/releases/x.apk"))
    }

    @Test
    fun httpIsRejectedInProduction() {
        assertFalse(trust.isAllowedAssetUrl("http://github.com/kuies001/KuiesLink/x.apk"))
    }

    @Test
    fun onlyGitHubDownloadHostsAreAllowedAsRedirects() {
        assertTrue(trust.isAllowedRedirectUrl("https://release-assets.githubusercontent.com/x"))
        assertTrue(trust.isAllowedRedirectUrl("https://objects.githubusercontent.com/x"))
        assertFalse(trust.isAllowedRedirectUrl("https://github.com.evil.example/x"))
        assertFalse(trust.isAllowedRedirectUrl("http://release-assets.githubusercontent.com/x"))
    }

    @Test
    fun releaseBuildTrustRejectsInsecureEndpoints() {
        assertThrows(IllegalArgumentException::class.java) {
            UpdateConfig.trust(
                debuggable = false,
                apiBase = "http://127.0.0.1:8788",
                assetBase = "http://127.0.0.1:8788/",
            )
        }
    }

    @Test
    fun debugTrustOnlyAcceptsLocalhostHttp() {
        val local = UpdateConfig.trust(
            debuggable = true,
            apiBase = "http://127.0.0.1:8788",
            assetBase = "http://127.0.0.1:8788/",
        )
        assertTrue(local.isAllowedAssetUrl("http://127.0.0.1:8788/v0.3.4-test/update.apk"))
        assertFalse(local.isAllowedAssetUrl("http://192.168.1.2:8788/update.apk"))

        assertThrows(IllegalArgumentException::class.java) {
            UpdateConfig.trust(
                debuggable = true,
                apiBase = "http://192.168.1.2:8788",
                assetBase = "http://192.168.1.2:8788/",
            )
        }
    }
}
