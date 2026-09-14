# Gesture Music Wear (React)

Web-based recreation of the Galaxy Watch 4+ Wear OS gesture music controller, ported from Kotlin / Jetpack Compose to React, TypeScript, and Tailwind CSS.

## Features

- **Gesture Detection Algorithms**:
  - **WristRotationDetector**: Universal track-switch detector — automatic dominant-axis selection (Gyro X or Gyro Y), low-pass filtering ($\alpha = 0.75$), trapezoidal angle integration, peak-angular-speed gate, and 3D acceleration-magnitude anti-noise gating. Works across watch wearing orientations, hand sides, and Wear OS device manufacturers (standard Android Sensor API conventions: rad/s, m/s²).
  - **DoublePinchDetector**: Universal, tilt-invariant play/pause detector using 3D acceleration magnitude (+ dedicated Z-axis sensitivity), quiet-gyroscope guard, and a robust two-pinch state machine with rebound and stable re-arm cycles.
  - **FistClenchDetector**: Multi-axis shockwave discriminator distinguishes a deliberate fist clench (activation/arming gesture) from a single directional pinch spike.
  - **GestureTrainer (DTW)**: 5-repetition Dynamic Time Warping training engine with motion energy analysis and variance thresholds for learning custom gestures.
  - **GestureArmingManager**: Guard state with a 15-second activity timeout to prevent accidental gestures.
- **Wear OS User Interface**:
  - Authentically renders the circular smartwatch AMOLED display (396×396) with curved framing, bezel accents, and smooth scroll behavior.
  - **Control Screen**: Service toggle, left/right wrist selection, sensitivity sliders (turn angle, pinch threshold, min/max duration, gesture cooldown), and persistence.
  - **Player Screen**: Media metadata, real-time playback position, transport buttons (Play/Pause, Next, Previous), and MediaSession API synchronization.
  - **Training Screen**: Repetition progress ring, auto-capture of motion samples, and gesture template saving.
- **Gesture & IMU Sensor Simulator**:
  - Emulates physical wrist rotations and double pinches directly in the browser with live IMU readings.
  - Supports the Web DeviceMotion API on supported mobile and smartwatch browsers.

## Gestures & Controls

| Gesture | Default Action | Algorithm |
|---|---|---|
| **Turn Wrist Right** | Next Track | `WristRotationDetector` — dominant-axis (X/Y) integration, optional left-hand sign mirroring |
| **Turn Wrist Left** | Previous Track | `WristRotationDetector` — dominant-axis (X/Y) integration, optional left-hand sign mirroring |
| **Double Pinch** | Play / Pause | `DoublePinchDetector` (tilt-invariant, 3D magnitude + Z-axis impulse, quiet-gyro guard) |
| **Fist Clench** | Disarm Guard (15s) | `FistClenchDetector` (multi-axis shockwave) / `GestureTrainer` DTW / `GestureArmingManager` |

## Development & Build

```bash
# Start development server
npm run dev

# Production build
npm run build
```
