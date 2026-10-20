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
}

/**
 * Persists the share mode only. URLs are never written to disk.
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val shareMode: Flow<ShareMode> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> ShareMode.fromStoredValue(preferences[SHARE_MODE_KEY]) }

    override suspend fun setShareMode(mode: ShareMode) {
        dataStore.edit { preferences -> preferences[SHARE_MODE_KEY] = mode.storedValue }
    }

    companion object {
        internal val SHARE_MODE_KEY = stringPreferencesKey("share_mode")
    }
}
