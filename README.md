# Tina Warehouse (Android)

App Android dùng camera điện thoại **thay máy quét mã vạch** để nhận hàng kho, làm việc với API công ty `https://apiwh-dev.tinashipping.com/api`. Đây là bản port từ app iOS `WarehouseScanner` (SwiftUI) đang chạy thật ở kho Mỹ.

> App iOS chỉ dùng để **tham khảo**. Không sửa app iOS từ project này.

## Trạng thái

| Phần | Trạng thái |
|---|---|
| Cắt mã vạch → tracking (`TrackingCutter`) | ✅ xong, có test |
| Hồ sơ trạm Mỹ OR_1 (mẫu cắt mã, quy tắc mã kho, sửa lỗi OCR) | ✅ xong, có test |
| Hồ sơ trạm Đức DE_FRANKFURT | 🟡 khung rỗng, chờ quy tắc từ manager kho Đức |
| API công ty (đăng nhập, refresh token 1 lần, phiên, kiện, ảnh, hư hỏng) | ✅ xong, có test (chưa dùng hết trên giao diện) |
| Đa ngôn ngữ VI/EN (`tr()`), lưu token mã hóa, AuthStore/AppStore | ✅ xong |
| Màn đăng nhập | ✅ xong (chờ build thử) |
| Danh sách phiên + bộ lọc, tạo phiên, cài đặt (giao diện, ngôn ngữ, âm thanh) (Đợt 3) | ✅ xong (chờ build thử) |
| Chi tiết phiên/kiện, xem ảnh (Đợt 4) | ⏳ chưa làm |
| Màn quét, ảnh label, hư hỏng (Đợt 5-6) | ⏳ chưa làm |
| Camera (CameraX) + mã vạch / OCR (ML Kit) | ⏳ chưa làm |

## Yêu cầu
- Android Studio mới nhất, Kotlin 2.0.21, Jetpack Compose.
- `minSdk` 26 (Android 8.0), `targetSdk` 36.
- Để thử camera nên dùng điện thoại thật (bật USB debugging).

## Chạy test
```
./gradlew testDebugUnitTest
```
Hoặc trong Android Studio: chuột phải thư mục `app/src/test` → **Run Tests**.

## Cấu trúc code (`app/src/main/java/com/example/tinawarehouse/`)
| Đường dẫn | Việc |
|---|---|
| `domain/scan/TrackingCutter.kt` | Cắt chuỗi mã vạch thành tracking, tạo danh sách mã ứng viên để hỏi công ty |
| `domain/station/StationProfile.kt` | Hồ sơ một trạm: cách cắt mã, quy tắc mã kho, sửa OCR, cách nhận trạm trên server |
| `domain/station/StationUS.kt` | **Quy tắc trạm Mỹ (OR_1 Hub Oregon)** — chép nguyên từ iOS |
| `domain/station/StationDE.kt` | Trạm Đức (DE_FRANKFURT), chưa có quy tắc |
| `domain/station/WarehouseRule.kt`, `RuleMatcher.kt` | Quy tắc mã kho + khớp các dòng OCR với quy tắc |
| `data/CompanyApi.kt`, `CompanyModels.kt`, `CompanyJson.kt`, `CompanyError.kt` | API công ty, mô hình dữ liệu, đọc JSON dễ tính |
| `data/HttpTransport.kt`, `Storage.kt`, `AndroidStorage.kt` | Gửi HTTP (OkHttp), cài đặt, token mã hóa |
| `i18n/` | `tr()` + bảng tiếng Anh |
| `state/AuthStore.kt`, `AppStore.kt` | Đăng nhập, trạm đang dùng |
| `ui/theme/`, `ui/login/`, `ui/TinaApp.kt` | Màu/chủ đề, màn đăng nhập, gốc app |
| `domain/session/SessionFormat.kt`, `state/SessionsStore.kt`, `state/NewSession.kt` | Định dạng phiên, danh sách phiên + bộ lọc, tạo phiên |
| `sound/` | Âm thanh theo trạng thái quét (bíp Android + giọng "Block") |
| `ui/sessions/`, `ui/settings/`, `ui/common/` | Màn danh sách phiên, tạo phiên, cài đặt, logo hãng |
| `TinaApplication.kt`, `MainActivity.kt` | Khởi tạo đối tượng dùng chung, activity |

Mỗi kho có quy tắc quét **riêng**: thêm trạm mới = tạo `object StationXX` giống `StationUS` rồi thêm vào `StationProfile.all`.

## Quy ước làm việc
- Mỗi lần sửa code: ghi vào `CHANGELOG.md` (đánh dấu ⭐ quan trọng / 🔥 khó) và cập nhật README này cho khớp.
- Tham chiếu logic gốc ở app iOS (chỉ đọc): `APIClient.swift`, `Stations/US/StationUS.swift`, `RuleMatcher.swift`, `AutoScanViewModel.swift`.
- Phân quyền trạm: trạm khả dụng = `me.stations`; nếu rỗng mà `systemRole == "ADMIN"` thì lấy toàn bộ `GET /stations`. Chọn trạm ở màn đăng nhập rồi **hậu kiểm** sau khi login.

## Việc còn mở
- Điền quy tắc quét kho Đức.
- Màn hình và luồng quét; lưu hàng chờ khi mất mạng; xử lý `requiresImportCheck`; dùng `customerCodeMappings` sửa lỗi OCR mã khách.
- Đổi API sang production + versioning.
