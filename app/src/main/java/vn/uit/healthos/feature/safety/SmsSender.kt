package vn.uit.healthos.feature.safety

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Pure Vietnamese SOS SMS formatter. Splitting into UCS-2 parts is left to [SmsManager.divideMessage]. */
object SosSmsFormatter {
    fun formatMessage(
        userName: String,
        trigger: String,
        occurredAt: Instant,
        location: SosLocation?,
        cardUrl: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val time = occurredAt.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm dd/MM"))
        val reason = if (trigger == SosTrigger.FALL.apiValue) "phát hiện té ngã" else "kích hoạt SOS khẩn cấp"
        val sb = StringBuilder()
        sb.append("[SOS HealthOS] ").append(userName.ifBlank { "Người dùng" })
            .append(" ").append(reason).append(" lúc ").append(time).append(".")
        if (location != null) {
            sb.append(" Vị trí: ").append(location.mapsUrl())
        }
        if (!cardUrl.isNullOrBlank()) {
            sb.append(" Thẻ y tế: ").append(cardUrl)
        }
        return sb.toString()
    }
}

data class SmsLaunchResult(val launch: SmsLaunch, val partCount: Int = 0)

/**
 * Hands an SOS SMS to the radio. Each part carries explicit sent/delivered PendingIntents addressed to
 * [SosEventReceiver], which records the real per-part outcome on the SOS event.
 */
@Singleton
class SmsSender @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.SEND_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    fun send(eventId: String, phone: String, message: String): SmsLaunchResult {
        if (!hasPermission()) return SmsLaunchResult(SmsLaunch.NO_PERMISSION)
        val cleaned = phone.trim()
        val phoneKey = SosEvents.phoneKey(cleaned)
        if (phoneKey.isEmpty()) return SmsLaunchResult(SmsLaunch.INVALID_NUMBER)
        return try {
            val manager = smsManager() ?: return SmsLaunchResult(SmsLaunch.ERROR)
            val parts: ArrayList<String> = manager.divideMessage(message)
            if (parts.isEmpty()) return SmsLaunchResult(SmsLaunch.ERROR)
            val sent = ArrayList<PendingIntent>(parts.size)
            val delivered = ArrayList<PendingIntent>(parts.size)
            parts.indices.forEach { part ->
                sent += SosEventReceiver.smsReportIntent(context, SosEventReceiver.ACTION_SMS_SENT, eventId, phoneKey, part)
                delivered += SosEventReceiver.smsReportIntent(
                    context,
                    SosEventReceiver.ACTION_SMS_DELIVERED,
                    eventId,
                    phoneKey,
                    part,
                )
            }
            manager.sendMultipartTextMessage(cleaned, null, parts, sent, delivered)
            SmsLaunchResult(SmsLaunch.QUEUED, parts.size)
        } catch (e: SecurityException) {
            Log.w(TAG, "SMS permission rejected by telephony", e)
            SmsLaunchResult(SmsLaunch.NO_PERMISSION)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "SMS rejected for invalid destination", e)
            SmsLaunchResult(SmsLaunch.INVALID_NUMBER)
        } catch (e: RuntimeException) {
            Log.w(TAG, "SMS could not be handed to the radio", e)
            SmsLaunchResult(SmsLaunch.ERROR)
        }
    }

    private fun smsManager(): SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(SmsManager::class.java)
    } else {
        @Suppress("DEPRECATION")
        SmsManager.getDefault()
    }

    private companion object {
        const val TAG = "SmsSender"
    }
}
