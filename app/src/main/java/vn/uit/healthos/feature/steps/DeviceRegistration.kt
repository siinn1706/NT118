package vn.uit.healthos.feature.steps

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import vn.uit.healthos.core.data.session.UserDataCleanup
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.DataEnvelope
import vn.uit.healthos.core.network.apiCall
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.deviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "healthos_device")

@Serializable
data class DeviceConnectBody(
    val provider: String = "health_connect",
    val model: String? = null,
    @SerialName("device_label") val deviceLabel: String? = null,
    @SerialName("external_account_id") val externalAccountId: String,
    val scopes: List<String> = listOf("Steps", "HeartRate"),
)

@Serializable
data class ConnectedDeviceDto(
    val id: String,
    val provider: String,
    val name: String,
    val connected: Boolean = true,
)

@Serializable
data class HealthRecordIn(
    @SerialName("external_id") val externalId: String,
    @SerialName("external_version") val externalVersion: Long? = null,
    @SerialName("metric_type") val metricType: String,
    val value: Double,
    val unit: String,
    @SerialName("recorded_at") val recordedAt: String,
    val source: String = "health_connect",
    @SerialName("source_app") val sourceApp: String = "vn.uit.healthos",
)

@Serializable
data class HealthIngestBatch(
    val provider: String = "health_connect",
    val records: List<HealthRecordIn>,
)

@Serializable
data class HealthIngestResultDto(
    val inserted: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val skipped: Int = 0,
)

interface DevicesApi {
    @POST("v1/devices")
    suspend fun connectDevice(@Body body: DeviceConnectBody): DataEnvelope<ConnectedDeviceDto>

    @POST("v1/devices/{deviceId}/ingest")
    suspend fun ingest(
        @Path("deviceId") deviceId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: HealthIngestBatch,
    ): DataEnvelope<HealthIngestResultDto>
}

@Singleton
class DeviceRegistration @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: DevicesApi,
    private val json: Json,
) {
    private val mutex = Mutex()

    suspend fun getOrRegisterDeviceId(): ApiResult<String> = mutex.withLock {
        val prefs = context.deviceDataStore.data.first()
        val existingId = prefs[KEY_DEVICE_ID]
        if (!existingId.isNullOrBlank()) {
            return ApiResult.Success(existingId)
        }
        val accountId = prefs[KEY_ACCOUNT_ID] ?: UUID.randomUUID().toString().also { generated ->
            context.deviceDataStore.edit { it[KEY_ACCOUNT_ID] = generated }
        }
        val label = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android Device" }

        val res = apiCall(json) {
            api.connectDevice(
                DeviceConnectBody(
                    provider = "health_connect",
                    model = Build.MODEL,
                    deviceLabel = label,
                    externalAccountId = accountId,
                    scopes = listOf("Steps", "HeartRate"),
                ),
            ).data
        }
        return when (res) {
            is ApiResult.Success -> {
                context.deviceDataStore.edit { it[KEY_DEVICE_ID] = res.data.id }
                ApiResult.Success(res.data.id)
            }
            is ApiResult.Failure -> res
        }
    }

    suspend fun cachedDeviceId(): String? = context.deviceDataStore.data.first()[KEY_DEVICE_ID]

    /** Forgets [deviceId] if it is still the cached one, so the next call registers again. */
    suspend fun invalidate(deviceId: String) = mutex.withLock {
        context.deviceDataStore.edit { prefs ->
            if (prefs[KEY_DEVICE_ID] == deviceId) prefs.remove(KEY_DEVICE_ID)
        }
    }

    /** Drops the registration and the account id it was made with; the next user registers afresh. */
    suspend fun clear() = mutex.withLock {
        context.deviceDataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_ACCOUNT_ID = stringPreferencesKey("external_account_id")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DevicesNetworkModule {
    @Provides
    @Singleton
    fun provideDevicesApi(retrofit: Retrofit): DevicesApi = retrofit.create(DevicesApi::class.java)

    @Provides
    @IntoSet
    fun provideDeviceRegistrationCleanup(registration: DeviceRegistration): UserDataCleanup =
        UserDataCleanup { registration.clear() }
}
