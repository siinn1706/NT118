# HealthOS Mobile

Native Android client (Kotlin, Jetpack Compose, Material 3) for the HealthOS backend. NT118 course project.

## Requirements

- Android Studio with JDK 21 (the bundled JBR works) and Android SDK platform 37.
- A running HealthOS backend; see [docs/backend-tunnel.md](docs/backend-tunnel.md).

## Run

1. Copy `local.properties.example` to `local.properties` and set `HEALTHOS_BASE_URL`.
2. Build and install:

```powershell
.\gradlew :app:installDebug
```

## Checks

```powershell
.\gradlew assembleDebug testDebugUnitTest lintDebug
```

## Project layout

- `app/src/main/java/vn/uit/healthos/core/designsystem` — Vital Bloom theme (colours, Be Vietnam Pro type, shapes, motion) and shared components (`VitalOrb`, `BentoCard`, `MetricTile`, buttons, state views).
- `core/designsystem/glass` — Liquid Glass layer built on [Kyant backdrop](https://github.com/Kyant0/AndroidLiquidGlass): `GlassRoot` paints the ambient backdrop, `Modifier.liquidGlass` draws frosted, refracting surfaces (Chrome for navigation, Material for cards, Control for buttons). Blur needs Android 12+ and refraction Android 13+; older devices get an opaque fallback. Tab screens pad their scroll content with `LocalBottomChromeInset` so it can pass under the floating tab bar.
- `core/network` — Retrofit/OkHttp setup, bearer auth with single-flight token refresh, `ApiResult` error mapping, SSE client.
- `core/data` — encrypted session store (DataStore + Tink), Room database, offline outbox (`OutboxRepository` + `OutboxWorker`).
- `navigation` — routes, bottom bar and `AppNavHost`; each feature adds one `xxxGraph()` call.
- `feature/<name>` — one package per feature, owned by one developer.

Plans and phase files live in [plans/](plans/).

## Adding an offline write

1. Implement `OutboxHandler` for a new type string, e.g. `meal.create`.
2. Bind it in the feature's Hilt module with `@Binds @IntoMap @StringKey("meal.create")`.
3. Call `OutboxRepository.enqueue("meal.create", payload, Payload.serializer())`.

The worker sends items oldest first once the device is online, keeps retryable failures, and drops items the server rejects.
