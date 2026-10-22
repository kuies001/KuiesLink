package tw.kuies.link.update

/**
 * The only place a release or APK URL is allowed to come from. Production pins the repository and
 * the download prefix; the debug build may point both at localhost for an end-to-end check.
 */
data class UpdateTrust(
    val owner: String,
    val repo: String,
    val apiBase: String,
    val assetBase: String,
    val allowInsecureLocalhost: Boolean = false,
) {
    init {
        val apiAllowed = apiBase.startsWith("https://") || (allowInsecureLocalhost && isLocal(apiBase))
        val assetAllowed =
            assetBase.startsWith("https://") || (allowInsecureLocalhost && isLocal(assetBase))
        require(apiAllowed && assetAllowed) { "Update endpoints must use HTTPS" }
    }

    val latestReleaseUrl: String
        get() = "${apiBase.trimEnd('/')}/repos/$owner/$repo/releases/latest"

    fun assetUrl(tagName: String, assetName: String): String =
        "${assetBase.trimEnd('/')}/$tagName/$assetName"

    fun isAllowedAssetUrl(url: String): Boolean {
        if (!url.startsWith(assetBase.trimEnd('/') + "/")) return false
        if (url.startsWith("https://")) return true
        return allowInsecureLocalhost && isLocal(url)
    }

    /** GitHub redirects release assets to a separate download host; every hop must stay there. */
    fun isAllowedRedirectUrl(url: String): Boolean {
        if (allowInsecureLocalhost && isLocal(url)) return true
        if (!url.startsWith("https://")) return false
        val host = url.removePrefix("https://").substringBefore('/').substringBefore(':')
        return host in ALLOWED_REDIRECT_HOSTS
    }

    private fun isLocal(url: String): Boolean =
        url.startsWith("http://127.0.0.1:") || url.startsWith("http://localhost:")

    private companion object {
        val ALLOWED_REDIRECT_HOSTS = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )
    }
}

object UpdateContract {
    const val OWNER = "kuies001"
    const val REPO = "KuiesLink"
    const val SCHEMA = 1
    const val MANIFEST_ASSET = "update.json"
    const val APK_PREFIX = "KuiesLink-v"
    const val APK_SUFFIX = ".apk"
    const val MAX_MANIFEST_BYTES = 32L * 1024
    const val MAX_APK_BYTES = 128L * 1024 * 1024
    const val MAX_VERSION_CODE = 2_100_000_000L

    fun apkAssetName(versionName: String): String = "$APK_PREFIX$versionName$APK_SUFFIX"
}
