# AlarmClockApp 4.19.9 — Báo thức Challenge

App báo thức Android: reo đúng giờ khi khóa máy, thử thách tắt chuông, sao lưu Google, widget màn hình chính.

Package: `com.alarmclock.dongho`  
Phiên bản hiện tại: **4.19.9** (`versionCode` 145)

## Tính năng chính

- Đặt báo, lặp 1 lần / mỗi ngày / T2–T6, hoãn, thử thách tắt chuông
- Reo khi khóa màn (`setShowWhenLocked`, FullScreenIntent, WakeLock)
- `AlarmManager.setAlarmClock` + `BootReceiver` (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, cài đè APK)
- `AlarmKeepAliveService` giữ thông báo “Báo thức đang bật” (`START_STICKY`, icon `ic_notification_alarm`)
- Xin tắt tối ưu hóa pin (`BatteryOptHelper`)
- Dialog thêm/sửa báo thức (không dùng màn full-screen)
- Hướng dẫn người mới + banner sự kiện (Tết / Halloween / Giáng sinh)
- Widget 1×1, 2×2, 4×2 — nền bo góc trong suốt, cập nhật từng phút
- Menu cập nhật: đọc GitHub Release, hiện “vX • Cập nhật ngay” khi có bản mới (APKPure)
- Đăng nhập Google: sinh nhật + sao lưu Firestore `users/{uid}/data/backup`
- Spotify / YouTube Music: mở app chọn nhạc
- Thời tiết TTS (OpenWeatherMap)
- SMS cứu viện + GPS (nếu đã cấp quyền)

## Dịch vụ / API

| Dịch vụ | Trạng thái |
|---------|------------|
| Spotify | Intent mở app |
| YouTube Music | Intent / search |
| OpenWeatherMap | TTS thời tiết |
| Firebase `alarmclockapp-8984a` | Cloud Sync Firestore |
| GitHub Releases | Kiểm tra bản mới |
| APKPure | `https://apkpure.com/p/com.alarmclock.dongho` |
| Smart Home | Chưa có token |

## Cloud Sync (Firestore)

1. https://console.firebase.google.com → project `alarmclockapp-8984a`
2. Build → Firestore Database → Create database
3. Test mode khi thử, siết rules khi phát hành
4. Region gần: `asia-southeast1`
5. Trong app: menu **Đăng nhập Google** — lần đầu đẩy báo lên cloud; máy mới thì kéo về

## Báo thức không bị mất trên Huawei / EMUI

1. Cài đặt → Ứng dụng → Báo thức Challenge → Pin → **Không tối ưu hóa**
2. Cho phép thông báo + hiện trên màn khóa
3. Không vuốt đóng thông báo đang bật (ongoing)
4. Sau reboot, BootReceiver đặt lại lịch + KeepAlive

## Bảo mật

Key đang có thể nằm trong `BuildConfig`. Repo public thì:

- Regenerate key trên từng console
- Đưa vào `local.properties`, không commit

## Build

```bash
./gradlew assembleDebug
```

Hoặc GitHub Actions trên repo `3hbin/AlarmClockApp`.

## Cấu trúc bổ sung gần đây

| Bản | Nội dung |
|-----|----------|
| 4.19.5 | Trả dialog thêm/sửa báo cũ |
| 4.19.6 | Hướng dẫn lần đầu + banner sự kiện |
| 4.19.7 | Widget bo góc, tick từng phút |
| 4.19.8 | Icon thông báo đơn sắc |
| 4.19.9 | Boot + KeepAlive ưu tiên cao, xin tắt tối ưu pin |
