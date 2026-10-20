package tw.kuies.link.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.kuies.link.domain.CleaningResult
import tw.kuies.link.domain.CleaningStatus
import tw.kuies.link.domain.ExtractionResult
import tw.kuies.link.domain.UrlCleaner
import tw.kuies.link.domain.UrlExtractor
import tw.kuies.link.settings.SettingsRepository
import tw.kuies.link.settings.ShareMode

class LinkViewModel(
    private val settingsRepository: SettingsRepository,
    private val cleaner: UrlCleaner = UrlCleaner(),
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LinkUiState())
    val uiState: StateFlow<LinkUiState> = _uiState.asStateFlow()

    private val shareChannel = Channel<ShareRequest>(Channel.BUFFERED)
    val shareRequests: Flow<ShareRequest> = shareChannel.receiveAsFlow()

    private val intake = ShareIntake()
    private var pendingShare: PendingShare? = null
    private var latestRequestId = 0L
    private var cleaningJob: Job? = null

    private data class PendingShare(val requestId: Long, val url: String)

    init {
        viewModelScope.launch {
            settingsRepository.shareMode
                .catch { emit(ShareMode.DEFAULT) }
                .collect { mode ->
                    val firstEmission = !_uiState.value.settingsLoaded
                    _uiState.update { it.copy(shareMode = mode, settingsLoaded = true) }
                    if (firstEmission) drainPendingShare()
                }
        }
    }

    // region share entry point

    fun onShareIntent(text: String?) {
        when (intake.accept(text)) {
            ShareIntake.Decision.Empty -> _uiState.update {
                it.copy(
                    showSettings = false,
                    candidates = emptyList(),
                    isCleaning = false,
                    notice = LinkNotice.EmptyShare,
                )
            }

            ShareIntake.Decision.Duplicate -> _uiState.update { it.copy(showSettings = false) }

            ShareIntake.Decision.Accepted -> startFromSharedText(text)
        }
    }

    private fun startFromSharedText(text: String?) {
        cleaningJob?.cancel()
        val extraction = UrlExtractor.extract(text)
        _uiState.update {
            it.copy(
                showSettings = false,
                result = null,
                notice = null,
                isCleaning = false,
                candidates = (extraction as? ExtractionResult.Multiple)?.urls.orEmpty(),
            )
        }
        when (extraction) {
            ExtractionResult.None -> showNotice(LinkNotice.NoUrlFound)
            is ExtractionResult.Multiple -> showNotice(LinkNotice.MultipleUrls)
            is ExtractionResult.Single -> queueSharedUrl(extraction.url)
        }
    }

    private fun queueSharedUrl(url: String) {
        val requestId = ++latestRequestId
        _uiState.update { it.copy(input = url, isCleaning = true) }
        pendingShare = PendingShare(requestId, url)
        // Wait for the stored mode; sharing in the wrong mode would be a visible surprise.
        if (_uiState.value.settingsLoaded) drainPendingShare()
    }

    private fun drainPendingShare() {
        val queued = pendingShare ?: return
        pendingShare = null
        if (queued.requestId != latestRequestId) return
        runCleaning(queued.url, queued.requestId, fromShare = true)
    }

    private fun runCleaning(url: String, requestId: Long, fromShare: Boolean) {
        cleaningJob?.cancel()
        cleaningJob = viewModelScope.launch {
            val result = withContext(computeDispatcher) { cleaner.clean(url) }
            if (requestId != latestRequestId) return@launch
            _uiState.update { it.copy(result = result, isCleaning = false) }
            if (fromShare) applyQuickMode(result)
        }
    }

    private fun applyQuickMode(result: CleaningResult) {
        if (_uiState.value.shareMode != ShareMode.QUICK) return
        when (result.status) {
            CleaningStatus.CLEANED -> shareChannel.trySend(
                ShareRequest(
                    url = result.cleanedUrl,
                    quickNotice = QuickShareNotice(CleaningStatus.CLEANED, result.removedCount),
                ),
            )

            CleaningStatus.UNCHANGED -> shareChannel.trySend(
                ShareRequest(
                    url = result.cleanedUrl,
                    quickNotice = QuickShareNotice(CleaningStatus.UNCHANGED, 0),
                ),
            )

            // Uncertain outcomes stay on the confirmation screen instead of being forwarded.
            CleaningStatus.UNRESOLVED_WRAPPER -> showNotice(LinkNotice.QuickShareUnresolved)
            CleaningStatus.INVALID_URL -> showNotice(LinkNotice.QuickShareInvalid)
            CleaningStatus.FAILED -> showNotice(LinkNotice.QuickShareFailed)
        }
    }

    // endregion

    // region manual input

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun onCleanClick() {
        cleaningJob?.cancel()
        when (val extraction = UrlExtractor.extract(_uiState.value.input)) {
            ExtractionResult.None -> _uiState.update {
                it.copy(
                    result = null,
                    candidates = emptyList(),
                    isCleaning = false,
                    notice = LinkNotice.NoUrlFound,
                )
            }

            is ExtractionResult.Multiple -> _uiState.update {
                it.copy(
                    result = null,
                    candidates = extraction.urls,
                    isCleaning = false,
                    notice = LinkNotice.MultipleUrls,
                )
            }

            is ExtractionResult.Single -> {
                val requestId = ++latestRequestId
                _uiState.update {
                    it.copy(
                        input = extraction.url,
                        candidates = emptyList(),
                        isCleaning = true,
                    )
                }
                runCleaning(extraction.url, requestId, fromShare = false)
            }
        }
    }

    fun onSelectCandidate(url: String) {
        cleaningJob?.cancel()
        val requestId = ++latestRequestId
        _uiState.update {
            it.copy(
                input = url,
                candidates = emptyList(),
                result = null,
                isCleaning = true,
                notice = null,
            )
        }
        runCleaning(url, requestId, fromShare = false)
    }

    fun onPasteText(text: String?) {
        if (text.isNullOrBlank()) {
            showNotice(LinkNotice.ClipboardEmpty)
            return
        }
        when (val extraction = UrlExtractor.extract(text)) {
            is ExtractionResult.Single -> _uiState.update {
                it.copy(input = extraction.url, candidates = emptyList(), notice = null)
            }

            is ExtractionResult.Multiple -> _uiState.update {
                it.copy(
                    input = text.trim(),
                    candidates = extraction.urls,
                    notice = LinkNotice.MultipleUrls,
                )
            }

            ExtractionResult.None -> _uiState.update {
                it.copy(input = text.trim(), candidates = emptyList(), notice = LinkNotice.NoUrlFound)
            }
        }
    }

    fun onClearInput() {
        _uiState.update { it.copy(input = "", candidates = emptyList(), notice = null) }
    }

    fun onRestoreOriginal() {
        val result = _uiState.value.result ?: return
        cleaningJob?.cancel()
        latestRequestId++
        _uiState.update {
            it.copy(
                input = result.originalUrl,
                result = null,
                candidates = emptyList(),
                isCleaning = false,
                notice = LinkNotice.RestoredOriginal,
            )
        }
    }

    // endregion

    fun onShareModeChange(mode: ShareMode) {
        if (_uiState.value.shareMode == mode) return
        _uiState.update { it.copy(shareMode = mode) }
        viewModelScope.launch { settingsRepository.setShareMode(mode) }
    }

    fun onOpenSettings() {
        _uiState.update { it.copy(showSettings = true) }
    }

    fun onCloseSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    fun showNotice(notice: LinkNotice) {
        _uiState.update { it.copy(notice = notice) }
    }

    fun onNoticeShown() {
        _uiState.update { it.copy(notice = null) }
    }
}
