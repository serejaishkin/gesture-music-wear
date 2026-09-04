import { GestureType } from '../types';

interface Sample {
  timestamp: number;
  gyroX: number;
  rawGyroX: number;
  linAccY: number;
}

/**
 * Wrist rotation detector for track switching.
 * Ported from WristRotationDetector.kt
 *
 * A light/slow turn must not be enough to trigger a track change.
 * The detector requires both a real angular-speed peak and
 * enough integrated rotation. Direction is mirrored for the left wrist.
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
  private antiNoiseAccY: number;
  private leftHand: boolean;

  private alpha = 0.8;
  private filteredGyroX = 0;
  private samples: Sample[] = [];
  private lastGestureTime = 0;
  private idleStartTime = 0;

  constructor(
    angleThresholdDegrees = 28,
    minAngularSpeed = 2.2,
    minDurationMs = 160,
    maxDurationMs = 600,
    cooldownMs = 1200,
    windowMs = 400,
    idleThreshold = 0.35,
    idleTimeoutMs = 180,
    antiNoiseAccY = 20,
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
    this.antiNoiseAccY = antiNoiseAccY;
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
    _gyroY: number,
    _gyroZ: number,
    _linAccX: number,
    linAccY: number,
    _linAccZ: number
  ): GestureType | null {
    if (Math.abs(linAccY) > this.antiNoiseAccY) {
      if (Math.abs(gyroX) < this.idleThreshold * 2) {
        this.resetWindow();
      }
      return null;
    }

    this.filteredGyroX = this.alpha * this.filteredGyroX + (1 - this.alpha) * gyroX;

    if (timestamp - this.lastGestureTime < this.cooldownMs) return null;

    if (Math.abs(this.filteredGyroX) < this.idleThreshold) {
      if (this.samples.length > 0) {
        if (this.idleStartTime === 0) this.idleStartTime = timestamp;
        if (timestamp - this.idleStartTime > this.idleTimeoutMs) {
          this.resetWindow();
        }
      }
      return null;
    }

    this.idleStartTime = 0;
    this.samples = this.samples.filter((s) => timestamp - s.timestamp <= this.windowMs);
    this.samples.push({
      timestamp,
      gyroX: this.filteredGyroX,
      rawGyroX: gyroX,
      linAccY,
    });

    if (this.samples.length < 2) return null;

    let maxAngularSpeed = 0;
    for (const s of this.samples) {
      const spd = Math.max(Math.abs(s.gyroX), Math.abs(s.rawGyroX));
      if (spd > maxAngularSpeed) maxAngularSpeed = spd;
    }

    if (maxAngularSpeed < this.minAngularSpeed) return null;

    const positive = this.samples.filter((s) => s.gyroX > this.idleThreshold).length;
    const negative = this.samples.filter((s) => s.gyroX < -this.idleThreshold).length;
    const dominant = Math.max(positive, negative);
    if (dominant < this.samples.length * 0.6) return null;

    let angle = 0;
    for (let i = 1; i < this.samples.length; i++) {
      const dt = (this.samples[i].timestamp - this.samples[i - 1].timestamp) / 1000;
      angle += ((this.samples[i].gyroX + this.samples[i - 1].gyroX) / 2) * dt;
    }

    let angleDegrees = (angle * 180) / Math.PI;

    const firstSample = this.samples[0];
    const lastSample = this.samples[this.samples.length - 1];
    const duration = lastSample.timestamp - firstSample.timestamp;

    if (duration < this.minDurationMs) return null;

    if (duration > this.maxDurationMs) {
      this.resetWindow();
      return null;
    }

    if (this.leftHand) {
      angleDegrees = -angleDegrees;
    }

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
    this.filteredGyroX = 0;
    this.lastGestureTime = 0;
  }

  private resetWindow() {
    this.samples = [];
    this.idleStartTime = 0;
  }
}
