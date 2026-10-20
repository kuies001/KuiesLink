package tw.kuies.link.ui

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tw.kuies.link.domain.CleaningStatus
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
        assertTrue(requests.single().closeAfterShare)
        assertEquals(CleaningStatus.CLEANED, viewModel.uiState.value.result?.status)
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
    fun quickModeFallsBackToConfirmWhenNothingWasRemoved() = runTest(dispatcher) {
        val viewModel = LinkViewModel(FakeSettings(ShareMode.QUICK), computeDispatcher = dispatcher)
        val requests = collectShareRequests(viewModel)

        viewModel.onShareIntent("https://example.com/p?id=1")
        advanceUntilIdle()

        assertTrue(requests.isEmpty())
        assertEquals(LinkNotice.QuickShareNoChange, viewModel.uiState.value.notice)
        assertEquals(CleaningStatus.ALREADY_CLEAN, viewModel.uiState.value.result?.status)
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

    private fun kotlinx.coroutines.test.TestScope.collectShareRequests(
        viewModel: LinkViewModel,
    ): List<ShareRequest> {
        val requests = mutableListOf<ShareRequest>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.shareRequests.toList(requests)
        }
        return requests
    }

    private class FakeSettings(initial: ShareMode) : SettingsRepository {
        private val state = MutableStateFlow(initial)
        override val shareMode: Flow<ShareMode> = state

        val current: ShareMode get() = state.value

        override suspend fun setShareMode(mode: ShareMode) {
            state.value = mode
        }
    }

    /** Emits nothing until the test decides the stored setting has been read. */
    private class ControlledSettings : SettingsRepository {
        private val state = MutableSharedFlow<ShareMode>(replay = 1)

        override val shareMode: Flow<ShareMode> = state

        override suspend fun setShareMode(mode: ShareMode) {
            state.emit(mode)
        }

        fun emit(mode: ShareMode) {
            state.tryEmit(mode)
        }
    }
}
