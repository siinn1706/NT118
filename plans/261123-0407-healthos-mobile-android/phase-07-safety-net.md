---
title: "Phase 7: Safety Net"
status: done-with-concerns
priority: P1
effort: 5d
dependencies: [1, 2]
---

# Phase 7: Safety Net (Dev B)

## Goal

Detect a fall (or a manual SOS), give the user 30 s to cancel, then automatically send an SMS from the phone and an email through the backend to every emergency contact with time, GPS link and the public emergency card; the emergency card QR is reachable from the lock screen.

## Backend contracts

Existing (verified): `GET/PATCH /v1/users/me/emergency-profile`, `POST/GET /v1/users/me/emergency-profile/tokens`, `DELETE .../tokens/{id}`, public page `<frontend>/e/{token}` (URL built in `emergency_profile.py`), SMTP helper `app/adapters/email_client.send_email`.

**New (HealthOS repo, branch `feature/emergency/sos-email`):** `POST /v1/emergency/sos`
- Body: `{ trigger: "fall" | "manual", lat?, lng?, accuracy_m?, occurred_at, client_event_id }`.
- Reads the caller's `emergency_contacts`, sends one email per contact that has an email (subject "[SOS] <name> cần hỗ trợ", body: time, Google Maps link, public card URL from an active share token), as a background task.
- Idempotent on `client_event_id`; rate limit 3 per 10 min per user; writes a security/audit event; returns `{ emailed: n, skipped_no_email: m }`.
- Tests: `backend/tests/api/v1/test_emergency_sos_endpoint.py` (happy path with SMTP mocked at the adapter, no contacts, idempotency, rate limit, auth required).

## Files to create (Android)

- `feature/safety/{SafetyGraph.kt, SafetyScreen.kt (status, contacts, test mode), FallDetector.kt (pure state machine), FallDetectionService.kt (foreground, type health), SosCountdownActivity.kt (full-screen, showWhenLocked), SosDispatcher.kt, SmsSender.kt, LocationProvider.kt, SosApi.kt, SosOutboxHandler.kt}`
- `feature/safety/card/{EmergencyCardActivity.kt (showWhenLocked, QR via ZXing), EmergencyCardTileService.kt (Quick Settings tile), EmergencyCardWidget.kt (Glance)}`

## Tasks

1. `FallDetector` (no Android deps, unit-testable): accelerometer magnitude at ~50 Hz; free-fall |a| < 0.5 g for ≥ 80 ms → impact |a| > 2.5 g within 1 s → near-stillness for 2 s → `FallSuspected`. Thresholds in one config object; "test mode" with lower thresholds for the demo (drop the phone onto a cushion).
2. `FallDetectionService` runs the detector, toggled from the Safety screen; permissions: `ACTIVITY_RECOGNITION`, `FOREGROUND_SERVICE_HEALTH`, `HIGH_SAMPLING_RATE_SENSORS`.
3. `SosCountdownActivity`: full-screen intent notification (guide the user to grant "full-screen notifications" on Android 14+), 30 s countdown with vibration + alarm sound, big "Tôi ổn — Hủy" button; manual SOS button on the Safety screen and in the chat emergency banner (Phase 5) opens the same flow with a 5 s countdown.
4. `SosDispatcher` on timeout: get location (`getCurrentLocation` high accuracy, 10 s timeout, fall back to last location), then in parallel:
   - `SmsSender`: `SmsManager.sendMultipartTextMessage` to every contact phone (Vietnamese text uses UCS-2, so split with `divideMessage`); track sent/delivered `PendingIntent`s; permission `SEND_SMS`.
   - Email: `POST /v1/emergency/sos`; if offline, queue in the outbox (it sends when the network returns).
   - Result screen lists each contact with SMS/email status.
5. Emergency card: ensure an active share token exists (create once), render QR of the public URL; reachable from the Quick Settings tile (works on the lock screen) and a Glance home widget with an SOS button.

## Verification

- Unit tests: `FallDetector` on recorded/synthetic traces (real fall, sitting down hard, walking, phone dropped on table) → only the fall triggers; SMS splitting for long Vietnamese text.
- Backend: `pytest backend/tests/api/v1/test_emergency_sos_endpoint.py` passes.
- On device: test-mode drop → countdown on lock screen → cancel works; let it expire → a second phone receives the SMS with a working Maps link, the contact inbox receives the email; airplane mode with SIM signal → SMS still sent, email sent after reconnect.

## Risks

- Real SMS costs and spam: in the demo use only team members' numbers; test mode is clearly labelled.
- Pixel 8 Pro must have a SIM with SMS; email needs SMTP credentials in the laptop's backend env (never committed).
- False positives → 30 s cancel window and thresholds tuned on recorded traces.

## Implementation notes (2026-11-23)

Verified against `siinn1706/NT208_HealthOS` (`backend/app/api/v1/endpoints/emergency.py`, `backend/app/schemas/emergency.py`):

- **Existing emergency profile & tokens (verified)**: Wired `GET/PATCH /v1/users/me/emergency-profile`, `POST /v1/users/me/emergency-profile/tokens` (`EmergencyShareTokenCreateBody { label, acknowledge_low_completeness }`), and cached `share_url` + card summary in DataStore (`SafetyPreferences`) so `EmergencyCardActivity` renders offline on the lock screen with a ZXing QR code.
- **Fall detector tuning**: Added a brief post-impact bounce-settle allowance (`postImpactSettleMs + 600 ms`) in `FallDetector` so residual cushion/floor bounce does not prematurely reset stillness detection while picking up the phone within 2 s still cleanly cancels detection.
- **Quick Settings & Glance**: Used `TileServiceCompat.startActivityAndCollapse` in `EmergencyCardTileService` for Android 14+ compatibility and built `EmergencyCardWidget` with `androidx.glance` offering one-tap Emergency Card and 5 s manual SOS buttons.
- **Dependencies & manifest**: Added `zxing-core`, `androidx-glance-appwidget`, and `androidx-glance-material3` from the version catalog to `app/build.gradle.kts`, and registered `ACCESS_FINE_LOCATION`, `SEND_SMS`, `USE_FULL_SCREEN_INTENT`, `VIBRATE`, `HIGH_SAMPLING_RATE_SENSORS`, `FallDetectionService`, `SosCountdownActivity`, `EmergencyCardActivity`, `EmergencyCardTileService`, and `EmergencyCardWidgetReceiver` in `AndroidManifest.xml`.

Verified: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passed (55 tests, including `FallDetectorTest` and `SosSmsFormatterTest`).

Still open:
- Backend companion endpoint `POST /v1/emergency/sos` (`NT208_HealthOS` branch `feature/emergency/sos-email` + `test_emergency_sos_endpoint.py`) is outside this Android workspace and must be added in the backend repo so SOS email alerts complete (client sends to `/v1/emergency/sos` and queues offline in `SosOutboxHandler`).
- On-device verification on Pixel 8 Pro with SIM (cushion-drop test mode, SMS delivery, lock-screen tile & widget).
