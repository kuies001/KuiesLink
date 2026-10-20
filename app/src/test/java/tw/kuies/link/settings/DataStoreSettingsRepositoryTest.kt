package tw.kuies.link.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Exercises the real DataStore implementation on the JVM (only the Activity supplies the file
 * location), so persistence is proven rather than faked.
 */
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun defaultsToConfirmMode() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(temporaryFolder.root, "default.preferences_pb") },
            )
            val repository = DataStoreSettingsRepository(store)

            assertEquals(ShareMode.CONFIRM, repository.shareMode.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun quickModeSurvivesRepositoryRecreation() = runTest {
        val file = File(temporaryFolder.root, "persist.preferences_pb")

        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val firstRepository = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }),
        )
        firstRepository.setShareMode(ShareMode.QUICK)
        assertEquals(ShareMode.QUICK, firstRepository.shareMode.first())
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = DataStoreSettingsRepository(
                PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file }),
            )
            assertEquals(ShareMode.QUICK, reopened.shareMode.first())
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun unknownStoredValueFallsBackToConfirmMode() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(temporaryFolder.root, "unknown.preferences_pb") },
            )
            store.edit { preferences ->
                preferences[DataStoreSettingsRepository.SHARE_MODE_KEY] = "not-a-mode"
            }

            assertEquals(ShareMode.CONFIRM, DataStoreSettingsRepository(store).shareMode.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun storedValuesRoundTrip() {
        ShareMode.entries.forEach { mode ->
            assertEquals(mode, ShareMode.fromStoredValue(mode.storedValue))
        }
        assertEquals(ShareMode.CONFIRM, ShareMode.fromStoredValue(null))
    }

    @Test
    fun defaultsToKuiesShortUrlTarget() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(temporaryFolder.root, "target-default.preferences_pb") },
            )

            assertEquals(
                PreferredShareTarget.KUIES_SHORT_URL,
                DataStoreSettingsRepository(store).preferredShareTarget.first(),
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun preferredTargetSurvivesRepositoryRecreation() = runTest {
        val file = File(temporaryFolder.root, "target-persist.preferences_pb")

        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val firstRepository = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }),
        )
        firstRepository.setPreferredShareTarget(PreferredShareTarget.CLEAN_URL)
        assertEquals(PreferredShareTarget.CLEAN_URL, firstRepository.preferredShareTarget.first())
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = DataStoreSettingsRepository(
                PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file }),
            )
            assertEquals(PreferredShareTarget.CLEAN_URL, reopened.preferredShareTarget.first())
            // The two settings are stored independently.
            assertEquals(ShareMode.CONFIRM, reopened.shareMode.first())
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun unknownStoredTargetFallsBackToShortUrl() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(temporaryFolder.root, "target-unknown.preferences_pb") },
            )
            store.edit { preferences ->
                preferences[DataStoreSettingsRepository.PREFERRED_SHARE_TARGET_KEY] = "not-a-target"
            }

            assertEquals(
                PreferredShareTarget.KUIES_SHORT_URL,
                DataStoreSettingsRepository(store).preferredShareTarget.first(),
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun targetStoredValuesRoundTrip() {
        PreferredShareTarget.entries.forEach { target ->
            assertEquals(target, PreferredShareTarget.fromStoredValue(target.storedValue))
        }
        assertEquals(PreferredShareTarget.KUIES_SHORT_URL, PreferredShareTarget.fromStoredValue(null))
    }
}
