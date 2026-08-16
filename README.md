# Gesture Music Wear

Wear OS приложение для Galaxy Watch 4+ (Android 16 / Wear OS 6) — управление музыкой жестами запястья.

## Жесты

| Жест | Действие |
|------|----------|
| Поворот запястья **вправо** | Следующий трек |
| Поворот запястья **влево** | Предыдущий трек |
| **Двойной щипок** | Play / Pause |

## Требования

- Galaxy Watch 4 или новее
- Wear OS 3+ (API 30+)
- Android 16 (targetSdk 36)
- Акселерометр + гироскоп

## Архитектура

- **Kotlin** — чистый код, без NDK (16KB page size ready)
- **Jetpack Compose for Wear OS 1.5** + Horologist 0.7.x
- **MVVM** — ViewModel + Compose UI
- **ForegroundService** — `connectedDevice|mediaPlayback`
- **SensorManager** — гироскоп + linear acceleration
- **MediaController** — управление любым плеером (Spotify, YouTube Music и т.д.)

## Сборка

```bash
./gradlew :app:assembleDebug
```

## Установка

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Настройка на часах

1. **Разрешить фоновую активность**: Настройки → Приложения → Gesture Music → Разрешения → Разрешить фоновую активность
2. **Запустить сервис** через приложение на часах
3. Открыть любой плеер и начать воспроизведение

## Лицензия

MIT
