---
title: "Phase 3: Home, Steps & Climate Coach"
status: done
priority: P1
effort: 5d
dependencies: [1]
---

# Phase 3: Home, Steps & Climate Coach (Dev B)

## Goal

A bento Home with the Vital Orb, live step count from the phone sensor synced to the backend, AI advice, exercise suggestions, hydration logging, and a Climate Coach card driven by air quality, UV and temperature.

## Backend contracts (verified)

- `GET /v1/dashboard/summary`, `GET /v1/dashboard/ai-advice`, `GET /v1/dashboard/exercise-suggestions`, `GET /v1/goals/progress`.
- Device + ingest: `POST /v1/devices` (register this phone once), `POST /v1/devices/{id}/ingest` with `HealthIngestBatch { records: [HealthRecordIn{ external_id, metric_type, value, ... }] }` (≤ 500 records, dedup by `external_id`). Metric types used: `steps`, `hydration_ml`, `heart_rate` (Phase 8).
- `GET /v1/vitals/timeseries`, `GET /v1/health-metrics/aggregated` for the 7-day chart.
- Weather/air (no key): Open-Meteo forecast API (temperature, apparent temperature, UV index) and Open-Meteo air-quality API (US AQI, PM2.5).

## Files to create

- `feature/home/{HomeGraph.kt, HomeScreen.kt, HomeViewModel.kt, HomeRepository.kt, DashboardApi.kt, VitalScore.kt}`
- `feature/steps/{StepSensorService.kt (foreground, type health), StepCounterRepository.kt, DailyStepEntity.kt, StepSyncHandler.kt (outbox handler), DeviceRegistration.kt}`
- `feature/hydration/{HydrationCard.kt, HydrationRepository.kt}`
- `feature/climate/{ClimateApi.kt, ClimateRepository.kt, ClimateCoach.kt (rules), ClimateCard.kt}`
- `feature/home/charts/WeeklyTrendChart.kt` (Vico)

## Tasks

1. Home layout: Vital Orb hero (score from `VitalScore` = weighted steps %, calories vs TDEE, hydration %, last resting HR), bento cards: steps, calories, hydration, AI advice, exercise suggestions (top 3, no library), Climate Coach, 7-day trend chart.
2. Room cache for the dashboard summary so Home renders offline with a "last updated" label.
3. Steps: `TYPE_STEP_COUNTER` (cumulative since boot) → daily delta stored in `DailyStepEntity`, handling reboot resets and midnight rollover; `ACTIVITY_RECOGNITION` permission flow. Batch to ingest every 15 min via WorkManager + outbox, `external_id = "steps-<date>-<deviceId>"` so re-sends update instead of duplicating.
4. Optional, only if a wearable is borrowed: read steps/HR from Health Connect and prefer them over the sensor.
5. Hydration quick-add (+250 ml) → outbox → ingest `hydration_ml`.
6. Climate Coach: fetch with last known location (coarse), cache 1 h. Rules: daily hydration target = base (35 ml/kg) + 500 ml when apparent temp ≥ 32 °C; outdoor exercise warning when US AQI > 150 or UV ≥ 8; texts in Vietnamese. Show source and timestamp.

## Verification

- Unit tests: step delta across reboot and midnight; `ClimateCoach` rule table; `VitalScore` bounds 0–1.
- On device: walk ~100 steps → Home updates within a few seconds; after sync, `GET /v1/health-metrics?metric_type=steps` shows the value; airplane mode → Home still renders from cache.

## Risks

- The step sensor only counts while the process can receive events; the foreground service keeps it alive. Battery impact acceptable for a demo.
- Open-Meteo outage → card hides itself, nothing else breaks.

## Implementation notes (2026-11-23)

Verified against `siinn1706/NT208_HealthOS` (`backend/app/api/v1/endpoints/{dashboard,devices,health_metrics,vitals}.py`, `backend/app/schemas/{dashboard,devices,sync,health_metrics}.py`, `backend/app/models/core.py`):

- **Device registration & ingest (verified)**:
  - `POST /v1/devices` with `provider = "health_connect"` requires a non-empty `external_account_id` (generated once per install and persisted in DataStore alongside the returned `device_id`) and `scopes` drawn from the closed vocabulary (`["Steps", "HeartRate"]`).
  - `POST /v1/devices/{device_id}/ingest` **requires** an `Idempotency-Key` header (`IDEMPOTENCY_KEY_REQUIRED` 400 if absent).
  - Confirmed `MetricTypeEnum` contains `steps`, `hydration_ml`, and `heart_rate`.
  - Built a shared `metric_ingest` outbox handler (`StepSyncHandler`) that registers the device on demand and sends steps, hydration, and (later) PPG heart rate through the same deduped batch ingest path.
- **Dashboard & charts (verified)**: Used `GET /v1/dashboard/summary`, `GET /v1/dashboard/ai-advice?locale=vi&surface=mobile`, `GET /v1/dashboard/exercise-suggestions`, and `GET /v1/dashboard/vitals-extended?days=7`, cached in Room (`DashboardCacheEntity`) so Home renders offline with a last-updated timestamp and outbox badge.
- **Climate Coach**: Uses `@BareClient` OkHttpClient for Open-Meteo forecast and air-quality APIs so the HealthOS bearer token is never sent to third-party hosts. Caches 1 hour and hides cleanly on outage.
- **Dependencies & schema**: Added `vico-compose-m3` and `play-services-location` from the version catalog to `app/build.gradle.kts`, added foreground-service (`health`), activity-recognition, notification, and coarse-location permissions to `AndroidManifest.xml`, and bumped `AppDatabase` to v2 (`DailyStepEntity`, `HydrationLogEntity`, `DashboardCacheEntity`).

Verified: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passed (including new unit tests `StepDeltaCalculatorTest`, `ClimateCoachTest`, `VitalScoreTest`).

Still open: on-device walk test and airplane-mode check on Pixel 8 Pro; optional Health Connect read if a wearable is borrowed.
