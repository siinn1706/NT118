---
title: "Phase 8: Camera Heart Rate"
status: done
priority: P1
effort: 4d
dependencies: [1, 3]
---

# Phase 8: Camera Heart Rate (Dev B)

## Goal

Measure heart rate with a fingertip over the rear camera and flash (PPG), show a live waveform, reject bad signals, save good readings to the backend, and produce a validation dataset against a fingertip pulse oximeter for the report.

## Method (see brainstorm report 10.1)

CameraX `ImageAnalysis` at 30 fps, torch on, AE/AWB locked through `Camera2Interop`; per frame take the mean of the V (Cr) plane in a centre ROI → detrend (moving average 1 s) → band-pass 0.7–3.5 Hz (2nd-order Butterworth biquads) → peak detection with a 0.33 s refractory period → BPM = 60 / median inter-beat interval over a 20 s window. Signal quality index: finger coverage (high mean, low spatial variance), amplitude above floor, IBI coefficient of variation < 0.15. Low quality → "Đặt lại ngón tay" instead of a number.

## Files to create

- `feature/ppg/{PpgGraph.kt, PpgScreen.kt (instructions, live waveform on Canvas, progress ring, result), PpgViewModel.kt, PpgAnalyzer.kt (ImageAnalysis.Analyzer → samples), PpgSignalProcessor.kt (pure Kotlin: filters, peaks, SQI, BPM), PpgRepository.kt}`
- `docs/ppg-validation.md` + `docs/ppg-validation.csv` (paired readings) + `tools/ppg_validation.py` (MAE, MAPE, Bland–Altman plot)

## Tasks

1. Camera setup on the back main camera, torch on, fixed exposure; handle permission denial and the camera being busy.
2. `PpgSignalProcessor` as a pure class fed by timestamps + values (handles variable frame timing by resampling to 30 Hz).
3. UI: animated finger placement guide, live waveform, 20 s progress, result with confidence label; save → ingest `heart_rate` (outbox) with `source = camera_ppg`; also shown on Home and in the Vital Score (Phase 3).
4. Validation: 20 paired readings (2 people × 10, seated, rest) vs a fingertip pulse oximeter; record both values in the CSV; script outputs MAE, MAPE and a Bland–Altman PNG for the report. Target: MAE ≤ 5 BPM at rest.

## Verification

- Unit tests: synthetic sine 1.2 Hz + noise + drift → 72 ± 2 BPM; flat signal → low quality; signal with motion spikes → rejected.
- On device: 3 readings each within ±5 BPM of the oximeter before moving to the full 20-reading validation.

## Risks

- Pixel 8 Pro has several rear cameras; the finger must cover the main lens and the flash — the guide screen shows exactly where.
- Torch heats the lens on long sessions → cap one measurement at 30 s.
- Clearly labelled "không thay thế thiết bị y tế"; no SpO2 or blood pressure claims.

## Implementation notes (2026-11-23)

- **Ingest source field (verified against backend `WearableSourceEnum`)**: `WearableSourceEnum` on the backend only allows canonical providers (`health_connect`, `manual`, `apple_health`, `google_fit`, `garmin`, `fitbit`, `google_health`) and would reject `source = "camera_ppg"` with 422. `PpgRepository` therefore submits `metric_type = "heart_rate"`, `source = "health_connect"`, and `source_app = "camera_ppg"` via the shared `metric_ingest` outbox handler so provenance is preserved cleanly.
- **DSP & safety**: Built pure Kotlin `PpgSignalProcessor` (uniform 30 Hz resampling, 1 s moving-average detrend, 2nd-order Butterworth 0.7–3.5 Hz biquads, 0.33 s refractory peak detection, median IBI, crest-factor motion rejection, IBI CV < 0.15 SQI) and `PpgAnalyzer` (V/Cr plane centre 50% ROI). Torch turns on only while measuring and turns off on completion/cancel/dispose; sessions hard-cap at 30 s.
- **Validation tooling**: Added header-only `docs/ppg-validation.csv` (no fabricated numbers), protocol in `docs/ppg-validation.md`, and `tools/ppg_validation.py` (MAE, RMSE, MAPE, bias, 95% LoA, Bland–Altman PNG).

Verified: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passed (including `PpgSignalProcessorTest` covering 72 ± 2 BPM synthetic sine with noise & drift, flat signal rejection, motion-spike rejection, and uncovered-lens detection).

Still open: collecting the 20 real paired readings against a fingertip pulse oximeter on Pixel 8 Pro and running `python tools/ppg_validation.py`.
