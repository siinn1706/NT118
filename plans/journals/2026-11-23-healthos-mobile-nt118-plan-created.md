---
title: HealthOS Mobile NT118 plan created
date: 2026-11-23
summary: "9-phase parallel plan for a Kotlin/Compose client on the HealthOS backend, demo 2026-12-12"
---

# HealthOS Mobile NT118 plan created

## What happened
Scouted the HealthOS repo (FastAPI Core, AI worker, RN mobile client) and verified the routes the Android app needs: auth with refresh tokens in the body, meals photo analysis jobs, SSE chat stream (`delta`/`ping`), device ingest batches, medications + reminder dose actions, emergency profile tokens and the public `/e/{token}` page. Wrote the plan at `plans/261123-0407-healthos-mobile-android/` (9 phases, Dev A data/API features, Dev B sensor/device features).

## Decision
- Kotlin + Jetpack Compose, single `app` module, package-by-feature; Room cache plus an outbox for offline writes.
- Reuse the HealthOS backend unchanged except a new `POST /v1/emergency/sos` endpoint for SOS email.
- Signature features: Safety Net (fall detection, SMS + email SOS), camera PPG heart rate with oximeter validation, streaming AI doctor with Vietnamese voice.
- Dropped: Pill Scanner, AI Rep Coach, FCM push, Apple Watch bridge (cannot pair with Android).
- Schedule is 40/40 person-days with no buffer; the cut order in Phase 9 is the buffer.

## Next steps
Resolve the `[UNVERIFIED]` contract items at the start of Phases 2, 4, 5 and 6; begin Phase 1 (repo bootstrap, design system, network core, cloudflared tunnel).

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
