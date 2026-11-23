---
title: "HealthOS Mobile — Android native app (NT118)"
description: "Build a new Kotlin + Jetpack Compose Android client on the HealthOS FastAPI backend, with Safety Net, camera heart rate and a context-aware AI doctor as signature features."
status: in-progress
priority: P1
effort: 40d
tags: [feature, mobile, android, frontend, backend]
blockedBy: []
blocks: []
created: 2026-11-23
---

# HealthOS Mobile — Android native app (NT118)

## Overview

Build a new Kotlin + Jetpack Compose Android client on the HealthOS FastAPI backend, with Safety Net, camera heart rate and a context-aware AI doctor as signature features. Demo on 2026-12-12 on a Pixel 8 Pro, with the backend on a laptop behind `cloudflared`. Scope and rationale: [brainstorm report](../reports/brainstorm-261123-1017-healthos-mobile-nt118.md), section 10.

Mode: **parallel**, two developers (Dev A: data/API-heavy features, Dev B: device/sensor-heavy features). Effort is in person-days: 40 planned against 40 available (2 people × 20 days, 11-23 → 12-12), so there is no buffer; the cut order in Phase 9 is the buffer.

## Phases

| # | Phase | Owner | Dates | Status |
|---|-------|-------|-------|--------|
| 1 | [Foundation](./phase-01-foundation.md) | A + B | 11-23 → 11-25 | In progress (code done, tunnel pending) |
| 2 | [Auth & Onboarding](./phase-02-auth-onboarding.md) | A | 11-26 → 11-28 | Done |
| 3 | [Home, Steps & Climate Coach](./phase-03-home-steps-climate.md) | B | 11-26 → 11-30 | Done |
| 4 | [Meals Snap](./phase-04-meals-snap.md) | A | 11-29 → 12-02 | Done |
| 5 | [AI Doctor Chat](./phase-05-ai-doctor-chat.md) | A | 12-03 → 12-06 | Done |
| 6 | [Medications & Alarms](./phase-06-medications-alarms.md) | A | 12-07 → 12-09 | Done |
| 7 | [Safety Net](./phase-07-safety-net.md) | B | 12-01 → 12-05 | Done (backend SOS email pending) |
| 8 | [Camera Heart Rate](./phase-08-camera-heart-rate.md) | B | 12-06 → 12-09 | Done |
| 9 | [Integration & Demo](./phase-09-integration-demo.md) | A + B | 12-10 → 12-12 | Done (device rehearsal & release signing pending) |

Phase 1 blocks every other phase. Phases 2/4/5/6 (A) and 3/7/8 (B) run in parallel with disjoint file ownership. Phase 9 needs all of them.

## Shared-file rule

Each feature lives in its own package `feature/<name>/`. Only these files are shared, and each edit to them is a small additive change in its own commit: `AndroidManifest.xml`, `core/data/db/AppDatabase.kt` (entity list), `navigation/AppNavHost.kt` (one graph call per feature, stubbed in Phase 1), `gradle/libs.versions.toml`. Room uses destructive migration until the schema freeze on 12-10.

## Acceptance criteria (whole plan)

- [x] Each ZenBuddy capability has an equal or better counterpart (steps, food scan, AI chat, reminders, exercise suggestions, profile/TDEE, weather).
- [ ] Safety Net, camera heart rate and the AI doctor run on the real Pixel 8 Pro against the tunnelled backend; no mock data.
- [ ] Offline: with the network off, the user can open Home (cached), log a meal manually and mark a dose taken; both sync automatically when the network returns.
- [x] Light and dark themes meet WCAG AA contrast (token table in the brainstorm report, section 8).
- [x] `./gradlew assembleDebug testDebugUnitTest lintDebug` passes on `main` at the 12-10 freeze.

## Non-goals

FCM push, appointments, visit-prep, PDF reports, achievements, Pill Scanner, AI Rep Coach, Wear OS, admin/subscription screens, full two-way offline sync, Play Store release.

## Validation Log

### Session 1 — 2026-11-23
- Apple Watch: cannot pair with Android; an iPhone Shortcuts bridge was offered and **declined**. Heart rate comes from camera PPG, steps from the Pixel sensor.
- Overrun cut order confirmed: chat voice/image → Climate Coach → Glance widget → adherence chart.
- Plan files stay in English.
- Open `[UNVERIFIED]` items to confirm at the start of their phase: OTP sign-up order and emergency-contacts write path (Phase 2), meal analysis response shape (Phase 4), emergency flag in the stream final event (Phase 5), reminder id in `/medications/today` (Phase 6).

## Repositories

- New Android repo rooted at `F:/Nam/UIT/NT118-Proj` (Gradle project at the root; `plans/` and `docs/` alongside).
- Backend changes (only the SOS email endpoint, Phase 7) go to a branch in the HealthOS repo.
