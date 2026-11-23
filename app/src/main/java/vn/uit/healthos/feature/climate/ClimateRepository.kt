package vn.uit.healthos.feature.climate

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class ClimateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val forecastApi: OpenMeteoForecastApi,
    private val airQualityApi: OpenMeteoAirQualityApi,
) {
    @Volatile
    private var cached: ClimateConditions? = null

    fun hasLocationPermission(): Boolean {
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    /** Returns cached conditions if < 1 hour old, otherwise fetches from Open-Meteo. Null on error or no permission. */
    suspend fun getConditions(forceRefresh: Boolean = false): ClimateConditions? {
        val now = System.currentTimeMillis()
        val existing = cached
        if (!forceRefresh && existing != null && (now - existing.fetchedAtMs) < CACHE_TTL_MS) {
            return existing
        }
        if (!hasLocationPermission()) return existing
        val coords = lastKnownCoordinates() ?: return existing

        return runCatching {
            coroutineScope {
                val weatherDef = async { forecastApi.currentForecast(coords.first, coords.second) }
                val aqiDef = async { airQualityApi.currentAirQuality(coords.first, coords.second) }
                val weather = weatherDef.await().current ?: return@coroutineScope null
                val aqi = aqiDef.await().current
                ClimateConditions(
                    temperatureC = weather.temperatureC ?: return@coroutineScope null,
                    apparentTempC = weather.apparentTempC ?: weather.temperatureC,
                    uvIndex = weather.uvIndex,
                    usAqi = aqi?.usAqi,
                    pm25 = aqi?.pm25,
                    fetchedAtMs = now,
                )
            }
        }.getOrNull()?.also { cached = it } ?: existing
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastKnownCoordinates(): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(loc.latitude to loc.longitude)
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    private companion object {
        const val CACHE_TTL_MS = 60 * 60_000L
    }
}
