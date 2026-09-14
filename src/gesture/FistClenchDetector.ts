import { GestureType } from '../types';

/**
 * Fist clench detector (Сжатие в кулак — жест активации приложения).
 *
 * Grounded in Android Sensor API standards:
 * When the user clenches their hand into a fist, isometric muscle contraction
 * of the forearm flexor tendons generates an abrupt high-jerk impulse
 * across multiple accelerometer axes with minimal rotational gyroscope velocity.
 *
 * This acts as the deliberative ACTIVATION / ARMING gesture (guard window)
 * to eliminate false triggers from incidental daily hand movements on any
 * Wear OS device (including Samsung Galaxy Watch 4+).
 */
export class FistClenchDetector {
  private clenchThreshold: number; // m/s^2 linear acceleration magnitude threshold
  private cooldownMs: number;
  private maxGyroMagnitude: number;

  private lastGestureTime = 0;
  private prevAccMag = 0;
  private prevTimestamp = 0;
  private isSettling = false;
  private settleStartTime = 0;

  constructor(
    clenchThreshold = 3.0,
    cooldownMs = 1200,
    maxGyroMagnitude = 1.8
  ) {
    this.clenchThreshold = clenchThreshold;
    this.cooldownMs = cooldownMs;
    this.maxGyroMagnitude = maxGyroMagnitude;
  }

  public updateSettings(threshold: number, cooldownMs: number) {
    this.clenchThreshold = threshold;
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
    // 1. Check cooldown period
    if (timestamp - this.lastGestureTime < this.cooldownMs) {
      this.prevTimestamp = timestamp;
      return null;
    }

    // 2. Gyroscope quietness guard: fist clenching occurs with a stationary forearm.
    // If the user is actively rotating their wrist, reject.
    const gyroMag = Math.max(Math.abs(gyroX), Math.abs(gyroY), Math.abs(gyroZ));
    if (gyroMag > this.maxGyroMagnitude) {
      this.isSettling = false;
      this.prevTimestamp = timestamp;
      return null;
    }

    // 3. Compute acceleration magnitude and jerk (rate of change)
    const currentMag = Math.sqrt(
      linAccX * linAccX + linAccY * linAccY + linAccZ * linAccZ
    );

    const dt = this.prevTimestamp > 0 ? Math.min(0.1, (timestamp - this.prevTimestamp) / 1000) : 0.02;
    const jerk = dt > 0.001 ? Math.abs(currentMag - this.prevAccMag) / dt : 0;

    this.prevAccMag = currentMag;
    this.prevTimestamp = timestamp;

    // 4. Handle post-impulse settling state
    if (this.isSettling) {
      if (currentMag < this.clenchThreshold * 0.5 || timestamp - this.settleStartTime > 350) {
        this.isSettling = false;
      }
      return null;
    }

    // 5. Detect muscular impulse:
    // Either absolute acceleration peak meets threshold or rapid onset jerk is high.
    // Discriminate from a single pinch spike via multi-axis spread: a clench is a
    // shockwave distributed across several accelerometer axes, whereas a pinch
    // produces one dominant directional impulse along a single axis.
    const minAxis = Math.min(Math.abs(linAccX), Math.abs(linAccY), Math.abs(linAccZ));
    const isBroadSpread = minAxis >= this.clenchThreshold * 0.3;

    const isHighImpulse = currentMag >= this.clenchThreshold && isBroadSpread;
    const isSharpJerk =
      jerk >= this.clenchThreshold * 14 &&
      currentMag >= this.clenchThreshold * 0.6 &&
      isBroadSpread;

    if (isHighImpulse || isSharpJerk) {
      this.lastGestureTime = timestamp;
      this.isSettling = true;
      this.settleStartTime = timestamp;
      return GestureType.ACTIVATE;
    }

    return null;
  }

  public reset() {
    this.lastGestureTime = 0;
    this.prevAccMag = 0;
    this.prevTimestamp = 0;
    this.isSettling = false;
    this.settleStartTime = 0;
  }
}
