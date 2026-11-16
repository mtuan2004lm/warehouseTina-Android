# NHẬT KÝ THAY ĐỔI — App Android quét kho (Tina Warehouse)

> Bản Android port từ app iOS `WarehouseScanner` (SwiftUI). App iOS đang chạy thật ở kho Mỹ nên **chỉ đọc để tham khảo, không sửa**.
> **Quy tắc:** mỗi khi sửa code app, thêm một mục mới ở đầu phần "Lịch sử" (mới nhất ở trên) và cập nhật `README.md` cho khớp.

**Ký hiệu:** ⭐ quan trọng · 🔥 khó / dễ sai · 🧪 chỉ để test · 📱 cần build lại app trong Android Studio

---

## Lịch sử thay đổi (mới nhất ở trên)

### 2026-11-16 · 📱 Đổi tên app thành "Warehouse Tina" + logo TN giống iOS
- `res/values/strings.xml`: `app_name` "Tina Warehouse" → **"Warehouse Tina"** (đúng `CFBundleDisplayName` của iOS). Tên gói `com.example.tinawarehouse` giữ nguyên.
- Biểu tượng app: lấy `AppIcon.png` của iOS (logo TN, nền trắng), thu còn 82% cho vừa vùng an toàn của adaptive icon → `mipmap-*/ic_launcher_foreground.png` (5 cỡ), `values/ic_launcher_bg.xml` (nền trắng), `mipmap-anydpi/ic_launcher.xml` + `ic_launcher_round.xml` trỏ sang logo mới (bỏ lớp monochrome).
- Các file biểu tượng robot cũ của template (`drawable/ic_launcher_*.xml`, `mipmap-*/ic_launcher*.webp`) còn trong project nhưng không được dùng; có thể xoá tay.

### 2026-11-16 · ⭐ 🔥 📱 Đợt 6: ảnh label + báo hư hỏng (giống iOS)
- **`state/DamageStore.kt`** ⭐: form báo hư hỏng (port `CompanyDamageSheet`): loại hư hỏng xếp theo tên, chọn nhiều; tối đa 10 ảnh; quyết định Nhận hàng / Từ chối. Báo mới = POST; sửa = PATCH (chỉ gửi quyết định khi đổi, không có ảnh mới thì giữ ảnh cũ, có ảnh mới thì THAY TOÀN BỘ ảnh cũ). Khoá quyết định khi báo cáo cũ đã Từ chối hoặc kiện ON_HOLD / RETURNED_TO_SELLER. Lỗi thì giữ nguyên form. Kèm `uploadLabelPhoto()` (gửi ảnh label, trả câu thông báo).
- **`ui/damage/DamageDialog.kt`**: bảng báo hư hỏng toàn màn hình, hỏi xác nhận trước khi gửi (GHI thật lên công ty).
- **`ui/common/PhotoTaker.kt`** 🔥: chụp ảnh bằng **app camera của máy** (`TakePicture` + `FileProvider`), tự xin quyền camera, xoay theo EXIF, thu nhỏ cạnh dài ≤ 1600 px, JPEG 70% (giống iOS). Khác iOS: giao diện lúc chụp tuỳ hãng máy.
- `ui/scan/ScanScreen.kt`: thêm nút **"Ảnh label"** và **"Hỏng"** cho kiện vừa ghi (mở bảng hư hỏng thì nhả camera quét). `state/ScanController.kt`: `showBanner()`.
- `ui/sessions/TrackingDetailScreen.kt`: nút "Báo hư hỏng" / "Sửa báo cáo / đổi ảnh"; đóng bảng thì tải lại kiện.
- `AndroidManifest.xml` + `res/xml/file_paths.xml`: khai báo `FileProvider` (thư mục cache `photos/` cho ảnh tạm, chụp xong xoá). `i18n/EnTable.kt` +1 câu. Không thêm thư viện.
- Test `state/Dot6Test.kt`: 6 test — **93/93 pass trên JVM trong sandbox**.
- 🔥 **Chưa kiểm chứng:** chụp ảnh và giao diện chưa build/chạy trên máy. Còn mở: "Kết thúc phiên" khi còn mã chờ END CODE chưa chặn/hỏi.

### 2026-11-16 · 🔥 📱 Sửa lỗi build Đợt 5: `when` thiếu nhánh `OFFLINE` ở `SessionDetailScreen.kt`
- Lỗi: "'when' expression must be exhaustive. Add the 'OFFLINE' branch" trong `ResultLine`. Đợt 5 thêm `FlowResult.Kind.OFFLINE` vào `ScanFlow` nhưng quên cập nhật màn chi tiết phiên.
- Sửa: `OFFLINE` hiện giống `ERROR` (biểu tượng cảnh báo, màu cam). Sandbox không bắt được lỗi này vì không biên dịch được file Compose.

### 2026-11-16 · ⭐ 🔥 📱 Đợt 5: màn quét camera (CameraX + ML Kit) giống iOS
- **`domain/scan/ScanEngine.kt`** ⭐🔥: port phần ghép "mã vạch + mã kho" của `AutoScanViewModel.swift`, viết đồng bộ (màn hình gọi `tick()` mỗi 50 ms) và tiêm đồng hồ để test. Cửa sổ đọc 2,5 giây; đủ cả hai thì chờ thêm ≤ 0,5 giây để gom các mã khác cùng tem (≤ 4); mã kho đọc trước mã vạch vẫn ghép được (trong 3 giây); hết giờ mà thiếu mã kho: tem tiếp theo của kiện đang chờ dùng mã kho cũ, tem mới lần 1 báo THIẾU MÃ KHO / lần 2 (trong 60 giây) gửi không kèm mã kho; mã đã gửi còn trong khung thì chỉ nhắc, rời khung > 0,7 giây rồi đưa lại → hỏi công ty → MÃ TRÙNG (không lặp trong 4 giây); `*ENDCODE*` chống đọc lặp 3 giây; kiện BLOCK → bỏ qua mọi đầu vào tới khi bấm "Quét tiếp tục".
- **`state/ScanController.kt`** ⭐: nối ScanEngine với **`ScanFlow`** (cùng quy tắc 3 mã + END CODE với ô gõ tay — **danh sách mã chờ dùng chung** giữa ô gõ tay và màn quét), thẻ kết quả, lịch sử, âm thanh; **mất mạng → giữ lại, tự gửi lại mỗi 4 giây** (`ScanFlow` thêm loại kết quả `OFFLINE`: lỗi chưa có phản hồi HTTP).
- **`camera/ScanCamera.kt`** 🔥: CameraX (preview + phân tích 1080p, giữ khung mới nhất) + ML Kit Barcode (12 loại mã) + ML Kit Text Recognition (latin); đèn flash; tự lấy nét/đo sáng lại ở giữa khung khi thấy chữ mà > 1,5 giây không đọc được mã vạch (port `refocusIfStuck`).
- **`ui/scan/ScanScreen.kt`**: luôn tối; ô ĐÃ GỬI, thẻ kết quả (SUCCESS + dải vàng HÀNG KINH DOANH / TING n/3 / THIẾU MÃ KHO / NO NAME / BẤM END CODE / MÃ TRÙNG / BLOCK / BỎ QUA), khung quét nét đứt với vòng cam theo tiến độ, dải "mất kết nối, đang tự gửi lại", nút END CODE + "Chọn mã chính", cảnh báo đỏ "BẮT BUỘC BẤM END CODE", màn che BLOCK / ĐÃ DỪNG QUÉT, menu Dừng quét / Kết thúc phiên, đèn, lịch sử quét, xin quyền camera, giữ màn hình sáng. **Chưa có nút Ảnh label / Hỏng (Đợt 6).**
- `ui/TinaApp.kt`: "Tiếp tục quét" mở màn quét; kết thúc phiên từ màn quét → về thẳng danh sách phiên. `ui/sessions/SessionDetailScreen.kt` đọc mã chờ trực tiếp từ `ScanFlow`. `i18n/EnTable.kt` +3 câu.
- Gradle: CameraX 1.4.2, ML Kit barcode-scanning 17.3.0, text-recognition 16.0.1. Manifest: quyền CAMERA (camera không bắt buộc). Giữ nguyên AGP 9.5.1 / Kotlin 2.2.10 / Gradle 9.6 đã có trên máy.
- Khác iOS: âm "mã trùng" và "bắt buộc END CODE" bên iOS có vẻ bị đặt ngược nhau; Android dùng đúng tên âm trong Cài đặt. OCR dùng ML Kit thay Vision — cần thử trên điện thoại thật.
- Test `scan/ScanEngineTest.kt`: 7 test ScanEngine + 5 test ScanController — **87/87 pass trên JVM trong sandbox** (lúc viết, 1 test kỳ vọng sai âm NO NAME cho mã kho `FD VAT HUE`; mã này là hàng kinh doanh nên đúng là âm "Hàng kinh doanh" như iOS → sửa test).
- 🔥 **Chưa kiểm chứng:** camera, ML Kit, giao diện chưa build/chạy; cần Sync Gradle và thử trên máy Android thật.

### 2026-11-14 · ⭐ 🔥 📱 Ô gõ mã ở chi tiết phiên theo quy tắc web: gom tối đa 3 mã rồi END CODE
- ⭐ **Android làm KHÁC ô gõ tay của iOS, GIỐNG web** (theo yêu cầu): iOS chỉ áp quy tắc nhiều mã + END CODE ở màn quét camera, còn ô gõ tay ghi ngay 1 mã (mã không có trong đơn phải bấm "Ghi thành kiện NO NAME"). **Người phụ trách iOS nên sửa ô gõ tay của iOS theo cho khớp.**
- **`state/ScanFlow.kt`** ⭐🔥 (mới, dùng chung cho ô gõ tay và màn quét camera ở Đợt 5) — port `sendCompany` của `AutoScanViewModel.swift`:
  - Mã công ty CÓ (FOUND) → ghi 1 kiện ngay; các mã đang chờ thành mã phụ.
  - Mã công ty KHÔNG CÓ → thêm vào kiện đang chờ (1/3, 2/3, 3/3), âm "ting".
  - Đủ 3 mã chờ mà nhập tiếp → chặn, "Bắt buộc bấm nút END CODE" (không hỏi công ty).
  - END CODE (nút hoặc gõ `ENDCODE`) → ghi kiện NO NAME: mã đầu là mã chính, còn lại là mã phụ.
  - Mã trùng / đã nằm trong kiện chờ / đã có trong danh sách kiện → không ghi. Phiên đã đóng → chỉ kiểm tra. Lỗi → không mất mã chờ. Mỗi lần xử lý một mã (chống Enter hai lần).
- **`ui/sessions/SessionDetailScreen.kt`**: ô "Quét hoặc gõ mã rồi Enter" (tự xoá sau mỗi lần nhập), ô kiện đang chờ giống web (mã chính to, mã phụ nhỏ, nút × bỏ mã, chạm mã phụ để chọn làm mã chính), nút cam "n / 3 END CODE". Còn mã chờ mà thoát → hỏi "Còn n mã chưa END CODE… Vẫn thoát?" (phần thêm, iOS không có). Bỏ nút "Ghi thành kiện NO NAME" và hộp xác nhận.
- `state/SessionDetailStore.kt` dùng `ScanFlow`; `Feedback` thêm PENDING / END_REQUIRED / NO_NAME (âm "Bắt buộc bấm END CODE", "Kiện NO NAME" theo cài đặt). `i18n/EnTable.kt` thêm 7 câu.
- Test: `state/ScanFlowTest.kt` 10 test mới; bỏ 5 test cũ của nút NO NAME trong `Dot4Test.kt`. **75/75 pass trên JVM trong sandbox.**
- 🔥 Còn mở: bấm "Kết thúc phiên" khi còn mã chờ thì các mã đó mất (chưa chặn/hỏi). Màn Compose chưa build thử.

### 2026-11-14 · ⭐ 🔥 📱 Đợt 4: chi tiết phiên, chi tiết kiện, xem ảnh (giống iOS)
- **`ui/sessions/SessionDetailScreen.kt` + `state/SessionDetailStore.kt`** ⭐🔥: logo hãng + bộ đếm lớn "đã quét / hãng khai"; ô gõ/dán mã (mã đã có trong danh sách → không gọi API; hỏi công ty song song các mã ứng viên của `TrackingCutter`; FOUND + phiên mở → ghi luôn như web; không có → nút "Ghi thành kiện NO NAME" có hỏi xác nhận; hiện lý do block, phát âm theo cài đặt); nút "Tiếp tục quét" (màn quét = Đợt 5, tạm là "Sắp có"); danh sách kiện tải theo trang 30, số thứ tự giảm dần giống web, nút sao chép mã (giữ lâu cũng chép), nhãn "Kiện không xác định"/"HƯ HỎNG"; "Kết thúc phiên" có hỏi xác nhận rồi quay về danh sách.
- **`ui/sessions/TrackingDetailScreen.kt` + `state/TrackingDetailStore.kt`**: chi tiết kiện chỉ đọc (mã, mã phụ, trạng thái, khớp đơn, khách, kệ, tình trạng, check-in, ảnh label, báo cáo hư hỏng, số lượng, hàng trong kiện, giữ hàng). Nút báo/sửa hư hỏng để Đợt 6.
- **`ui/common/PhotoViewer.kt`, `RemotePhoto.kt`**: xem ảnh toàn màn hình (vuốt đổi ảnh, chụm phóng to tối đa 6×, chạm đúp về cỡ gốc). 🔥 Tải ảnh bằng OkHttp có sẵn + bộ nhớ đệm 48 MB, **không thêm Coil** để đỡ rủi ro build. Link ảnh bắt đầu bằng "/" được ghép với máy chủ công ty.
- `domain/session/TrackingFormat.kt`: chữ trạng thái kiện, số lượng, khớp đơn, tình trạng, quyết định.
- `ui/TinaApp.kt`: thêm màn chi tiết phiên/kiện; màn chi tiết giữ "store" trong ngăn xếp nên quay lại không mất dữ liệu.
- Test `state/Dot4Test.kt`: 10 test — **70/70 test pass trên JVM trong sandbox** (Đợt 1–4). Lúc viết test có một chỗ hiểu sai (cho rằng dòng kết quả ghi luôn "ĐANG BLOCK"); đã so lại iOS và sửa test cho khớp iOS.
- 🔥 **Chưa kiểm chứng:** các màn Compose chưa build trong Android Studio.

### 2026-11-13 · ⭐ 🔥 📱 Đợt 3: danh sách phiên + tạo phiên + cài đặt (giống iOS)
- **`ui/sessions/SessionsScreen.kt`** ⭐: danh sách phiên (mã `DHL0024`, logo hãng, cửa, người giao, xe, giờ đến, trạm, người mở; số đã quét / hãng khai xanh khi đủ, vàng khi thiếu), thanh lọc Hãng · Trạng thái · Cửa · Giờ xe đến (khoảng ngày) + Xoá lọc, kéo xuống để tải lại. Bấm phiên → màn "Sắp có" (chi tiết phiên là Đợt 4).
- **`state/SessionsStore.kt`** 🔥: bộ lọc gọi API (ngày 00:00–23:59:59 theo giờ máy), chỉ nhận kết quả của lần tải mới nhất, chỉ hiện phiên của trạm đang chọn (phiên không rõ trạm vẫn hiện), sắp xếp id giảm dần, nhớ các cửa đã thấy, 401 → đăng xuất. `reset()` khi đăng xuất để người sau không thấy dữ liệu cũ.
- **`ui/sessions/NewSessionScreen.kt`, `state/NewSession.kt`** ⭐: tạo phiên ở trạm đã chọn (trạm được gán, hoặc mọi trạm nếu ADMIN), hỏi xác nhận trước khi ghi lên công ty, giờ xe đến không quá 1 giờ sau hiện tại.
- **`ui/settings/SettingsScreen.kt`**: tài khoản, trạm (admin đổi được), Đăng xuất, giao diện (hệ thống/sáng/tối), ngôn ngữ VI/EN, âm thanh theo từng trạng thái quét (+ nghe thử, khôi phục mặc định).
- **`sound/`**: `ScanSound` (9 trạng thái, khoá lưu giống iOS) và `SoundPlayer` (âm bíp ToneGenerator + giọng "Block"). 🔥 Âm iOS (mã 1057, 1103…) không có trên Android nên dùng âm bíp tương đương; mã âm là số riêng của app (1–15), không phải mã ToneGenerator.
- **Tài nguyên**: `res/raw/block_voice.m4a` (giải mã từ chuỗi base64 trong `SettingsView.swift`), 9 logo hãng PNG trong `drawable-nodpi` (đổi từ SVG iOS bằng rsvg-convert; có bản `_dark` cho Amazon/OnTrac/USPS, chọn theo giao diện đang dùng).
- `ui/TinaApp.kt`: điều hướng đơn giản bằng ngăn xếp màn hình + nút Back; `TinaApplication.kt`: thêm `sessions`, `sound`; `i18n/EnTable.kt`: thêm "Nghe thử", "Tải lại".
- Test `state/Dot3Test.kt`: 10 test (định dạng giờ/đếm, logo hãng, lọc + sắp xếp + tham số API, tạo phiên thành công/không có trạm/lỗi API, âm mặc định) — **pass trên JVM trong sandbox** (cùng 11 test Đợt 2 = 21).
- 🔥 **Chưa kiểm chứng:** các màn Compose và `SoundPlayer` chưa build trong Android Studio. Dùng `PullToRefreshBox`, `SegmentedButton` (Material3 1.3, đang đánh dấu Experimental → có `@OptIn`); nếu Material3 của BOM 2024.09.00 báo thiếu API nào thì báo lại.

### 2026-11-13 · 🔥 📱 Sửa lỗi build Đợt 2: "Platform declaration clash" ở `TinaApplication.kt`
- Lỗi: trong `UiPrefs`, thuộc tính `var lang ... private set` tự sinh hàm `setLang(String)` trùng với hàm `setLang()` tự viết (tương tự `theme`/`setTheme`).
- Sửa: đổi tên hàm thành `updateLang()` / `updateTheme()` (cập nhật chỗ gọi ở `LoginScreen.kt`). Quy tắc nhớ: đừng đặt hàm `setX` khi đã có thuộc tính `x` có setter.

### 2026-11-13 · ⭐ 🔥 📱 Đợt 2: nền tảng (API, token, đa ngôn ngữ, đăng nhập) + màn đăng nhập giống iOS
- **`data/CompanyApi.kt`** ⭐🔥: toàn bộ endpoint công ty (đăng nhập/đăng xuất, me, stations, carriers, phiên, kiện, hư hỏng, quét, ảnh label…). **Refresh token dùng MỘT lần**: gặp 401 thì làm mới đúng một lần rồi gọi lại; nhiều lệnh cùng 401 chỉ làm mới một lần (`Mutex`); làm mới lỗi 400/401/403 → xoá token + phát `sessionExpired` + lỗi `SESSION_EXPIRED`. Timeout 15s (90s khi upload ảnh). Mật khẩu không bao giờ được lưu.
- **`data/CompanyModels.kt`, `CompanyJson.kt`, `CompanyError.kt`**: mô hình dữ liệu đọc JSON "dễ tính" (số/chuỗi lẫn lộn, thiếu trường không làm hỏng danh sách), `displayCode` kiểu `DHL0024`, `blockHints` (lý do block), `imageUrls`.
- **`data/HttpTransport.kt`, `Storage.kt`** (giao diện trừu tượng, dùng để test) và **`data/AndroidStorage.kt`** (OkHttp, SharedPreferences, **token mã hóa bằng EncryptedSharedPreferences/Keystore**). 🔥 phần này chưa biên dịch được trong sandbox.
- **`i18n/L10n.kt` + `EnTable.kt`**: `tr("câu tiếng Việt", args)` y như iOS, 258 câu EN lấy từ `Localization.swift`; đổi VI/EN là giao diện vẽ lại ngay.
- **`state/AuthStore.kt`, `AppStore.kt`**: đăng nhập chọn trạm rồi hậu kiểm (`Tài khoản không có {0}` / `không có trạm nào`), nhớ tên đăng nhập, `availableStationIds`; hết phiên thì về màn đăng nhập.
- **UI**: `ui/theme/Color.kt`, `Theme.kt` (màu navy/cam/xanh/đỏ/xám của iOS; sáng/tối/hệ thống), `ui/login/LoginScreen.kt` (nền gradient, nút VI/EN, logo thẻ trắng, thanh chọn trạm, ô có biểu tượng, hiện/ẩn mật khẩu), `ui/TinaApp.kt` (đăng nhập xong vào màn tạm có nút Đăng xuất — danh sách phiên là Đợt 3), `TinaApplication.kt`, `MainActivity.kt`.
- `AndroidManifest.xml`: thêm INTERNET + VIBRATE, `TinaApplication`, **`allowBackup=false`** (không để token bị sao lưu ra ngoài). Logo: `res/drawable-nodpi/logo_tina.png` (chép từ iOS, chỉ đọc).
- Thư viện mới: OkHttp 4.12.0, kotlinx-serialization-json 1.7.3, coroutines-android 1.9.0, security-crypto 1.1.0-alpha06, material-icons-extended.
- Test `data/CoreTest.kt`: 11 test (tr VI/EN, displayCode, kiện lẫn lộn, blockHints, đăng nhập lưu token, 5 lệnh 401 chỉ refresh 1 lần, refresh lỗi → hết phiên, ADMIN lấy `/stations`, đăng nhập đúng/sai/trạm không được phép) — **pass trên JVM trong sandbox**.
- 🔥 **Chưa kiểm chứng:** các file Compose/Android và các phiên bản thư viện chưa build trong Android Studio (sandbox không có Maven/SDK). Nếu Gradle báo không tìm thấy phiên bản nào, đổi sang bản gần nhất trong `libs.versions.toml`.

### 2026-11-13 · ⭐ 🔥 📱 Sửa lỗi build lần đầu: hạ phiên bản thư viện AndroidX cho khớp AGP 8.13.2
- Lỗi: `:app:checkDebugAarMetadata` báo "4 issues were found when checking AAR metadata" — `androidx.core:core-ktx:1.19.0` yêu cầu Android Gradle Plugin 9.1.0 trở lên, trong khi project dùng AGP 8.13.2. Lỗi có sẵn từ file phiên bản của template, không do code đợt 1.
- Sửa `gradle/libs.versions.toml`: `coreKtx` 1.19.0 → 1.16.0, `lifecycleRuntimeKtx` 2.10.0 → 2.9.0, `activityCompose` 1.13.0 → 1.10.1. Chọn hạ thư viện thay vì nâng AGP lên 9.1.0 vì nâng AGP kéo theo nâng Gradle và đổi cách cấu hình Kotlin (rủi ro cao hơn).
- 🔥 Các phiên bản trên chưa được build thử (sandbox không có Android SDK). Nếu Sync/Build còn báo lỗi AAR metadata ở thư viện khác thì hạ tiếp thư viện đó, đừng nâng AGP.

### 2026-11-13 · ⭐ 🔥 Đợt 1: port logic cắt mã vạch + hồ sơ trạm từ iOS (chưa có giao diện)
- **Hạ `minSdk` 35 → 26** (`app/build.gradle.kts`). Project mới tạo để 35 nghĩa là chỉ chạy trên Android 15 trở lên; máy kho thường cũ hơn. Chỉ đổi đúng 1 dòng.
- **`domain/scan/TrackingCutter.kt`** ⭐: cắt chuỗi mã vạch thành tracking (USPS/UPS/FedEx/DHL/Amazon + biến thể ZIP+4). Cùng thứ tự thử và cùng kết quả với `TrackingCutter` trong `APIClient.swift`. Khác iOS ở một điểm có chủ ý: **trạm được truyền vào hàm** (`candidates(barcode, profile)`) thay vì đọc biến toàn cục "trạm đang chọn" — dễ test, và sau này đổi trạm trong Cài đặt không phải sửa hàm.
- **`domain/station/`**: `StationProfile.kt` (hồ sơ trạm, `matches()`, `resolve()`), `StationUS.kt` (12 mẫu cắt mã, 4 quy tắc mã kho, `ocrFix`), `StationDE.kt` (khung trạm Đức — **chưa có quy tắc**, chờ manager kho Đức), `WarehouseRule.kt`, `RuleMatcher.kt` (khớp mã kho từ các dòng OCR). Nội dung quy tắc trạm Mỹ chép nguyên từ `Stations/US/StationUS.swift` ngày 09/11/2026.
- **Test** `app/src/test/.../TrackingCutterTest.kt` + `StationRulesTest.kt`: 39 test (từng hãng, ZIP+4, sửa lỗi OCR, khớp quy tắc, nhận trạm OR_1 không dính OREGON thừa).
- 🔥 **Phát hiện ở bản iOS (CHƯA sửa, giữ nguyên để khớp iOS):** mã dạng `420 + ZIP9 + tracking` bị mẫu `420 + ZIP5` khớp trước nên `primary` (mã dùng khi ghi kiện) ra `"0123" + tracking` chứ không phải tracking thật; tracking thật vẫn nằm trong danh sách ứng viên hỏi công ty. Test `usps_420_zip9_listContainsRealTracking` ghi lại hành vi này. Cần xác nhận với người phụ trách iOS trước khi sửa (sửa cả hai app cùng lúc).
- Kiểm thử: đã chạy 39 test bằng Kotlin 2.0.21 + JUnit 4.13.2 trên JVM — **pass hết**. Chưa chạy `./gradlew test` trong Android Studio, chưa build APK, chưa chạy trên điện thoại. Android dùng bộ regex ICU còn JVM dùng bộ của Java; các mẫu ở đây đơn giản nên khác biệt không đáng kể, nhưng nên chạy test một lần trong Android Studio.
