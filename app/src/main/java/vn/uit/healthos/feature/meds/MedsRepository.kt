package vn.uit.healthos.feature.meds

import androidx.work.WorkManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import vn.uit.healthos.core.data.outbox.OutboxDao
import vn.uit.healthos.core.data.outbox.OutboxRepository
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.apiCall
import vn.uit.healthos.core.time.localDayTicker
import vn.uit.healthos.feature.widget.WidgetKind
import vn.uit.healthos.feature.widget.WidgetUpdater
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedsRepository @Inject constructor(
    private val api: MedsApi,
    private val doseCacheDao: DoseCacheDao,
    private val alarmScheduler: DoseAlarmScheduler,
    private val outboxRepository: OutboxRepository,
    private val outboxDao: OutboxDao,
    private val workManager: WorkManager,
    private val widgetUpdater: WidgetUpdater,
    private val json: Json,
) {
    private val zone: ZoneId = ZoneId.of(DEFAULT_MED_TZID)

    /** Serializes cache writes so a refresh never overwrites a dose action recorded while it was in flight. */
    private val cacheMutex = Mutex()

    /** Today's doses; switches to the new day's window at local midnight. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTodayDoses(): Flow<List<DoseCacheEntity>> =
        localDayTicker(zone).flatMapLatest { day ->
            doseCacheDao.observeInWindow(startOfDayMs(day), startOfDayMs(day.plusDays(1)))
        }

    suspend fun listPlans(): ApiResult<List<MedicationPlanDto>> =
        apiCall(json) { api.listPlans().data }

    suspend fun getPlanDetail(planId: String): ApiResult<MedicationPlanDetailDto> =
        apiCall(json) { api.getPlanDetail(planId).data }

    suspend fun getAdherence(planId: String, period: AdherencePeriod = AdherencePeriod.MONTH): ApiResult<AdherenceDto> =
        apiCall(json) { api.getAdherence(planId, period.apiValue).data }

    suspend fun createPlan(body: MedicationPlanCreateBody): ApiResult<MedicationPlanDetailDto> {
        val res = apiCall(json) { api.createPlan(body).data }
        if (res is ApiResult.Success) refreshAndReschedule()
        return res
    }

    /** [reminderIds] are the plan's child reminders, used to stop cached doses that carry no plan id. */
    suspend fun pausePlan(
        planId: String,
        reminderIds: Collection<String>,
        reason: String? = null,
    ): ApiResult<MedicationPlanDto> {
        val res = apiCall(json) { api.pausePlan(planId, MedicationPauseBody(reason)).data }
        if (res is ApiResult.Success) {
            stopPlanDoses(planId, reminderIds)
            refreshAndReschedule()
        }
        return res
    }

    suspend fun resumePlan(planId: String): ApiResult<MedicationPlanDto> {
        val res = apiCall(json) { api.resumePlan(planId).data }
        if (res is ApiResult.Success) refreshAndReschedule()
        return res
    }

    suspend fun refillPlan(planId: String, supplyUnits: Int): ApiResult<MedicationPlanDto> =
        apiCall(json) { api.refillPlan(planId, MedicationRefillBody(supplyUnits)).data }

    suspend fun deletePlan(planId: String, reminderIds: Collection<String>): ApiResult<Unit> {
        val res = apiCall(json) { api.deletePlan(planId) }
        if (res is ApiResult.Success) {
            stopPlanDoses(planId, reminderIds)
            refreshAndReschedule()
        }
        return res
    }

    /**
     * Updates Room immediately so offline actions reflect in the UI at once, cancels/snoozes the local alarm,
     * and enqueues the backend sync in the outbox. Doses that are no longer open (taken, skipped, cancelled…) are
     * left untouched so repeated taps or stale notifications cannot send duplicate actions.
     */
    suspend fun recordDoseAction(dose: DoseCacheEntity, action: String) {
        val recorded = cacheMutex.withLock { recordDoseActionLocked(dose, action) }
        if (recorded) widgetUpdater.update(WidgetKind.NEXT_DOSE)
    }

    /** Returns true when the action was recorded. */
    private suspend fun recordDoseActionLocked(dose: DoseCacheEntity, action: String): Boolean {
        val current = doseCacheDao.getById(dose.occurrenceId) ?: dose
        alarmScheduler.dismissNotification(current.occurrenceId)
        if (!DoseStatus.of(current.status).isOpen) return false
        val spec = mapDoseAction(action, current.reminderId, current.occurrenceId) ?: return false
        val snoozeUntil = if (action == DoseActionReceiver.ACTION_SNOOZE) {
            alarmScheduler.scheduleSnooze(current, minutes = spec.payload.snoozeMinutes)
        } else {
            alarmScheduler.cancel(current.occurrenceId)
            null
        }
        doseCacheDao.upsertAll(listOf(current.copy(status = spec.cacheStatus, snoozedUntilMs = snoozeUntil)))
        outboxRepository.enqueue(
            type = spec.outboxType,
            payload = spec.payload,
            serializer = DoseActionOutboxPayload.serializer(),
        )
        return true
    }

    /**
     * Refreshes today + the next 48 h of occurrences into Room, drops doses the server no longer has, stops alarms
     * for doses that are no longer open, and re-arms exact alarms for the rest.
     */
    suspend fun refreshAndReschedule(): ApiResult<Unit> {
        RescheduleWorker.schedulePeriodic(workManager)
        val nowMs = System.currentTimeMillis()
        val fromMs = minOf(startOfDayMs(LocalDate.now(zone)), nowMs - LOOKBACK_MS)
        val toMs = nowMs + LOOKAHEAD_MS

        val todayRes = apiCall(json) { api.getTodayDoses(DEFAULT_MED_TZID).data }
        val occRes = apiCall(json) {
            api.listOccurrences(
                Instant.ofEpochMilli(fromMs).toString(),
                Instant.ofEpochMilli(toMs).toString(),
                DEFAULT_MED_TZID,
            ).data
        }

        // Network calls stay outside the lock so an offline refresh never delays a dose action.
        cacheMutex.withLock { reconcile(todayRes, occRes, fromMs, toMs, nowMs) }
        widgetUpdater.update(WidgetKind.NEXT_DOSE)
        return if (todayRes is ApiResult.Failure) todayRes else ApiResult.Success(Unit)
    }

    private suspend fun reconcile(
        todayRes: ApiResult<List<MedicationDoseDto>>,
        occRes: ApiResult<List<ReminderOccurrenceDto>>,
        fromMs: Long,
        toMs: Long,
        nowMs: Long,
    ) {
        if (todayRes is ApiResult.Success || occRes is ApiResult.Success) {
            val fetched = LinkedHashMap<String, DoseCacheEntity>()
            (occRes as? ApiResult.Success)?.data?.forEach { occ ->
                if (occ.type != MEDICINE_REMINDER_TYPE) return@forEach
                val ms = parseIsoMillis(occ.scheduledAt) ?: return@forEach
                fetched[occ.id] = DoseCacheEntity(
                    occurrenceId = occ.id,
                    reminderId = occ.reminderId,
                    planId = "",
                    planName = occ.title,
                    strength = null,
                    scheduledAtMs = ms,
                    status = occ.status,
                    snoozedUntilMs = occ.snoozedUntil?.let(::parseIsoMillis),
                )
            }
            (todayRes as? ApiResult.Success)?.data?.forEach { dose ->
                val ms = parseIsoMillis(dose.scheduledAt) ?: return@forEach
                fetched[dose.occurrenceId] = DoseCacheEntity(
                    occurrenceId = dose.occurrenceId,
                    reminderId = dose.reminderId,
                    planId = dose.medicationPlanId,
                    planName = dose.planName,
                    strength = dose.strength,
                    scheduledAtMs = ms,
                    status = dose.status,
                    snoozedUntilMs = fetched[dose.occurrenceId]?.snoozedUntilMs,
                )
            }

            val diff = diffDoseCache(
                cached = doseCacheDao.getInWindow(fromMs, toMs),
                fetched = fetched,
                authoritative = occRes is ApiResult.Success,
                queuedActionIds = queuedDoseActionIds(),
            )
            if (diff.deleteIds.isNotEmpty()) doseCacheDao.deleteByIds(diff.deleteIds)
            if (diff.upserts.isNotEmpty()) doseCacheDao.upsertAll(diff.upserts)
            diff.cancelAlarmIds.forEach(alarmScheduler::stop)
        }

        doseCacheDao.pruneOlderThan(nowMs - PRUNE_AGE_MS)
        alarmScheduler.scheduleAll(doseCacheDao.upcomingPending(nowMs), nowMs)
    }

    /** Stops every open cached dose of a plan right away, so a paused/deleted plan goes quiet even offline. */
    private suspend fun stopPlanDoses(planId: String, reminderIds: Collection<String>) {
        cacheMutex.withLock {
            doseCacheDao.getForPlan(planId, reminderIds.toList())
                .filter { DoseStatus.of(it.status).isOpen }
                .forEach { dose ->
                    alarmScheduler.stop(dose.occurrenceId)
                    doseCacheDao.updateStatus(dose.occurrenceId, DoseStatus.CANCELLED.wire, null)
                }
        }
        widgetUpdater.update(WidgetKind.NEXT_DOSE)
    }

    private suspend fun queuedDoseActionIds(): Set<String> {
        val ids = HashSet<String>()
        var cursor = 0L
        while (true) {
            val batch = outboxDao.pending(afterId = cursor, limit = OUTBOX_PAGE_SIZE)
            if (batch.isEmpty()) break
            batch.forEach { item ->
                if (item.type in DOSE_OUTBOX_TYPES) {
                    decodeDosePayload(json, item.payloadJson)?.let { ids += it.occurrenceId }
                }
            }
            cursor = batch.last().id
        }
        return ids
    }

    private fun startOfDayMs(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

    private companion object {
        const val MEDICINE_REMINDER_TYPE = "medicine"
        const val LOOKBACK_MS = 12 * 3600_000L
        const val LOOKAHEAD_MS = 48 * 3600_000L
        const val PRUNE_AGE_MS = 48 * 3600_000L
        const val OUTBOX_PAGE_SIZE = 200
    }
}
