import { GestureType } from '../types';

/**
 * Universal Double Pinch Detector for Play/Pause.
 *
 * Grounded in official Wear OS motion sensor mechanics:
 * A pinch gesture (index finger snapping against the thumb) transmits a sharp,
 * transient micro-acceleration shockwave into the watch chassis.
 *
 * Universal & Tilt-Invariant Design:
 * - Employs 3D acceleration magnitude (along with dedicated Z-axis sensitivity)
 *   so the gesture works when the watch is face-up, tilted, or hanging down.
 * - Enforces quiet gyroscope state (to avoid confusion with wrist rotations or arm swings).
 * - Tracks a robust two-pinch state machine with rebound/re-arm cycles.
 */
export class DoublePinchDetector {
  private thresholdUp: number;
  private thresholdDown: number;
  private maxAccMag: number;
  private windowMs: number;
  private cooldownMs: number;
  private maxGyroMagnitude: number;

  private static readonly PINCH_TIMEOUT_MS = 450;
  private static readonly MIN_PINCH_INTERVAL_MS = 100;

  private lastGestureTime = 0;
  private pinches: number[] = [];

  private state: 'IDLE' | 'IMPULSE_ACTIVE' = 'IDLE';
  private impulseStartTime = 0;
  private armed = true;
  private stableStartTime = 0;

  constructor(
    thresholdUp = 3.2,
    thresholdDown = -1.8,
    maxAccMag = 14.0,
    windowMs = 900,
    cooldownMs = 1000,
    maxGyroMagnitude = 2.5
  ) {
    this.thresholdUp = thresholdUp;
    this.thresholdDown = thresholdDown;
    this.maxAccMag = maxAccMag;
    this.windowMs = windowMs;
    this.cooldownMs = cooldownMs;
    this.maxGyroMagnitude = maxGyroMagnitude;
  }

  public updateSettings(pinchThreshold: number, cooldownMs: number) {
    this.thresholdUp = pinchThreshold;
    this.thresholdDown = -(pinchThreshold * 0.6);
    this.cooldownMs = cooldownMs;
  }

  public process(
    timestamp: number,
    gyroX: number,
    gyroY: number,
    gyroZ: number,
    linAccX: number,
    linAccY: number,
    linAccZ: number
  ): GestureType | null {
    const accMag = Math.sqrt(linAccX * linAccX + linAccY * linAccY + linAccZ * linAccZ);

    // 1. Rearm check after firing
    if (!this.armed) {
      if (accMag <= 1.8) {
        if (this.stableStartTime === 0) this.stableStartTime = timestamp;
        if (
          timestamp - this.stableStartTime >= 80 &&
          timestamp - this.lastGestureTime >= this.cooldownMs
        ) {
          this.armed = true;
          this.stableStartTime = 0;
        }
      } else {
        this.stableStartTime = 0;
        if (timestamp - this.lastGestureTime >= this.cooldownMs + 400) {
          this.armed = true;
        }
      }
      return null;
    }

    // 2. Cooldown check
    if (timestamp - this.lastGestureTime < this.cooldownMs) return null;

    // 3. Gyroscope guard: pinch must occur with low rotational movement
    const gyroMag = Math.max(Math.abs(gyroX), Math.abs(gyroY), Math.abs(gyroZ));
    if (gyroMag > this.maxGyroMagnitude) {
      if (
        this.state === 'IMPULSE_ACTIVE' &&
        timestamp - this.impulseStartTime > DoublePinchDetector.PINCH_TIMEOUT_MS
      ) {
        this.state = 'IDLE';
      }
      return null;
    }

    // 4. Clean expired pinch timestamps from window
    if (this.pinches.length > 0) {
      this.pinches = this.pinches.filter((t) => timestamp - t <= this.windowMs);
    }

    // 5. Detect sharp impulse (Z-axis or total 3D magnitude)
    const isImpulse =
      (linAccZ > this.thresholdUp && linAccZ <= this.maxAccMag) ||
      (accMag > this.thresholdUp && accMag <= this.maxAccMag);

    if (this.state === 'IDLE') {
      if (isImpulse) {
        this.state = 'IMPULSE_ACTIVE';
        this.impulseStartTime = timestamp;
      }
    } else if (this.state === 'IMPULSE_ACTIVE') {
      // Rebound or return to baseline within timeout window
      const isRebound =
        linAccZ < this.thresholdDown ||
        (timestamp - this.impulseStartTime >= 40 && accMag < this.thresholdUp * 0.5);

      if (isRebound) {
        this.pinches.push(timestamp);
        this.state = 'IDLE';

        // Check if we have two valid pinches
        if (this.pinches.length >= 2) {
          const first = this.pinches[this.pinches.length - 2];
          const second = this.pinches[this.pinches.length - 1];
          const interval = second - first;

          if (interval >= DoublePinchDetector.MIN_PINCH_INTERVAL_MS && interval <= this.windowMs) {
            this.lastGestureTime = timestamp;
            this.pinches = [];
            this.armed = false;
            this.stableStartTime = 0;
            return GestureType.PLAY_PAUSE;
          }
        }
      } else if (timestamp - this.impulseStartTime > DoublePinchDetector.PINCH_TIMEOUT_MS) {
        this.state = 'IDLE';
      }
    }

    return null;
  }

  public reset() {
    this.pinches = [];
    this.state = 'IDLE';
    this.impulseStartTime = 0;
    this.lastGestureTime = 0;
    this.armed = true;
    this.stableStartTime = 0;
  }
}
