package vn.uit.healthos.feature.safety

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import vn.uit.healthos.feature.profile.EmergencyContactDto
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.safetyDataStore: DataStore<Preferences> by preferencesDataStore(name = "healthos_safety")

data class SafetyLocalState(
    val fallDetectionEnabled: Boolean = false,
    val testModeEnabled: Boolean = false,
    val shareUrl: String? = null,
    val cardFullName: String = "",
    val cardBloodType: String = "",
    val cardAllergies: String = "",
    val cardConditions: String = "",
    val cardPrimaryContactName: String = "",
    val cardPrimaryContactPhone: String = "",
)

@Singleton
class SafetyPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val contactsSerializer = ListSerializer(EmergencyContactDto.serializer())

    /** An unreadable store must never block an SOS: it reads as empty. */
    private val prefs: Flow<Preferences> = context.safetyDataStore.data.catch { e ->
        if (e is IOException) {
            Log.w(TAG, "Safety preferences unreadable", e)
            emit(emptyPreferences())
        } else {
            throw e
        }
    }

    val state: Flow<SafetyLocalState> = prefs.map { values ->
        SafetyLocalState(
            fallDetectionEnabled = values[KEY_FALL_ENABLED] ?: false,
            testModeEnabled = values[KEY_TEST_MODE] ?: false,
            shareUrl = values[KEY_SHARE_URL],
            cardFullName = values[KEY_CARD_NAME].orEmpty(),
            cardBloodType = values[KEY_CARD_BLOOD].orEmpty(),
            cardAllergies = values[KEY_CARD_ALLERGIES].orEmpty(),
            cardConditions = values[KEY_CARD_CONDITIONS].orEmpty(),
            cardPrimaryContactName = values[KEY_CARD_ICE_NAME].orEmpty(),
            cardPrimaryContactPhone = values[KEY_CARD_ICE_PHONE].orEmpty(),
        )
    }

    suspend fun current(): SafetyLocalState = state.first()

    /** Every emergency contact last fetched from `GET /users/me`, available offline for SOS SMS. */
    suspend fun cachedContacts(): List<EmergencyContactDto> {
        val raw = prefs.first()[KEY_CONTACTS] ?: return emptyList()
        return try {
            json.decodeFromString(contactsSerializer, raw)
        } catch (e: SerializationException) {
            Log.w(TAG, "Discarding unreadable contact cache", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Discarding unreadable contact cache", e)
            emptyList()
        }
    }

    suspend fun cachedUserName(): String = prefs.first()[KEY_USER_NAME].orEmpty()

    /** Replaces the offline SOS cache with the authoritative profile values. */
    suspend fun saveProfileCache(userName: String?, contacts: List<EmergencyContactDto>) {
        context.safetyDataStore.edit { values ->
            values[KEY_CONTACTS] = json.encodeToString(contactsSerializer, contacts)
            if (!userName.isNullOrBlank()) values[KEY_USER_NAME] = userName
        }
    }

    suspend fun setFallDetectionEnabled(enabled: Boolean) {
        context.safetyDataStore.edit { it[KEY_FALL_ENABLED] = enabled }
    }

    suspend fun setTestModeEnabled(enabled: Boolean) {
        context.safetyDataStore.edit { it[KEY_TEST_MODE] = enabled }
    }

    /** The QR token this device can show again, or null when none was minted here. */
    suspend fun qrToken(): StoredQrToken? {
        val values = prefs.first()
        val url = values[KEY_SHARE_URL] ?: return null
        return StoredQrToken(id = values[KEY_QR_TOKEN_ID], shareUrl = url, expiresAtMs = values[KEY_QR_EXPIRES_AT])
    }

    suspend fun saveQrToken(token: StoredQrToken) {
        context.safetyDataStore.edit { values ->
            values[KEY_SHARE_URL] = token.shareUrl
            if (token.id != null) values[KEY_QR_TOKEN_ID] = token.id else values.remove(KEY_QR_TOKEN_ID)
            if (token.expiresAtMs != null) values[KEY_QR_EXPIRES_AT] = token.expiresAtMs else values.remove(KEY_QR_EXPIRES_AT)
        }
    }

    /**
     * Forgets [rejected] once the server no longer accepts it, so neither the card nor an SOS shows a dead link.
     * A token minted after [rejected] was read is kept.
     */
    suspend fun clearQrToken(rejected: StoredQrToken) {
        context.safetyDataStore.edit { values ->
            if (values[KEY_SHARE_URL] != rejected.shareUrl) return@edit
            values.remove(KEY_SHARE_URL)
            values.remove(KEY_QR_TOKEN_ID)
            values.remove(KEY_QR_EXPIRES_AT)
        }
    }

    suspend fun saveCachedCard(card: PublicEmergencyCardDto) {
        val ice = card.emergencyContacts.firstOrNull()
        context.safetyDataStore.edit { values ->
            values[KEY_CARD_NAME] = card.fullName.orEmpty()
            values[KEY_CARD_BLOOD] = card.bloodType.orEmpty()
            values[KEY_CARD_ALLERGIES] = card.allergies.joinToString(", ")
            values[KEY_CARD_CONDITIONS] = card.chronicConditions.joinToString(", ")
            values[KEY_CARD_ICE_NAME] = ice?.name.orEmpty()
            values[KEY_CARD_ICE_PHONE] = ice?.phone.orEmpty()
        }
    }

    /** Drops every per-user value (settings, card, contact cache). */
    suspend fun clearUserData() {
        context.safetyDataStore.edit { it.clear() }
    }

    private companion object {
        const val TAG = "SafetyPreferences"
        val KEY_FALL_ENABLED = booleanPreferencesKey("fall_enabled")
        val KEY_TEST_MODE = booleanPreferencesKey("test_mode")
        val KEY_SHARE_URL = stringPreferencesKey("share_url")
        val KEY_QR_TOKEN_ID = stringPreferencesKey("qr_token_id")
        val KEY_QR_EXPIRES_AT = longPreferencesKey("qr_token_expires_at")
        val KEY_CARD_NAME = stringPreferencesKey("card_name")
        val KEY_CARD_BLOOD = stringPreferencesKey("card_blood")
        val KEY_CARD_ALLERGIES = stringPreferencesKey("card_allergies")
        val KEY_CARD_CONDITIONS = stringPreferencesKey("card_conditions")
        val KEY_CARD_ICE_NAME = stringPreferencesKey("card_ice_name")
        val KEY_CARD_ICE_PHONE = stringPreferencesKey("card_ice_phone")
        val KEY_CONTACTS = stringPreferencesKey("sos_contacts_json")
        val KEY_USER_NAME = stringPreferencesKey("sos_user_name")
    }
}
