# Gesture Music Wear (Wear OS)

Wear OS gesture music controller for Galaxy Watch 4+ and other Wear OS devices. Control music playback on your phone using hand gestures from your smartwatch.

## Features

- **Gesture Detection Algorithms**:
  - **WristRotationDetector**: Universal track-switch detector — automatic dominant-axis selection (Gyro X or Gyro Y), low-pass filtering ($\alpha = 0.75$), trapezoidal angle integration, peak-angular-speed gate, and 3D acceleration-magnitude anti-noise gating. Works across watch wearing orientations, hand sides, and Wear OS device manufacturers (standard Android Sensor API conventions: rad/s, m/s²).
  - **DoublePinchDetector**: Universal, tilt-invariant play/pause detector using 3D acceleration magnitude (+ dedicated Z-axis sensitivity), quiet-gyroscope guard, and a robust two-pinch state machine with rebound and stable re-arm cycles.
  - **FistClenchDetector**: Multi-axis shockwave discriminator distinguishes a deliberate fist clench (activation/arming gesture) from a single directional pinch spike.
  - **Gesture Training System**: 5-repetition training engine with motion energy analysis and adaptive thresholds for learning custom gestures.
  - **GestureArmingManager**: Guard state with a 12-second activity timeout to prevent accidental gestures.
- **Native Wear OS User Interface**:
  - Authentically designed for circular smartwatch AMOLED displays with curved framing and smooth scroll behavior.
  - **Control Screen**: Service toggle, left/right wrist selection, sensitivity sliders (turn angle, pinch threshold, clench threshold, gesture cooldown), and persistence.
  - **Player Screen**: Media metadata, transport buttons (Play/Pause, Next, Previous), volume controls, and MediaSession API synchronization.
  - **Training Screen**: Repetition progress ring, auto-capture of motion samples, and gesture template saving.
  - **Sensors Screen**: Live IMU sensor readings and diagnostic information.
- **Foreground Service**: Gesture detection works in background with proper wake lock and notification handling.

## Gestures & Controls

| Gesture | Default Action | Algorithm |
|---|---|---|
| **Turn Wrist Right** | Next Track | `WristRotationDetector` — dominant-axis (X/Y) integration, optional left-hand sign mirroring |
| **Turn Wrist Left** | Previous Track | `WristRotationDetector` — dominant-axis (X/Y) integration, optional left-hand sign mirroring |
| **Double Pinch** | Play / Pause | `DoublePinchDetector` (tilt-invariant, 3D magnitude + Z-axis impulse, quiet-gyro guard) |
| **Fist Clench** | Disarm Guard (12s) | `FistClenchDetector` (multi-axis shockwave) / `GestureArmingManager` |

## Development & Build

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

## Requirements

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK API 26+ (Android 8.0)
- Wear OS device or emulator
- Bluetooth connection to phone with media player

## Permissions

- `WAKE_LOCK` - Keep sensor processing active
- `VIBRATE` - Haptic feedback for gestures
- `HIGH_SAMPLING_RATE_SENSORS` - High-frequency sensor data
- `FOREGROUND_SERVICE` - Background gesture detection
- `POST_NOTIFICATIONS` - Service notification
- `BLUETOOTH_CONNECT` - Media session connectivity
