---
title: "Phase 2: Auth & Onboarding"
status: done
priority: P1
effort: 3d
dependencies: [1]
---

# Phase 2: Auth & Onboarding (Dev A)

## Goal

A user can sign up, sign in (with MFA when enabled), complete onboarding with a TDEE target, and the app locks behind biometrics after time in the background.

## Backend contracts (verified in HealthOS)

- `POST /v1/auth/login` → `AuthToken { access_token, refresh_token, user_id, onboarding_status, ... }`; `POST /v1/auth/login/mfa`; `POST /v1/auth/refresh`; `POST /v1/auth/logout`; `GET /v1/auth/me`.
- Sign-up / reset: `POST /v1/auth/request-otp`, `POST /v1/auth/verify-otp`, `POST /v1/auth/reset-password`, `GET /v1/auth/check-email`, `GET /v1/auth/check-username`. Copy the exact sign-up sequence from the RN client `mobile/src/api/services/auth-service.ts` and `mobile/app/auth/*.tsx`.
- Profile: `GET/PATCH /v1/users/me`, `GET/PUT /v1/users/me/onboarding-draft`, `POST /v1/users/me/avatar`; goals: `GET/POST /v1/health-goals`; preferences: `GET/PATCH /v1/preferences/me`.

## Files to create

- `feature/auth/{AuthGraph.kt, SignInScreen.kt, SignUpScreen.kt, OtpScreen.kt, MfaScreen.kt, ForgotPasswordScreen.kt, AuthViewModel.kt, AuthRepository.kt, AuthApi.kt}`
- `feature/onboarding/{OnboardingGraph.kt, OnboardingScreen.kt (pager: body → activity → goal → emergency contacts), OnboardingViewModel.kt, TdeeCalculator.kt}`
- `feature/profile/{ProfileGraph.kt, MeScreen.kt, SettingsScreen.kt, ProfileViewModel.kt}`
- `core/data/session/AppLockManager.kt`

## Tasks

1. Auth screens with inline validation, loading and error states; route by `onboarding_status` after login.
2. `TdeeCalculator`: Mifflin–St Jeor BMR × activity factor, goal adjustment (lose −500, keep 0, gain +300 kcal); show the formula breakdown on the result step.
3. Onboarding saves a draft after each step (`PUT onboarding-draft`), commits with `PATCH /users/me` and creates a calorie goal via `POST /health-goals`.
4. Onboarding includes **emergency contacts (name, phone, email)** stored in the emergency profile (`PATCH /v1/users/me/emergency-profile`); Phase 7 depends on this data.
5. `AppLockManager`: `BiometricPrompt` (BIOMETRIC_STRONG or DEVICE_CREDENTIAL) on resume after > 60 s in background; toggle in Settings.
6. Me tab: profile summary, theme toggle (system/light/dark), dynamic colour toggle, app lock toggle, logout.

## Verification

- Unit tests: `TdeeCalculator` against hand-computed cases; `AuthViewModel` state transitions incl. MFA branch.
- On device: new account → OTP → onboarding → Home; kill app → reopen → biometric prompt; logout clears tokens.

## Risks

- [UNVERIFIED] exact OTP sign-up order and whether emergency contacts live in `user_profiles.emergency_contacts` writable via the emergency-profile PATCH — confirm against `backend/app/schemas/emergency.py` before building step 4.

## Implementation notes (2026-11-23)

Verified against `siinn1706/NT208_HealthOS` (`backend/app/api/v1/endpoints/{auth,users,health_goals,preferences,emergency}.py` and matching schemas):

- **OTP sign-up order (verified)**: `POST /v1/auth/request-otp` receives `{ email, purpose: "signup", name, username, password }`, validates password complexity, hashes the password, and stores pending registration in Redis (`signup:pending:{email}`). Then `POST /v1/auth/verify-otp` with `{ email, purpose: "signup", code }` creates the user and returns `DataEnvelope<AuthToken>`. For `reset_password`, `verify-otp` returns `DataEnvelope<OtpVerified>` and `POST /v1/auth/reset-password` completes the reset and returns `DataEnvelope<AuthToken>`.
- **Availability checks (verified)**: `GET /v1/auth/check-email` and `GET /v1/auth/check-username` return bare `{ "available": bool }` (not wrapped in `DataEnvelope`).
- **Emergency contacts write path (deviation from plan)**: `PATCH /v1/users/me/emergency-profile` (`EmergencyProfileUpdate`) does not accept `emergency_contacts`. Instead, `emergency_contacts: list[{ name, phone, relationship, email? }]` lives on `PATCH /v1/users/me` (`UserProfileUpdate`). Implemented accordingly so onboarding and profile write contacts directly to `/v1/users/me`.
- **Health goals (deviation from plan)**: `POST /v1/health-goals` (`HealthGoalCreate`) stores `{ target_weight_kg (30..200), deadline? }` rather than a calorie target. Onboarding posts `target_weight_kg` to `/v1/health-goals` and stores the Mifflin–St Jeor TDEE / daily calorie target locally in `AppPreferencesRepository` (DataStore) for Home and Meals.
- **Dependencies & Activity**: Added `androidx.biometric` and `coil-compose` / `coil-network-okhttp` (already in version catalog) to `app/build.gradle.kts`; changed `MainActivity` to `FragmentActivity` for `BiometricPrompt`.
- **Auth gating**: Wired `AppNavHost` with bootstrap routing (`AuthRoute` / `OnboardingRoute` / `HomeRoute`) and session-expired redirect + snackbar.

Verified: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passed (including new unit tests `TdeeCalculatorTest`, `AuthViewModelTest`, `AppLockTest`).

Still open: on-device verification on the Pixel 8 Pro against the tunnelled backend (sign-up OTP, onboarding draft resume, biometric prompt after > 60 s in background, and logout).
