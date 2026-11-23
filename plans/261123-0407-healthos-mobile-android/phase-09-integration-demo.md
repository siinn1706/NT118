---
title: "Phase 9: Integration & Demo"
status: done-with-concerns
priority: P1
effort: 6d
dependencies: [2, 3, 4, 5, 6, 7, 8]
---

# Phase 9: Integration & Demo (Dev A + Dev B)

## Goal

Freeze, harden and rehearse: one signed APK on the Pixel, the backend running through the tunnel, a scripted demo that shows every signature feature, and the report material.

## Tasks

### 12-10 — Freeze (both)
1. Schema freeze: stop destructive Room migration; export Room schema; bump `versionCode`.
2. Full regression on device through the tunnel: auth → onboarding → Home → steps → meals snap → chat (stream + voice) → meds alarm → SOS (test mode) → PPG.
3. Offline pass: airplane mode → Home cached, manual meal, dose taken, SOS SMS → reconnect → outbox drains (check backend data).
4. Accessibility sweep: TalkBack labels on icon buttons, touch targets ≥ 48 dp, font scale 1.3 without clipping, contrast in both themes.

### 12-11 — Polish (A: app, B: demo kit)
5. A: fix regressions, empty/error states, app icon + splash in Vital Bloom, signed release APK (`assembleRelease` with a local keystore kept out of git).
6. B: `docs/demo-script.md` (8–10 min, order: Vital Orb Home → meals snap → AI doctor voice + emergency banner → meds alarm on lock screen → fall SOS with a second phone receiving SMS/email → camera heart rate vs oximeter → offline sync), comparison table vs ZenBuddy, PPG validation results.
7. B: `docs/backend-tunnel.md` checklist: start Docker stack, warm the AI worker with one image, verify SMTP, start `cloudflared`, health-check both hostnames.

### 12-12 — Demo
8. Morning rehearsal on the venue network; fallback: phone hotspot for the laptop; pre-recorded video of each signature feature in case the network fails.

## Verification

- `./gradlew assembleDebug testDebugUnitTest lintDebug` green; backend SOS tests green.
- Every acceptance criterion in `plan.md` ticked with evidence (screenshot, backend record, or test output).

## Risks

- Venue Wi-Fi blocks the tunnel → hotspot + recorded video.
- Late features: cut order is voice/image in chat → Climate Coach → Glance widget → adherence chart.

## Implementation notes (2026-11-23)

Completed all code, freeze, polish, accessibility, and documentation deliverables in the Android repository:

- **Schema freeze**: Removed `fallbackToDestructiveMigration` from `core/data/DataModule.kt`; exported frozen Room schema v4 at `app/schemas/vn.uit.healthos.core.data.db.AppDatabase/4.json`; bumped `versionCode = 2`, `versionName = "1.0.0"` in `app/build.gradle.kts`.
- **Visual & accessibility polish**: Replaced default template launcher/splash vectors with Vital Bloom teal + heart/ECG adaptive icons (`ic_launcher_background.xml`, `ic_launcher_foreground.xml`); audited touch targets (raised chat speaker & attachment dismiss `IconButton`s to 48 dp with Vietnamese `contentDescription`); verified zero remaining `FeaturePlaceholder` references across production navigation; added `ThemeContrastTest` proving WCAG AA ≥ 4.5:1 contrast ratios for light and dark theme token pairs.
- **Demo kit & checklists**: Authored `docs/demo-script.md` (8–10 min live walkthrough, ZenBuddy comparison table, venue fallback plan) and added the 15-minute pre-demo checklist to `docs/backend-tunnel.md`. No features were cut.

Verified: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passed with zero errors.

Still open (require physical Pixel 8 Pro, live laptop tunnel, local signing keystore, and backend branch):
- Signed release APK (`assembleRelease` with team's local keystore kept out of git).
- Backend companion endpoint `POST /v1/emergency/sos` + `pytest backend/tests/api/v1/test_emergency_sos_endpoint.py` in `siinn1706/NT208_HealthOS`.
- Full on-device regression, airplane-mode sync verification, 20-reading pulse-oximeter PPG validation, and venue morning rehearsal.
