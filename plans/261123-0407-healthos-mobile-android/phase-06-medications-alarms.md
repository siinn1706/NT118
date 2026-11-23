---
title: "Phase 6: Medications & Alarms"
status: done
priority: P1
effort: 3d
dependencies: [1, 2]
---

# Phase 6: Medications & Alarms (Dev A)

## Goal

Create a medication plan, get an exact alarm at each dose time with "Đã uống / Bỏ qua / Hoãn 10 phút" actions right on the notification, working offline and surviving reboot.

## Backend contracts (verified)

- `POST /v1/medications` (plan + dose reminders atomically), `GET /v1/medications`, `GET /v1/medications/today`, `GET /v1/medications/history`, `GET /v1/medications/{id}`, `PATCH`, `POST /{id}/pause|resume|refill`, `DELETE`, `GET /{id}/adherence`.
- Per-dose actions reuse reminders: `POST /v1/reminders/{id}/done`, `/skip`, `/snooze`; `GET /v1/reminders/occurrences`.

## Files to create

- `feature/meds/{MedsGraph.kt, MedsScreen.kt (today timeline), MedPlanFormScreen.kt, MedDetailScreen.kt (adherence chart), MedsViewModel.kt, MedsRepository.kt, MedsApi.kt, DoseAlarmScheduler.kt, DoseAlarmReceiver.kt, DoseActionReceiver.kt, BootReceiver.kt, DoseCacheEntity.kt, DoseOutboxHandler.kt, RescheduleWorker.kt}`

## Tasks

1. Plan form: name, dosage, dose times (multiple), start/end date, refill threshold.
2. Cache the next 48 h of doses in Room from `GET /medications/today` + occurrences; `DoseAlarmScheduler` sets `AlarmManager.setAlarmClock` per dose (permission `USE_EXACT_ALARM`; app is not published to Play, so the policy restriction does not apply).
3. `DoseAlarmReceiver` posts a high-priority notification (channel "Nhắc uống thuốc") with three actions; `DoseActionReceiver` updates Room immediately and enqueues the outbox (`dose.done|skip|snooze`); snooze also schedules a new local alarm in 10 min.
4. `BootReceiver` + `RescheduleWorker` (periodic 12 h) re-read the cache and reschedule; plan edits/pauses reschedule immediately.
5. Meds tab: today timeline with states (pending/taken/skipped/missed), refill warning when remaining ≤ threshold, adherence % per plan.

## Verification

- Unit tests: scheduler computes alarm times across midnight and DST-free `Asia/Ho_Chi_Minh`; action receiver → outbox payloads.
- On device: plan with a dose 2 min ahead → alarm fires with the screen off; tap "Đã uống" in airplane mode → shown as taken; reconnect → `GET /medications/today` shows done; reboot → future alarms still fire.

## Risks

- OEM battery restrictions do not apply on Pixel; note for other phones only.
- [UNVERIFIED] whether `/medications/today` returns the reminder id needed for `/reminders/{id}/done` — confirm in `schemas/medications.py`.

## Implementation notes (2026-11-23)

Verified against `siinn1706/NT208_HealthOS` (`backend/app/schemas/{medications,reminders}.py`, `backend/app/api/v1/endpoints/{medications,reminders}.py`):

- **`/v1/medications/today` shape (verified)**: `MedicationDoseDTO` returns both `occurrence_id` and `reminder_id` (plus `medication_plan_id`, `plan_name`, `strength`, `scheduled_at`, `status`). Per-dose actions call `POST /v1/reminders/{reminder_id}/done` (`{ occurrence_id }`), `/skip` (`{ occurrence_id }`), and `/snooze` (`{ occurrence_id, minutes }`).
- **Serialization fix caught by unit test**: `ReminderSnoozeBody.minutes` must not carry a Kotlin default value (`= 10`) because `kotlinx.serialization` omits default values by default (`encodeDefaults = false`), which would cause backend 422 on snooze. Kept `minutes: Int` required so `"minutes":10` is always emitted.
- **Alarms, reboot & offline sync**: Added `USE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED` permissions plus `DoseAlarmReceiver`, `DoseActionReceiver`, and `BootReceiver` to `AndroidManifest.xml`; bumped `AppDatabase` to v4 (`DoseCacheEntity`). `DoseActionReceiver` updates Room immediately, schedules a 10-minute snooze alarm if snoozed, and enqueues `dose.done|skip|snooze` into the outbox. `RescheduleWorker` refreshes and reschedules every 12 h.

Verified: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passed (including `DoseAlarmSchedulerTest` and `DoseActionOutboxTest`).

Still open: on-device alarm verification with screen off, airplane-mode taken action syncing on reconnect, and post-reboot alarm persistence on Pixel 8 Pro.
