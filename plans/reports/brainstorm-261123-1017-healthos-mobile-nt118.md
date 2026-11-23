# Brainstorm — HealthOS Mobile (NT118)

Nguồn: `siinn1706/NT208_HealthOS` (scout 2026-11-23), đồ án tham khảo ZenBuddy (ảnh đề bài).

## 1. Contract

- **Outcome:** App Android native (Kotlin) mới hoàn toàn về UI, tái dùng Core API FastAPI + AI worker của HealthOS, vượt ZenBuddy ở mọi trục ZenBuddy có và có 4–5 tính năng "chỉ mobile mới làm được" để demo.
- **Constraints:** Kotlin (Java chỉ khi giảng viên bắt buộc); UI build mới, không port RN/Next; backend giữ contract `/v1/**` + bearer token hiện có; dữ liệu sức khỏe → bảo mật/biometric; demo chạy được khi mạng yếu (offline-first).
- **Non-goals:** Admin/RBAC UI, subscription/pricing, landing/articles, video call WebRTC, cloud OAuth wearable (Google Health), iOS, Wear OS app đầy đủ.
- **Acceptance:** (1) mỗi tính năng ZenBuddy có bản tương đương hoặc tốt hơn; (2) ≥4 tính năng signature chạy thật trên máy thật; (3) offline: mở app, ghi bữa ăn/uống thuốc khi mất mạng, sync lại khi có mạng; (4) light/dark đạt WCAG AA; (5) không mock data.

## 2. HealthOS đang có gì (đã verify trong repo)

Core FastAPI: auth + MFA + refresh session, onboarding draft, dashboard + AI advice, meals (upload ảnh → AI worker YOLO + CalorieCLIP), chat AI qua WebSocket (LLM OpenAI-compatible, RAG medical knowledge, `medical_safety`), triage engine rule-based + visit brief PDF, medications (plan, refill threshold, pause, audit), reminders (recurrence), appointments + prep checklist + assets, prescriptions, emergency profile + public share token + QR + wallet PDF, health metrics/vitals, health-connect sync schema (`024_health_connect_sync`), goals/streaks/achievements, reports PDF, notifications in-app, devices. Push FCM và SMS đang **stub**.

→ Backend đã mạnh hơn ZenBuddy rất nhiều. Điểm yếu nằm ở client, push và trải nghiệm mobile-native.

## 3. Giữ / Bỏ / Cải tiến

| Nhóm | Quyết định | Lý do |
|---|---|---|
| Auth, MFA, onboarding | **Giữ + cải tiến**: thêm BiometricPrompt khóa app, Credential Manager (Google sign-in) | Health data cần khóa app; mobile-native |
| Dashboard + AI advice | **Giữ, làm lại** thành bento + "Vital Orb" | Signature UI |
| Meal snap AI | **Cải tiến**: hybrid on-device (ML Kit labeling pre-check, offline) → server YOLO/CalorieCLIP | ZenBuddy chỉ cloud |
| AI chat (RAG + safety) | **Giữ + thêm** voice VN (SpeechRecognizer + TTS), context từ data thật của user, tạo lịch trình → ghi thẳng vào reminders | ZenBuddy chatbot chung chung |
| Medications + reminders | **Cải tiến**: exact alarm + full-screen intent, action "Đã uống/Hoãn" ngay trên notification, OCR hộp thuốc/đơn thuốc → tạo plan | Mobile-native, ZenBuddy không có thuốc |
| Appointments + visit prep + triage | **Giữ**, rút gọn UI; brief PDF share qua Android Sharesheet | Khác biệt về y khoa |
| Emergency profile/QR | **Nâng cấp mạnh** (xem signature) | |
| Health metrics/vitals | **Giữ**, nguồn dữ liệu = Health Connect + nhập tay + camera PPG | |
| Goals/streaks/achievements | **Giữ** | Gamification cho người trẻ |
| Reports PDF | **Giữ** (share/print) | |
| Admin, RBAC, subscriptions, articles, leaderboard, insurance form, video call, GitHub OAuth, security log UI | **Bỏ khỏi app** (backend vẫn giữ) | Không thuộc trải nghiệm mobile, tốn effort |
| Google Health cloud wearable OAuth | **Thay** bằng Health Connect on-device | API chính thức Android, nhận data từ Mi Band/Samsung/Fitbit |

## 4. Đối chiếu ZenBuddy → bản của mình

| ZenBuddy | HealthOS Mobile |
|---|---|
| Sensor đếm bước | Health Connect (bước, nhịp tim, giấc ngủ, cân nặng) + fallback `TYPE_STEP_COUNTER` |
| CameraX + Vision API ước calo | Hybrid on-device/server, chỉnh khẩu phần, món Việt (seed ingredients có sẵn) |
| Gemini chatbot lịch trình | Bác sĩ AI có RAG + triage + safety, voice tiếng Việt, đọc data thật, tạo reminder |
| Calendar + notification | Exact alarm thuốc, action trên notification, Glance widget, Quick Settings tile |
| Thư viện bài tập offline | **AI Rep Coach**: ML Kit Pose Detection đếm rep + chấm form (offline) + thư viện seed Room |
| Hồ sơ + TDEE | Giữ + mục tiêu BMI/cân nặng có sẵn backend |
| Dashboard thời tiết | **AQI + chỉ số nhiệt (UV/heat index)** → tự điều chỉnh mục tiêu nước, cảnh báo tập ngoài trời (bối cảnh HN/HCM) |
| Room + Firestore | Room (offline-first) + FastAPI Core sync qua WorkManager; FCM chỉ dùng cho push |
| XML + RecyclerView + MPAndroidChart + Glide | Jetpack Compose + Material 3, Vico charts, Coil |

## 5. Signature features (tính "đột phá")

Xếp theo tỉ lệ wow/effort. Nên chốt 4–5 cái để demo, không làm hết.

1. **Safety Net** — phát hiện té ngã (accelerometer/gyro, foreground service) + đếm ngược 30s → SOS SMS kèm GPS tới người thân; Emergency Card trên màn hình khóa qua Glance widget + Quick Settings tile mở QR (dùng public share token đã có). *Effort trung bình, demo rất mạnh.*
2. **Camera Heart Rate (PPG)** — ngón tay che camera + đèn flash, CameraX `ImageAnalysis` lấy kênh đỏ → lọc → BPM, lưu vào vitals. Ghi rõ "không thay thiết bị y tế". *Effort trung bình, wow cao, thuần mobile.*
3. **AI Rep Coach** — ML Kit Pose Detection đếm squat/push-up/jumping jack, cảnh báo sai form, ghi vào goals/streaks. Thay thế "thư viện bài tập tĩnh" của ZenBuddy. *Effort trung bình.*
4. **Pill Scanner** — ML Kit Text Recognition đọc hộp thuốc/đơn thuốc → điền sẵn form medication plan → exact alarm. *Effort thấp–trung bình.*
5. **Context-aware Doctor AI** — chat streaming WebSocket + RAG đã có, thêm voice VN và "tool": tạo reminder, mở visit-prep, triage đỏ → nút gọi 115. *Effort thấp vì backend sẵn.*
6. **Climate Coach** — AQI/UV/nhiệt độ → điều chỉnh mục tiêu nước, nhắc nghỉ, gợi ý tập trong nhà. *Effort thấp.*
7. *(Stretch)* Wear OS Tile hiển thị Vital Score; NFC tag ghi link emergency.

## 6. Lựa chọn thiết kế

### 6.1 UI toolkit
- **A. Jetpack Compose + Material 3 (đề xuất).** Giả định chính: môn cho phép Compose. Hỏng khi rubric bắt XML/Java. Lợi: animation (Vital Orb, morph), dark mode, ít boilerplate, khác biệt rõ với ZenBuddy.
- **B. XML Views + Material Components.** Giả định: giảng viên chấm theo XML/RecyclerView. Hỏng: tốn effort animation, trông giống ZenBuddy.

### 6.2 Backend
- **A. FastAPI Core HealthOS + FCM cho push (đề xuất).** Giả định: có chỗ chạy backend khi demo (laptop Docker + Cloudflare tunnel hoặc VPS). Hỏng khi mạng phòng demo chặn tunnel → giảm thiểu bằng Room offline-first và chạy LAN.
- **B. Firebase thuần như ZenBuddy.** Rẻ hạ tầng nhưng vứt bỏ toàn bộ RAG, triage, AI food model, PDF — mất lợi thế lớn nhất.
- **C. FastAPI + Firestore song song.** Hai nguồn sự thật, conflict sync. Loại.

### 6.3 AI vision
- **Hybrid (đề xuất):** on-device ML Kit cho pose/OCR/pre-check món ăn (offline, nhanh), server YOLO + CalorieCLIP cho ước lượng calo chính xác. Hỏng khi AI worker không chạy → fallback nhập tay + tìm ingredient.

**Better approaches:** Health Connect thay cho tự đếm bước bằng sensor (ZenBuddy) — API chính thức, nhận data mọi wearable, backend đã có schema sync; chi phí: xin quyền HC, cần app HC trên Android < 14.

## 7. Kiến trúc đề xuất

Kotlin 2.x · Compose + Material 3 · MVVM + UDF (ViewModel → `StateFlow<UiState>`) · Hilt · Navigation Compose (type-safe) · Room + DataStore · Retrofit/OkHttp + kotlinx.serialization · OkHttp WebSocket (chat) · WorkManager (sync, refill check) · AlarmManager exact (thuốc) · CameraX · ML Kit (Pose, Text, Image Labeling) · Health Connect · Glance widgets · Vico charts · Coil · BiometricPrompt · FCM.

Một module `app`, package theo feature (`feature/meals`, `feature/meds`, …) + `core/{data,network,designsystem}`. Multi-module không cần ở quy mô đồ án.

## 8. Theme — "Vital Bloom"

Style: **Biomimetic/Organic 2.0 + Bento grid**. Tránh anti-pattern "generic health app" (xanh dương bệnh viện).

Signature: **Vital Orb** — blob mềm ở Home, "thở" theo nhịp (dùng lại cho bài thở), màu chuyển teal → amber → coral theo Vital Score. Card bento bo góc 24dp, bề mặt tonal, không shadow nặng.

| Token | Light | Dark |
|---|---|---|
| primary | `#0F766E` (teal) | `#5EEAD4` |
| onPrimary | `#FFFFFF` | `#04201C` |
| tertiary/accent (CTA, tim) | `#E11D48` (coral-rose) | `#FDA4AF` |
| `VitalColors.energy` (năng lượng, streak) | `#B45309` (amber) | `#FCD34D` |
| secondary (chip/nav selection) | `#4A635F` (sage) | `#B1CCC6` |
| background | `#F4FAF7` (mint-white) | `#0A1512` (green-black) |
| surface / card | `#FFFFFF` | `#14211D` |
| onBackground | `#13201C` | `#E6F0EC` |
| muted text | `#5B6B66` | `#93A7A0` |
| error | `#DC2626` | `#F87171` |

Contrast đã kiểm (WCAG): white/primary 5.47, text/bg 15.9 (light) – 16.0 (dark), muted 5.31 / 7.33, white/coral 4.70, white/amber 5.02 → tất cả ≥ AA 4.5.

Typography: **Be Vietnam Pro** (hỗ trợ dấu tiếng Việt tốt, Google Downloadable Fonts) cho cả heading/body; số liệu dùng `fontFeatureSettings = "tnum"`. Scale M3: Display 36/44, Title 22/28, Body 16/24, Label 14/20.

Icons: Material Symbols Rounded (không emoji). Motion: 200–300 ms, spring cho card/orb, tôn trọng "Remove animations" của hệ thống. Dynamic color (Material You) **tắt mặc định** để giữ nhận diện, cho bật trong Settings.

## 9. Ưu tiên triển khai

- **Must:** auth+biometric, onboarding/TDEE, Home bento + Vital Orb, Health Connect, meals snap hybrid, AI chat, meds + exact alarm, offline sync, dark mode.
- **Should (signature):** Safety Net, Camera PPG, AI Rep Coach, Pill Scanner, Climate Coach, Glance widget.
- **Could:** visit-prep PDF, appointments, achievements, Wear OS tile, NFC.

## 10. Quyết định vòng 2 (2026-11-23)

- Compose + Kotlin được chấp nhận → chọn 6.1-A.
- Nhóm 2 người, demo **2026-12-12** (~19 ngày) → scope phải cắt mạnh.
- Backend: laptop + `cloudflared` tunnel → 6.2-A giữ nguyên.
- Thiết bị: Pixel 8 Pro, chưa có wearable → bước chân lấy từ `TYPE_STEP_COUNTER` của điện thoại, ghi vào Health Connect; wearable là tùy chọn nếu mượn được.
- Repo mới riêng cho app Android.
- **Bỏ:** Pill Scanner, AI Rep Coach.

### Signature còn lại
1. **Safety Net** — phát hiện té ngã + SOS SMS kèm GPS + thẻ khẩn cấp QR trên màn hình khóa (widget/Quick Settings tile).
2. **Camera Heart Rate (PPG)** — có bước kiểm chứng với máy đo SpO2 kẹp ngón (xem 10.1).
3. **Bác sĩ AI theo ngữ cảnh** — chat streaming, RAG, voice tiếng Việt, tạo reminder.
4. **Climate Coach** — AQI/UV/nhiệt độ chỉnh mục tiêu nước (làm sau cùng, cắt được).

### 10.1 Camera PPG — cơ chế và độ chính xác
- Ngón trỏ đặt **nhẹ** che kín cả ống kính chính lẫn đèn flash phía sau. Đèn torch chiếu xuyên mô, mỗi nhịp tim làm thể tích máu mao mạch thay đổi → độ sáng kênh đỏ dao động theo nhịp. Ấn mạnh sẽ ép hết máu khỏi mao mạch, mất tín hiệu.
- Xử lý: CameraX `ImageAnalysis` ~30 fps, khóa AE/AWB (Camera2Interop), lấy trung bình kênh đỏ mỗi frame → detrend → band-pass 0.7–3.5 Hz (42–210 BPM) → đếm đỉnh hoặc FFT trên cửa sổ 15–30 giây. Có kiểm tra chất lượng tín hiệu: ngón tay chưa phủ kín, cử động, tín hiệu phẳng → yêu cầu đo lại thay vì trả số bừa.
- Độ chính xác: nghiên cứu xác thực thuật toán đo bằng camera của Google (Communications Medicine, 2022) báo sai số trung bình khoảng 2% khi nghỉ. Thuật toán tự viết thường kém hơn; mục tiêu thực tế là sai số ±3–5 BPM khi ngồi yên. Sai lệch lớn khi cử động, tay lạnh, hoặc loạn nhịp. Không đo SpO2/huyết áp bằng camera vì không đáng tin.
- Kiểm chứng trong báo cáo: đo song song với máy SpO2 kẹp ngón (~20 lần đo), báo MAE, MAPE và biểu đồ Bland-Altman. Ghi rõ "không thay thế thiết bị y tế".

### 10.2 Ưu tiên mới (19 ngày, 2 người)
- **Must:** đăng nhập + biometric, onboarding/TDEE, Home bento + Vital Orb, bước chân, quét món ăn (server), bác sĩ AI (text trước, voice sau), thuốc + exact alarm + action trên notification, Safety Net, PPG, dark mode, cache Room cho Home/thuốc/bữa ăn.
- **Should:** voice cho chat, widget Glance thẻ khẩn cấp, Climate Coach, biểu đồ xu hướng.
- **Cut:** appointments, visit-prep, reports PDF, achievements, đồng bộ offline hai chiều đầy đủ (chỉ hàng đợi ghi cho bữa ăn và liều thuốc).

### 10.3 Chốt vòng 3
- Laptop demo đủ tài nguyên chạy AI worker (YOLO + CalorieCLIP) → quét món ăn dùng model server làm đường chính.
- Bài tập: giữ một thẻ gợi ý bài tập trên Home, lấy từ `GET /v1/dashboard/exercise-suggestions` có sẵn; không làm thư viện bài tập.
- SOS gửi tự động qua hai kênh:
  - **SMS** từ máy bằng `SmsManager` (quyền `SEND_SMS`, app không lên Play Store). Chạy được khi mất Internet, cần SIM có sóng.
  - **Email** qua backend: thêm endpoint mới `POST /v1/emergency/sos` đọc `user_profiles.emergency_contacts` (đã có trường email) và gửi bằng `adapters/email_client.send_email` (SMTP có sẵn). Cần SMTP được cấu hình trên laptop demo và máy có Internet.
  - Nội dung gồm tên, thời điểm, link Google Maps từ GPS, link thẻ khẩn cấp công khai (public share token đã có).
  - Người dùng có 30 giây đếm ngược kèm rung + âm báo để hủy trước khi gửi.

Scope đã chốt, sẵn sàng chuyển sang lập plan.
