# AlarmClockApp v3.7 – Báo thức nâng cao + API

## Keys đã gắn
| Dịch vụ | Trạng thái |
|---------|------------|
| Spotify Client ID | ✅ Intent mở Spotify |
| YouTube Music API | ✅ Intent / search |
| OpenWeatherMap | ✅ TTS thời tiết |
| Firebase (google-services.json) | ✅ Cloud Sync Firestore |
| Smart Home | ❌ Chưa có token |

## Cách dùng tính năng mới
1. Nhấn **giữ** nút **+** → màn hình Tính năng nâng cao
2. **Thời tiết (TTS)** – đọc thời tiết Hà Nội
3. **Phát Spotify** – mở app Spotify
4. **Phát YouTube Music** – mở YT Music
5. **Cloud Sync** – đẩy / kéo danh sách báo thức (cần bật Firestore trên Firebase Console)

## Bật Firestore (bắt buộc cho Cloud Sync)
1. Vào https://console.firebase.google.com → project `alarmclockapp-8984a`
2. Build → Firestore Database → Create database
3. Chọn **Start in test mode** (chỉ để thử, sau này siết rules)
4. Chọn region gần (asia-southeast1)

## Cảnh báo bảo mật
Các API key đang nằm trong `BuildConfig`. Nếu repo **public**, hãy:
- Regenerate key trên từng console
- Chuyển sang `local.properties` + không commit

## Build
```bash
./gradlew assembleDebug
```

## Emergency SMS + GPS (v3.7)
- **Đặt số cứu viện** → lưu số điện thoại
- **Gửi SMS cứu viện + GPS** → gửi tin kèm link Google Maps vị trí hiện tại
- **Lưu vị trí hiện tại** → lưu làm “nhà/cơ quan”
- **Kiểm tra gần vị trí đã lưu** → báo khoảng cách

Cần cấp quyền SMS + Vị trí khi hệ thống hỏi.
