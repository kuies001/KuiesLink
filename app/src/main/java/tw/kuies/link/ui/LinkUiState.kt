package tw.kuies.link.ui

import tw.kuies.link.domain.CleaningResult
import tw.kuies.link.settings.ShareMode

data class LinkUiState(
    val input: String = "",
    val result: CleaningResult? = null,
    val candidates: List<String> = emptyList(),
    val shareMode: ShareMode = ShareMode.DEFAULT,
    val settingsLoaded: Boolean = false,
    val showSettings: Boolean = false,
    val isCleaning: Boolean = false,
    val notice: LinkNotice? = null,
)

enum class LinkNotice {
    NoUrlFound,
    MultipleUrls,
    EmptyShare,
    QuickShareNoChange,
    QuickShareInvalid,
    QuickShareFailed,
    RestoredOriginal,
    ClipboardEmpty,
    Copied,
}

data class ShareRequest(
    val url: String,
    val closeAfterShare: Boolean,
)
