# Запись IMU и прогон жестов на ПК

1. Часы: экран «Диагностика» → кнопка «метка» выбирает жест (none/outward/inward/pinch/fist/walk) → «⏺ REC» → сделать жест → «⏹ СТОП».
   Запись останавливается сама при сворачивании приложения.
2. Забрать файлы: `adb pull /sdcard/Android/data/com.example.gesturemusicwear.debug/files/imu .`
3. Прогнать на ПК: `tools/replay.sh imu_123.csv [angle pinch clench leftHand fistGuard]`
   Выводит каждое срабатывание с меткой; срабатывания при метке `none`/`walk` = фантомы.

CSV: `t_ms,gx,gy,gz,ax,ay,az,label` (гиро рад/с, акселерометр м/с² с гравитацией).
Логика жестов живёт в `GestureEngine` (без Android-зависимостей), её используют и приложение, и сервис.
