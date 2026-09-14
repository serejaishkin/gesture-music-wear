import { GestureType, Settings, EngineStrategy } from '../types';
import { WristRotationDetector } from './WristRotationDetector';
import { DoublePinchDetector } from './DoublePinchDetector';
import { FistClenchDetector } from './FistClenchDetector';
import { GestureTrainer, TrainingEvent } from './GestureTrainer';
import { GestureArmingManager } from './GestureArmingManager';
import { audioPlayer } from '../media/AudioPlayerService';

const DEFAULT_SETTINGS: Settings = {
  angleThreshold: 28,
  pinchThreshold: 3.2,
  fistClenchThreshold: 3.2,
  fistClenchEnabled: true,
  minDuration: 160,
  maxDuration: 600,
  gestureCooldown: 1000,
  leftHand: false,
  vibrationEnabled: true,
  vibrationIntensity: 'medium',
  vibrationDuration: 60,
  speedTolerance: 40,
};

const SETTINGS_KEY = 'gesture_music_settings';

export class GestureManager {
  private wristDetector: WristRotationDetector;
  private pinchDetector: DoublePinchDetector;
  private fistDetector: FistClenchDetector;
  private trainer: GestureTrainer;
  private armingManager: GestureArmingManager;

  public settings: Settings;
  public isRunning = false;
  public lastGesture = '';
  public lastGestureRaw: GestureType | null = null;
  public strategyName: EngineStrategy = 'RawSensor (universal)';
  public saveMessage = '';

  // Dynamic Gravity estimation for real hardware sensors
  private gravX = 0;
  private gravY = 0;
  private gravZ = 9.8;
  private lastTrainingNotifyTime = 0;

  // Cross-gesture debounce and suppression window
  private lastGestureTimestamp = 0;
  private static readonly CROSS_GESTURE_DEBOUNCE_MS = 300;

  // Training state
  public isTrainingMode = false;
  public trainingGestureType: GestureType | null = null;
  public trainingProgress = 0;
  public trainingRepetitions = 0;
  public trainingDone = false;
  public trainingSuccess = false;

  private subscribers: Array<() => void> = [];
  private motionListener: ((e: DeviceMotionEvent) => void) | null = null;

  public get isArmed(): boolean {
    return this.armingManager.isArmed;
  }

  constructor() {
    this.settings = this.loadSettings();
    this.wristDetector = new WristRotationDetector(
      this.settings.angleThreshold,
      2.0,
      this.settings.minDuration,
      this.settings.maxDuration,
      this.settings.gestureCooldown,
      400,
      0.35,
      180,
      24,
      this.settings.leftHand
    );
    this.pinchDetector = new DoublePinchDetector(
      this.settings.pinchThreshold,
      -(this.settings.pinchThreshold * 0.6),
      14.0,
      900,
      this.settings.gestureCooldown
    );
    this.fistDetector = new FistClenchDetector(
      this.settings.fistClenchThreshold,
      this.settings.gestureCooldown
    );
    this.trainer = new GestureTrainer();
    this.armingManager = new GestureArmingManager(15000);
  }

  public subscribe(fn: () => void): () => void {
    this.subscribers.push(fn);
    return () => {
      this.subscribers = this.subscribers.filter((s) => s !== fn);
    };
  }

  private notify() {
    for (const sub of this.subscribers) {
      sub();
    }
  }

  private loadSettings(): Settings {
    try {
      const saved = localStorage.getItem(SETTINGS_KEY);
      if (saved) {
        return { ...DEFAULT_SETTINGS, ...JSON.parse(saved) };
      }
    } catch {
      // ignore
    }
    return { ...DEFAULT_SETTINGS };
  }

  public saveSettings() {
    try {
      localStorage.setItem(SETTINGS_KEY, JSON.stringify(this.settings));
      this.saveMessage = 'Сохранено';
      this.notify();
      setTimeout(() => {
        this.saveMessage = '';
        this.notify();
      }, 1500);
    } catch {
      // ignore
    }
  }

  public restoreDefaults() {
    this.settings = { ...DEFAULT_SETTINGS };
    this.updateDetectors();
    this.saveSettings();
  }

  public updateAngleThreshold(val: number) {
    this.settings.angleThreshold = val;
    this.updateDetectors();
    this.notify();
  }

  public updatePinchThreshold(val: number) {
    this.settings.pinchThreshold = val;
    this.updateDetectors();
    this.notify();
  }

  public updateFistClenchThreshold(val: number) {
    this.settings.fistClenchThreshold = val;
    this.updateDetectors();
    this.notify();
  }

  public updateFistClenchEnabled(val: boolean) {
    this.settings.fistClenchEnabled = val;
    this.updateDetectors();
    this.notify();
  }

  public updateMinDuration(val: number) {
    this.settings.minDuration = val;
    this.updateDetectors();
    this.notify();
  }

  public updateMaxDuration(val: number) {
    this.settings.maxDuration = val;
    this.updateDetectors();
    this.notify();
  }

  public updateGestureCooldown(val: number) {
    this.settings.gestureCooldown = val;
    this.updateDetectors();
    this.notify();
  }

  public updateLeftHand(val: boolean) {
    this.settings.leftHand = val;
    this.updateDetectors();
    this.notify();
  }

  public updateVibrationEnabled(val: boolean) {
    this.settings.vibrationEnabled = val;
    this.notify();
  }

  public updateVibrationIntensity(val: 'light' | 'medium' | 'strong') {
    this.settings.vibrationIntensity = val;
    if (val === 'light') this.settings.vibrationDuration = 35;
    else if (val === 'medium') this.settings.vibrationDuration = 65;
    else if (val === 'strong') this.settings.vibrationDuration = 110;
    this.triggerFeedback();
    this.notify();
  }

  public updateVibrationDuration(val: number) {
    this.settings.vibrationDuration = val;
    this.triggerFeedback();
    this.notify();
  }

  public updateSpeedTolerance(val: number) {
    this.settings.speedTolerance = val;
    this.trainer.setSpeedTolerance(val);
    this.notify();
  }

  public triggerFeedback(durationOverride?: number) {
    if (!this.settings.vibrationEnabled) return;
    const dur = durationOverride ?? this.settings.vibrationDuration;
    audioPlayer.triggerHaptic(dur);
  }

  public getLastRepetitionStats() {
    return this.trainer.lastRepetitionStats;
  }

  public setStrategyName(name: EngineStrategy) {
    this.strategyName = name;
    this.notify();
  }

  private updateDetectors() {
    this.trainer.setSpeedTolerance(this.settings.speedTolerance);
    this.wristDetector.updateSettings(
      this.settings.angleThreshold,
      this.settings.gestureCooldown,
      this.settings.leftHand,
      this.settings.minDuration,
      this.settings.maxDuration
    );
    this.pinchDetector.updateSettings(
      this.settings.pinchThreshold,
      this.settings.gestureCooldown
    );
    this.fistDetector.updateSettings(
      this.settings.fistClenchThreshold,
      this.settings.gestureCooldown
    );
  }

  public startService() {
    if (this.isRunning) return;
    this.isRunning = true;
    this.updateDetectors();
    this.setupHardwareSensors();
    this.notify();
  }

  public stopService() {
    if (!this.isRunning) return;
    this.isRunning = false;
    this.removeHardwareSensors();
    this.wristDetector.reset();
    this.pinchDetector.reset();
    this.fistDetector.reset();
    this.armingManager.deactivate();
    this.notify();
  }

  private setupHardwareSensors() {
    // 1. Check for native Android bridge sensors (Wear OS Galaxy Watch 4+)
    if (typeof window !== 'undefined') {
      const w = window as any;
      w.onAndroidSensorData = (gx: number, gy: number, gz: number, ax: number, ay: number, az: number) => {
        if (this.isRunning) {
          this.processSample(performance.now(), gx, gy, gz, ax, ay, az);
        }
      };
      if (w.AndroidBridge && typeof w.AndroidBridge.startSensors === 'function') {
        try {
          w.AndroidBridge.startSensors();
        } catch {
          // ignore
        }
      }
    }

    if (typeof window === 'undefined' || !('DeviceMotionEvent' in window)) return;

    this.motionListener = (event: DeviceMotionEvent) => {
      const rot = event.rotationRate;
      const rawAcc = event.accelerationIncludingGravity || event.acceleration;

      if (!rot || !rawAcc) return;

      // Official mapping:
      // W3C DeviceMotionEvent beta -> rotation around X (pitch)
      // W3C DeviceMotionEvent gamma -> rotation around Y (roll)
      // W3C DeviceMotionEvent alpha -> rotation around Z (yaw)
      // Convert degrees/sec to radians/sec for Android IMU standard
      const gx = (((rot.beta !== null ? rot.beta : rot.alpha) || 0) * Math.PI) / 180;
      const gy = (((rot.gamma !== null ? rot.gamma : rot.beta) || 0) * Math.PI) / 180;
      const gz = (((rot.alpha !== null ? rot.alpha : rot.gamma) || 0) * Math.PI) / 180;

      let ax = 0;
      let ay = 0;
      let az = 0;

      // If true linear acceleration is provided by device:
      if (
        event.acceleration &&
        (event.acceleration.x !== null || event.acceleration.y !== null || event.acceleration.z !== null)
      ) {
        ax = event.acceleration.x || 0;
        ay = event.acceleration.y || 0;
        az = event.acceleration.z || 0;
      } else {
        // High-pass filter to isolate linear acceleration from 1G gravity
        const rx = rawAcc.x || 0;
        const ry = rawAcc.y || 0;
        const rz = rawAcc.z || 0;

        const alpha = 0.85;
        this.gravX = alpha * this.gravX + (1 - alpha) * rx;
        this.gravY = alpha * this.gravY + (1 - alpha) * ry;
        this.gravZ = alpha * this.gravZ + (1 - alpha) * rz;

        ax = rx - this.gravX;
        ay = ry - this.gravY;
        az = rz - this.gravZ;
      }

      this.processSample(performance.now(), gx, gy, gz, ax, ay, az);
    };

    window.addEventListener('devicemotion', this.motionListener);
  }

  private removeHardwareSensors() {
    if (typeof window !== 'undefined') {
      const w = window as any;
      if (w.AndroidBridge && typeof w.AndroidBridge.stopSensors === 'function') {
        try {
          w.AndroidBridge.stopSensors();
        } catch {
          // ignore
        }
      }
    }
    if (this.motionListener) {
      window.removeEventListener('devicemotion', this.motionListener);
      this.motionListener = null;
    }
  }

  public processSample(
    timestamp: number,
    gx: number,
    gy: number,
    gz: number,
    ax: number,
    ay: number,
    az: number
  ) {
    if (this.isTrainingMode) {
      const event = this.trainer.addSample(gx, gy, gz, ax, ay, az);
      this.trainingProgress = this.trainer.getRecordingProgress();
      this.trainingRepetitions = this.trainer.getTrainingRepetitionCount();

      if (event === TrainingEvent.REPETITION_ACCEPTED) {
        this.triggerFeedback(this.settings.vibrationDuration);
        if (this.trainingRepetitions >= this.trainer.getRequiredRepetitions()) {
          this.finishTrainingSession();
        }
        this.notify();
      } else if (timestamp - this.lastTrainingNotifyTime > 50) {
        this.lastTrainingNotifyTime = timestamp;
        this.notify();
      }
      return;
    }

    if (!this.isRunning) return;

    // Update the arming window expiration
    this.armingManager.update(timestamp);

    // Cross-gesture lockout check
    if (timestamp - this.lastGestureTimestamp < GestureManager.CROSS_GESTURE_DEBOUNCE_MS) {
      return;
    }

    // 1. Check custom trained DTW gestures first
    const learned = this.trainer.recognize(gx, gy, gz, ax, ay, az);
    if (learned === GestureType.ACTIVATE) {
      this.armingManager.activate(timestamp);
      this.lastGestureTimestamp = timestamp;
      this.triggerFeedback(this.settings.vibrationDuration * 2);
      this.dispatchGesture(learned);
      return;
    }

    const effectiveLearned =
      this.trainer.hasTrainedGesture(GestureType.ACTIVATE) && !this.armingManager.isArmed
        ? null
        : learned;

    let gesture: GestureType | null = effectiveLearned;

    // 2. Fall back to heuristic detectors
    if (!gesture) {
      // A. Check Fist Clench for deliberate ACTIVATION/ARMING
      const fist = this.settings.fistClenchEnabled
        ? this.fistDetector.process(timestamp, gx, gy, gz, ax, ay, az)
        : null;

      if (fist === GestureType.ACTIVATE) {
        gesture = fist;
      } else {
        // B. Media controls (Wrist rotation & Double pinch) are guarded by arming state
        const canExecuteMedia = !this.settings.fistClenchEnabled || this.armingManager.isArmed;
        if (canExecuteMedia) {
          const wrist = this.wristDetector.process(timestamp, gx, gy, gz, ax, ay, az);
          const pinch = this.pinchDetector.process(timestamp, gx, gy, gz, ax, ay, az);
          gesture = wrist || pinch;
        }
      }
    }

    if (gesture) {
      this.handleDetectedGesture(gesture, timestamp);
    }
  }

  private handleDetectedGesture(gesture: GestureType, timestamp: number) {
    this.lastGestureTimestamp = timestamp;

    if (gesture === GestureType.ACTIVATE) {
      this.armingManager.activate(timestamp);
      this.triggerFeedback(this.settings.vibrationDuration * 2);
      this.dispatchGesture(gesture);
      return;
    }

    // Media action keeps the arming window alive
    this.armingManager.touch(timestamp);
    this.triggerFeedback(this.settings.vibrationDuration);

    // Dispatch media actions
    if (gesture === GestureType.NEXT_TRACK) {
      audioPlayer.nextTrack();
    } else if (gesture === GestureType.PREVIOUS_TRACK) {
      audioPlayer.previousTrack();
    } else if (gesture === GestureType.PLAY_PAUSE) {
      audioPlayer.playPause();
    }

    this.dispatchGesture(gesture);
  }

  private dispatchGesture(gesture: GestureType) {
    this.lastGestureRaw = gesture;
    this.lastGesture = this.formatGestureLabel(gesture);
    this.notify();
  }

  public formatGestureLabel(gesture: GestureType): string {
    switch (gesture) {
      case GestureType.NEXT_TRACK:
        return '➡️ Следующий трек';
      case GestureType.PREVIOUS_TRACK:
        return '⬅️ Предыдущий трек';
      case GestureType.PLAY_PAUSE:
        return '⏯️ Play / Pause';
      case GestureType.ACTIVATE:
        return '✊ Активация (кулак)';
      default:
        return gesture;
    }
  }

  // Training methods
  public startTraining(gestureType: GestureType) {
    this.trainingGestureType = gestureType;
    this.isTrainingMode = true;
    this.trainingProgress = 0;
    this.trainingRepetitions = 0;
    this.trainingDone = false;
    this.trainingSuccess = false;
    this.trainer.startTraining();
    this.triggerFeedback(this.settings.vibrationDuration);
    this.notify();
  }

  private finishTrainingSession() {
    const type = this.trainingGestureType;
    if (!type) return;

    this.isTrainingMode = false;
    this.trainingDone = true;
    const success = this.trainer.saveTraining(type);
    this.trainingSuccess = success;
    if (success) {
      this.triggerFeedback(this.settings.vibrationDuration * 2);
    }
    this.notify();
  }

  public stopTraining() {
    this.isTrainingMode = false;
    this.trainingGestureType = null;
    this.trainer.cancelTraining();
    this.trainingDone = true;
    this.trainingSuccess = false;
    this.notify();
  }

  public clearTraining() {
    this.trainer.clearAll();
    this.armingManager.deactivate();
    this.trainingGestureType = null;
    this.isTrainingMode = false;
    this.trainingDone = true;
    this.trainingSuccess = true;
    this.trainingProgress = 0;
    this.trainingRepetitions = 0;
    this.notify();
  }

  public getTrainer(): GestureTrainer {
    return this.trainer;
  }
}

export const gestureManager = new GestureManager();
