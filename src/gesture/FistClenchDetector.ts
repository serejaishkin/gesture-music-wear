import { GestureType } from '../types';

/**
 * Fist clench detector (Сжатие в кулак).
 *
 * When the user clenches their hand into a fist, the forearm flexor tendons
 * flex abruptly, generating a micro-shockwave / transient acceleration spike
 * across the watch casing with minimal rotational gyroscope velocity.
 *
 * Supports single firm clench or double clench to trigger PLAY_PAUSE.
 */
export class FistClenchDetector {
  private clenchThreshold: number; // m/s^2 impulse threshold (default 3.2)
  private cooldownMs: number;
  private maxGyroMagnitude: number;
  private windowMs: number;

  private lastGestureTime = 0;
  private clenches: number[] = [];
  private prevAccMag = 0;
  private prevTimestamp = 0;
  private isPrimed = false;
  private primeTime = 0;

  constructor(
    clenchThreshold = 3.2,
    cooldownMs = 1200,
    maxGyroMagnitude = 1.8,
    windowMs = 800
  ) {
    this.clenchThreshold = clenchThreshold;
    this.cooldownMs = cooldownMs;
    this.maxGyroMagnitude = maxGyroMagnitude;
    this.windowMs = windowMs;
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
    if (timestamp - this.lastGestureTime < this.cooldownMs) return null;

    // Gyroscope check: fist clenching happens with a stationary arm/wrist.
    // If the user is rotating their wrist, ignore.
    const gyroMag = Math.max(Math.abs(gyroX), Math.abs(gyroY), Math.abs(gyroZ));
    if (gyroMag > this.maxGyroMagnitude) {
      this.isPrimed = false;
      return null;
    }

    const currentMag = Math.sqrt(
      linAccX * linAccX + linAccY * linAccY + linAccZ * linAccZ
    );

    const dt = this.prevTimestamp > 0 ? (timestamp - this.prevTimestamp) / 1000 : 0.02;
    const jerk = dt > 0 ? Math.abs(currentMag - this.prevAccMag) / dt : 0;

    this.prevAccMag = currentMag;
    this.prevTimestamp = timestamp;

    // Remove expired clenches
    this.clenches = this.clenches.filter((t) => timestamp - t <= this.windowMs);

    // Sudden muscle impulse (acc magnitude spike OR high jerk with low rotation)
    const isImpulse = currentMag >= this.clenchThreshold || jerk >= this.clenchThreshold * 18;

    if (isImpulse) {
      if (!this.isPrimed || timestamp - this.primeTime > 80) {
        this.isPrimed = true;
        this.primeTime = timestamp;
        this.clenches.push(timestamp);

        // Double clench within window (e.g. Samsung Wear OS "Clench Fist Twice")
        if (this.clenches.length >= 2) {
          const first = this.clenches[this.clenches.length - 2];
          const second = this.clenches[this.clenches.length - 1];

          if (second - first >= 120 && second - first <= this.windowMs) {
            this.lastGestureTime = timestamp;
            this.clenches = [];
            this.isPrimed = false;
            return GestureType.PLAY_PAUSE;
          }
        }
      }
    }

    return null;
  }

  public reset() {
    this.clenches = [];
    this.isPrimed = false;
    this.primeTime = 0;
    this.lastGestureTime = 0;
    this.prevAccMag = 0;
    this.prevTimestamp = 0;
  }
}
