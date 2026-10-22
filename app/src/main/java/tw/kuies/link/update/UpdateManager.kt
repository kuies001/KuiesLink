package tw.kuies.link.update

import java.io.File

/**
 * End-to-end gate for one update: release -> manifest -> download -> hash -> archive checks.
 * Nothing reaches the installer unless every check passed.
 */
class UpdateManager(
    private val trust: UpdateTrust,
    private val api: UpdateApi,
    private val checker: UpdateChecker,
    private val downloader: ApkDownloader,
    private val verifier: ApkVerifier,
    private val packageName: String,
    private val currentVersionCode: Long,
) {
    suspend fun checkForUpdate(): UpdateCheck {
        val release = when (val result = api.fetchLatestRelease()) {
            is ApiResult.Failure -> return UpdateCheck.Failed(result.failure)
            is ApiResult.Success -> result.value
        }
        if (release.draft || release.prerelease) {
            return UpdateCheck.Failed(UpdateFailure.NO_STABLE_RELEASE)
        }
        if (!SAFE_TAG.matches(release.tagName) || release.tagName.contains("..")) {
            return UpdateCheck.Failed(UpdateFailure.MANIFEST_INVALID)
        }
        val manifestAsset = release.assets.firstOrNull {
            it.name == UpdateContract.MANIFEST_ASSET
        } ?: return UpdateCheck.Failed(UpdateFailure.MANIFEST_MISSING)
        if (manifestAsset.size > UpdateContract.MAX_MANIFEST_BYTES) {
            return UpdateCheck.Failed(UpdateFailure.MANIFEST_INVALID)
        }
        val manifestUrl = trust.assetUrl(release.tagName, UpdateContract.MANIFEST_ASSET)
        if (manifestAsset.downloadUrl != manifestUrl) {
            return UpdateCheck.Failed(UpdateFailure.ASSET_MISMATCH)
        }
        val manifestText = when (val result = api.fetchManifest(manifestUrl)) {
            is ApiResult.Failure -> return UpdateCheck.Failed(result.failure)
            is ApiResult.Success -> result.value
        }
        return checker.evaluate(release, manifestText)
    }

    suspend fun downloadAndVerify(
        update: AvailableUpdate,
        onProgress: (Int) -> Unit,
    ): PrepareResult {
        val file = when (val result = downloader.download(update, onProgress)) {
            is PrepareResult.Failed -> return result
            is PrepareResult.Prepared -> result.apk
        }
        val failure = verifier.verify(
            apk = file,
            packageName = packageName,
            expectedVersionCode = update.versionCode,
            currentVersionCode = currentVersionCode,
        )
        if (failure != null) {
            file.delete()
            return PrepareResult.Failed(failure)
        }
        return PrepareResult.Prepared(file)
    }

    private companion object {
        val SAFE_TAG = Regex("""^v\d+(\.\d+){1,3}(-[0-9A-Za-z.-]+)?$""")
    }
}

/** The ViewModel-facing seam; MainActivity wires the real HTTP/PackageManager implementation. */
interface UpdateCoordinator {
    suspend fun checkForUpdate(): UpdateCheck
    suspend fun downloadAndVerify(
        update: AvailableUpdate,
        onProgress: (Int) -> Unit,
    ): PrepareResult

    fun canInstallPackages(): Boolean
    fun openInstallPermissionSettings(): Boolean
    fun launchInstaller(apk: File): Boolean
    fun discard(apk: File)
}

class DefaultUpdateCoordinator(
    private val manager: UpdateManager,
    private val installer: UpdateInstaller,
) : UpdateCoordinator {
    override suspend fun checkForUpdate(): UpdateCheck = manager.checkForUpdate()

    override suspend fun downloadAndVerify(
        update: AvailableUpdate,
        onProgress: (Int) -> Unit,
    ): PrepareResult = manager.downloadAndVerify(update, onProgress)

    override fun canInstallPackages(): Boolean = installer.canInstallPackages()

    override fun openInstallPermissionSettings(): Boolean =
        installer.openInstallPermissionSettings()

    override fun launchInstaller(apk: File): Boolean = installer.launchInstaller(apk)

    override fun discard(apk: File) {
        runCatching { apk.delete() }
    }
}

object UpdateConfig {
    /** Release builds always use the pinned GitHub endpoints; only debug may point at localhost. */
    fun trust(debuggable: Boolean, apiBase: String, assetBase: String): UpdateTrust {
        val api = apiBase.trim().trimEnd('/')
        val asset = assetBase.trim().trimEnd('/') + "/"
        val endpointsAreHttps = api.startsWith("https://") && asset.startsWith("https://")
        if (!endpointsAreHttps) {
            require(debuggable) { "Update endpoints must use HTTPS" }
            require(isLocal(api) && isLocal(asset)) { "Only localhost may use HTTP in debug" }
        }
        return UpdateTrust(
            owner = UpdateContract.OWNER,
            repo = UpdateContract.REPO,
            apiBase = api,
            assetBase = asset,
            allowInsecureLocalhost = !endpointsAreHttps,
        )
    }

    private fun isLocal(url: String): Boolean =
        url.startsWith("http://127.0.0.1:") || url.startsWith("http://localhost:")
}
