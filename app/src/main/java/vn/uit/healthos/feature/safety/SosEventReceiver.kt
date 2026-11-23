package vn.uit.healthos.feature.safety

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Explicit-only receiver (not exported) for SOS notification actions and SMS sent/delivered reports.
 * Every controller call is synchronous, so no `goAsync` is needed.
 */
@AndroidEntryPoint
class SosEventReceiver : BroadcastReceiver() {

    @Inject
    lateinit var sosController: SosController

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        when (intent.action) {
            ACTION_CANCEL -> sosController.cancel(eventId)
            ACTION_SEND_NOW -> sosController.sendNow(eventId)
            ACTION_SMS_SENT -> {
                val outcome = if (resultCode == Activity.RESULT_OK) SmsPartOutcome.SENT else SmsPartOutcome.SEND_FAILED
                recordSms(intent, eventId, outcome)
            }
            ACTION_SMS_DELIVERED -> {
                val format = intent.getStringExtra(EXTRA_FORMAT)
                val status = intent.getByteArrayExtra(EXTRA_PDU)?.let { pdu ->
                    runCatching { SmsMessage.createFromPdu(pdu, format)?.status }.getOrNull()
                }
                SmsDeliveryReport.outcome(format, status)?.let { recordSms(intent, eventId, it) }
            }
        }
    }

    private fun recordSms(intent: Intent, eventId: String, outcome: SmsPartOutcome) {
        val phoneKey = intent.getStringExtra(EXTRA_PHONE_KEY) ?: return
        val part = intent.getIntExtra(EXTRA_PART, -1)
        if (part < 0) return
        sosController.recordSmsPart(eventId, phoneKey, part, outcome)
    }

    companion object {
        const val ACTION_CANCEL = "vn.uit.healthos.sos.CANCEL"
        const val ACTION_SEND_NOW = "vn.uit.healthos.sos.SEND_NOW"
        const val ACTION_SMS_SENT = "vn.uit.healthos.sos.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "vn.uit.healthos.sos.SMS_DELIVERED"
        private const val EXTRA_EVENT_ID = "sos_event_id"
        private const val EXTRA_PHONE_KEY = "sos_phone_key"
        private const val EXTRA_PART = "sos_part"

        // Filled in by the telephony stack on delivery reports.
        private const val EXTRA_PDU = "pdu"
        private const val EXTRA_FORMAT = "format"

        private const val REQUEST_CANCEL = 7021
        private const val REQUEST_SEND_NOW = 7022

        fun actionIntent(context: Context, action: String, eventId: String): PendingIntent {
            val intent = Intent(context, SosEventReceiver::class.java)
                .setAction(action)
                .setPackage(context.packageName)
                .putExtra(EXTRA_EVENT_ID, eventId)
            val requestCode = if (action == ACTION_CANCEL) REQUEST_CANCEL else REQUEST_SEND_NOW
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        /**
         * One PendingIntent per (event, contact, part, kind), made distinct by its data URI. The delivered report
         * must be mutable so the telephony stack can attach the status PDU; it stays explicit, which Android 14
         * requires for mutable PendingIntents.
         */
        fun smsReportIntent(
            context: Context,
            action: String,
            eventId: String,
            phoneKey: String,
            part: Int,
        ): PendingIntent {
            val kind = if (action == ACTION_SMS_DELIVERED) "delivered" else "sent"
            val intent = Intent(context, SosEventReceiver::class.java)
                .setAction(action)
                .setPackage(context.packageName)
                .setData(Uri.parse("healthos-sos://sms/$eventId/$phoneKey/$part/$kind"))
                .putExtra(EXTRA_EVENT_ID, eventId)
                .putExtra(EXTRA_PHONE_KEY, phoneKey)
                .putExtra(EXTRA_PART, part)
            val mutability = if (action == ACTION_SMS_DELIVERED) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, 0, intent, mutability or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
