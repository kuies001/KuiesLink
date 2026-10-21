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
import tw.kuies.link.settings.PreferredShareTarget
import tw.kuies.link.settings.ShareMode
import tw.kuies.link.settings.ThemeMode

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
        LinkNotice.CopiedShortUrl -> R.string.notice_copied_short_url
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
    onShorten: () -> Unit,
    onRetryShorten: () -> Unit,
    onCopyShortUrl: (String) -> Unit,
    onShareShortUrl: (String) -> Unit,
    onBackToHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onShareModeChange: (ShareMode) -> Unit,
    onPreferredShareTargetChange: (PreferredShareTarget) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onExpandDetailsChange: (Boolean) -> Unit,
    onShowTechnicalInfoChange: (Boolean) -> Unit,
    onNoticeShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val notice = state.notice
    // NoUrlFound is shown inline under the input; a snackbar would be a second, louder warning.
    val noticeMessage = if (notice != null && notice != LinkNotice.NoUrlFound) {
        stringResource(notice.messageRes)
    } else {
        null
    }

    LaunchedEffect(notice) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onNoticeShown()
        }
    }

    BackHandler(enabled = state.showSettings) { onCloseSettings() }
    BackHandler(enabled = !state.showSettings && state.result != null) { onBackToHome() }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            state.showSettings -> SettingsScreen(
                state = state,
                contentPadding = innerPadding,
                onBack = onCloseSettings,
                onShareModeChange = onShareModeChange,
                onPreferredShareTargetChange = onPreferredShareTargetChange,
                onThemeModeChange = onThemeModeChange,
                onExpandDetailsChange = onExpandDetailsChange,
                onShowTechnicalInfoChange = onShowTechnicalInfoChange,
            )

            state.result != null -> ResultScreen(
                state = state,
                contentPadding = innerPadding,
                onBack = onBackToHome,
                onCopyUrl = onCopyUrl,
                onShareUrl = onShareUrl,
                onShorten = onShorten,
                onRetryShorten = onRetryShorten,
                onRetryClean = onClean,
                onCopyShortUrl = onCopyShortUrl,
                onShareShortUrl = onShareShortUrl,
            )

            else -> HomeScreen(
                state = state,
                contentPadding = innerPadding,
                onInputChange = onInputChange,
                onPaste = onPaste,
                onClean = onClean,
                onClearInput = onClearInput,
                onSelectCandidate = onSelectCandidate,
                onOpenSettings = onOpenSettings,
            )
        }
    }
}
