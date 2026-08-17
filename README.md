# Gesture Music Wear

Wear OS приложение для Galaxy Watch 4+ — управление музыкой жестами запястья.

## Жесты
| Жест | Действие |
|------|----------|
| Поворот вправо | Следующий трек |
| Поворот влево | Предыдущий трек |
| Двойной щипок | Play / Pause |

## Сборка
```bash
./gradlew assembleDebug
```

## Установка
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```
