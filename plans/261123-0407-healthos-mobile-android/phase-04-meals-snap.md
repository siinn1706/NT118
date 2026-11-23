---
title: "Phase 4: Meals Snap"
status: done
priority: P1
effort: 4d
dependencies: [1, 2]
---

# Phase 4: Meals Snap (Dev A)

## Goal

Photograph a meal, get detected Vietnamese dishes with calories and portion options from the HealthOS AI worker, adjust, and save; daily calories compared against the TDEE target; manual entry works offline.

## Backend contracts (verified)

- `POST /v1/meals/analyze-photo` (multipart image) → `job_id`; poll `GET /v1/meals/analyze-photo/{job_id}` until done/failed.
- `POST /v1/meals`, `GET /v1/meals`, `GET /v1/meals/{id}`, `PATCH`, `DELETE`, `GET /v1/meals/calories-summary`, `GET /v1/meals/{id}/ingredients`.
- Manual search: `GET /v1/nutrition/ingredients`; suggestions: `GET /v1/nutrition/suggestions`.

## Files to create

- `feature/meals/{MealsGraph.kt, MealsScreen.kt (today list + calorie ring), SnapScreen.kt (CameraX preview + capture + gallery picker), AnalysisResultScreen.kt (detections, portion chips, editable kcal), ManualEntryScreen.kt, MealsViewModel.kt, MealsRepository.kt, MealsApi.kt, MealOutboxHandler.kt}`

## Tasks

1. CameraX `ImageCapture` + Photo Picker fallback; downscale to ≤ 1280 px JPEG q85 before upload.
2. Polling with backoff (1 s → 3 s, timeout 60 s), cancellable; progress UI with the captured image blurred behind.
3. Result screen: each detection as a card with portion options from the response and live kcal total; user can remove or add items via ingredient search; save → `POST /meals` with the image reference the analysis returned.
4. Manual entry writes to the outbox when offline (`type = meal.create`), shows as "pending sync".
5. Meals tab: today's meals, calorie ring vs TDEE target from Phase 2, 7-day bar chart.

## Verification

- Unit tests: polling state machine (success, failed, timeout, cancel); outbox handler maps payload to `POST /meals`.
- On device against the laptop AI worker: snap a real dish → detections in < 15 s → save → appears in list and Home calories.

## Risks

- AI worker slow on first request (model warm-up) → warm it in the demo checklist (Phase 9).
- [UNVERIFIED] exact response shape of the analysis job (detections, portion options, image reference) — read `backend/app/schemas/meals.py` and the RN snap flow before modelling DTOs.

## Implementation notes (2026-11-23)

Verified against `siinn1706/NT208_HealthOS` (`backend/app/api/v1/endpoints/{meals,nutrition}.py`, `backend/app/schemas/{meals,ingredients}.py`):

- **Analysis job response shape (verified)**:
  - `POST /v1/meals/analyze-photo` (multipart `image` + optional `name`) returns a **bare** `AnalysisJobResponse { job_id, status, meal_id }` (202, not wrapped in `DataEnvelope`) and already persists a `Meal` row with its uploaded `image_url`.
  - `GET /v1/meals/analyze-photo/{job_id}` returns bare `AnalysisJobResponse { job_id, status ("pending"|"processing"|"analyzed"|"failed"), meal_id, nutrition_result, result, error }`.
  - `nutrition_result` carries `dish_name`, `calories`, `protein_g`, `carbs_g`, `fat_g`, `portion_options: list[dict]`, `ingredients: list[dict]`, and `warnings`. Saving the reviewed result updates that meal in place via `PATCH /v1/meals/{meal_id}` (keeping the server `image_url`), while discarding calls `DELETE /v1/meals/{meal_id}`.
- **Offline manual entry**: Manual creation sends `Idempotency-Key` to `POST /v1/meals` (`MealCreateBody`). On retryable network failure it stores a local `PendingMealEntity` (Room v3) and enqueues `meal.create` in the outbox (`MealOutboxHandler`), displaying the meal immediately with a "pending sync" badge and clearing it once synced.
- **Dependencies & manifest**: Added CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`) from the version catalog to `app/build.gradle.kts` and `CAMERA` permission to `AndroidManifest.xml`.

Verified: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passed (including new unit tests `MealPhotoPollerTest` and `MealOutboxHandlerTest`).

Still open: on-device photo snap test against the laptop AI worker (warm-up and < 15 s turnaround).
