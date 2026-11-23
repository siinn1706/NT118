---
title: "Phase 1: Foundation"
status: in-progress
priority: P1
effort: 6d
dependencies: []
---

# Phase 1: Foundation

## Goal

A runnable app skeleton that logs nothing in yet but already has the Vital Bloom design system, the network/data core, navigation with stubs for every feature, and a working connection to the tunnelled backend.

## Ownership

- **Dev A:** Gradle setup, `core/network`, `core/data`, `core/di`, `navigation`, backend tunnel.
- **Dev B:** `core/designsystem` (theme + components) and the `dev/` gallery screen.

## Files to create

- `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`, `app/src/main/java/vn/uit/healthos/{HealthOsApp.kt, MainActivity.kt}`
- `core/designsystem/theme/{Color.kt, Theme.kt, Type.kt, Shape.kt, Motion.kt}`
- `core/designsystem/component/{BentoCard.kt, VitalOrb.kt, PrimaryButton.kt, MetricTile.kt, SectionHeader.kt, LoadingState.kt, ErrorState.kt}`
- `core/network/{ApiModule.kt, AuthInterceptor.kt, TokenAuthenticator.kt, ApiResult.kt, SseClient.kt}`
- `core/data/session/{TokenStore.kt, SessionRepository.kt}`
- `core/data/db/{AppDatabase.kt, Converters.kt}`, `core/data/outbox/{OutboxEntity.kt, OutboxDao.kt, OutboxWorker.kt, OutboxRepository.kt}`
- `navigation/{AppNavHost.kt, Destinations.kt, BottomBar.kt}` + a stub `fun NavGraphBuilder.<feature>Graph()` in every `feature/*` package
- `feature/dev/DesignGalleryScreen.kt` (debug builds only)
- `.gitignore`, `README.md`, `local.properties.example` (`HEALTHOS_BASE_URL=`)

## Tasks

### Dev A
1. Create the repo; Gradle Kotlin DSL; version catalog with the latest stable Compose BOM, Material 3, Hilt, Navigation Compose (type-safe routes), Room, DataStore, Retrofit + OkHttp + `okhttp-sse` + kotlinx.serialization, WorkManager, Coil, Vico, CameraX, Play Services Location, Health Connect, Glance, Biometric, ZXing core. `minSdk 28`, `compile/targetSdk` = latest stable.
2. `BuildConfig.HEALTHOS_BASE_URL` read from `local.properties` (never committed); debug-only cleartext allowed for LAN fallback.
3. `TokenStore`: DataStore + Tink AEAD keyed by Android Keystore; holds `access_token`, `refresh_token`, `user_id`.
4. `AuthInterceptor` adds `Authorization: Bearer`; `TokenAuthenticator` calls `POST /v1/auth/refresh` once on 401 (mutex, single-flight), clears the session on failure.
5. `ApiResult<T>` sealed type + error mapping from the Core `error_codes` envelope. Mirror DTO shapes from the HealthOS RN client (`mobile/src/api/**`) and the Core schemas.
6. `SseClient` wrapper over `okhttp-sse` exposing `Flow<SseEvent>` (events `delta`, `ping`, final event).
7. Outbox: Room table `(id, type, payloadJson, createdAt, attempts)`; `OutboxWorker` (CoroutineWorker, `NetworkType.CONNECTED`, exponential backoff) dispatches by `type` to handlers registered via Hilt multibinding, so features add handlers without editing the worker.
8. `AppNavHost` + bottom bar with 5 tabs: Home, Meals, Chat, Meds, Me. All feature graphs stubbed.
9. Backend: run HealthOS (Docker) on the laptop, set up a named `cloudflared` tunnel with **two hostnames**: Core API and the Next.js frontend (the emergency QR links to `<frontend>/e/{token}`). Record the steps in `docs/backend-tunnel.md`.

### Dev B
10. Theme tokens exactly as in the brainstorm report, section 8 (light + dark), Material 3 `ColorScheme`; dynamic color off by default with a toggle hook.
11. Be Vietnam Pro via Downloadable Fonts (`GoogleFont` provider) with a bundled fallback; M3 type scale (Display 36/44, Title 22/28, Body 16/24, Label 14/20); tabular numbers style for metrics.
12. Shapes: cards 24dp, buttons full-round; motion tokens 200/250/300 ms + spring specs; respect the system "remove animations" setting.
13. `VitalOrb`: Canvas blob with 6–8 control points animated by `rememberInfiniteTransition`; params `score: Float` (colour teal → amber → coral) and `breathsPerMinute`.
14. Remaining components + `DesignGalleryScreen` showing every component in light and dark.

## Verification

- `./gradlew assembleDebug testDebugUnitTest lintDebug` passes.
- Unit tests: `TokenAuthenticator` refresh single-flight (MockWebServer: two parallel 401 → one refresh call); `OutboxWorker` retries then succeeds.
- On the Pixel: the app opens, the gallery renders in both themes, and `GET /v1/health` through the tunnel returns OK from a debug button.

## Implementation notes (2026-11-23)

Built on the Android Studio template already in the repo root. Deviations from the steps above:

- Kotlin raised to **2.4.10** (current libraries ship 2.4 metadata), declared via `org.jetbrains.kotlin.android` with `apply false` so AGP 9 built-in Kotlin uses it; KSP 2.3.14, Hilt 2.60.1, Compose BOM 2026.11.01.
- `compileSdk 37` (OkHttp 5.5 requires it), `targetSdk 36`, `minSdk 29` (template value, kept).
- Be Vietnam Pro is **bundled** in `res/font` (4 weights, OFL) instead of Downloadable Fonts: works offline and needs no GMS cert resource.
- `SectionHeader`, `LoadingState`, `ErrorState`, `FeaturePlaceholder` live together in `component/StateViews.kt`; added `DetailScaffold` and `SecondaryButton`.
- `TokenStore` is an interface (`EncryptedTokenStore` = DataStore + Tink AES-256-GCM, Keystore-wrapped keyset) so tests use an in-memory store.
- Outbox logic lives in the pure `OutboxProcessor`; `OutboxWorker` only wires Hilt. A refresh that fails on the network keeps the session; only 401/403 from `/v1/auth/refresh` signs out.
- Colour roles differ from brainstorm §8: Material `secondary` drives chip/nav selection, so coral there tinted every selected state pink. Now `secondary` = sage (`#4A635F` / `#B1CCC6`), `tertiary` = coral (`#E11D48` / `#FDA4AF`), amber = `VitalColors.energy` (`#B45309` / `#FCD34D`). Bottom-bar selection is pinned to `primaryContainer`.
- `vitalColorFor` uses narrow bands (>= 0.7 teal, 0.5 amber, <= 0.3 coral): a wide teal→amber lerp passes through off-brand green.

Verified: `assembleDebug`, `testDebugUnitTest` (13 tests), `lintDebug` pass; Design gallery checked on the emulator in light and dark.

Still open (needs the team's accounts/devices): run HealthOS + create the named tunnel per `docs/backend-tunnel.md`, then ping from the Pixel 8 Pro.

## Risks

- Free `trycloudflare` URLs rotate → use a named tunnel so the base URL stays fixed.
- Shared files conflict between A and B → the shared-file rule in `plan.md`.
