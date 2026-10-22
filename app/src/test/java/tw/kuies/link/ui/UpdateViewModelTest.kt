package tw.kuies.link.ui

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tw.kuies.link.domain.CleaningStatus
import tw.kuies.link.settings.PreferredShareTarget
import tw.kuies.link.settings.SettingsRepository
import tw.kuies.link.settings.ShareMode
import tw.kuies.link.settings.ThemeMode
import tw.kuies.link.update.AvailableUpdate
import tw.kuies.link.update.PrepareResult
import tw.kuies.link.update.UpdateCheck
import tw.kuies.link.update.UpdateCoordinator
import tw.kuies.link.update.UpdateFailure

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun checkFindsAnAvailableVersion() = runTest(dispatcher) {
        val viewModel = viewModel(FakeUpdater(UpdateCheck.Available(update())))

        viewModel.onCheckUpdate()
        advanceUntilIdle()

        assertEquals(UpdatePhase.Available, viewModel.uiState.value.update.phase)
        assertEquals(11L, viewModel.uiState.value.update.available?.versionCode)
    }

    @Test
    fun checkReportsUpToDate() = runTest(dispatcher) {
        val viewModel = viewModel(FakeUpdater(UpdateCheck.UpToDate))

        viewModel.onCheckUpdate()
        advanceUntilIdle()

        assertEquals(UpdatePhase.UpToDate, viewModel.uiState.value.update.phase)
    }

    @Test
    fun checkFailureIsShownFriendly() = runTest(dispatcher) {
        val viewModel = viewModel(FakeUpdater(UpdateCheck.Failed(UpdateFailure.NETWORK)))

        viewModel.onCheckUpdate()
        advanceUntilIdle()

        assertEquals(UpdatePhase.Failed, viewModel.uiState.value.update.phase)
        assertEquals(UpdateFailure.NETWORK, viewModel.uiState.value.update.failure)
    }

    @Test
    fun downloadWithoutInstallPermissionAsksForIt() = runTest(dispatcher) {
        val apk = preparedApk()
        val updater = FakeUpdater(
            checkResult = UpdateCheck.Available(update()),
            prepareResult = PrepareResult.Prepared(apk),
            canInstall = false,
        )
        val viewModel = viewModel(updater)

        viewModel.onCheckUpdate()
        advanceUntilIdle()
        viewModel.onDownloadUpdate()
        advanceUntilIdle()

        assertEquals(UpdatePhase.NeedsPermission, viewModel.uiState.value.update.phase)
        assertEquals(apk.absolutePath, viewModel.uiState.value.update.apkPath)
        assertNull(updater.launched)
    }

    @Test
    fun downloadOpensTheSystemInstaller() = runTest(dispatcher) {
        val apk = preparedApk()
        val updater = FakeUpdater(
            checkResult = UpdateCheck.Available(update()),
            prepareResult = PrepareResult.Prepared(apk),
            canInstall = true,
        )
        val viewModel = viewModel(updater)

        viewModel.onCheckUpdate()
        advanceUntilIdle()
        viewModel.onDownloadUpdate()
        advanceUntilIdle()

        assertEquals(UpdatePhase.ReadyToInstall, viewModel.uiState.value.update.phase)
        assertEquals(apk, updater.launched)
    }

    @Test
    fun installWithoutPermissionStopsAtThePermissionGate() = runTest(dispatcher) {
        val apk = preparedApk()
        val updater = FakeUpdater(
            checkResult = UpdateCheck.Available(update()),
            prepareResult = PrepareResult.Prepared(apk),
            canInstall = false,
        )
        val viewModel = viewModel(updater)
        viewModel.onCheckUpdate()
        advanceUntilIdle()
        viewModel.onDownloadUpdate()
        advanceUntilIdle()

        updater.canInstall = false
        viewModel.onInstallUpdate()

        assertEquals(UpdatePhase.NeedsPermission, viewModel.uiState.value.update.phase)
        assertNull(updater.launched)
    }

    @Test
    fun failedInstallerLaunchIsReported() = runTest(dispatcher) {
        val apk = preparedApk()
        val updater = FakeUpdater(
            checkResult = UpdateCheck.Available(update()),
            prepareResult = PrepareResult.Prepared(apk),
            canInstall = true,
            launchResult = false,
        )
        val viewModel = viewModel(updater)

        viewModel.onCheckUpdate()
        advanceUntilIdle()
        viewModel.onDownloadUpdate()
        advanceUntilIdle()

        assertEquals(UpdatePhase.Failed, viewModel.uiState.value.update.phase)
        assertEquals(UpdateFailure.INSTALL_UNAVAILABLE, viewModel.uiState.value.update.failure)
    }

    @Test
    fun grantingInstallPermissionWhileAwayAllowsInstall() = runTest(dispatcher) {
        val apk = preparedApk()
        val updater = FakeUpdater(
            checkResult = UpdateCheck.Available(update()),
            prepareResult = PrepareResult.Prepared(apk),
            canInstall = false,
        )
        val viewModel = viewModel(updater)
        viewModel.onCheckUpdate()
        advanceUntilIdle()
        viewModel.onDownloadUpdate()
        advanceUntilIdle()
        assertEquals(UpdatePhase.NeedsPermission, viewModel.uiState.value.update.phase)

        updater.canInstall = true
        viewModel.onResume()

        assertEquals(UpdatePhase.ReadyToInstall, viewModel.uiState.value.update.phase)
    }

    @Test
    fun cancellingDiscardsThePreparedFile() = runTest(dispatcher) {
        val apk = preparedApk()
        val updater = FakeUpdater(
            checkResult = UpdateCheck.Available(update()),
            prepareResult = PrepareResult.Prepared(apk),
            canInstall = false,
        )
        val viewModel = viewModel(updater)
        viewModel.onCheckUpdate()
        advanceUntilIdle()
        viewModel.onDownloadUpdate()
        advanceUntilIdle()

        viewModel.onCancelUpdateDownload()

        assertEquals(UpdatePhase.Idle, viewModel.uiState.value.update.phase)
        assertEquals(apk, updater.discarded)
    }

    @Test
    fun aCancelledInstallLeavesTheAppUsable() = runTest(dispatcher) {
        val apk = preparedApk()
        val updater = FakeUpdater(
            checkResult = UpdateCheck.Available(update()),
            prepareResult = PrepareResult.Prepared(apk),
            canInstall = true,
        )
        val viewModel = viewModel(updater)
        viewModel.onCheckUpdate()
        advanceUntilIdle()
        viewModel.onDownloadUpdate()
        advanceUntilIdle()

        // The user backed out of the system installer; the update stays ready and core cleaning works.
        viewModel.onInputChange("https://example.com/p?utm_source=news&id=7")
        viewModel.onCleanClick()
        advanceUntilIdle()

        assertEquals(UpdatePhase.ReadyToInstall, viewModel.uiState.value.update.phase)
        assertEquals(CleaningStatus.CLEANED, viewModel.uiState.value.result?.status)
        assertTrue(updater.launched != null)
    }

    @Test
    fun missingCoordinatorFailsClosed() = runTest(dispatcher) {
        val viewModel = viewModel(null)

        viewModel.onCheckUpdate()
        advanceUntilIdle()

        assertEquals(UpdatePhase.Failed, viewModel.uiState.value.update.phase)
        assertEquals(UpdateFailure.UNKNOWN, viewModel.uiState.value.update.failure)
    }

    private fun viewModel(updater: UpdateCoordinator?): LinkViewModel = LinkViewModel(
        settingsRepository = FakeSettings(),
        computeDispatcher = dispatcher,
        updater = updater,
    )

    private fun preparedApk(): File =
        temporaryFolder.newFile("KuiesLink-v0.3.4-test.apk").apply { writeBytes(byteArrayOf(1, 2)) }

    private fun update(): AvailableUpdate = AvailableUpdate(
        tagName = "v0.3.4-test",
        versionCode = 11,
        versionName = "0.3.4-test",
        changelog = "測試更新",
        apkAssetName = "KuiesLink-v0.3.4-test.apk",
        apkUrl = "https://github.com/kuies001/KuiesLink/releases/download/v0.3.4-test/" +
            "KuiesLink-v0.3.4-test.apk",
        apkSizeBytes = 1024,
        sha256 = "a".repeat(64),
    )

    private class FakeUpdater(
        var checkResult: UpdateCheck,
        var prepareResult: PrepareResult = PrepareResult.Failed(UpdateFailure.UNKNOWN),
        var canInstall: Boolean = true,
        var launchResult: Boolean = true,
    ) : UpdateCoordinator {
        var launched: File? = null
        var discarded: File? = null

        override suspend fun checkForUpdate(): UpdateCheck = checkResult

        override suspend fun downloadAndVerify(
            update: AvailableUpdate,
            onProgress: (Int) -> Unit,
        ): PrepareResult {
            onProgress(42)
            return prepareResult
        }

        override fun canInstallPackages(): Boolean = canInstall

        override fun openInstallPermissionSettings(): Boolean = true

        override fun launchInstaller(apk: File): Boolean {
            launched = apk
            return launchResult
        }

        override fun discard(apk: File) {
            discarded = apk
        }
    }

    private class FakeSettings : SettingsRepository {
        private val mode = MutableStateFlow(ShareMode.CONFIRM)
        private val target = MutableStateFlow(PreferredShareTarget.KUIES_SHORT_URL)
        private val theme = MutableStateFlow(ThemeMode.SYSTEM)
        private val expand = MutableStateFlow(false)
        private val technical = MutableStateFlow(false)

        override val shareMode: Flow<ShareMode> = mode
        override val preferredShareTarget: Flow<PreferredShareTarget> = target
        override val themeMode: Flow<ThemeMode> = theme
        override val expandDetails: Flow<Boolean> = expand
        override val showTechnicalInfo: Flow<Boolean> = technical

        override suspend fun setShareMode(mode: ShareMode) {
            this.mode.value = mode
        }

        override suspend fun setPreferredShareTarget(target: PreferredShareTarget) {
            this.target.value = target
        }

        override suspend fun setThemeMode(mode: ThemeMode) {
            theme.value = mode
        }

        override suspend fun setExpandDetails(enabled: Boolean) {
            expand.value = enabled
        }

        override suspend fun setShowTechnicalInfo(enabled: Boolean) {
            technical.value = enabled
        }
    }
}
