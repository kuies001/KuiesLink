package tw.kuies.link

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import tw.kuies.link.settings.DataStoreSettingsRepository
import tw.kuies.link.settings.linkSettingsDataStore
import tw.kuies.link.domain.CleaningStatus
import tw.kuies.link.ui.LinkApp
import tw.kuies.link.ui.LinkNotice
import tw.kuies.link.ui.LinkViewModel
import tw.kuies.link.ui.QuickShareNotice
import tw.kuies.link.ui.ShareRequest
import tw.kuies.link.ui.theme.KuiesLinkTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LinkViewModel by viewModels {
        viewModelFactory {
            initializer {
                LinkViewModel(
                    settingsRepository = DataStoreSettingsRepository(
                        applicationContext.linkSettingsDataStore,
                    ),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A restored instance (rotation, process death) re-delivers the original intent; the
        // ViewModel already owns that state, so only a genuinely new launch is dispatched.
        if (savedInstanceState == null) {
            dispatchShareIntent(intent)
        }

        setContent {
            KuiesLinkTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(viewModel) {
                    viewModel.shareRequests.collect { request -> launchChooser(request) }
                }

                LinkApp(
                    state = state,
                    onInputChange = viewModel::onInputChange,
                    onPaste = { viewModel.onPasteText(readClipboardText()) },
                    onClean = viewModel::onCleanClick,
                    onClearInput = viewModel::onClearInput,
                    onSelectCandidate = viewModel::onSelectCandidate,
                    onCopyUrl = { url -> copyUrl(url) },
                    onShareUrl = { url -> launchChooser(ShareRequest(url)) },
                    onRestoreOriginal = viewModel::onRestoreOriginal,
                    onOpenSettings = viewModel::onOpenSettings,
                    onCloseSettings = viewModel::onCloseSettings,
                    onShareModeChange = viewModel::onShareModeChange,
                    onNoticeShown = viewModel::onNoticeShown,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchShareIntent(intent)
    }

    private fun dispatchShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val type = intent.type
        if (type != null && !type.startsWith("text/", ignoreCase = true)) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        viewModel.onShareIntent(text)
    }

    private fun readClipboardText(): String? {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }

    private fun copyUrl(url: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.result_cleaned_label), url),
        )
        // Android 13+ shows its own copy confirmation; only older versions need ours.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            viewModel.showNotice(LinkNotice.Copied)
        }
    }

    private fun launchChooser(request: ShareRequest) {
        // Shown right before the sheet opens; one toast per share event, never re-fired by
        // recomposition or Activity recreation (the request itself is one-shot).
        request.quickNotice?.let(::showQuickNotice)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, request.url)
        }
        val chooser = Intent.createChooser(send, getString(R.string.share_chooser_title)).apply {
            putParcelableArrayListExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayListOf(ComponentName(this@MainActivity, MainActivity::class.java)),
            )
        }
        // No Activity Result / finish(): closing the sheet must land back on this Activity with
        // the cleaning result intact. The chooser's result code says nothing about a share.
        startActivity(chooser)
    }

    private fun showQuickNotice(notice: QuickShareNotice) {
        val message = when (notice.status) {
            CleaningStatus.CLEANED -> getString(R.string.quick_notice_cleaned, notice.removedCount)
            CleaningStatus.UNCHANGED -> getString(R.string.quick_notice_unchanged)
            else -> return
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
