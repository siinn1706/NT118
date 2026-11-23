package vn.uit.healthos.feature.safety

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchronous, durable holder of the current [SosEvent]. SharedPreferences with `commit` is used instead of
 * DataStore so that a transition is on disk before the caller acts on it (sending SMS, posting notifications)
 * and so the very first frame of the countdown screen already sees the armed event.
 */
@Singleton
class SosEventStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val _event = MutableStateFlow(read())
    val event: StateFlow<SosEvent?> = _event.asStateFlow()

    /** Atomically applies [transform] to the current event, persisting before publishing. Returns the new value. */
    fun update(transform: (SosEvent?) -> SosEvent?): SosEvent? = synchronized(lock) {
        val current = _event.value
        val next = transform(current)
        if (next != current) {
            write(next)
            _event.value = next
        }
        next
    }

    fun clear() {
        update { null }
    }

    private fun read(): SosEvent? {
        val raw = prefs.getString(KEY_EVENT, null) ?: return null
        return try {
            json.decodeFromString(SosEvent.serializer(), raw)
        } catch (e: SerializationException) {
            Log.w(TAG, "Discarding unreadable SOS event", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Discarding unreadable SOS event", e)
            null
        }
    }

    private fun write(event: SosEvent?) {
        prefs.edit(commit = true) {
            if (event == null) {
                remove(KEY_EVENT)
            } else {
                putString(KEY_EVENT, json.encodeToString(SosEvent.serializer(), event))
            }
        }
    }

    private companion object {
        const val TAG = "SosEventStore"
        const val PREFS_NAME = "healthos_sos_event"
        const val KEY_EVENT = "event"
    }
}
