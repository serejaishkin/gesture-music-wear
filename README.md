# Gesture Music Wear (React)

Web-based recreation of the Galaxy Watch 4+ Wear OS gesture music controller, ported from Kotlin / Jetpack Compose to React, TypeScript, and Tailwind CSS.

## Features

- **Gesture Detection Algorithms**:
  - **WristRotationDetector**: Real-time trapezoidal integration of Gyroscope X angular speed with low-pass filtering ($\alpha = 0.8$), angular thresholds, and anti-noise accelerometer gating.
  - **DoublePinchDetector**: State-machine-based linear acceleration detection ($Z$-axis threshold check, quiet gyroscope guard, stable rearm cycle) to trigger Play/Pause.
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
| **Turn Wrist Right** | Next Track | `WristRotationDetector` (negative angle for right hand, positive for left) |
| **Turn Wrist Left** | Previous Track | `WristRotationDetector` (positive angle for right hand, negative for left) |
| **Double Pinch** | Play / Pause | `DoublePinchDetector` ($+3.5\,\mathrm{m/s^2}$ upward, $-2.5\,\mathrm{m/s^2}$ rebound) |
| **Activation Gesture** | Disarm Guard (15s) | `GestureTrainer` DTW template / `GestureArmingManager` |

## Development & Build

```bash
# Start development server
npm run dev

# Production build
npm run build
```
