# PROJECT MAP — Gesture Music Wear

> Документ передачи проекта другому ИИ. Обновлять при существенных изменениях архитектуры, диагностики или текущего блокера.
> Последняя проверка: 2026-09-27.
> Основная ветка: `main`.

## 1. Назначение проекта

Wear OS-приложение для Galaxy Watch 4+ и других Wear OS-устройств.

Основная идея:
- распознавать жесты по IMU часов;
- управлять музыкой на телефоне;
- работать как на экране приложения, так и в фоне через Foreground Service;
- иметь встроенное обучение/калибровку жестов;
- показывать живую диагностику акселерометра и гироскопа.

Целевая платформа: Wear OS, native Android Java.
Текущую архитектуру НЕ переписывать без отдельной необходимости.

## 2. Текущая архитектура

Основные компоненты:

- `MainActivity.java`
  - native circular Wear OS UI;
  - 4 экрана:
    - 0 — Tile;
    - 1 — Player;
    - 2 — Settings;
    - 3 — Sensors/Training;
  - регистрация SensorManager;
  - обработка IMU;
  - обучение жестам;
  - haptic feedback;
  - управление MediaSession/AudioManager;
  - сохранение настроек через SharedPreferences.

- `GestureForegroundService.java`
  - обработка сенсоров в фоне;
  - Foreground Service;
  - уведомление;
  - повторяет основную обработку жестов для фонового режима.

- `WristRotationDetector.java`
  - распознавание вращения кисти;
  - использует X/Y/Z гироскопа;
  - выбирает доминирующую ось;
  - фильтрация;
  - интегрирование угла;
  - антишумовой контроль по ускорению.

- `DoublePinchDetector.java`
  - двойной pinch;
  - play/pause.

- `FistClenchDetector.java`
  - сжатие кулака;
  - используется как активация/взведение защиты.

- `GestureArmingManager`
  - логика защитного окна активации.

## 3. Сенсоры

В Manifest уже присутствуют:

- `android.hardware.sensor.accelerometer` — required;
- `android.hardware.sensor.gyroscope` — required;
- `HIGH_SAMPLING_RATE_SENSORS`.

В `MainActivity`:

- `SensorManager`;
- `TYPE_GYROSCOPE`;
- `TYPE_ACCELEROMETER`;
- `mSensorsActive = true` по умолчанию;
- состояние сохраняется в SharedPreferences под ключом `active`.

Текущая диагностика:
- `mSensorEventCount` считает входящие SensorEvent;
- `mSensorRegistrationOk` хранит состояние регистрации;
- экран диагностики должен показывать:
  - состояние регистрации;
  - количество событий;
  - X/Y/Z гироскопа;
  - X/Y/Z акселерометра;
  - интенсивность.

ВАЖНО:
Если графики/значения не двигаются, сначала диагностировать цепочку:
`SensorManager -> registerListener -> onSensorChanged -> UI`.
Не начинать с изменения алгоритмов жестов.

## 4. Haptics

Manifest содержит:

`android.permission.VIBRATE`.

В MainActivity:
- `mHapticsEnabled = true` по умолчанию;
- настройка сохраняется как `haptics`;
- используется `VibratorManager` на Android S+;
- fallback на `Vibrator`;
- есть диагностические проверки `hasVibrator()`;
- ошибки вибрации логируются;
- обучение вызывает вибрацию при старте/успехе/ошибке.

ВАЖНО:
Пользователь сообщил:
> «вибро нет даже при открытом приложении или в разделе обучения графики не двигаются».

Это указывает на проблему ниже уровня алгоритма жестов: регистрацию сенсоров, получение событий, состояние приложения/настроек или haptic API.

## 5. Foreground Service

Manifest содержит:

- `FOREGROUND_SERVICE`;
- `FOREGROUND_SERVICE_HEALTH`;
- service:
  `GestureForegroundService`;
- `android:foregroundServiceType="health"`.

Логика:
- Activity при foreground работает с собственными SensorListener;
- при уходе Activity в background запускается Foreground Service;
- это сделано потому, что continuous sensors в фоне ограничены Android;
- ранее из FGS был убран 10-минутный partial WakeLock.

НЕ возвращать WakeLock без доказанной необходимости.

## 6. Gesture pipeline

### Fist
Сначала проверяется Fist Guard.

Если fist распознан:
- приложение становится armed;
- окно активации: 12 секунд;
- вибрация;
- дальнейшие media-жесты разрешаются.

### Wrist rotation

Параметры MainActivity сейчас примерно:
- angle threshold: 35°;
- minimum angular speed: 0.8 rad/s;
- min duration: 100 ms;
- max duration: 1000 ms;
- cooldown: 650 ms;
- analysis window: 800 ms;
- idle threshold: 0.25;
- idle timeout: 200 ms;
- anti-noise acceleration magnitude: 35 m/s².

Detector:
- доминирующая ось выбирается из X/Y/Z;
- используется интегрирование гироскопа;
- для левой руки знак зеркалится;
- effective threshold ограничен более чувствительным диапазоном.

### Double pinch

Использует 3D acceleration magnitude, Z-axis sensitivity и quiet-gyro guard.

### Fist clench

Использует multi-axis shockwave discriminator.

## 7. Training

Training находится на экране 3.

Есть режимы:

- `TRAINING_OUTWARD`;
- `TRAINING_INWARD`;
- `TRAINING_PINCH`;
- `TRAINING_FIST`.

Цель: 5 повторений.

Есть ring/progress UI и ручное подтверждение повторения.

Критически исправленный баг:
раньше training samples сохраняли `SensorEvent.timestamp` (elapsed time since boot), а подтверждение сравнивало с `System.currentTimeMillis()`.
Это разные временные шкалы.

Исправлено:
- training confirmation использует `SystemClock.elapsedRealtime()`;
- `recordTrainingRepetition()` также использует `SystemClock.elapsedRealtime()`.

Для гироскопического training:
- считается magnitude;
- направление зависит от руки;
- motion threshold сейчас около 60° для wrist training.

## 8. Последние исправления

Последний известный commit:

`5ae8bc76f473e25e840dc6dfab1b57984b2fc28d`

В нём:
- добавлена диагностика количества SensorEvent;
- добавлен статус регистрации сенсоров;
- улучшена диагностика haptic feedback;
- добавлены проверки наличия вибратора;
- логируются ошибки вибрации;
- добавлены timestamps/диагностика.

До него были исправления:
- запуск/объявление Foreground Service;
- регистрация гироскопа и акселерометра;
- переход Activity/FGS при уходе в background;
- X/Y/Z gyro в WristRotationDetector;
- исправление training timestamp;
- исправление media command kind: play/pause — это `kind == 3`, а не `kind == 1`.

## 9. Текущий блокер

Пользователь сообщает:

> вибрации нет даже при открытом приложении;
> в разделе обучения графики/показания сенсоров не двигаются.

Это сейчас главный блокер.

Приоритет диагностики:

1. Проверить, что `mSensorManager != null`.
2. Проверить `getDefaultSensor(TYPE_GYROSCOPE)`.
3. Проверить `getDefaultSensor(TYPE_ACCELEROMETER)`.
4. Проверить реальные boolean-результаты `registerListener()`.
5. Проверить, вызывается ли `onSensorChanged()`.
6. Проверить, растёт ли `mSensorEventCount`.
7. Проверить, не выключены ли сенсоры через SharedPreferences `active`.
8. Проверить, что `mCurrentScreen == 3` при открытом training/sensor экране.
9. Для вибрации отдельно проверить:
   - `mHapticsEnabled`;
   - `VIBRATE`;
   - `Vibrator.hasVibrator()`;
   - успешность вызова `vibrate()`.
10. Только после этого менять thresholds/detectors.

## 10. Что НЕ делать

- Не переписывать проект на Compose/другую архитектуру.
- Не заменять native SensorManager без доказательства, что он не работает.
- Не начинать с полной замены gesture detectors.
- Не убирать Foreground Service.
- Не считать отсутствие распознавания жеста доказательством отсутствия SensorEvent.
- Не заявлять, что сборка/тест на часах прошёл, если реально не выполнялся.
- Не менять сразу несколько независимых подсистем без диагностического результата.

## 11. Следующий технический шаг

Нужно проверить текущий `registerSensors()`.

Желательная реализация должна учитывать реальный boolean return:

```java
boolean gyroOk = mGyroscope != null &&
        mSensorManager.registerListener(
                this, mGyroscope,
                SensorManager.SENSOR_DELAY_GAME);

boolean accelOk = mAccelerometer != null &&
        mSensorManager.registerListener(
                this, mAccelerometer,
                SensorManager.SENSOR_DELAY_GAME);

mSensorRegistrationOk = gyroOk && accelOk;
```

И логировать:
- наличие каждого сенсора;
- имя;
- vendor;
- registration result;
- event count.

Не считать регистрацию успешной просто потому, что `SensorManager` не бросил исключение.

## 12. Диагностический контракт для другого ИИ

Если пользователь сообщает «графики не двигаются», следующий ИИ должен сначала запросить/получить:

- значение `Сенсоры: ... событий N`;
- текущие Gyro X/Y/Z;
- Accel X/Y/Z;
- состояние включения жестов;
- состояние haptics;
- Logcat по tag `GestureMusicWear`.

Интерпретация:

- `events = 0` -> проблема регистрации/сенсора/жизненного цикла;
- `events > 0`, но UI не меняется -> проблема UI/updateLiveSensorsUI;
- сенсоры двигаются, но жест не срабатывает -> анализировать detector;
- haptics не работает при events > 0 -> отдельно диагностировать Vibrator;
- foreground работает, background нет -> анализировать FGS lifecycle/permissions.

## 13. Документальная база

README проекта описывает:
- Wear OS gesture controller;
- WristRotationDetector;
- DoublePinchDetector;
- FistClenchDetector;
- training;
- native circular UI;
- foreground service;
- permissions.

Этот PROJECT_MAP является рабочим handoff-документом и должен содержать более актуальное техническое состояние, чем общий README.

## 14. Правило работы с проектом

Перед изменением кода:
1. проверить текущий `main`;
2. посмотреть последний commit и состояние конкретного файла;
3. не опираться на старую локальную версию;
4. сделать минимальный целевой фикс;
5. отдельно проверить связанные места;
6. после изменения указать commit SHA;
7. явно разделять:
   - исправлено в коде;
   - требует проверки на реальных часах;
   - реально протестировано.

