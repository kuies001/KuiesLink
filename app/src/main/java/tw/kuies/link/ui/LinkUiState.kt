package tw.kuies.link.ui

import tw.kuies.link.data.ShortenError
import tw.kuies.link.data.ShortenResponse
import tw.kuies.link.domain.CleaningResult
import tw.kuies.link.domain.CleaningStatus
import tw.kuies.link.settings.PreferredShareTarget
import tw.kuies.link.settings.ShareMode

data class LinkUiState(
    val input: String = "",
    val result: CleaningResult? = null,
    val candidates: List<String> = emptyList(),
    val shareMode: ShareMode = ShareMode.DEFAULT,
    val preferredShareTarget: PreferredShareTarget = PreferredShareTarget.DEFAULT,
    val settingsLoaded: Boolean = false,
    val showSettings: Boolean = false,
    val isCleaning: Boolean = false,
    val isShortening: Boolean = false,
    val shortResult: ShortenResponse? = null,
    val shortenError: ShortenError? = null,
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
    CopiedShortUrl,
}

data class ShareRequest(
    val url: String,
    /**
     * Set only for quick-mode auto shares; the host shows it once, before the system chooser, and
     * manual shares keep it null so they never show a cleaning report.
     */
    val quickNotice: QuickShareNotice? = null,
)

data class QuickShareNotice(
    val status: CleaningStatus,
    val removedCount: Int,
    /** The shared URL is a KuiesLink short URL, so the notice must not claim local cleaning. */
    val isShortUrl: Boolean = false,
)
