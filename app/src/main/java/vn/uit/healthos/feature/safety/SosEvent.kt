package vn.uit.healthos.feature.safety

import kotlinx.serialization.Serializable
import vn.uit.healthos.feature.profile.EmergencyContactDto

@Serializable
enum class SosTrigger(val apiValue: String, val countdownSeconds: Int) {
    FALL("fall", 30),
    MANUAL("manual", 5),
}

@Serializable
enum class SosPhase { COUNTDOWN, DISPATCHING, DONE, CANCELLED }

/** What happened when the SMS was handed (or not) to the radio. */
@Serializable
enum class SmsLaunch { PENDING, QUEUED, NO_PERMISSION, INVALID_NUMBER, ERROR }

/** Per-part report delivered by the SMS sent/delivered PendingIntents. */
enum class SmsPartOutcome { SENT, SEND_FAILED, DELIVERED, DELIVERY_FAILED }

/** User-facing SMS status derived from the real radio reports. */
enum class SmsStatus { SENDING, SENT, DELIVERED, FAILED, NO_PERMISSION }

@Serializable
data class SmsRecord(
    val launch: SmsLaunch = SmsLaunch.PENDING,
    val partCount: Int = 0,
    val sentOk: Set<Int> = emptySet(),
    val sentFailed: Set<Int> = emptySet(),
    val delivered: Set<Int> = emptySet(),
    val deliveryFailed: Set<Int> = emptySet(),
) {
    val status: SmsStatus
        get() = when (launch) {
            SmsLaunch.PENDING -> SmsStatus.SENDING
            SmsLaunch.NO_PERMISSION -> SmsStatus.NO_PERMISSION
            SmsLaunch.INVALID_NUMBER, SmsLaunch.ERROR -> SmsStatus.FAILED
            SmsLaunch.QUEUED -> when {
                sentFailed.isNotEmpty() || deliveryFailed.isNotEmpty() -> SmsStatus.FAILED
                partCount > 0 && delivered.size >= partCount -> SmsStatus.DELIVERED
                partCount > 0 && sentOk.size >= partCount -> SmsStatus.SENT
                else -> SmsStatus.SENDING
            }
        }

    fun record(part: Int, outcome: SmsPartOutcome): SmsRecord {
        if (part !in 0 until partCount) return this
        return when (outcome) {
            SmsPartOutcome.SENT -> copy(sentOk = sentOk + part)
            SmsPartOutcome.SEND_FAILED -> copy(sentFailed = sentFailed + part)
            SmsPartOutcome.DELIVERED -> copy(sentOk = sentOk + part, delivered = delivered + part)
            SmsPartOutcome.DELIVERY_FAILED -> copy(deliveryFailed = deliveryFailed + part)
        }
    }
}

@Serializable
data class SosContactRow(
    val name: String,
    val phone: String,
    val email: String? = null,
    val sms: SmsRecord = SmsRecord(),
) {
    val phoneKey: String get() = SosEvents.phoneKey(phone)
}

/** One SOS incident, persisted so countdown and dispatch survive Activity and process recreation. */
@Serializable
data class SosEvent(
    val id: String,
    val trigger: SosTrigger,
    val armedAtMs: Long,
    val deadlineMs: Long,
    val phase: SosPhase = SosPhase.COUNTDOWN,
    val location: SosLocation? = null,
    val locationResolved: Boolean = false,
    val contacts: List<SosContactRow> = emptyList(),
    val emailQueued: Boolean = false,
    val contactsRefreshed: Boolean = false,
    val finishedAtMs: Long? = null,
) {
    val isActive: Boolean get() = phase == SosPhase.COUNTDOWN || phase == SosPhase.DISPATCHING

    val smsSentCount: Int
        get() = contacts.count { it.sms.status == SmsStatus.SENT || it.sms.status == SmsStatus.DELIVERED }
}

/** Pure transitions of [SosEvent]. Every transition is a no-op unless it targets the current event id. */
object SosEvents {

    /** Returns the still-active event unchanged, so a repeated trigger never starts a second incident. */
    fun arm(current: SosEvent?, newId: String, trigger: SosTrigger, nowMs: Long): SosEvent {
        if (current != null && current.isActive) return current
        return SosEvent(
            id = newId,
            trigger = trigger,
            armedAtMs = nowMs,
            deadlineMs = nowMs + trigger.countdownSeconds * 1_000L,
        )
    }

    fun cancel(current: SosEvent?, id: String, nowMs: Long): SosEvent? = current.ifTarget(id) {
        if (it.phase == SosPhase.COUNTDOWN) it.copy(phase = SosPhase.CANCELLED, finishedAtMs = nowMs) else it
    }

    /** Countdown expiry and "send now" both land here; only a counting-down event moves on. */
    fun beginDispatch(current: SosEvent?, id: String): SosEvent? = current.ifTarget(id) {
        if (it.phase == SosPhase.COUNTDOWN) it.copy(phase = SosPhase.DISPATCHING) else it
    }

    fun withLocation(current: SosEvent?, id: String, location: SosLocation?): SosEvent? = current.ifDispatching(id) {
        it.copy(location = location, locationResolved = true)
    }

    /** Adds a row for every contact whose phone is not already in the event. */
    fun addContacts(current: SosEvent?, id: String, contacts: List<EmergencyContactDto>): SosEvent? =
        current.ifDispatching(id) { event ->
            val known = event.contacts.mapTo(mutableSetOf()) { it.phoneKey }
            val added = contacts.mapNotNull { contact ->
                val key = phoneKey(contact.phone)
                if (key.isEmpty() || !known.add(key)) {
                    null
                } else {
                    SosContactRow(name = contact.name, phone = contact.phone.trim(), email = contact.email)
                }
            }
            if (added.isEmpty()) event else event.copy(contacts = event.contacts + added)
        }

    fun recordLaunch(current: SosEvent?, id: String, phoneKey: String, launch: SmsLaunch, partCount: Int): SosEvent? =
        current.updateRow(id, phoneKey) { it.copy(sms = it.sms.copy(launch = launch, partCount = partCount)) }

    fun recordSmsPart(current: SosEvent?, id: String, phoneKey: String, part: Int, outcome: SmsPartOutcome): SosEvent? =
        current.updateRow(id, phoneKey) { it.copy(sms = it.sms.record(part, outcome)) }

    fun markEmailQueued(current: SosEvent?, id: String): SosEvent? = current.ifDispatching(id) {
        it.copy(emailQueued = true)
    }

    fun markContactsRefreshed(current: SosEvent?, id: String): SosEvent? = current.ifDispatching(id) {
        it.copy(contactsRefreshed = true)
    }

    fun complete(current: SosEvent?, id: String, nowMs: Long): SosEvent? = current.ifDispatching(id) {
        it.copy(phase = SosPhase.DONE, finishedAtMs = nowMs)
    }

    /**
     * A countdown whose deadline passed long ago was abandoned (process killed, device rebooted). Sending it now
     * would alert contacts about a stale incident, so it is closed as cancelled instead.
     */
    fun dropStaleCountdown(current: SosEvent?, nowMs: Long): SosEvent? {
        if (current == null || current.phase != SosPhase.COUNTDOWN) return current
        if (nowMs - current.deadlineMs <= STALE_COUNTDOWN_MS) return current
        return current.copy(phase = SosPhase.CANCELLED, finishedAtMs = nowMs)
    }

    const val STALE_COUNTDOWN_MS = 10 * 60_000L

    fun remainingSeconds(event: SosEvent, nowMs: Long): Int {
        val remainingMs = (event.deadlineMs - nowMs).coerceAtLeast(0L)
        return ((remainingMs + 999L) / 1_000L).toInt()
    }

    /** Digits-only phone key; a Vietnamese `+84` / `84` prefix is folded into the local `0` form. */
    fun phoneKey(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (phone.trim().startsWith("+84") || (digits.startsWith("84") && digits.length >= 11)) {
            "0" + digits.removePrefix("84")
        } else {
            digits
        }
    }

    private inline fun SosEvent?.ifTarget(id: String, block: (SosEvent) -> SosEvent): SosEvent? =
        if (this != null && this.id == id) block(this) else this

    private inline fun SosEvent?.ifDispatching(id: String, block: (SosEvent) -> SosEvent): SosEvent? =
        ifTarget(id) { if (it.phase == SosPhase.DISPATCHING) block(it) else it }

    /** SMS reports may arrive after the dispatch completed, so rows stay updatable in DONE too. */
    private inline fun SosEvent?.updateRow(
        id: String,
        phoneKey: String,
        block: (SosContactRow) -> SosContactRow,
    ): SosEvent? = ifTarget(id) { event ->
        if (event.phase != SosPhase.DISPATCHING && event.phase != SosPhase.DONE) return@ifTarget event
        if (event.contacts.none { it.phoneKey == phoneKey }) return@ifTarget event
        event.copy(contacts = event.contacts.map { if (it.phoneKey == phoneKey) block(it) else it })
    }
}

/**
 * Interprets an SMS status report. 3GPP TP-Status: 0x00-0x1F completed, 0x20-0x3F still trying,
 * 0x40 and above permanent failure. Other formats only report completion reliably, so a report
 * with status 0 (or without a parsable PDU) counts as delivered and anything else stays unknown.
 */
object SmsDeliveryReport {
    fun outcome(format: String?, status: Int?): SmsPartOutcome? {
        if (status == null) return SmsPartOutcome.DELIVERED
        if (format == FORMAT_3GPP) {
            return when {
                status < 0x20 -> SmsPartOutcome.DELIVERED
                status < 0x40 -> null
                else -> SmsPartOutcome.DELIVERY_FAILED
            }
        }
        return if (status == 0) SmsPartOutcome.DELIVERED else null
    }

    const val FORMAT_3GPP = "3gpp"
}
