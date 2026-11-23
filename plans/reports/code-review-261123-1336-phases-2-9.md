# Code review — phases 2–9 (Gemini implementation)

Date: 2026-11-23. Scope: everything under `app/src/main/java/vn/uit/healthos/feature/**` plus shared wiring, checked against the plan and the HealthOS backend (`backend/app`, fresh clone).

## Outcome

- Build, 61 unit tests and lint pass. No Kotlin warnings, no raw hex in features, no TODO/fake markers.
- Endpoint paths, methods and DTO field names match the backend everywhere except the missing `POST /v1/emergency/sos` (known, backend work).
- The defects are in behaviour: lifecycle, failure paths, data mapping and Android platform rules. 17 High, grouped below by fix priority.

Spot-verified by the controller: Room migration missing, SMS result receiver missing, `EmergencyCardActivity` exported, Home KPI keys, quick-reminder `type` dropped by `encodeDefaults=false`, chat `Error` frame leaves `isStreaming=true`, `/v1/emergency/sos` absent.

## P0 — fix before any device demo

| # | Area | File | Problem |
|---|------|------|---------|
| 1 | DB | `core/data/DataModule.kt:57` | Room v4, no migrations and no destructive fallback. Any device with a v1–v3 build crashes at first DB access. Add `fallbackToDestructiveMigrationFrom(1,2,3)` (cache tables) or real migrations. |
| 2 | SOS | `safety/SosCountdownActivity.kt:146` | Countdown + dispatch live in Activity composition. Rotation/theme change restarts countdown and re-sends SMS; destroy mid-dispatch loses the email enqueue. Move to service/WorkManager with a persisted event id. |
| 3 | SOS | `safety/FallDetectionService.kt:102` | Full-screen intent shows only heads-up when the phone is in use, fallback `startActivity` is blocked (BAL). Fall SOS never fires. Run the timer in the service; Activity only offers cancel. |
| 4 | SOS | `safety/SosDispatcher.kt:52-72` | Offline fallback has only the first contact from the public card (may be empty). SMS waits for `GET /users/me` (up to 45 s). Cache all contacts locally, send SMS first. |
| 5 | SOS | `safety/SmsSender.kt:90-111` | Sent/delivered PendingIntents implicit, no receiver registered. "Đã gửi" is not a real status. |
| 6 | SOS | `safety/SosDispatcher.kt:105` | Backend has no `/v1/emergency/sos`; 404 is non-retryable so the email is dropped, not queued. Add the endpoint or queue until it exists. |
| 7 | Steps | `steps/StepSyncHandler.kt:52-78`, `auth/AuthRepository.kt:169` | Cached `device_id` never cleared on logout or on 404. After re-seed/user switch every ingest 404s and is dropped silently. Re-register on 404, clear on logout. Logout also leaves outbox and caches for the next user. |
| 8 | Steps | `steps/StepSensorService.kt:72-85` | One coroutine per sensor event, unsynchronised read-modify-write. Out-of-order write hits the reboot branch and adds the whole boot counter (30k+). Serialise with a Mutex/Channel. |
| 9 | Home | `home/HomeViewModel.kt:163-178` | Reads `calories`, `heart_rate`, `hydration_ml` KPIs; backend sends `caloriesBurned`, `heartRate`, `steps`, `sleepScore`, intake in `goals[key=calories]`. Calories tile always empty, Vital Score wrong. |
| 10 | Home | `home/HomeScreen.kt:395` | Rule-based fallback returns codes (`EXERCISE_WALK_MORE`, `CALORIE_LOW`) + `message_params`; shown raw. Map to strings. |
| 11 | Chat | `chat/ChatViewModel.kt:240-246`, `ChatStreamParser.kt:96` | Server `error` frame leaves `isStreaming=true`; Retry no-ops. Parser ignores `display_text`. |
| 12 | Chat | `chat/ChatApi.kt:69` | `QuickReminderCreateBody.type` default omitted by `encodeDefaults=false`; backend requires it → 422 every time. |
| 13 | Meds | `meds/MedsRepository.kt:115-136`, `DoseAlarmReceiver.kt:142` | Paused/deleted plans: cancelled rows never reach `cancel()`, receiver doesn't check status. Alarms keep ringing. |
| 14 | Meds | `meds/MedsScreen.kt:124,249` | `fired` status loses action buttons; `cancelled` shows as pending. |
| 15 | Profile | `core/data/session/AppPreferencesRepository.kt:104` | Calorie target/activity/goal wiped on logout or expiry and never recomputed (completed onboarding routes to Home). |
| 16 | Fall | `safety/FallDetectionService.kt:71` | Non-wake-up accelerometer, no wake lock: unreliable with screen off. |
| 17 | PPG | `ppg/PpgSignalProcessor.kt:65-76` | No-finger samples stay in the 20 s window → LOW_QUALITY / timeout. Also analyzer-thread vs main-thread `reset()` race can crash (`PpgViewModel.kt:51`). |

### P0 fix status (2026-11-23)

All 17 P0 rows are fixed. `assembleDebug`, `testDebugUnitTest` (113 tests) and `lintDebug` pass. Nothing has been checked on a device yet.

- **#1:** Room is now v5, with an auto-migration from 4 to 5 that adds `outbox.dedupe_key`. Versions 1–3 fall back to a destructive migration.
- **#2–#6, #16:**
  - `SosController` runs the countdown and dispatch from a persisted event id. Fall SOS is armed inside `FallDetectionService`, and manual SOS runs in `SosAlertService`.
  - SMS goes out first, to every cached contact. Sent and delivered status comes from an explicit receiver.
  - The SOS email waits in the outbox, which retries 404 responses.
  - Fall detection uses the wake-up accelerometer, or a wake lock when the phone has none.
- **#7:** a 404 from ingest clears the cached device and registers again.
  - `SessionCleaner` handles account changes:
    - explicit logout wipes the outbox, the caches, the device registration and the SOS state;
    - session expiry keeps everything;
    - a different account signing in wipes everything, including nutrition targets.
- **#8:** sensor readings go through a conflated channel and a mutex.
  - Step totals for a given day replace any pending upload for that day.
  - The finished day is uploaded at rollover.
- **#9, #10:** `DashboardSummaryDto.toKpis()` reads the real backend keys.
  - Every insight and exercise code maps to a Vietnamese string, sorted by ascending priority.
  - The Vital Score shows an empty state when there is no data.
- **#11, #12:** an `error` frame ends the stream and shows `display_text`.
  - Retry re-sends the same `client_message_id`.
  - Required request fields no longer have defaults, so they are always sent: `type`/`repeat` and `locale`/`content_type`.
- **#13, #14:** each refresh diffs the dose cache and cancels alarms for doses that are gone or closed.
  - The receiver re-checks the cached status before ringing.
  - Every backend dose status has an explicit UI mapping.
- **#15:** nutrition targets are kept per user across logout.
  - `NutritionTargetRestorer` rebuilds them from `/users/me` and the health goal when they are missing. Activity level falls back to moderate, because the backend does not store it.
- **#17:** the PPG window resets when the finger lifts or frames stop.
  - Processor state is guarded by one lock.
  - The stale `lastGoodBpm` fallback is removed.

P1 items fixed along the way:
- bottom-bar `popUpTo`;
- step outbox spam;
- Meds: notification permission, offline actions surviving a refresh, refill warning, midnight rollover;
- PPG: camera binding and auto-exposure lock;
- Safety: keep-screen-on, permission prompts before SOS, SOS notification cleanup.

Contacts saved through the profile now refresh the offline SOS cache.

Still open:
- the remaining P1/P2 items;
- the backend `POST /v1/emergency/sos`;
- `EmergencyCardActivity` is still exported;
- fall detection is not restored after a reboot.

### P1 fix status (2026-11-23)

Every P1 bullet below is fixed. `assembleDebug`, `testDebugUnitTest` (163 tests) and `lintDebug` all pass. Nothing has been checked on a device yet.

- **Errors:**
  - User-facing errors go through `ApiError.toUiText()` (`core/network/ApiErrorText.kt`) and hold `core/ui/UiText`.
  - Backend codes and HTTP statuses map to Vietnamese strings in `strings_errors.xml`.
- **App lock:**
  - The cold-start lock is applied once the setting has loaded, so it no longer depends on lifecycle order.
  - The lock screen offers sign-out when the device has no credential.
  - Any sign-out now returns the user to the sign-in screen.
- **Navigation:** the bottom bar now pops back to Home.
- **Emergency contacts:** a new editor (`EmergencyContactsRoute`) opens from Me and from Safety. Safety shows a call-to-action when the user has no contacts.
- **Meals:**
  - Meals are grouped by local day, using UTC queries plus paging.
  - Photos are rotated using their EXIF orientation.
  - Cancelling or failing an analysis deletes the server meal, queued through `meal.delete` when the delete can't run immediately.
  - A failed edit keeps the draft.
  - A permanently failed pending row is removed, and is never counted twice.
- **Midnight:** `core/time/localDayTicker()` drives Home, Hydration and Meds.
- **Chat:**
  - SSE uses `trySendBlocking`, so no events are lost.
  - The stream survives rotation.
  - Image attach is hidden.
- **Safety:**
  - The QR code is reused, with a scoped revoke and retry on 409.
  - `EmergencyCardActivity` is no longer exported.
  - `SafetyBootReceiver` restores fall detection after a reboot or an update, without the location type.

Known limits:
- A fall SOS sent after a reboot has no location until monitoring is restarted from the open app.
- The QR auto-revoke cannot tell apart two Android phones that belong to the same user.
- A pending meal row dropped by the outbox stays visible until the other queued meal items drain.
- Onboarding still uses its own, looser contact validation.

### P2 fix status (2026-11-23)

Every P2 item is fixed. `assembleDebug`, `testDebugUnitTest` and `lintDebug` all pass.

- **Defaults that looked like data:**
  - Missing AQI or UV readings are now nullable. The screen shows "—", and a missing reading never triggers an outdoor warning.
  - Onboarding fields for date of birth, height and weight now start empty.
  - The target weight starts from the weight the user entered.
  - Vital Score shows an empty state when there is no data; this was fixed together with the P0 items.
- **Contact validation:** onboarding contacts now use the same validation as the contacts editor (`EmergencyContactInput`), so the hard-coded "Người thân" fallback is gone.
- **Hard-coded UI strings** moved to resources:
  - chat: reminder-created message, empty reply, voice errors;
  - PPG: the 30-second timeout;
  - meds: adherence labels and form placeholders;
  - meals: macro labels.
- **Strings left in code on purpose:** text sent to the server (default dish and reminder names) and the SOS SMS body.
- **Adherence:** the request now sends `period=7d|30d|90d` (`AdherencePeriod`) instead of `period_days`.
- **`FLAG_SECURE`:** applied while app lock is on.
- **Voice input:**
  - Stopping dictation now delivers the final result.
  - Dictated text is appended to what was already typed.
  - Errors are mapped to Vietnamese strings.
  - Leaving the screen cancels dictation.
- **Done earlier:**
  - exercise priority order;
  - tests that only covered dead code;
  - SOS notification cleanup.

## P1 — fix before rehearsal

- Bottom-bar `popUpTo(findStartDestination())` is a no-op after first-run sign-in → back stack grows (`navigation/AppNavHost.kt:108`).
- App lock: permanent lockout when device credential removed; cold-start lock skipped if DataStore emits late (`AppLockManager.kt:52-90`).
- Raw English server/network messages in UI; map `ApiError.code` to strings (`AuthViewModel.kt:436`, `OnboardingViewModel.kt:217`, `SseClient.kt:31`).
- Emergency contacts editable only in onboarding, and optional there; Safety depends on them.
- Meals: pending row stuck/double-counted on permanent failure (`MealOutboxHandler.kt:48`); failed PATCH discards edits (`MealsViewModel.kt:305`); cancel/fail leaves a server meal (`:183`); EXIF rotation ignored (`ImageDownscaler.kt:31`); UTC day grouping hides pre-07:00 meals (`MealsRepository.kt:29`).
- Midnight rollover: Home steps/water, meds "today" window, previous-day steps never uploaded.
- Step outbox spam vs 6/min ingest limit → items exhausted after offline period.
- Chat: retry duplicates user message and drops attachment; SSE `trySend` drops deltas on burst (`SseClient.kt:24`); rotation cancels stream (`ChatScreen.kt:99`); image attach never reaches the model (backend sends text only) — hide or label.
- Meds: `POST_NOTIFICATIONS` never requested from Meds; refresh overwrites queued offline actions and re-arms alarms; refill warning hard-coded `<= 5` on the wrong field.
- Safety: no keep-screen-on during countdown; manual SOS never asks for SMS/location permission; QR mints a new token each tap (6th → 409); `EmergencyCardActivity` exported; fall detection not restored after reboot.
- PPG: stale `lastGoodBpm` reported; camera can bind after leaving screen; AE lock missing.

## P2 — polish

Fake defaults (`VitalScore` 0.72 with no data, AQI/UV 0, onboarding DOB/height/weight placeholders submitted as data); hard-coded Vietnamese strings in ViewModels; exercise priority order reversed; adherence `period_days` vs backend `period=30d`; tests covering dead code (`DoseAlarmTimeCalculator`, `splitForSms`); no `FLAG_SECURE`; voice input tap-to-toggle and loses final result; SOS notification not cancelled.

## Open questions

- Has the demo Pixel ever had a v1–v3 build? Decides migration vs destructive fallback.
- Will `POST /v1/emergency/sos` be added to the backend before 12-12, and who owns it?
- Should the calorie target survive logout (per-user cache) or be recomputed from `/users/me`?
- Is the backend `fire_due_occurrences` beat task running in the demo setup (affects Meds #14)?
- Hide chat image attach for the demo, since the backend never sends images to the model?
