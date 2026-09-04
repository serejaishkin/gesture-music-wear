/**
 * Prevents accidental media commands by requiring a deliberate activation gesture.
 * Ported directly from GestureArmingManager.kt
 */
export class GestureArmingManager {
  private activeTimeoutMs: number;
  public isArmed = false;
  private armedAt = 0;

  constructor(activeTimeoutMs = 15000) {
    this.activeTimeoutMs = activeTimeoutMs;
  }

  public activate(now: number): boolean {
    this.isArmed = true;
    this.armedAt = now;
    return true;
  }

  public deactivate() {
    this.isArmed = false;
    this.armedAt = 0;
  }

  public update(now: number): boolean {
    if (this.isArmed && this.activeTimeoutMs > 0 && now - this.armedAt >= this.activeTimeoutMs) {
      this.deactivate();
    }
    return this.isArmed;
  }

  public touch(now: number) {
    if (this.isArmed) {
      this.armedAt = now;
    }
  }
}
