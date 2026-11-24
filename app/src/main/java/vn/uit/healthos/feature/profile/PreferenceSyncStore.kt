package vn.uit.healthos.feature.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import dagger.multibindings.StringKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import vn.uit.healthos.core.data.outbox.OutboxHandler
import vn.uit.healthos.core.data.session.UserDataCleanup
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.apiCall
import vn.uit.healthos.core.network.map
import javax.inject.Inject
import javax.inject.Singleton

const val OUTBOX_TYPE_PREFERENCES_PATCH = "preferences.patch"

/** The outbox item only wakes the sender; the values to send are read from [PreferenceSyncStore] at send time. */
@Serializable
data class PreferencesPatchPayload(val reason: String = "preferences")

private val Context.preferenceSyncDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "healthos_preference_sync",
)

/**
 * Remembers which preference changes the server has not confirmed yet (the dirty flags), and the leaderboard
 * visibility, which only exists on the server and is cached here for the settings screen.
 */
@Singleton
class PreferenceSyncStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val dataStore: DataStore<Preferences> get() = context.preferenceSyncDataStore

    val leaderboardVisibility: Flow<LeaderboardVisibility> = dataStore.data.map { prefs ->
        LeaderboardVisibility.fromApi(prefs[KEY_LEADERBOARD]) ?: LeaderboardVisibility.Default
    }

    suspend fun pending(): PendingPreferences = dataStore.data.first().toPending()

    /** Marks the set fields of [change] as dirty with their new values. */
    suspend fun markPending(change: PendingPreferences) {
        dataStore.edit { prefs ->
            val merged = prefs.toPending().plus(change)
            prefs.writePending(merged)
        }
    }

    /** Clears the dirty flags that [sent] confirmed, keeping anything changed again while it was in flight. */
    suspend fun clearSent(sent: PendingPreferences) {
        dataStore.edit { prefs ->
            prefs.writePending(PreferenceSyncPolicy.remainingAfterSent(prefs.toPending(), sent))
        }
    }

    suspend fun setLeaderboardVisibility(visibility: LeaderboardVisibility) {
        dataStore.edit { it[KEY_LEADERBOARD] = visibility.apiValue }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toPending() = PendingPreferences(
        themeMode = this[KEY_PENDING_THEME],
        accentColor = this[KEY_PENDING_ACCENT],
        background = this[KEY_PENDING_BACKGROUND],
        leaderboardVisibility = this[KEY_PENDING_LEADERBOARD],
    )

    private fun MutablePreferences.writePending(pending: PendingPreferences) {
        putOrRemove(KEY_PENDING_THEME, pending.themeMode)
        putOrRemove(KEY_PENDING_ACCENT, pending.accentColor)
        putOrRemove(KEY_PENDING_BACKGROUND, pending.background)
        putOrRemove(KEY_PENDING_LEADERBOARD, pending.leaderboardVisibility)
    }

    private fun MutablePreferences.putOrRemove(key: Preferences.Key<String>, value: String?) {
        if (value == null) remove(key) else this[key] = value
    }

    private companion object {
        val KEY_PENDING_THEME = stringPreferencesKey("pending_theme_mode")
        val KEY_PENDING_ACCENT = stringPreferencesKey("pending_accent_color")
        val KEY_PENDING_BACKGROUND = stringPreferencesKey("pending_background")
        val KEY_PENDING_LEADERBOARD = stringPreferencesKey("pending_leaderboard_visibility")
        val KEY_LEADERBOARD = stringPreferencesKey("leaderboard_visibility")
    }
}

/**
 * Sends the pending preference fields with one PATCH. Network and server errors stay queued (the outbox retries);
 * a validation rejection (400/422) drops the local claim so the next sync adopts the server value instead.
 */
@Singleton
class PreferencesOutboxHandler @Inject constructor(
    private val api: ProfileApi,
    private val store: PreferenceSyncStore,
    private val json: Json,
) : OutboxHandler {

    override suspend fun send(payloadJson: String): ApiResult<Unit> {
        val sent = store.pending()
        val body = PreferenceSyncPolicy.patchBody(sent) ?: return ApiResult.Success(Unit)
        val result = apiCall(json) { api.patchPreferences(body).data }
        when (result) {
            is ApiResult.Success -> store.clearSent(sent)
            is ApiResult.Failure -> if (result.error.httpStatus == 400 || result.error.httpStatus == 422) {
                store.clearSent(sent)
            }
        }
        return result.map { }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferenceSyncModule {
    @Binds
    @IntoMap
    @StringKey(OUTBOX_TYPE_PREFERENCES_PATCH)
    abstract fun bindPreferencesPatch(handler: PreferencesOutboxHandler): OutboxHandler

    companion object {
        @Provides
        @IntoSet
        fun providePreferenceSyncCleanup(store: PreferenceSyncStore): UserDataCleanup = UserDataCleanup { store.clear() }
    }
}
