package tw.kuies.link

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import tw.kuies.link.ui.LinkViewModel
import tw.kuies.link.ui.ShareDialogScreen
import tw.kuies.link.ui.ShareEntry
import tw.kuies.link.ui.theme.KuiesLinkTheme

/**
 * Translucent share target. It lives in its own task (see the manifest affinity) so the source
 * app's task stays directly behind the dialog; finishing returns there and never reveals the
 * KuiesLink home task.
 */
class ShareActivity : ComponentActivity() {

    private val viewModel: LinkViewModel by viewModels {
        viewModelFactory {
            initializer { createLinkViewModel(applicationContext, ShareEntry.DIALOG) }
        }
    }

    /**
     * The system chooser reports the chosen target through EXTRA_CHOSEN_COMPONENT. A null result
     * means the user backed out, so the dialog stays; when the hand-off really happened we finish
     * and the receiving app owns the screen.
     */
    private val shareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data?.chosenComponent() != null) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        if (savedInstanceState == null) {
            dispatchShareIntent(intent)
        } else if (!viewModel.hasShareState()) {
            // The share state cannot be rebuilt after process death; returning to the source app
            // is better than re-posting the URL or showing an empty dialog.
            finish()
            return
        }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            KuiesLinkTheme(darkTheme = true) {
                ShareDialogScreen(
                    state = state,
                    onCopyShortUrl = ::copyShortUrl,
                    onShareShortUrl = ::launchChooser,
                    onFinish = ::finish,
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

    private fun copyShortUrl(url: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.short_url_copy_label), url),
        )
        // Android 13+ shows its own copy confirmation; only older versions need ours.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.notice_copied_short_url, Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchChooser(url: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        val chooser = Intent.createChooser(send, getString(R.string.short_share_chooser_title)).apply {
            putParcelableArrayListExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayListOf(
                    ComponentName(this@ShareActivity, ShareActivity::class.java),
                    ComponentName(this@ShareActivity, MainActivity::class.java),
                ),
            )
        }
        shareLauncher.launch(chooser)
    }
}

@Suppress("DEPRECATION")
private fun Intent.chosenComponent(): ComponentName? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
    } else {
        getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT)
    }
