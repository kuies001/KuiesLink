package tw.kuies.link

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import tw.kuies.link.data.OkHttpShortUrlRepository
import tw.kuies.link.data.ShortUrlApiClient
import tw.kuies.link.settings.DataStoreSettingsRepository
import tw.kuies.link.settings.linkSettingsDataStore
import tw.kuies.link.ui.LinkViewModel
import tw.kuies.link.ui.ShareEntry
import tw.kuies.link.update.AndroidApkMetadataSource
import tw.kuies.link.update.AndroidUpdateInstaller
import tw.kuies.link.update.ApkDownloader
import tw.kuies.link.update.ApkVerifier
import tw.kuies.link.update.DefaultUpdateCoordinator
import tw.kuies.link.update.UpdateApi
import tw.kuies.link.update.UpdateChecker
import tw.kuies.link.update.UpdateConfig
import tw.kuies.link.update.UpdateManager

/**
 * Builds the same cleaning pipeline for the home screen and the external-share dialog. Only the
 * presentation entry differs; cleaning rules, shortener and settings stay identical.
 */
fun createLinkViewModel(
    context: Context,
    shareEntry: ShareEntry = ShareEntry.HOME,
): LinkViewModel {
    val appContext = context.applicationContext
    val repository = OkHttpShortUrlRepository(
        ShortUrlApiClient(
            baseUrl = appContext.getString(R.string.shortener_api_base_url),
            // Only a debuggable build may talk plain HTTP, and only to localhost (the end-to-end
            // check uses `adb reverse` to a local backend).
            allowLocalhostHttp = appContext.isDebuggable(),
        ),
    )
    return LinkViewModel(
        settingsRepository = DataStoreSettingsRepository(appContext.linkSettingsDataStore),
        shortenRequest = repository::shorten,
        updater = buildUpdateCoordinator(appContext),
        shareEntry = shareEntry,
    )
}

private fun Context.isDebuggable(): Boolean =
    (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

private fun buildUpdateCoordinator(context: Context): DefaultUpdateCoordinator {
    val trust = UpdateConfig.trust(
        debuggable = context.isDebuggable(),
        apiBase = BuildConfig.UPDATE_API_BASE,
        assetBase = BuildConfig.UPDATE_ASSET_BASE,
    )
    val versionCode = context.installedVersionCode()
    val manager = UpdateManager(
        trust = trust,
        api = UpdateApi(trust),
        checker = UpdateChecker(trust, context.packageName, versionCode, Build.VERSION.SDK_INT),
        downloader = ApkDownloader(
            client = UpdateApi.downloadClient(),
            trust = trust,
            directory = File(context.cacheDir, "updates"),
        ),
        verifier = ApkVerifier(AndroidApkMetadataSource(context)),
        packageName = context.packageName,
        currentVersionCode = versionCode,
    )
    return DefaultUpdateCoordinator(manager, AndroidUpdateInstaller(context))
}

private fun Context.installedVersionCode(): Long {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    return info.longVersionCode
}
