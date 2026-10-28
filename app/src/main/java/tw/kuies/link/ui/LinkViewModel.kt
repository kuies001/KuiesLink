package tw.kuies.link.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.kuies.link.data.ShortenError
import tw.kuies.link.data.ShortenResponse
import tw.kuies.link.data.ShortenResult
import tw.kuies.link.domain.CleaningResult
import tw.kuies.link.domain.CleaningStatus
import tw.kuies.link.domain.ExtractionResult
import tw.kuies.link.domain.UrlCleaner
import tw.kuies.link.domain.UrlExtractor
import tw.kuies.link.settings.PreferredShareTarget
import tw.kuies.link.settings.SettingsRepository
import tw.kuies.link.settings.ShareMode
import tw.kuies.link.settings.ThemeMode
import tw.kuies.link.update.PrepareResult
import tw.kuies.link.update.UpdateCheck
import tw.kuies.link.update.UpdateCoordinator
import tw.kuies.link.update.UpdateFailure

class LinkViewModel(
    private val settingsRepository: SettingsRepository,
    private val cleaner: UrlCleaner = UrlCleaner(),
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /**
     * One shortener call. Production injects the HTTP repository; the default fails closed so a
     * mis-wired build can never silently create short URLs.
     */
    private val shortenRequest: suspend (String) -> ShortenResult = {
        ShortenResult.Failure(ShortenError.CANCELLED)
    },
    private val updater: UpdateCoordinator? = null,
    private val shareEntry: ShareEntry = ShareEntry.HOME,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LinkUiState())
    val uiState: StateFlow<LinkUiState> = _uiState.asStateFlow()

    private val shareChannel = Channel<ShareRequest>(Channel.BUFFERED)
    val shareRequests: Flow<ShareRequest> = shareChannel.receiveAsFlow()

    private val intake = ShareIntake()
    private var pendingShare: PendingShare? = null
    private var latestRequestId = 0L
    private var cleaningJob: Job? = null

    private var shortenJob: Job? = null
    private var shortenRequestId = 0L
    private var lastShortenedUrl: String? = null
    private var updateJob: Job? = null

    private data class PendingShare(val requestId: Long, val url: String)

    private data class SettingsSnapshot(
        val shareMode: ShareMode = ShareMode.DEFAULT,
        val preferredShareTarget: PreferredShareTarget = PreferredShareTarget.DEFAULT,
        val themeMode: ThemeMode = ThemeMode.DEFAULT,
        val expandDetails: Boolean = false,
        val showTechnicalInfo: Boolean = false,
    )

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.shareMode,
                settingsRepository.preferredShareTarget,
                settingsRepository.themeMode,
                settingsRepository.expandDetails,
                settingsRepository.showTechnicalInfo,
            ) { mode, target, theme, expand, technical ->
                SettingsSnapshot(mode, target, theme, expand, technical)
            }
                .catch { emit(SettingsSnapshot()) }
                .collect { snapshot ->
                    val firstEmission = !_uiState.value.settingsLoaded
                    _uiState.update {
                        it.copy(
                            shareMode = snapshot.shareMode,
                            preferredShareTarget = snapshot.preferredShareTarget,
                            themeMode = snapshot.themeMode,
                            expandDetailsByDefault = snapshot.expandDetails,
                            showTechnicalInfo = snapshot.showTechnicalInfo,
                            settingsLoaded = true,
                        )
                    }
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
        invalidateShortening()
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
            if (fromShare) {
                when (shareEntry) {
                    ShareEntry.HOME -> applyQuickMode(result)
                    ShareEntry.DIALOG -> prepareDialogResult(result)
                }
            }
        }
    }

    /**
     * The dialog's primary action is the short URL, so it is created without an extra tap. The
     * user still decides whether to copy or share it; nothing is handed to another app here.
     */
    private fun prepareDialogResult(result: CleaningResult) {
        if (!result.isShareable) return
        startShortening(result.originalUrl, autoShare = false)
    }

    /** True while a share is being cleaned/shortened or its result is on screen. */
    fun hasShareState(): Boolean = _uiState.value.let {
        it.isCleaning || it.result != null || it.candidates.isNotEmpty() || it.notice != null
    }

    private fun applyQuickMode(result: CleaningResult) {
        if (_uiState.value.shareMode != ShareMode.QUICK) return
        val preferShortUrl =
            _uiState.value.preferredShareTarget == PreferredShareTarget.KUIES_SHORT_URL
        when (result.status) {
            CleaningStatus.CLEANED, CleaningStatus.UNCHANGED -> {
                if (preferShortUrl) {
                    startShortening(result.originalUrl, autoShare = true)
                } else {
                    shareCleaned(result)
                }
            }

            CleaningStatus.UNRESOLVED_WRAPPER -> {
                // The backend resolves Facebook/Threads wrappers, so this only stops the flow when
                // the user asked for the locally cleaned URL.
                if (preferShortUrl) {
                    startShortening(result.originalUrl, autoShare = true)
                } else {
                    showNotice(LinkNotice.QuickShareUnresolved)
                }
            }

            // Uncertain outcomes stay on the confirmation screen instead of being forwarded.
            CleaningStatus.INVALID_URL -> showNotice(LinkNotice.QuickShareInvalid)
            CleaningStatus.FAILED -> showNotice(LinkNotice.QuickShareFailed)
        }
    }

    private fun shareCleaned(result: CleaningResult) {
        val status = if (result.status == CleaningStatus.CLEANED) {
            CleaningStatus.CLEANED
        } else {
            CleaningStatus.UNCHANGED
        }
        shareChannel.trySend(
            ShareRequest(
                url = result.cleanedUrl,
                quickNotice = QuickShareNotice(status, result.removedCount),
            ),
        )
    }

    // endregion

    // region shortening

    fun onShortenClick() {
        val result = _uiState.value.result ?: return
        if (!result.isShareable || _uiState.value.isShortening) return
        if (lastShortenedUrl == result.originalUrl && _uiState.value.shortResult != null) return
        startShortening(result.originalUrl, autoShare = false)
    }

    fun onRetryShorten() {
        val result = _uiState.value.result ?: return
        if (!result.isShareable || _uiState.value.isShortening) return
        startShortening(result.originalUrl, autoShare = false)
    }

    /**
     * Sends the raw original URL (not the locally cleaned one) because strict mode on the server
     * is authoritative for resolution, cleaning and the short code itself.
     */
    private fun startShortening(url: String, autoShare: Boolean) {
        shortenJob?.cancel()
        val requestId = ++shortenRequestId
        _uiState.update { it.copy(isShortening = true, shortenError = null) }
        shortenJob = viewModelScope.launch {
            val outcome = shortenRequest(url)
            // A newer share/clean/restore owns the UI now; never let a stale response land.
            if (requestId != shortenRequestId) return@launch
            when (outcome) {
                is ShortenResult.Success -> {
                    lastShortenedUrl = url
                    _uiState.update {
                        it.copy(
                            isShortening = false,
                            shortResult = outcome.response,
                            shortenError = null,
                        )
                    }
                    if (autoShare) shareShortUrl(outcome.response)
                }

                is ShortenResult.Failure -> {
                    lastShortenedUrl = null
                    _uiState.update {
                        it.copy(
                            isShortening = false,
                            shortResult = null,
                            shortenError = outcome.error,
                        )
                    }
                }
            }
        }
    }

    private fun shareShortUrl(response: ShortenResponse) {
        val status = if (response.hasKnownTrackingRemoved) {
            CleaningStatus.CLEANED
        } else {
            CleaningStatus.UNCHANGED
        }
        shareChannel.trySend(
            ShareRequest(
                url = response.shortUrl,
                quickNotice = QuickShareNotice(
                    status = status,
                    removedCount = response.removedCount,
                    isShortUrl = true,
                ),
            ),
        )
    }

    private fun invalidateShortening() {
        shortenJob?.cancel()
        shortenJob = null
        shortenRequestId++
        lastShortenedUrl = null
        _uiState.update {
            it.copy(isShortening = false, shortResult = null, shortenError = null)
        }
    }

    // endregion

    // region manual input

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun onCleanClick() {
        cleaningJob?.cancel()
        invalidateShortening()
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
        invalidateShortening()
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
        invalidateShortening()
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

    fun onPreferredShareTargetChange(target: PreferredShareTarget) {
        if (_uiState.value.preferredShareTarget == target) return
        _uiState.update { it.copy(preferredShareTarget = target) }
        viewModelScope.launch { settingsRepository.setPreferredShareTarget(target) }
    }

    fun onThemeModeChange(mode: ThemeMode) {
        if (_uiState.value.themeMode == mode) return
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun onExpandDetailsChange(enabled: Boolean) {
        if (_uiState.value.expandDetailsByDefault == enabled) return
        _uiState.update { it.copy(expandDetailsByDefault = enabled) }
        viewModelScope.launch { settingsRepository.setExpandDetails(enabled) }
    }

    fun onShowTechnicalInfoChange(enabled: Boolean) {
        if (_uiState.value.showTechnicalInfo == enabled) return
        _uiState.update { it.copy(showTechnicalInfo = enabled) }
        viewModelScope.launch { settingsRepository.setShowTechnicalInfo(enabled) }
    }

    fun onCheckUpdate() {
        val coordinator = updater
        if (coordinator == null) {
            _uiState.update {
                it.copy(update = UpdateUiState(phase = UpdatePhase.Failed, failure = UpdateFailure.UNKNOWN))
            }
            return
        }
        val current = _uiState.value.update.phase
        if (current == UpdatePhase.Checking || current == UpdatePhase.Downloading) return
        updateJob?.cancel()
        _uiState.value.update.apkPath?.let { coordinator.discard(File(it)) }
        _uiState.update { it.copy(update = UpdateUiState(phase = UpdatePhase.Checking)) }
        updateJob = viewModelScope.launch {
            val result = coordinator.checkForUpdate()
            _uiState.update { state ->
                state.copy(
                    update = when (result) {
                        is UpdateCheck.UpToDate -> UpdateUiState(phase = UpdatePhase.UpToDate)
                        is UpdateCheck.Available -> UpdateUiState(
                            phase = UpdatePhase.Available,
                            available = result.update,
                        )

                        is UpdateCheck.Failed -> UpdateUiState(
                            phase = UpdatePhase.Failed,
                            failure = result.failure,
                        )
                    },
                )
            }
        }
    }

    fun onDownloadUpdate() {
        val coordinator = updater ?: return
        val update = _uiState.value.update.available ?: return
        if (_uiState.value.update.phase == UpdatePhase.Downloading) return
        updateJob?.cancel()
        _uiState.value.update.apkPath?.let { coordinator.discard(File(it)) }
        _uiState.update {
            it.copy(
                update = it.update.copy(
                    phase = UpdatePhase.Downloading,
                    progress = 0,
                    failure = null,
                    apkPath = null,
                ),
            )
        }
        updateJob = viewModelScope.launch {
            val result = coordinator.downloadAndVerify(update) { percent ->
                _uiState.update { state ->
                    state.copy(update = state.update.copy(progress = percent))
                }
            }
            when (result) {
                is PrepareResult.Prepared -> {
                    _uiState.update {
                        it.copy(
                            update = it.update.copy(
                                phase = UpdatePhase.ReadyToInstall,
                                progress = 100,
                                apkPath = result.apk.absolutePath,
                            ),
                        )
                    }
                    if (!coordinator.canInstallPackages()) {
                        _uiState.update {
                            it.copy(update = it.update.copy(phase = UpdatePhase.NeedsPermission))
                        }
                    } else {
                        launchInstaller(coordinator, result.apk)
                    }
                }

                is PrepareResult.Failed -> _uiState.update {
                    it.copy(
                        update = it.update.copy(
                            phase = UpdatePhase.Failed,
                            failure = result.failure,
                            progress = null,
                        ),
                    )
                }
            }
        }
    }

    fun onInstallUpdate() {
        val coordinator = updater ?: return
        val apk = _uiState.value.update.apkPath?.let(::File)
        if (apk == null || !apk.isFile) {
            _uiState.update {
                it.copy(
                    update = it.update.copy(
                        phase = UpdatePhase.Failed,
                        failure = UpdateFailure.DOWNLOAD_FAILED,
                    ),
                )
            }
            return
        }
        if (!coordinator.canInstallPackages()) {
            _uiState.update { it.copy(update = it.update.copy(phase = UpdatePhase.NeedsPermission)) }
            return
        }
        launchInstaller(coordinator, apk)
    }

    fun onOpenInstallPermission() {
        val coordinator = updater ?: return
        if (!coordinator.openInstallPermissionSettings()) {
            _uiState.update {
                it.copy(
                    update = it.update.copy(
                        phase = UpdatePhase.Failed,
                        failure = UpdateFailure.INSTALL_UNAVAILABLE,
                    ),
                )
            }
        }
    }

    /** Returning from the system permission screen must not leave the dialog stuck on "前往設定". */
    fun onResume() {
        val coordinator = updater ?: return
        if (_uiState.value.update.phase != UpdatePhase.NeedsPermission) return
        if (coordinator.canInstallPackages()) {
            _uiState.update {
                it.copy(update = it.update.copy(phase = UpdatePhase.ReadyToInstall))
            }
        }
    }

    fun onCancelUpdateDownload() {
        updateJob?.cancel()
        updateJob = null
        updater?.let { coordinator ->
            _uiState.value.update.apkPath?.let { coordinator.discard(File(it)) }
        }
        _uiState.update { it.copy(update = UpdateUiState()) }
    }

    private fun launchInstaller(coordinator: UpdateCoordinator, apk: File) {
        if (!coordinator.launchInstaller(apk)) {
            _uiState.update {
                it.copy(
                    update = it.update.copy(
                        phase = UpdatePhase.Failed,
                        failure = UpdateFailure.INSTALL_UNAVAILABLE,
                    ),
                )
            }
        }
    }

    fun onBackToHome() {
        cleaningJob?.cancel()
        invalidateShortening()
        latestRequestId++
        _uiState.update {
            it.copy(
                result = null,
                candidates = emptyList(),
                isCleaning = false,
                notice = null,
            )
        }
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
