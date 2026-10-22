package tw.kuies.link.update

/**
 * Pure decision layer: everything here can be unit-tested with raw GitHub/manifest JSON. A release
 * is accepted only when the tag, manifest, asset name, asset URL, size and hashes agree.
 */
class UpdateChecker(
    private val trust: UpdateTrust,
    private val packageName: String,
    private val currentVersionCode: Long,
    private val deviceSdk: Int,
) {
    fun evaluate(release: GitHubRelease, manifestText: String): UpdateCheck {
        if (release.draft || release.prerelease) {
            return UpdateCheck.Failed(UpdateFailure.NO_STABLE_RELEASE)
        }
        val manifest = UpdateJson.parseManifest(manifestText)
            ?: return UpdateCheck.Failed(UpdateFailure.MANIFEST_INVALID)

        if (manifest.schema != UpdateContract.SCHEMA) {
            return UpdateCheck.Failed(UpdateFailure.MANIFEST_INVALID)
        }
        if (manifest.packageName != packageName) {
            return UpdateCheck.Failed(UpdateFailure.PACKAGE_MISMATCH)
        }
        if (!VERSION_NAME.matches(manifest.versionName)) {
            return UpdateCheck.Failed(UpdateFailure.MANIFEST_INVALID)
        }
        if (release.tagName != "v${manifest.versionName}") {
            return UpdateCheck.Failed(UpdateFailure.MANIFEST_INVALID)
        }
        if (manifest.versionCode <= 0 || manifest.versionCode > UpdateContract.MAX_VERSION_CODE) {
            return UpdateCheck.Failed(UpdateFailure.MANIFEST_INVALID)
        }
        if (manifest.minSdk <= 0 || manifest.minSdk > deviceSdk) {
            return UpdateCheck.Failed(UpdateFailure.INCOMPATIBLE)
        }
        if (manifest.sizeBytes <= 0 || manifest.sizeBytes > UpdateContract.MAX_APK_BYTES) {
            return UpdateCheck.Failed(UpdateFailure.APK_TOO_LARGE)
        }
        if (!SHA_256.matches(manifest.sha256)) {
            return UpdateCheck.Failed(UpdateFailure.MANIFEST_INVALID)
        }
        val expectedApkName = UpdateContract.apkAssetName(manifest.versionName)
        if (manifest.apkAsset != expectedApkName) {
            return UpdateCheck.Failed(UpdateFailure.ASSET_MISMATCH)
        }

        val manifestAsset = release.assets.firstOrNull { it.name == UpdateContract.MANIFEST_ASSET }
            ?: return UpdateCheck.Failed(UpdateFailure.MANIFEST_MISSING)
        if (manifestAsset.downloadUrl != trust.assetUrl(release.tagName, UpdateContract.MANIFEST_ASSET)) {
            return UpdateCheck.Failed(UpdateFailure.ASSET_MISMATCH)
        }

        val apkAsset = release.assets.firstOrNull { it.name == expectedApkName }
            ?: return UpdateCheck.Failed(UpdateFailure.ASSET_MISSING)
        if (apkAsset.size != manifest.sizeBytes) {
            return UpdateCheck.Failed(UpdateFailure.ASSET_MISMATCH)
        }
        if (apkAsset.downloadUrl != trust.assetUrl(release.tagName, expectedApkName)) {
            return UpdateCheck.Failed(UpdateFailure.ASSET_MISMATCH)
        }

        if (manifest.versionCode <= currentVersionCode) {
            return UpdateCheck.UpToDate
        }
        return UpdateCheck.Available(
            AvailableUpdate(
                tagName = release.tagName,
                versionCode = manifest.versionCode,
                versionName = manifest.versionName,
                changelog = manifest.changelog.trim().take(MAX_CHANGELOG_CHARS),
                apkAssetName = expectedApkName,
                apkUrl = apkAsset.downloadUrl,
                apkSizeBytes = manifest.sizeBytes,
                sha256 = manifest.sha256.lowercase(),
            ),
        )
    }

    private companion object {
        val VERSION_NAME = Regex("""^\d+(\.\d+){1,3}(-[0-9A-Za-z.-]+)?$""")
        val SHA_256 = Regex("""^[0-9a-fA-F]{64}$""")
        const val MAX_CHANGELOG_CHARS = 4000
    }
}
