import { GestureType } from '../types';

/**
 * Double pinch detector for play/pause.
 * Enhanced to handle multiple watch angles, 3D impulse signatures,
 * and reliable rearm cycles.
 */
export class DoublePinchDetector {
  private thresholdUp: number;
  private thresholdDown: number;
  private maxAccZ: number;
  private maxXYAccel: number;
  private windowMs: number;
  private cooldownMs: number;
  private maxGyroMagnitude: number;

  private static readonly REARM_ACCEL = 1.6;
  private static readonly REARM_STABLE_MS = 100;
  private static readonly PINCH_TIMEOUT_MS = 450;
  private static readonly FALLBACK_REARM_MS = 1500;

  private lastGestureTime = 0;
  private pinches: number[] = [];

  private state: 'IDLE' | 'UP_DETECTED' = 'IDLE';
  private upTime = 0;
  private armed = true;
  private stableStartTime = 0;

  constructor(
    thresholdUp = 3.2,
    thresholdDown = -1.8,
    maxAccZ = 12.0,
    maxXYAccel = 5.5,
    windowMs = 900,
    cooldownMs = 1000,
    maxGyroMagnitude = 2.5
  ) {
    this.thresholdUp = thresholdUp;
    this.thresholdDown = thresholdDown;
    this.maxAccZ = maxAccZ;
    this.maxXYAccel = maxXYAccel;
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

    if (!this.armed) {
      if (accMag <= DoublePinchDetector.REARM_ACCEL) {
        if (this.stableStartTime === 0) this.stableStartTime = timestamp;
        if (
          timestamp - this.stableStartTime >= DoublePinchDetector.REARM_STABLE_MS &&
          timestamp - this.lastGestureTime >= this.cooldownMs
        ) {
          this.armed = true;
          this.stableStartTime = 0;
        }
      } else {
        this.stableStartTime = 0;
        if (timestamp - this.lastGestureTime >= DoublePinchDetector.FALLBACK_REARM_MS) {
          this.armed = true;
        }
      }
      return null;
    }

    if (timestamp - this.lastGestureTime < this.cooldownMs) return null;

    // Check gyroscope: pinch must be without large wrist rotation
    const gyroMagnitude = Math.max(Math.abs(gyroX), Math.abs(gyroY), Math.abs(gyroZ));
    if (gyroMagnitude > this.maxGyroMagnitude) {
      if (
        this.state === 'UP_DETECTED' &&
        timestamp - this.upTime > DoublePinchDetector.PINCH_TIMEOUT_MS
      ) {
        this.state = 'IDLE';
      }
      return null;
    }

    this.pinches = this.pinches.filter((t) => timestamp - t <= this.windowMs);

    const xyMag = Math.sqrt(linAccX * linAccX + linAccY * linAccY);

    // Primary Z-axis spike OR tilted 3D impulse
    const isUpSpike =
      (linAccZ > this.thresholdUp && linAccZ <= this.maxAccZ && xyMag <= this.maxXYAccel) ||
      (accMag > this.thresholdUp && accMag <= this.maxAccZ && Math.abs(linAccZ) > this.thresholdUp * 0.7);

    if (this.state === 'IDLE') {
      if (isUpSpike) {
        this.state = 'UP_DETECTED';
        this.upTime = timestamp;
      }
    } else if (this.state === 'UP_DETECTED') {
      // Rebound down or settling after pulse
      const isDownSpike = linAccZ < this.thresholdDown || (timestamp - this.upTime >= 60 && accMag < 1.4);

      if (isDownSpike) {
        this.pinches.push(timestamp);
        this.state = 'IDLE';

        if (this.pinches.length >= 2) {
          const first = this.pinches[this.pinches.length - 2];
          const second = this.pinches[this.pinches.length - 1];

          if (second - first >= 100 && second - first <= this.windowMs) {
            this.lastGestureTime = timestamp;
            this.pinches = [];
            this.armed = false;
            this.stableStartTime = 0;
            return GestureType.PLAY_PAUSE;
          }
        }
      } else if (timestamp - this.upTime > DoublePinchDetector.PINCH_TIMEOUT_MS) {
        this.state = 'IDLE';
      }
    }

    return null;
  }

  public reset() {
    this.pinches = [];
    this.state = 'IDLE';
    this.upTime = 0;
    this.lastGestureTime = 0;
    this.armed = true;
    this.stableStartTime = 0;
  }
}
