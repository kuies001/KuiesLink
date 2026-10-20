package tw.kuies.link.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tw.kuies.link.R
import tw.kuies.link.settings.ShareMode

val LinkNotice.messageRes: Int
    @StringRes get() = when (this) {
        LinkNotice.NoUrlFound -> R.string.notice_no_url
        LinkNotice.MultipleUrls -> R.string.notice_multiple_urls
        LinkNotice.EmptyShare -> R.string.notice_empty_share
        LinkNotice.QuickShareFailed -> R.string.notice_quick_failed
        LinkNotice.QuickShareUnresolved -> R.string.notice_quick_unresolved
        LinkNotice.QuickShareInvalid -> R.string.notice_quick_invalid
        LinkNotice.RestoredOriginal -> R.string.notice_restored
        LinkNotice.ClipboardEmpty -> R.string.notice_clipboard_empty
        LinkNotice.Copied -> R.string.notice_copied
    }

@Composable
fun LinkApp(
    state: LinkUiState,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onClean: () -> Unit,
    onClearInput: () -> Unit,
    onSelectCandidate: (String) -> Unit,
    onCopyUrl: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onRestoreOriginal: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onShareModeChange: (ShareMode) -> Unit,
    onNoticeShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val notice = state.notice
    val noticeMessage = if (notice != null) stringResource(notice.messageRes) else null

    LaunchedEffect(notice) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onNoticeShown()
        }
    }

    BackHandler(enabled = state.showSettings) { onCloseSettings() }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.showSettings) {
            SettingsScreen(
                shareMode = state.shareMode,
                contentPadding = innerPadding,
                onBack = onCloseSettings,
                onShareModeChange = onShareModeChange,
            )
        } else {
            HomeScreen(
                state = state,
                contentPadding = innerPadding,
                onInputChange = onInputChange,
                onPaste = onPaste,
                onClean = onClean,
                onClearInput = onClearInput,
                onSelectCandidate = onSelectCandidate,
                onCopyUrl = onCopyUrl,
                onShareUrl = onShareUrl,
                onRestoreOriginal = onRestoreOriginal,
                onOpenSettings = onOpenSettings,
            )
        }
    }
}
