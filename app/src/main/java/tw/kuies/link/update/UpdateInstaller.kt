package tw.kuies.link.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

interface UpdateInstaller {
    fun canInstallPackages(): Boolean
    fun openInstallPermissionSettings(): Boolean
    fun launchInstaller(apk: File): Boolean
}

/** Official install path: FileProvider + the system package installer, always user-confirmed. */
class AndroidUpdateInstaller(private val context: Context) : UpdateInstaller {

    override fun canInstallPackages(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    override fun openInstallPermissionSettings(): Boolean = runCatching {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    override fun launchInstaller(apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
