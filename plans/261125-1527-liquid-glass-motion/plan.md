# Kế hoạch: chuyển động Liquid Glass cho HealthOS

Trạng thái: chờ duyệt. Nhánh: `feat/liquid-glass-ui`. Ngày lập: 25/11/2026.

## 1. Mục tiêu

Liquid Glass của Apple là chất liệu kính và chuyển động được thiết kế như một khối. Bản hiện tại của app mới có phần chất liệu: kính mờ, khúc xạ ở viền, viền sáng. Về chuyển động, app chỉ có ba thứ rời rạc: thấu kính kéo trên thanh tab, quầng sáng dưới ngón tay, và lò xo khi nhấn.

Mục tiêu của đợt này là cho các bề mặt kính xuất hiện, nhập vào nhau, tách ra và đổi hình theo đúng nhịp của iOS 26. Thông số được đo theo các dự án đã hiệu chỉnh với iOS thật, không tự đặt.

**Tiêu chí hoàn thành:**
- Mỗi tương tác ở mục 4 có video quay từ máy ảo.
- Thời lượng mở và đóng nằm trong khoảng ±15 % so với bảng thông số ở mục 3.

## 2. Những gì Apple và cộng đồng đã làm (nguồn ở cuối)

1. **Chuyển động là một phần của chất liệu.** Apple nói phần hình và phần chuyển động của Liquid Glass được thiết kế cùng nhau. Kính xuất hiện hoặc biến mất bằng cách tăng dần hoặc giảm dần độ khúc xạ và hiệu ứng thấu kính, không phải bằng cách mờ dần độ trong suốt (WWDC25 session 219).
2. **Nhập và đổi hình.** Các phần tử kính đặt trong cùng một `GlassEffectContainer` sẽ nhập vào nhau khi đủ gần; ngưỡng do tham số `spacing` quyết định. Khi một phần tử hiện ra hoặc ẩn đi, nó đổi hình từ phần tử kia sang thông qua `glassEffectID` (WWDC25 session 323). Kính không lấy mẫu từ kính khác, nên các phần tử phải được gom nhóm.
3. **Tương tác.** Với `.interactive()`, kính phản ứng khi chạm: phồng lên, phát sáng ở điểm chạm và co giãn đàn hồi theo ngón tay. Chạm trực tiếp được nhấn mạnh hơn so với dùng trackpad (HIG Motion).
4. **Thích nghi.** Kính đổi giữa sáng và tối theo nội dung nằm phía sau. Khi người dùng bật "giảm chuyển động" hoặc "giảm trong suốt", hiệu ứng được thay bằng dạng đơn giản hơn.
5. **Dự án mở đã làm được.**
   - `liquid_glass_widgets` (Flutter) có "Liquid Morph Engine": hai khối kính nối với nhau bằng shader SDF metaball, thông số lò xo đo từ trace 60 fps của iOS thật.
   - `fluidkit` (React) có metaball và lò xo.
   - Catalog của Kyant (thư viện app đang dùng) có sẵn `SdfShader.kt` (hợp mượt hai hình), `DampedDragAnimation` và `InteractiveHighlight`. App mới dùng hai thứ sau, chưa dùng SDF.

## 3. Thông số chuyển động (nguồn: Liquid Morph Engine, hiệu chỉnh với iOS 27)

| Chuyển động | Lò xo | Kết quả mong đợi |
|---|---|---|
| Mở bảng nổi từ nút kích hoạt | ω₀ = 17 rad/s, ζ ≈ 0.76 | Bay theo đường cong chữ J, vọt quá khoảng 2.5 %, xong sau khoảng 420 ms |
| Đóng bảng về nút | ω₀ = 18 rad/s, ζ ≈ 0.86 | Thu về tâm nút (scale 1.0 → 0.20), biến mất sau khoảng 280 ms |
| Nút rộng co lại khi mở | ω₀ = 25 rad/s, ζ = 0.94 | Từ viên dài co về hình tròn tại chỗ |
| Nút rộng nở lại khi đóng | ω₀ = 13 rad/s, ζ = 0.795 | Đạt đủ rộng ở khoảng 320 ms, rộng thêm 1.6 % ở khoảng 400 ms rồi trở lại |
| Nút giật nhẹ khi đóng | S(17, 0.70) − S(18, 1.0) | Đỉnh ở khoảng 183 ms, lệch 5–16 px, nén 0.96; ổn định sau khoảng 540 ms |
| Bán kính nhập SDF | 0 → 28 dp | Tạo "cổ" giọt nước nối hai khối khi chúng tách hoặc nhập |

Ghi chú về bảng:
- Độ đặc của kính không được giảm bằng alpha. Nó giữ nguyên cho tới khi bảng còn khoảng 25 % hành trình, rồi mới tan dần trong khúc hợp SDF.
- Khi bật giảm chuyển động: lò xo nhảy thẳng tới đích, đổi hình được thay bằng mờ dần ngắn khoảng 150 ms, và tắt phần giật nhẹ.

## 4. Phạm vi

### Hiện có, giữ lại
Chất liệu kính ba lớp, thấu kính kéo trên thanh tab và bộ chọn, quầng sáng khi chạm, viền sáng theo cảm biến nghiêng, các thành phần kính, màu nhấn và kiểu nền.

### Còn thiếu, sẽ làm (theo thứ tự ưu tiên)

1. **Nền tảng chuyển động**, trong `core/designsystem/motion/`:
   - `LiquidSprings`: các token ở mục 3, viết dưới dạng ω₀/ζ chuyển sang stiffness và damping của Compose.
   - `GlassMorphController`: open/close, các phase, giật nhẹ, xử lý giảm chuyển động.
   - Modifier `glassMaterialize(progress)`: tăng hoặc giảm lens, blur và viền sáng thay cho alpha.
   - `SdfUnionShader`: port từ `SdfShader.kt` của Kyant để hai capsule kính nhập vào nhau. Từ Android 13 trở lên dùng shader; từ Android 12 trở xuống thay bằng giao hai hình bo tròn.
2. **Thanh tab thu gọn khi cuộn** (như iOS 26): cuộn xuống thì thanh co thành viên nhỏ chỉ còn icon tab đang chọn, cuộn lên hoặc chạm thì nở lại. Có nhập và tách SDF với nút nổi bên cạnh.
3. **Menu và bảng đổi hình từ nút**:
   - Viên SOS trên Home: nhấn giữ thì bảng xác nhận mọc ra từ chính viên đó.
   - Nút "+" của Bữa ăn và Thuốc mở menu kính.
   - `GlassDialog` mọc từ nút kích hoạt, không bật ra giữa màn hình.
4. **Nhóm nút trên thanh công cụ**: các nút kính đứng gần nhau được nhập thành một khối và tách ra khi một nút mở rộng, ví dụ nút làm mới quay rồi tách ra.
5. **Chuyển màn**: màn con mọc ra từ thẻ được chạm (shared bounds), nút quay lại hình tròn đổi hình từ chính thẻ đó.
6. **Kính tự đổi sáng hoặc tối theo nội dung phía sau thanh tab**: lấy mẫu độ sáng vài lần mỗi giây và đổi có hysteresis.

### Ngoài phạm vi đợt này
Tính năng mới, backend, widget. Các tính năng đó đã nằm sẵn trên nhánh và không đụng lại.

## 5. Các phase

| Phase | Nội dung | Thời gian ước tính | Nghiệm thu |
|---|---|---|---|
| 0 | Sửa hai lỗi còn lại: viên SOS hiện "SOS SOS", biểu tượng thanh trạng thái không đổi màu theo theme tối của app. Commit và push để PR có bản mới nhất. | 20 phút | Ảnh chụp sáng và tối |
| 1 | Nền tảng chuyển động (mục 4.1), kèm unit test cho phần toán lò xo: thời gian ổn định và độ vọt quá khớp bảng ±15 %. | 3–4 giờ | Test qua; trang demo trong Thư viện giao diện |
| 2 | Thanh tab thu gọn và nhập SDF (mục 4.2). | 2–3 giờ | Video cuộn Home: thu gọn và nở lại |
| 3 | Menu, bảng và hộp thoại đổi hình từ nút (mục 4.3). | 3 giờ | Video mở và đóng; đo thời lượng theo khung hình |
| 4 | Nhóm nút trên thanh công cụ và chuyển màn (mục 4.4 và 4.5). | 3 giờ | Video |
| 5 | Kính thích nghi (mục 4.6) và hoàn thiện giảm chuyển động / giảm trong suốt. | 2 giờ | Video nền sáng/tối; video bật giảm chuyển động |

Sau mỗi phase sẽ có:
- Quay màn hình máy ảo bằng `adb screenrecord`, cắt khung hình để đo thời lượng, và gửi video cho bạn.
- Commit riêng cho phase đó.
- Push lên PR 1.

Mỗi phase dừng lại chờ bạn xem video trước khi sang phase sau.

## 6. Rủi ro

- **Hiệu năng:** shader SDF và việc ghi layer có thể tụt khung hình trên máy yếu. Mỗi phase sẽ đo frame time bằng `dumpsys gfxinfo`. Nếu quá 16 ms thì hạ độ phân giải của layer hoặc tắt SDF trên máy đó.
- **Phiên bản Android:** Android 12 trở xuống không có RuntimeShader, nên phần đổi hình dùng phương án hình học đơn giản hơn.
- **Ngân sách thời gian:** chỉ làm đúng 6 mục ở phần 4. Mục nào không đạt sau một lần làm thì báo lại, không kéo dài phiên.

## Nguồn

- [WWDC25 session 323: Build a SwiftUI app with the new design](https://developer.apple.com/videos/play/wwdc2025/323/)
- [WWDC25 session 219: Meet Liquid Glass](https://developer.apple.com/videos/play/wwdc2025/219/)
- [HIG: Motion](https://developer.apple.com/design/human-interface-guidelines/motion.md)
- [HIG: Materials](https://developer.apple.com/design/human-interface-guidelines/materials.md)
- [Liquid Glass technology overview](https://developer.apple.com/documentation/technologyoverviews/liquid-glass.md)
- [liquid_glass_widgets: Liquid Morph Engine (Flutter)](https://github.com/sdegenaar/liquid_glass_widgets/blob/main/docs/LIQUID_MORPH_ENGINE.md)
- [fluidkit (React)](https://github.com/runvendo/fluidkit)
- [LiquidGlassReference (SwiftUI)](https://github.com/conorluddy/LiquidGlassReference)
- [Understanding GlassEffectContainer](https://dev.to/arshtechpro/understanding-glasseffectcontainer-in-ios-26-2n8p)
- Kyant AndroidLiquidGlass catalog utils: `SdfShader.kt`, `DampedDragAnimation.kt`, `InteractiveHighlight.kt`

## 7. Nhật ký thực hiện

### Phase 0 (xong, đã push)
- Viên SOS chỉ còn một nhãn. Biểu tượng thanh trạng thái đổi theo theme của app.
- Ba lỗi chỉ lộ ra trên Galaxy S21 (cỡ chữ 1,3 lần) đã được sửa:
  - Chữ mặc định màu đen trên nền tối.
  - Đơn vị số liệu bị ngắt giữa từ.
  - Nhãn tab và lời chào bị cắt.

### Phase 1 (xong, đã push)
- Có 20 unit test. Số đo theo phép tính giải tích khớp bảng ở mục 3, sai lệch trong khoảng ±15 %.
- Video trên máy thật: bảng mở bay theo đường cong chữ J, đạt 98 % kích thước sau khoảng 308 ms.

### Phase 1.5: hiệu năng (phát sinh khi đo trên máy thật)
Đo bằng `dumpsys gfxinfo framestats` trên Galaxy S21, màn hình 120 Hz.

| Tình huống | Trước | Sau |
|---|---|---|
| Home đứng yên | 50–60 khung/giây vẽ lại liên tục, 34 ms mỗi khung | 0 khung |
| Cuộn Home, trung vị | 47 ms (đỉnh 191 ms khi thẻ còn bo góc dạng Path) | 28–40 ms |
| Cuộn Home, toàn nền đặc (mức sàn) | | 18,8 ms |
| Cuộn màn Tôi, trung vị | 141 ms (khi thẻ còn bo góc dạng Path) | 25 ms |

Các thay đổi đã làm:
- Quả cầu VitalOrb chỉ thở 2 nhịp khi xuất hiện hoặc khi đổi nhóm điểm.
- Thẻ nội dung dùng chất liệu thường: không lấy mẫu backdrop, bo góc rrect tăng tốc phần cứng.

Việc còn mở, sẽ làm trước phase 2:
- Khi đổi hình, app chỉ đạt khoảng 24 khung/giây (41,7 ms mỗi khung).
- Toàn bộ màn hình bị ghi vào layer ở mọi khung để thanh tab lấy mẫu, kể cả ở chế độ nền đặc.
- Mục tiêu: trung vị dưới 12 ms khi cuộn, và từ 60 khung/giây trở lên khi đổi hình.

### Gói hiệu năng (xong)
Đo trên Galaxy S21 (Android 15, cỡ chữ 1,3 lần). Cột "bây giờ" là bản benchmark có R8, baseline profile và biên dịch speed-profile, tương đương bản cài từ Google Play.

| Chỉ số | Bản debug ban đầu | Bây giờ |
|---|---|---|
| Mở app lạnh | 1.050 ms | 169–192 ms |
| Chuyển tab, khung giật nhất (p99) | 150–600 ms | 29–125 ms |
| Cuộn Home, trung vị | 47 ms | 12,8 ms (9 % số khung quá 16 ms) |
| Cuộn màn Tôi, trung vị | 22–25 ms | 12,2 ms |
| Home đứng yên | 50–60 khung/giây | 0 khung |

Nguyên nhân và cách sửa:
- **Bản debug:** Compose chạy kèm các kiểm tra debug và mã không được tối ưu. Đã bật R8, sinh baseline profile trên chính máy này, và thêm build type `benchmark` để đo.
- **Kính lấy mẫu backdrop thừa:**
  - Viên chọn và hàng icon ẩn của thanh tab giờ chỉ lấy mẫu khi đang nhấn.
  - Hiệu ứng mép cuộn dùng gradient thay cho blur trực tiếp.
  - Kính chỉ nằm trên nền tĩnh và thẻ nội dung không còn lấy mẫu trực tiếp.
- **Chuyển tab:** NavHost trước đây dùng fade mặc định 700 ms. Giờ là crossfade 150 ms, và các tab hiện dữ liệu đã lưu ngay (snapshot_cache trong Room, dữ liệu được coi là mới trong 60 giây).
- **R8:** đã thêm keep rule cho constructor của ActionCallback của widget, vì R8 xóa chúng làm nút widget crash. Kho token mã hóa vẫn giữ phiên sau 4 lần đóng hẳn app.
