package tw.kuies.link.update

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import java.io.File
import java.security.MessageDigest

/**
 * Reads the archive and the installed package through PackageManager. minSdk is 28, so
 * GET_SIGNING_CERTIFICATES is always available.
 */
class AndroidApkMetadataSource(context: Context) : ApkMetadataSource {
    private val packageManager = context.packageManager

    override fun readArchive(apk: File): ApkMetadata? {
        val info = packageManager.getPackageArchiveInfo(
            apk.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES,
        ) ?: return null
        val signers = info.signingInfo?.apkContentsSigners ?: return null
        return ApkMetadata(
            packageName = info.packageName,
            versionCode = info.longVersionCode,
            signerSha256 = signers.digestSet(),
        )
    }

    override fun readInstalled(packageName: String): ApkMetadata? {
        val info = packageManager.getPackageInfo(
            packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val signers = info.signingInfo?.apkContentsSigners ?: return null
        return ApkMetadata(
            packageName = info.packageName,
            versionCode = info.longVersionCode,
            signerSha256 = signers.digestSet(),
        )
    }

    private fun Array<Signature>.digestSet(): Set<String> = map { signature ->
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }.toSet()
}
