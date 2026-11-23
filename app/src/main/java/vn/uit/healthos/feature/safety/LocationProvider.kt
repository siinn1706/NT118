package vn.uit.healthos.feature.safety

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Serializable
data class SosLocation(
    val lat: Double,
    val lng: Double,
    val accuracyM: Float?,
) {
    fun mapsUrl(): String = "https://maps.google.com/?q=$lat,$lng"
}

@Singleton
class LocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * High-accuracy fix with a timeout, falling back to the last known location. Never throws: a revoked
     * permission or a location stack failure yields null so an SOS still goes out without coordinates.
     */
    @SuppressLint("MissingPermission")
    suspend fun getBestLocation(timeoutMs: Long = 10_000L): SosLocation? {
        if (!hasPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val cts = CancellationTokenSource()
                cont.invokeOnCancellation { cts.cancel() }
                try {
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { loc ->
                            cont.resume(loc?.let { SosLocation(it.latitude, it.longitude, it.accuracy) })
                        }
                        .addOnFailureListener { cont.resume(null) }
                } catch (e: SecurityException) {
                    cont.resume(null)
                }
            }
        }
        if (fresh != null) return fresh

        return withTimeoutOrNull(LAST_LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                try {
                    client.lastLocation
                        .addOnSuccessListener { loc ->
                            cont.resume(loc?.let { SosLocation(it.latitude, it.longitude, it.accuracy) })
                        }
                        .addOnFailureListener { cont.resume(null) }
                } catch (e: SecurityException) {
                    cont.resume(null)
                }
            }
        }
    }

    private companion object {
        const val LAST_LOCATION_TIMEOUT_MS = 3_000L
    }
}
