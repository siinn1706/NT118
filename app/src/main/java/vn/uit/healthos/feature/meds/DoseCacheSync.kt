package vn.uit.healthos.feature.meds

/** Changes to apply to the dose cache and to local alarms after fetching a window of occurrences. */
data class DoseCacheDiff(
    val upserts: List<DoseCacheEntity>,
    val deleteIds: List<String>,
    val cancelAlarmIds: List<String>,
)

/**
 * Reconciles cached doses with freshly fetched ones.
 *
 * @param cached rows currently cached inside the fetched time window.
 * @param fetched server rows keyed by occurrence id.
 * @param authoritative true when [fetched] is the complete server list for the window, so cached rows missing from
 *   it no longer exist server-side (plan deleted, reminder removed) and must be dropped.
 * @param queuedActionIds occurrences with a Taken / Skip / Snooze still waiting in the outbox. Their local status
 *   wins over the server copy (which has not seen the action yet) unless the server cancelled the dose.
 */
fun diffDoseCache(
    cached: List<DoseCacheEntity>,
    fetched: Map<String, DoseCacheEntity>,
    authoritative: Boolean,
    queuedActionIds: Set<String>,
): DoseCacheDiff {
    val cachedById = cached.associateBy { it.occurrenceId }
    val upserts = fetched.values.map { remote ->
        mergeDose(cachedById[remote.occurrenceId], remote, remote.occurrenceId in queuedActionIds)
    }
    val deleteIds = if (authoritative) {
        cached.map { it.occurrenceId }.filterNot { it in fetched }
    } else {
        emptyList()
    }
    val closedIds = upserts.filterNot { DoseStatus.of(it.status).isOpen }.map { it.occurrenceId }
    return DoseCacheDiff(
        upserts = upserts,
        deleteIds = deleteIds,
        cancelAlarmIds = (deleteIds + closedIds).distinct(),
    )
}

private fun mergeDose(local: DoseCacheEntity?, remote: DoseCacheEntity, hasQueuedAction: Boolean): DoseCacheEntity {
    if (local == null) return remote
    // The occurrences feed carries no plan metadata; keep what the today feed cached earlier.
    val withPlan = if (remote.planId.isBlank() && local.planId.isNotBlank()) {
        remote.copy(planId = local.planId, planName = local.planName, strength = remote.strength ?: local.strength)
    } else {
        remote
    }
    val keepLocalStatus = hasQueuedAction && DoseStatus.of(remote.status) != DoseStatus.CANCELLED
    return if (keepLocalStatus) {
        withPlan.copy(status = local.status, snoozedUntilMs = local.snoozedUntilMs)
    } else {
        withPlan
    }
}
