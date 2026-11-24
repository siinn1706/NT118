package vn.uit.healthos.core.data.outbox

import vn.uit.healthos.feature.safety.OUTBOX_TYPE_SOS_EMAIL

/**
 * Which queued writes outlive the session that queued them.
 *
 * An SOS email is a safety alert, not account data: signing out, or the session expiring while the phone is
 * offline, must not silently cancel it. Such items stay queued when the account's local data is wiped and are sent
 * as soon as the same account is signed in again ([OutboxEntity.ownerUserId] keeps them from going out under another
 * account). Every other type is wiped with the rest of the account's data on sign-out.
 */
object OutboxRetention {
    val SESSION_INDEPENDENT_TYPES: List<String> = listOf(OUTBOX_TYPE_SOS_EMAIL)
}
