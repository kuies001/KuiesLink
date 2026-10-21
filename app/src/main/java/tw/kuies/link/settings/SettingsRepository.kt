package tw.kuies.link.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
    val expandDetails: Flow<Boolean>
    suspend fun setExpandDetails(enabled: Boolean)
    val showTechnicalInfo: Flow<Boolean>
    suspend fun setShowTechnicalInfo(enabled: Boolean)
}

/**
 * Persists user preferences only. URLs are never written to disk.
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

    override val themeMode: Flow<ThemeMode> = preferences
        .map { stored -> ThemeMode.fromStoredValue(stored[THEME_MODE_KEY]) }

    override val expandDetails: Flow<Boolean> = preferences
        .map { stored -> stored[EXPAND_DETAILS_KEY] ?: false }

    override val showTechnicalInfo: Flow<Boolean> = preferences
        .map { stored -> stored[SHOW_TECHNICAL_INFO_KEY] ?: false }

    override suspend fun setShareMode(mode: ShareMode) {
        dataStore.edit { stored -> stored[SHARE_MODE_KEY] = mode.storedValue }
    }

    override suspend fun setPreferredShareTarget(target: PreferredShareTarget) {
        dataStore.edit { stored -> stored[PREFERRED_SHARE_TARGET_KEY] = target.storedValue }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { stored -> stored[THEME_MODE_KEY] = mode.storedValue }
    }

    override suspend fun setExpandDetails(enabled: Boolean) {
        dataStore.edit { stored -> stored[EXPAND_DETAILS_KEY] = enabled }
    }

    override suspend fun setShowTechnicalInfo(enabled: Boolean) {
        dataStore.edit { stored -> stored[SHOW_TECHNICAL_INFO_KEY] = enabled }
    }

    companion object {
        internal val SHARE_MODE_KEY = stringPreferencesKey("share_mode")
        internal val PREFERRED_SHARE_TARGET_KEY = stringPreferencesKey("preferred_share_target")
        internal val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        internal val EXPAND_DETAILS_KEY = booleanPreferencesKey("expand_details")
        internal val SHOW_TECHNICAL_INFO_KEY = booleanPreferencesKey("show_technical_info")
    }
}
