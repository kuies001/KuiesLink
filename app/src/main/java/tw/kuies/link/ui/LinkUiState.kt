package tw.kuies.link.ui

import tw.kuies.link.domain.CleaningResult
import tw.kuies.link.domain.CleaningStatus
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
    QuickShareFailed,
    QuickShareUnresolved,
    QuickShareInvalid,
    RestoredOriginal,
    ClipboardEmpty,
    Copied,
}

data class ShareRequest(
    val url: String,
    val closeAfterShare: Boolean,
    /**
     * Set only for quick-mode auto shares; the host shows it once, before the system chooser, and
     * manual shares keep it null so they never show a cleaning report.
     */
    val quickNotice: QuickShareNotice? = null,
)

data class QuickShareNotice(
    val status: CleaningStatus,
    val removedCount: Int,
)
