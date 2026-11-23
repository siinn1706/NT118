package vn.uit.healthos.core.data.session

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.crypto.tink.Aead
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicReference

/**
 * Stores the session as one AES-256-GCM encrypted JSON blob in DataStore.
 * The Tink keyset is wrapped by an Android Keystore master key, so the blob is useless off-device.
 */
class EncryptedTokenStore(
    private val dataStore: DataStore<Preferences>,
    private val aead: Aead,
    private val json: Json,
) : TokenStore {

    private val cache = AtomicReference<CacheState>(CacheState.Unloaded)

    override val tokens: Flow<SessionTokens?> = dataStore.data.map { prefs -> decode(prefs[KEY]) }

    override fun current(): SessionTokens? = when (val state = cache.get()) {
        is CacheState.Loaded -> state.tokens
        CacheState.Unloaded -> runBlocking { tokens.first() }.also { cache.set(CacheState.Loaded(it)) }
    }

    override suspend fun save(tokens: SessionTokens) {
        val plaintext = json.encodeToString(SessionTokens.serializer(), tokens).encodeToByteArray()
        val encrypted = Base64.encodeToString(aead.encrypt(plaintext, ASSOCIATED_DATA), Base64.NO_WRAP)
        dataStore.edit { it[KEY] = encrypted }
        cache.set(CacheState.Loaded(tokens))
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
        cache.set(CacheState.Loaded(null))
    }

    private fun decode(encoded: String?): SessionTokens? {
        if (encoded == null) return null
        return runCatching {
            val plaintext = aead.decrypt(Base64.decode(encoded, Base64.NO_WRAP), ASSOCIATED_DATA)
            json.decodeFromString(SessionTokens.serializer(), plaintext.decodeToString())
        }.getOrNull()
    }

    private sealed interface CacheState {
        data object Unloaded : CacheState
        data class Loaded(val tokens: SessionTokens?) : CacheState
    }

    private companion object {
        val KEY = stringPreferencesKey("session_blob")
        val ASSOCIATED_DATA = "healthos-session".encodeToByteArray()
    }
}
