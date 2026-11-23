package vn.uit.healthos.feature.safety

import vn.uit.healthos.core.data.outbox.OutboxRepository
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.feature.profile.EmergencyContactDto
import vn.uit.healthos.feature.profile.ProfileRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the dispatch steps of a [SosPhase.DISPATCHING] event. Each step records its result on the persisted
 * event and is skipped when already recorded, so resuming after process death never repeats completed work.
 *
 * Order: location (bounded), SMS to every cached contact, email enqueued in the outbox, and only then the
 * network refresh of `GET /users/me`, which can only add SMS for contacts missing from the cache.
 */
@Singleton
class SosDispatcher @Inject constructor(
    private val store: SosEventStore,
    private val locationProvider: LocationProvider,
    private val smsSender: SmsSender,
    private val profileRepository: ProfileRepository,
    private val safetyPreferences: SafetyPreferences,
    private val outboxRepository: OutboxRepository,
) {
    suspend fun dispatch(eventId: String) {
        var event = activeEvent(eventId) ?: return

        if (!event.locationResolved) {
            val location = locationProvider.getBestLocation(timeoutMs = LOCATION_TIMEOUT_MS)
            event = store.update { SosEvents.withLocation(it, eventId, location) }.takeIfActive(eventId) ?: return
        }

        val local = safetyPreferences.current()
        val smsText = SosSmsFormatter.formatMessage(
            userName = safetyPreferences.cachedUserName().ifBlank { local.cardFullName },
            trigger = event.trigger.apiValue,
            occurredAt = Instant.ofEpochMilli(event.armedAtMs),
            location = event.location,
            cardUrl = local.shareUrl,
        )

        sendSms(eventId, offlineContacts(local), smsText)

        if (!event.emailQueued) {
            outboxRepository.enqueue(
                type = OUTBOX_TYPE_SOS_EMAIL,
                payload = SosEmailRequestBody(
                    trigger = event.trigger.apiValue,
                    lat = event.location?.lat,
                    lng = event.location?.lng,
                    accuracyM = event.location?.accuracyM,
                    occurredAt = Instant.ofEpochMilli(event.armedAtMs).toString(),
                    clientEventId = event.id,
                ),
                serializer = SosEmailRequestBody.serializer(),
            )
            event = store.update { SosEvents.markEmailQueued(it, eventId) }.takeIfActive(eventId) ?: return
        }

        if (!event.contactsRefreshed) {
            val profile = (profileRepository.getProfile() as? ApiResult.Success)?.data ?: return
            val contacts = profile.emergencyContacts.orEmpty()
            safetyPreferences.saveProfileCache(profile.fullName ?: profile.displayName, contacts)
            sendSms(eventId, contacts, smsText)
            store.update { SosEvents.markContactsRefreshed(it, eventId) }
        }
    }

    /** Cached profile contacts; before any profile was cached, the emergency card's primary contact. */
    private suspend fun offlineContacts(local: SafetyLocalState): List<EmergencyContactDto> {
        val cached = safetyPreferences.cachedContacts()
        if (cached.isNotEmpty() || local.cardPrimaryContactPhone.isBlank()) return cached
        return listOf(
            EmergencyContactDto(
                name = local.cardPrimaryContactName.ifBlank { local.cardPrimaryContactPhone },
                phone = local.cardPrimaryContactPhone,
                relationship = "",
            ),
        )
    }

    /** Adds rows for new contacts, then sends every row not yet handed to the radio. */
    private fun sendSms(eventId: String, contacts: List<EmergencyContactDto>, text: String) {
        val event = store.update { SosEvents.addContacts(it, eventId, contacts) }.takeIfActive(eventId) ?: return
        event.contacts
            .filter { it.sms.launch == SmsLaunch.PENDING }
            .forEach { row ->
                val result = smsSender.send(eventId, row.phone, text)
                store.update { SosEvents.recordLaunch(it, eventId, row.phoneKey, result.launch, result.partCount) }
            }
    }

    private fun activeEvent(eventId: String): SosEvent? = store.event.value.takeIfActive(eventId)

    private fun SosEvent?.takeIfActive(eventId: String): SosEvent? =
        this?.takeIf { it.id == eventId && it.phase == SosPhase.DISPATCHING }

    private companion object {
        const val LOCATION_TIMEOUT_MS = 10_000L
    }
}
