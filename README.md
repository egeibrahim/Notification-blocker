# Notifilter

Jetpack Compose tabanlı bildirim filtreleme uygulaması. Room Database ve WorkManager kullanır.

## Özellikler

- **NotificationRecord tablosu**: `id`, `packageName`, `appName`, `content`, `channelId`, `timestamp`, `isBlocked`, `blockReason` alanları
- **Gece temizlik görevi**: WorkManager her gece 02:00 civarında 7 günden eski kayıtları siler

## Proje Yapısı

- `data/entity/NotificationRecord.kt` - Room Entity
- `data/dao/NotificationRecordDao.kt` - DAO (CRUD + deleteOlderThan)
- `data/database/AppDatabase.kt` - Room veritabanı
- `worker/NotificationCleanupWorker.kt` - 7+ günlük kayıtları silen WorkManager işçisi
- `NotificationCleanupScheduler.kt` - Gece 02:00'da çalışacak periyodik iş zamanlaması

## Kurulum

1. Android Studio ile projeyi açın
2. Gradle sync yapın (gerekirse `./gradlew wrapper` ile wrapper oluşturun)
3. Emülatör veya cihazda çalıştırın

## Kullanım

Veritabanına erişim için `NotifilterApplication.database.notificationRecordDao()` kullanılabilir.
