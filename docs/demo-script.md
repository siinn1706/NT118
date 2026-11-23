# HealthOS Mobile — Live Demo Script (8–10 minutes)

Device: **Pixel 8 Pro** (with active SIM for SMS) connected to the laptop HealthOS backend over a named Cloudflare tunnel. Backup phone on stage to receive live SMS and email alerts.

## 1. Vital Orb Home, Live Steps & Climate Coach (~1.5 min)

1. Unlock app (show **Biometric App Lock** prompt when returning from background).
2. Point out the **Vital Orb** hero score and breathing animation, steps counter, calorie target (computed via Mifflin–St Jeor TDEE during onboarding), hydration quick-add (`+250 ml`), AI advice, top 3 exercise suggestions, and 7-day trend chart.
3. Show the **Climate Coach** card (Open-Meteo live temp/UV/US AQI) adjusting the hydration target when apparent temperature ≥ 32 °C or showing outdoor exercise warnings.

## 2. Meals Snap — Vietnamese Dish Recognition (~1.5 min)

1. Open **Bữa ăn → Chụp món ăn**, snap a real meal photo (or pick a prepared dish photo from gallery).
2. Show the blurred-photo progress overlay polling the HealthOS AI worker (`POST /v1/meals/analyze-photo` → backoff poll).
3. On the result screen, select a portion option, adjust or add an ingredient from the bilingual catalog, and save → calorie ring and today's list update immediately.

## 3. Context-Aware AI Doctor Chat (~1.5 min)

1. Open **Bác sĩ AI**. Tap a context chip (e.g., *"Hôm nay tôi ăn gì?"*) to show token-by-token SSE streaming grounded in the user's own HealthOS data.
2. Tap the microphone button and ask a follow-up question in Vietnamese (live partial transcription), then tap the speaker icon to hear Vietnamese TTS and *"Tạo nhắc nhở"* to create a prefilled reminder.
3. Type an emergency phrase (*"đau ngực dữ dội, khó thở"*) → server short-circuits immediately and the red banner (**Gọi 115** + **Mở SOS**) appears.

## 4. Medications & Exact Lock-Screen Alarms (~1 min)

1. Open **Thuốc**, show today's dose timeline, adherence % per plan, and refill warnings.
2. Trigger a pre-scheduled dose alarm (scheduled 2 minutes ahead before going on stage) with the screen off → show the high-priority notification with **Đã uống / Bỏ qua / Hoãn 10 phút**.

## 5. Safety Net — Fall Detection, SOS & Emergency QR (~2 min)

1. Open **Tôi → Safety Net**, enable **Chế độ thử nghiệm (demo)** and fall detection.
2. Lock the screen and gently drop the Pixel onto a cushion → full-screen **30 s SOS countdown** wakes the screen with alarm + vibration. Demonstrate **"Tôi ổn — Hủy"** first.
3. Tap **Gửi SOS khẩn cấp** (5 s countdown) and let it expire → show the per-contact dispatch report and show the backup phone receiving the Vietnamese multipart SMS with the live Google Maps GPS link and public Emergency Card link (`<app>/e/{token}`), plus the SOS email in the inbox.
4. From the lock screen, pull down Quick Settings and tap the **Thẻ y tế khẩn** tile → scan the ZXing QR code with the second phone to open the public Emergency Health Card.

## 6. Camera Heart Rate (PPG) (~1.5 min)

1. Open **Tôi → Đo nhịp tim** while wearing the reference fingertip pulse oximeter on the other hand.
2. Place fingertip gently over the rear main camera + flash → show the live PPG waveform on Canvas, signal quality indicator, and 20 s measurement completing within ±5 BPM of the pulse oximeter.
3. Tap **Lưu vào HealthOS** and reference the 20-reading Bland–Altman validation results (`docs/ppg-validation.md` + `tools/ppg_validation.py`).

## 7. Offline Resilience (~1 min)

1. Turn on **Airplane Mode**.
2. Show **Tổng quan** still rendering from Room cache with the offline timestamp banner.
3. Log a manual meal (**Bữa ăn → Nhập thủ công**) → appears immediately under *Đang chờ đồng bộ*. Mark a medication dose **Đã uống** → updates immediately.
4. Disable Airplane Mode → `OutboxWorker` drains automatically and the backend records appear on refresh.

---

## Feature Comparison vs ZenBuddy

| Capability | ZenBuddy (reference) | HealthOS Mobile (NT118) |
|---|---|---|
| Activity & Steps | Step counter | Live `TYPE_STEP_COUNTER` foreground service (reboot & midnight safe) + deduped backend ingest |
| Food Scan | Meal photo calorie estimate | CameraX snap + HealthOS AI worker + Vietnamese portion options + bilingual ingredient search + offline manual entry |
| AI Health Assistant | Generic chat | Context-aware SSE streaming AI Doctor grounded in user records & medical RAG + Vietnamese voice I/O + emergency short-circuit |
| Medication Reminders | Basic notifications | `AlarmManager.setAlarmClock` exact alarms surviving reboot + lock-screen Taken/Skip/Snooze 10m + offline sync + adherence breakdown |
| Exercise & Profile/TDEE | Static tips + BMI | Mifflin–St Jeor TDEE breakdown + backend exercise suggestions + weight goal sync |
| Weather | Basic weather display | Open-Meteo Climate Coach (heat +500 ml hydration adjustment, US AQI > 150 & UV ≥ 8 outdoor warnings) |
| **Signature: Safety Net** | — | 50 Hz accelerometer 3-stage fall detection, lock-screen SOS countdown, GPS + UCS-2 SMS + SOS email, lock-screen QR tile & Glance widget |
| **Signature: Camera Heart Rate** | — | Rear-camera PPG (Butterworth 0.7–3.5 Hz, SQI rejection, live waveform) validated against pulse oximeter |

## Venue Fallback Plan

1. If venue Wi-Fi blocks Cloudflare tunnel traffic: tether laptop to phone mobile hotspot.
2. If cellular/internet fails completely: play pre-recorded screen captures of each signature feature and run offline steps/PPG/fall-detection locally on the Pixel 8 Pro.
