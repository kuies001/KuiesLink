package tw.kuies.link.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

val Context.linkSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "link_settings")

interface SettingsRepository {
    val shareMode: Flow<ShareMode>
    suspend fun setShareMode(mode: ShareMode)
    val preferredShareTarget: Flow<PreferredShareTarget>
    suspend fun setPreferredShareTarget(target: PreferredShareTarget)
}

/**
 * Persists the share mode and the preferred share target only. URLs are never written to disk.
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private val preferences: Flow<Preferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    override val shareMode: Flow<ShareMode> = preferences
        .map { stored -> ShareMode.fromStoredValue(stored[SHARE_MODE_KEY]) }

    override val preferredShareTarget: Flow<PreferredShareTarget> = preferences
        .map { stored -> PreferredShareTarget.fromStoredValue(stored[PREFERRED_SHARE_TARGET_KEY]) }

    override suspend fun setShareMode(mode: ShareMode) {
        dataStore.edit { stored -> stored[SHARE_MODE_KEY] = mode.storedValue }
    }

    override suspend fun setPreferredShareTarget(target: PreferredShareTarget) {
        dataStore.edit { stored -> stored[PREFERRED_SHARE_TARGET_KEY] = target.storedValue }
    }

    companion object {
        internal val SHARE_MODE_KEY = stringPreferencesKey("share_mode")
        internal val PREFERRED_SHARE_TARGET_KEY = stringPreferencesKey("preferred_share_target")
    }
}
