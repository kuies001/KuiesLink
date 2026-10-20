package tw.kuies.link.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tw.kuies.link.data.ShortenError
import tw.kuies.link.data.ShortenResponse
import tw.kuies.link.data.ShortenResult
import tw.kuies.link.domain.CleaningStatus
import tw.kuies.link.domain.TrackingRule
import tw.kuies.link.domain.TrackingRulesRepository
import tw.kuies.link.domain.UrlCleaner
import tw.kuies.link.settings.PreferredShareTarget
import tw.kuies.link.settings.SettingsRepository
import tw.kuies.link.settings.ShareMode

@OptIn(ExperimentalCoroutinesApi::class)
class LinkViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun quickModeSharesTheCleanedUrlOnce() = runTest(dispatcher) {
        val viewModel = LinkViewModel(FakeSettings(ShareMode.QUICK), computeDispatcher = dispatcher)
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent("看這個 https://example.com/p?utm_source=igsh&id=7")
        advanceUntilIdle()

        assertEquals(1, requests.size)
        assertEquals("https://example.com/p?id=7", requests.single().url)
        assertEquals(CleaningStatus.CLEANED, requests.single().quickNotice?.status)
        assertEquals(1, requests.single().quickNotice?.removedCount)
        assertEquals(CleaningStatus.CLEANED, viewModel.uiState.value.result?.status)
    }

    @Test
    fun quickModeKeepsTheResultAfterTheShareRequestIsConsumed() = runTest(dispatcher) {
        val viewModel = LinkViewModel(FakeSettings(ShareMode.QUICK), computeDispatcher = dispatcher)
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent("https://www.instagram.com/reel/DeuAqoizRW9/?vrfl=djBqNnQzZ2J6Y2Fu")
        advanceUntilIdle()

        // Consuming the one-shot share event must not clear the cleaning result the sheet
        // returns to (the activity is no longer finished after sharing).
        assertEquals(1, requests.size)
        assertEquals(
            "https://www.instagram.com/reel/DeuAqoizRW9/",
            viewModel.uiState.value.result?.cleanedUrl,
        )
        assertEquals(1, viewModel.uiState.value.result?.removedCount)
        assertEquals(
            "https://www.instagram.com/reel/DeuAqoizRW9/?vrfl=djBqNnQzZ2J6Y2Fu",
            viewModel.uiState.value.input,
        )
    }

    @Test
    fun aDifferentShareIntentAfterTheFirstIsStillProcessed() = runTest(dispatcher) {
        val viewModel = LinkViewModel(FakeSettings(ShareMode.QUICK), computeDispatcher = dispatcher)
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent("https://example.com/a?utm_source=one")
        viewModel.onShareIntent("https://example.com/b?utm_source=two")
        advanceUntilIdle()

        assertEquals(2, requests.size)
        assertEquals("https://example.com/a", requests[0].url)
        assertEquals("https://example.com/b", requests[1].url)
    }

    @Test
    fun duplicateShareIntentDoesNotShareOrNavigateAgain() = runTest(dispatcher) {
        val viewModel = LinkViewModel(FakeSettings(ShareMode.QUICK), computeDispatcher = dispatcher)
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent("https://example.com/p?utm_source=a")
        viewModel.onShareIntent("https://example.com/p?utm_source=a")
        advanceUntilIdle()

        assertEquals(1, requests.size)
    }

    @Test
    fun confirmModeNeverOpensTheShareSheetAutomatically() = runTest(dispatcher) {
        val viewModel = LinkViewModel(FakeSettings(ShareMode.CONFIRM), computeDispatcher = dispatcher)
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent("https://example.com/p?fbclid=1")
        advanceUntilIdle()

        assertTrue(requests.isEmpty())
        assertEquals("https://example.com/p", viewModel.uiState.value.result?.cleanedUrl)
    }

    @Test
    fun shareIsNotProcessedBeforeTheStoredModeIsLoaded() = runTest(dispatcher) {
        val settings = ControlledSettings()
        val viewModel = LinkViewModel(settings, computeDispatcher = dispatcher)
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent("https://example.com/p?utm_source=a")
        advanceUntilIdle()

        assertTrue(requests.isEmpty())
        assertTrue(viewModel.uiState.value.isCleaning)

        settings.emit(ShareMode.QUICK)
        advanceUntilIdle()

        assertEquals(1, requests.size)
    }

    @Test
    fun quickModeSharesUnchangedUrlWithItsOwnNotice() = runTest(dispatcher) {
        val viewModel = LinkViewModel(FakeSettings(ShareMode.QUICK), computeDispatcher = dispatcher)
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent("https://example.com/p?id=1")
        advanceUntilIdle()

        assertEquals(1, requests.size)
        assertEquals("https://example.com/p?id=1", requests.single().url)
        assertEquals(CleaningStatus.UNCHANGED, requests.single().quickNotice?.status)
        assertEquals(0, requests.single().quickNotice?.removedCount)
        assertEquals(CleaningStatus.UNCHANGED, viewModel.uiState.value.result?.status)
    }

    @Test
    fun quickModeNoticeUsesTheRealRemovedCount() = runTest(dispatcher) {
        val viewModel = LinkViewModel(FakeSettings(ShareMode.QUICK), computeDispatcher = dispatcher)
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent(
            "https://www.instagram.com/reel/DcClyQgikR4/?exln=Yjg5MXAy&igsh=MW9xZ2F0",
        )
        advanceUntilIdle()

        assertEquals(1, requests.size)
        assertEquals("https://www.instagram.com/reel/DcClyQgikR4/", requests.single().url)
        assertEquals(2, requests.single().quickNotice?.removedCount)
        assertEquals(CleaningStatus.CLEANED, requests.single().quickNotice?.status)
    }

    @Test
    fun quickModeStopsOnAnUnresolvedWrapper() = runTest(dispatcher) {
        val viewModel = LinkViewModel(FakeSettings(ShareMode.QUICK), computeDispatcher = dispatcher)
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2F&h=AT1")
        advanceUntilIdle()

        assertTrue(requests.isEmpty())
        assertEquals(LinkNotice.QuickShareUnresolved, viewModel.uiState.value.notice)
        assertEquals(CleaningStatus.UNRESOLVED_WRAPPER, viewModel.uiState.value.result?.status)
    }

    @Test
    fun quickModeStopsWhenCleaningFails() = runTest(dispatcher) {
        val brokenRules = object : TrackingRulesRepository() {
            override fun ruleFor(host: String, parameterName: String): TrackingRule? =
                error("simulated failure")
        }
        val viewModel = LinkViewModel(
            settingsRepository = FakeSettings(ShareMode.QUICK),
            cleaner = UrlCleaner(brokenRules),
            computeDispatcher = dispatcher,
        )
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent("https://example.com/?utm_source=a")
        advanceUntilIdle()

        assertTrue(requests.isEmpty())
        assertEquals(LinkNotice.QuickShareFailed, viewModel.uiState.value.notice)
        assertEquals("https://example.com/?utm_source=a", viewModel.uiState.value.result?.cleanedUrl)
    }

    @Test
    fun multipleUrlsAreNeverAutoShared() = runTest(dispatcher) {
        val viewModel = LinkViewModel(FakeSettings(ShareMode.QUICK), computeDispatcher = dispatcher)
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent("https://a.example/1 https://b.example/2?utm_source=x")
        advanceUntilIdle()

        assertTrue(requests.isEmpty())
        assertEquals(2, viewModel.uiState.value.candidates.size)
        assertEquals(LinkNotice.MultipleUrls, viewModel.uiState.value.notice)
    }

    @Test
    fun manualCleanNeverOpensTheShareSheetEvenInQuickMode() = runTest(dispatcher) {
        val viewModel = LinkViewModel(FakeSettings(ShareMode.QUICK), computeDispatcher = dispatcher)
        val requests = collectShareRequests(viewModel)

        viewModel.onInputChange("https://example.com/?fbclid=1")
        viewModel.onCleanClick()
        advanceUntilIdle()

        assertTrue(requests.isEmpty())
        assertEquals("https://example.com/", viewModel.uiState.value.result?.cleanedUrl)
    }

    @Test
    fun emptyShareShowsAnExplanationInsteadOfCrashing() = runTest(dispatcher) {
        val viewModel = LinkViewModel(FakeSettings(ShareMode.CONFIRM), computeDispatcher = dispatcher)
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent(null)
        advanceUntilIdle()

        assertEquals(LinkNotice.EmptyShare, viewModel.uiState.value.notice)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun switchingModeIsPersistedThroughTheRepository() = runTest(dispatcher) {
        val settings = FakeSettings(ShareMode.CONFIRM)
        val viewModel = LinkViewModel(settings, computeDispatcher = dispatcher)
        advanceUntilIdle()

        viewModel.onShareModeChange(ShareMode.QUICK)
        advanceUntilIdle()

        assertEquals(ShareMode.QUICK, settings.current)
        assertEquals(ShareMode.QUICK, viewModel.uiState.value.shareMode)
    }

    @Test
    fun restorePutsTheOriginalUrlBackIntoTheInput() = runTest(dispatcher) {
        val viewModel = LinkViewModel(FakeSettings(ShareMode.CONFIRM), computeDispatcher = dispatcher)

        viewModel.onInputChange("https://example.com/p?utm_source=a#frag")
        viewModel.onCleanClick()
        advanceUntilIdle()

        viewModel.onRestoreOriginal()

        assertEquals("https://example.com/p?utm_source=a#frag", viewModel.uiState.value.input)
        assertEquals(null, viewModel.uiState.value.result)
        assertEquals(LinkNotice.RestoredOriginal, viewModel.uiState.value.notice)
    }

    @Test
    fun manualShortenSendsTheOriginalUrlAndShowsTheShortUrl() = runTest(dispatcher) {
        val sent = mutableListOf<String>()
        val viewModel = LinkViewModel(
            settingsRepository = FakeSettings(ShareMode.CONFIRM),
            computeDispatcher = dispatcher,
            shortenRequest = { url ->
                sent += url
                success(originalUrl = url)
            },
        )
        val requests = collectShareRequests(viewModel)

        viewModel.onInputChange("https://example.com/p?utm_source=news&id=7")
        viewModel.onCleanClick()
        advanceUntilIdle()
        viewModel.onShortenClick()
        advanceUntilIdle()

        assertEquals(listOf("https://example.com/p?utm_source=news&id=7"), sent)
        assertEquals("https://u.kuies.tw/abc", viewModel.uiState.value.shortResult?.shortUrl)
        assertEquals(false, viewModel.uiState.value.isShortening)
        assertNull(viewModel.uiState.value.shortenError)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun doubleTapOnShortenSendsExactlyOneRequest() = runTest(dispatcher) {
        val gate = CompletableDeferred<ShortenResult>()
        var calls = 0
        val viewModel = LinkViewModel(
            settingsRepository = FakeSettings(ShareMode.CONFIRM),
            computeDispatcher = dispatcher,
            shortenRequest = { calls++; gate.await() },
        )

        viewModel.onInputChange("https://example.com/p?utm_source=news")
        viewModel.onCleanClick()
        advanceUntilIdle()

        viewModel.onShortenClick()
        viewModel.onShortenClick()
        viewModel.onShortenClick()
        advanceUntilIdle()

        assertEquals(1, calls)

        gate.complete(success())
        advanceUntilIdle()
        assertEquals("https://u.kuies.tw/abc", viewModel.uiState.value.shortResult?.shortUrl)
    }

    @Test
    fun theSameUrlIsNotShortenedTwice() = runTest(dispatcher) {
        var calls = 0
        val viewModel = LinkViewModel(
            settingsRepository = FakeSettings(ShareMode.CONFIRM),
            computeDispatcher = dispatcher,
            shortenRequest = { calls++; success() },
        )

        viewModel.onInputChange("https://example.com/p?utm_source=news")
        viewModel.onCleanClick()
        advanceUntilIdle()
        viewModel.onShortenClick()
        advanceUntilIdle()
        viewModel.onShortenClick()
        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals("https://u.kuies.tw/abc", viewModel.uiState.value.shortResult?.shortUrl)
    }

    @Test
    fun quickModeWithShortTargetSharesTheShortUrlNotTheCleanedOne() = runTest(dispatcher) {
        val viewModel = LinkViewModel(
            settingsRepository = FakeSettings(
                ShareMode.QUICK,
                PreferredShareTarget.KUIES_SHORT_URL,
            ),
            computeDispatcher = dispatcher,
            shortenRequest = { success(shortUrl = "https://u.kuies.tw/xyz") },
        )
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent("https://example.com/p?utm_source=igsh&id=7")
        advanceUntilIdle()

        assertEquals(1, requests.size)
        assertEquals("https://u.kuies.tw/xyz", requests.single().url)
        assertEquals(true, requests.single().quickNotice?.isShortUrl)
        assertEquals("https://u.kuies.tw/xyz", viewModel.uiState.value.shortResult?.shortUrl)
    }

    @Test
    fun quickModeWithShortTargetResolvesWrappersThroughTheBackend() = runTest(dispatcher) {
        val sent = mutableListOf<String>()
        val viewModel = LinkViewModel(
            settingsRepository = FakeSettings(
                ShareMode.QUICK,
                PreferredShareTarget.KUIES_SHORT_URL,
            ),
            computeDispatcher = dispatcher,
            shortenRequest = { url ->
                sent += url
                success(
                    shortUrl = "https://u.kuies.tw/wrap",
                    resolutionStatus = "resolved",
                )
            },
        )
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2F&h=AT1")
        advanceUntilIdle()

        assertEquals(
            listOf("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2F&h=AT1"),
            sent,
        )
        assertEquals(1, requests.size)
        assertEquals("https://u.kuies.tw/wrap", requests.single().url)
    }

    @Test
    fun quickModeWithCleanTargetNeverCallsTheBackend() = runTest(dispatcher) {
        var calls = 0
        val viewModel = LinkViewModel(
            settingsRepository = FakeSettings(
                ShareMode.QUICK,
                PreferredShareTarget.CLEAN_URL,
            ),
            computeDispatcher = dispatcher,
            shortenRequest = { calls++; success() },
        )
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent("https://example.com/p?utm_source=igsh&id=7")
        advanceUntilIdle()

        assertEquals(0, calls)
        assertEquals("https://example.com/p?id=7", requests.single().url)
        assertEquals(false, requests.single().quickNotice?.isShortUrl)
    }

    @Test
    fun quickModeStaysOnTheConfirmationScreenWhenShorteningFails() = runTest(dispatcher) {
        var calls = 0
        val viewModel = LinkViewModel(
            settingsRepository = FakeSettings(
                ShareMode.QUICK,
                PreferredShareTarget.KUIES_SHORT_URL,
            ),
            computeDispatcher = dispatcher,
            shortenRequest = {
                calls++
                ShortenResult.Failure(ShortenError.TIMEOUT)
            },
        )
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent("https://example.com/p?utm_source=igsh")
        advanceUntilIdle()
        // No automatic retry: one POST per deliberate action, even for an uncertain timeout.
        advanceUntilIdle()

        assertEquals(1, calls)
        assertTrue(requests.isEmpty())
        assertEquals(ShortenError.TIMEOUT, viewModel.uiState.value.shortenError)
        assertEquals(false, viewModel.uiState.value.isShortening)
        assertEquals("https://example.com/p", viewModel.uiState.value.result?.cleanedUrl)
    }

    @Test
    fun aNewShareCancelsTheOldRequestAndItsLateResponse() = runTest(dispatcher) {
        val gateA = CompletableDeferred<ShortenResult>()
        val gateB = CompletableDeferred<ShortenResult>()
        val viewModel = LinkViewModel(
            settingsRepository = FakeSettings(
                ShareMode.QUICK,
                PreferredShareTarget.KUIES_SHORT_URL,
            ),
            computeDispatcher = dispatcher,
            shortenRequest = { url ->
                if (url.contains("/a")) gateA.await() else gateB.await()
            },
        )
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent("https://example.com/a?utm_source=one")
        advanceUntilIdle()
        viewModel.onShareIntent("https://example.com/b?utm_source=two")
        advanceUntilIdle()

        gateA.complete(success(shortUrl = "https://u.kuies.tw/a"))
        advanceUntilIdle()
        assertTrue("stale response must not be shared", requests.isEmpty())

        gateB.complete(success(shortUrl = "https://u.kuies.tw/b"))
        advanceUntilIdle()

        assertEquals(1, requests.size)
        assertEquals("https://u.kuies.tw/b", requests.single().url)
        assertEquals("https://u.kuies.tw/b", viewModel.uiState.value.shortResult?.shortUrl)
    }

    @Test
    fun aNewCleanDiscardsThePreviousShortUrl() = runTest(dispatcher) {
        val viewModel = LinkViewModel(
            settingsRepository = FakeSettings(ShareMode.CONFIRM),
            computeDispatcher = dispatcher,
            shortenRequest = { success() },
        )

        viewModel.onInputChange("https://example.com/a?utm_source=one")
        viewModel.onCleanClick()
        advanceUntilIdle()
        viewModel.onShortenClick()
        advanceUntilIdle()
        assertEquals("https://u.kuies.tw/abc", viewModel.uiState.value.shortResult?.shortUrl)

        viewModel.onInputChange("https://example.com/b?utm_source=two")
        viewModel.onCleanClick()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.shortResult)
        assertNull(viewModel.uiState.value.shortenError)
    }

    @Test
    fun theShortResultSurvivesWithoutAnotherRequest() = runTest(dispatcher) {
        var calls = 0
        val viewModel = LinkViewModel(
            settingsRepository = FakeSettings(ShareMode.CONFIRM),
            computeDispatcher = dispatcher,
            shortenRequest = { calls++; success() },
        )

        viewModel.onInputChange("https://example.com/p?utm_source=news")
        viewModel.onCleanClick()
        advanceUntilIdle()
        viewModel.onShortenClick()
        advanceUntilIdle()

        // An Activity recreation only re-collects state; it must not trigger another POST.
        assertEquals(1, calls)
        assertEquals("https://u.kuies.tw/abc", viewModel.uiState.value.shortResult?.shortUrl)
    }

    @Test
    fun preferredShareTargetIsPersistedThroughTheRepository() = runTest(dispatcher) {
        val settings = FakeSettings(ShareMode.CONFIRM, PreferredShareTarget.CLEAN_URL)
        val viewModel = LinkViewModel(settings, computeDispatcher = dispatcher)
        advanceUntilIdle()

        viewModel.onPreferredShareTargetChange(PreferredShareTarget.KUIES_SHORT_URL)
        advanceUntilIdle()

        assertEquals(PreferredShareTarget.KUIES_SHORT_URL, settings.currentTarget)
        assertEquals(
            PreferredShareTarget.KUIES_SHORT_URL,
            viewModel.uiState.value.preferredShareTarget,
        )
    }

    private fun kotlinx.coroutines.test.TestScope.collectShareRequests(
        viewModel: LinkViewModel,
    ): List<ShareRequest> {
        val requests = mutableListOf<ShareRequest>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.shareRequests.toList(requests)
        }
        return requests
    }

    private fun success(
        shortUrl: String = "https://u.kuies.tw/abc",
        targetUrl: String = "https://example.com/p?id=7",
        originalUrl: String? = null,
        removedCount: Int = 1,
        cleaningStatus: String = "cleaned",
        resolutionStatus: String = "not_needed",
        warnings: List<String> = emptyList(),
    ): ShortenResult = ShortenResult.Success(
        ShortenResponse(
            shortUrl = shortUrl,
            targetUrl = targetUrl,
            originalUrl = originalUrl,
            resolvedUrl = null,
            processingStatus = "completed",
            resolutionStatus = resolutionStatus,
            cleaningStatus = cleaningStatus,
            removedCount = removedCount,
            removedParameters = emptyList(),
            warnings = warnings,
        ),
    )

    /** The legacy quick-mode tests cover the locally cleaned target, so that is the default here. */
    private class FakeSettings(
        initial: ShareMode,
        initialTarget: PreferredShareTarget = PreferredShareTarget.CLEAN_URL,
    ) : SettingsRepository {
        private val state = MutableStateFlow(initial)
        private val targetState = MutableStateFlow(initialTarget)

        override val shareMode: Flow<ShareMode> = state
        override val preferredShareTarget: Flow<PreferredShareTarget> = targetState

        val current: ShareMode get() = state.value
        val currentTarget: PreferredShareTarget get() = targetState.value

        override suspend fun setShareMode(mode: ShareMode) {
            state.value = mode
        }

        override suspend fun setPreferredShareTarget(target: PreferredShareTarget) {
            targetState.value = target
        }
    }

    /** Emits nothing for the share mode until the test decides the stored setting has been read. */
    private class ControlledSettings : SettingsRepository {
        private val state = MutableSharedFlow<ShareMode>(replay = 1)
        private val targetState = MutableStateFlow(PreferredShareTarget.CLEAN_URL)

        override val shareMode: Flow<ShareMode> = state
        override val preferredShareTarget: Flow<PreferredShareTarget> = targetState

        override suspend fun setShareMode(mode: ShareMode) {
            state.emit(mode)
        }

        override suspend fun setPreferredShareTarget(target: PreferredShareTarget) {
            targetState.value = target
        }

        fun emit(mode: ShareMode) {
            state.tryEmit(mode)
        }
    }
}
