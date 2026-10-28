package tw.kuies.link

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import tw.kuies.link.settings.ThemeMode
import tw.kuies.link.ui.LinkApp
import tw.kuies.link.ui.LinkNotice
import tw.kuies.link.ui.LinkViewModel
import tw.kuies.link.ui.ShareRequest
import tw.kuies.link.ui.theme.KuiesLinkTheme

/**
 * The home screen and the manual "paste a URL" flow. Incoming text shares never land here any
 * more: they go to [ShareActivity], which owns a separate task so the source app keeps its
 * screen and this task is never pulled to the front.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: LinkViewModel by viewModels {
        viewModelFactory {
            initializer { createLinkViewModel(applicationContext) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (state.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            KuiesLinkTheme(darkTheme = darkTheme) {
                LinkApp(
                    state = state,
                    onInputChange = viewModel::onInputChange,
                    onPaste = { viewModel.onPasteText(readClipboardText()) },
                    onClean = viewModel::onCleanClick,
                    onClearInput = viewModel::onClearInput,
                    onSelectCandidate = viewModel::onSelectCandidate,
                    onCopyUrl = { url ->
                        copyUrl(url, R.string.result_cleaned_label, LinkNotice.Copied)
                    },
                    onShareUrl = { url -> launchChooser(ShareRequest(url)) },
                    onShorten = viewModel::onShortenClick,
                    onRetryShorten = viewModel::onRetryShorten,
                    onCopyShortUrl = { url ->
                        copyUrl(url, R.string.short_url_copy_label, LinkNotice.CopiedShortUrl)
                    },
                    onShareShortUrl = { url ->
                        launchChooser(
                            request = ShareRequest(url),
                            chooserTitleRes = R.string.short_share_chooser_title,
                        )
                    },
                    onBackToHome = viewModel::onBackToHome,
                    onOpenSettings = viewModel::onOpenSettings,
                    onCloseSettings = viewModel::onCloseSettings,
                    onShareModeChange = viewModel::onShareModeChange,
                    onPreferredShareTargetChange = viewModel::onPreferredShareTargetChange,
                    onThemeModeChange = viewModel::onThemeModeChange,
                    onExpandDetailsChange = viewModel::onExpandDetailsChange,
                    onShowTechnicalInfoChange = viewModel::onShowTechnicalInfoChange,
                    onCheckUpdate = viewModel::onCheckUpdate,
                    onDownloadUpdate = viewModel::onDownloadUpdate,
                    onInstallUpdate = viewModel::onInstallUpdate,
                    onOpenInstallPermission = viewModel::onOpenInstallPermission,
                    onCancelUpdateDownload = viewModel::onCancelUpdateDownload,
                    onNoticeShown = viewModel::onNoticeShown,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    private fun readClipboardText(): String? {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }

    private fun copyUrl(url: String, @StringRes labelRes: Int, notice: LinkNotice) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(labelRes), url))
        // Android 13+ shows its own copy confirmation; only older versions need ours.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            viewModel.showNotice(notice)
        }
    }

    private fun launchChooser(
        request: ShareRequest,
        @StringRes chooserTitleRes: Int = R.string.share_chooser_title,
    ) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, request.url)
        }
        val chooser = Intent.createChooser(send, getString(chooserTitleRes)).apply {
            putParcelableArrayListExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayListOf(ComponentName(this@MainActivity, MainActivity::class.java)),
            )
        }
        // No Activity Result / finish(): closing the sheet must land back on this Activity with
        // the cleaning result intact. The chooser's result code says nothing about a share.
        startActivity(chooser)
    }
}
