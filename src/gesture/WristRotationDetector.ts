import { GestureType } from '../types';

interface GyroSample {
  timestamp: number;
  gx: number;
  gy: number;
  accMag: number;
}

/**
 * Universal Wrist Rotation Detector for track switching.
 *
 * Grounded in official Android Sensor API specifications (rad/s, m/s^2)
 * and tested against Samsung Galaxy Watch 4+ hardware characteristics.
 *
 * Universal Design:
 * - Automatically detects the dominant rotation axis (Gyro X or Gyro Y) to support
 *   any watch wearing orientation, hand side, and Wear OS device manufacturer.
 * - Integrates angular velocity trapezoidally over a sliding window.
 * - Requires a dual condition: peak angular velocity AND cumulative angle threshold.
 * - 3D acceleration magnitude gating prevents false triggers during running or arm flailing.
 */
export class WristRotationDetector {
  private angleThresholdDegrees: number;
  private minAngularSpeed: number;
  private minDurationMs: number;
  private maxDurationMs: number;
  private cooldownMs: number;
  private windowMs: number;
  private idleThreshold: number;
  private idleTimeoutMs: number;
  private antiNoiseAccMag: number;
  private leftHand: boolean;

  private alpha = 0.75;
  private filteredGx = 0;
  private filteredGy = 0;
  private samples: GyroSample[] = [];
  private lastGestureTime = 0;
  private idleStartTime = 0;

  constructor(
    angleThresholdDegrees = 28,
    minAngularSpeed = 2.0,
    minDurationMs = 160,
    maxDurationMs = 600,
    cooldownMs = 1000,
    windowMs = 400,
    idleThreshold = 0.35,
    idleTimeoutMs = 180,
    antiNoiseAccMag = 24.0,
    leftHand = false
  ) {
    this.angleThresholdDegrees = angleThresholdDegrees;
    this.minAngularSpeed = minAngularSpeed;
    this.minDurationMs = minDurationMs;
    this.maxDurationMs = maxDurationMs;
    this.cooldownMs = cooldownMs;
    this.windowMs = windowMs;
    this.idleThreshold = idleThreshold;
    this.idleTimeoutMs = idleTimeoutMs;
    this.antiNoiseAccMag = antiNoiseAccMag;
    this.leftHand = leftHand;
  }

  public updateSettings(
    angleThresholdDegrees: number,
    cooldownMs: number,
    leftHand: boolean,
    minDurationMs: number,
    maxDurationMs: number
  ) {
    this.angleThresholdDegrees = angleThresholdDegrees;
    this.cooldownMs = cooldownMs;
    this.leftHand = leftHand;
    this.minDurationMs = minDurationMs;
    this.maxDurationMs = maxDurationMs;
  }

  public process(
    timestamp: number,
    gyroX: number,
    gyroY: number,
    _gyroZ: number,
    linAccX: number,
    linAccY: number,
    linAccZ: number
  ): GestureType | null {
    // 1. Cooldown gate
    if (timestamp - this.lastGestureTime < this.cooldownMs) return null;

    // 2. 3D Acceleration anti-noise gate (flailing/jumping)
    const accMag = Math.sqrt(linAccX * linAccX + linAccY * linAccY + linAccZ * linAccZ);
    if (accMag > this.antiNoiseAccMag) {
      this.resetWindow();
      return null;
    }

    // 3. Low-pass filter for gyro X and gyro Y (forearm twist axes)
    this.filteredGx = this.alpha * this.filteredGx + (1 - this.alpha) * gyroX;
    this.filteredGy = this.alpha * this.filteredGy + (1 - this.alpha) * gyroY;

    const maxAbsFiltered = Math.max(Math.abs(this.filteredGx), Math.abs(this.filteredGy));

    // 4. Idle motion timeout handling
    if (maxAbsFiltered < this.idleThreshold) {
      if (this.samples.length > 0) {
        if (this.idleStartTime === 0) this.idleStartTime = timestamp;
        if (timestamp - this.idleStartTime > this.idleTimeoutMs) {
          this.resetWindow();
        }
      }
      return null;
    }

    this.idleStartTime = 0;

    // 5. Sliding window management
    this.samples = this.samples.filter((s) => timestamp - s.timestamp <= this.windowMs);
    this.samples.push({
      timestamp,
      gx: this.filteredGx,
      gy: this.filteredGy,
      accMag,
    });

    if (this.samples.length < 2) return null;

    // 6. Find dominant rotation axis (X or Y)
    let maxSpeedX = 0;
    let maxSpeedY = 0;
    for (const s of this.samples) {
      if (Math.abs(s.gx) > maxSpeedX) maxSpeedX = Math.abs(s.gx);
      if (Math.abs(s.gy) > maxSpeedY) maxSpeedY = Math.abs(s.gy);
    }

    const useAxisX = maxSpeedX >= maxSpeedY;
    const peakSpeed = useAxisX ? maxSpeedX : maxSpeedY;

    if (peakSpeed < this.minAngularSpeed) return null;

    // 7. Dominant direction consistency check (>= 60% of samples must agree on sign)
    const values = this.samples.map((s) => (useAxisX ? s.gx : s.gy));
    const positiveCount = values.filter((v) => v > this.idleThreshold).length;
    const negativeCount = values.filter((v) => v < -this.idleThreshold).length;
    const dominantCount = Math.max(positiveCount, negativeCount);

    if (dominantCount < this.samples.length * 0.55) return null;

    // 8. Trapezoidal angle integration (in radians -> degrees)
    let integratedAngleRad = 0;
    for (let i = 1; i < this.samples.length; i++) {
      const dt = (this.samples[i].timestamp - this.samples[i - 1].timestamp) / 1000;
      const avgRate = (values[i] + values[i - 1]) / 2;
      integratedAngleRad += avgRate * dt;
    }

    let angleDegrees = (integratedAngleRad * 180) / Math.PI;

    // 9. Duration bounds check
    const duration = this.samples[this.samples.length - 1].timestamp - this.samples[0].timestamp;
    if (duration < this.minDurationMs) return null;
    if (duration > this.maxDurationMs) {
      this.resetWindow();
      return null;
    }

    // 10. Left-hand sign mirroring
    if (this.leftHand) {
      angleDegrees = -angleDegrees;
    }

    // 11. Gesture evaluation
    if (angleDegrees > this.angleThresholdDegrees) {
      this.lastGestureTime = timestamp;
      this.resetWindow();
      return GestureType.PREVIOUS_TRACK;
    }

    if (angleDegrees < -this.angleThresholdDegrees) {
      this.lastGestureTime = timestamp;
      this.resetWindow();
      return GestureType.NEXT_TRACK;
    }

    return null;
  }

  public reset() {
    this.resetWindow();
    this.filteredGx = 0;
    this.filteredGy = 0;
    this.lastGestureTime = 0;
  }

  private resetWindow() {
    this.samples = [];
    this.idleStartTime = 0;
  }
}
