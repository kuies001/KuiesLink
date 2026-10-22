package tw.kuies.link.update

import java.io.File

data class ApkMetadata(
    val packageName: String,
    val versionCode: Long,
    val signerSha256: Set<String>,
)

/** Seam so the signature/package rules can be unit-tested without an Android PackageManager. */
interface ApkMetadataSource {
    fun readArchive(apk: File): ApkMetadata?
    fun readInstalled(packageName: String): ApkMetadata?
}

class ApkVerifier(private val source: ApkMetadataSource) {
    fun verify(
        apk: File,
        packageName: String,
        expectedVersionCode: Long,
        currentVersionCode: Long,
    ): UpdateFailure? {
        val archive = source.readArchive(apk) ?: return UpdateFailure.PACKAGE_MISMATCH
        if (archive.packageName != packageName) return UpdateFailure.PACKAGE_MISMATCH
        if (archive.versionCode != expectedVersionCode) return UpdateFailure.VERSION_MISMATCH
        if (archive.versionCode <= currentVersionCode) return UpdateFailure.VERSION_MISMATCH

        val installed = source.readInstalled(packageName) ?: return UpdateFailure.SIGNATURE_MISMATCH
        if (installed.signerSha256.isEmpty() || archive.signerSha256.isEmpty()) {
            return UpdateFailure.SIGNATURE_MISMATCH
        }
        if (installed.signerSha256 != archive.signerSha256) {
            return UpdateFailure.SIGNATURE_MISMATCH
        }
        return null
    }
}
