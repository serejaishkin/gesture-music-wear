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
  private gravity = { x: 0, y: 0, z: 9.8 };

  // Training state
  public isTrainingMode = false;
  public trainingGestureType: GestureType | null = null;
  public trainingProgress = 0;
  public trainingRepetitions = 0;
  public trainingDone = false;
  public trainingSuccess = false;

  private lastGestureTime = 0;
  private subscribers: Array<() => void> = [];
  private motionListener: ((e: DeviceMotionEvent) => void) | null = null;

  constructor() {
    this.settings = this.loadSettings();
    this.wristDetector = new WristRotationDetector(
      this.settings.angleThreshold,
      2.2,
      this.settings.minDuration,
      this.settings.maxDuration,
      this.settings.gestureCooldown,
      400,
      0.35,
      180,
      20,
      this.settings.leftHand
    );
    this.pinchDetector = new DoublePinchDetector(
      this.settings.pinchThreshold,
      -(this.settings.pinchThreshold * 0.6),
      12.0,
      5.5,
      900,
      this.settings.gestureCooldown
    );
    this.fistDetector = new FistClenchDetector(
      this.settings.fistClenchThreshold,
      this.settings.gestureCooldown
    );
    this.trainer = new GestureTrainer();
    this.armingManager = new GestureArmingManager();
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

  public setStrategyName(name: EngineStrategy) {
    this.strategyName = name;
    this.notify();
  }

  private updateDetectors() {
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
    if (typeof window === 'undefined' || !('DeviceMotionEvent' in window)) return;

    this.motionListener = (event: DeviceMotionEvent) => {
      const rot = event.rotationRate;
      const rawAcc = event.accelerationIncludingGravity || event.acceleration;

      if (!rot || !rawAcc) return;

      // Web rotation rate is degrees/s; convert to radians/s for IMU matching
      const gx = ((rot.alpha || 0) * Math.PI) / 180;
      const gy = ((rot.beta || 0) * Math.PI) / 180;
      const gz = ((rot.gamma || 0) * Math.PI) / 180;

      let ax = 0;
      let ay = 0;
      let az = 0;

      // If true linear acceleration is given by browser:
      if (
        event.acceleration &&
        (event.acceleration.x !== null || event.acceleration.y !== null || event.acceleration.z !== null)
      ) {
        ax = event.acceleration.x || 0;
        ay = event.acceleration.y || 0;
        az = event.acceleration.z || 0;
      } else {
        // High-pass filter to isolate linear acceleration from constant 1G gravity
        const rx = rawAcc.x || 0;
        const ry = rawAcc.y || 0;
        const rz = rawAcc.z || 0;

        const alpha = 0.85;
        this.gravity.x = alpha * this.gravity.x + (1 - alpha) * rx;
        this.gravity.y = alpha * this.gravity.y + (1 - alpha) * ry;
        this.gravity.z = alpha * this.gravity.z + (1 - alpha) * rz;

        ax = rx - this.gravity.x;
        ay = ry - this.gravity.y;
        az = rz - this.gravity.z;
      }

      this.processSample(performance.now(), gx, gy, gz, ax, ay, az);
    };

    window.addEventListener('devicemotion', this.motionListener);
  }

  private removeHardwareSensors() {
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
        audioPlayer.triggerHaptic(45);
        if (this.trainingRepetitions >= this.trainer.getRequiredRepetitions()) {
          this.finishTrainingSession();
        }
      }
      this.notify();
      return;
    }

    if (!this.isRunning) return;

    this.armingManager.update(timestamp);

    // 1. Check learned DTW gestures first
    const learned = this.trainer.recognize(gx, gy, gz, ax, ay, az);
    if (learned === GestureType.ACTIVATE) {
      this.armingManager.activate(timestamp);
      this.lastGestureTime = timestamp;
      audioPlayer.triggerHaptic(120);
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
      const wrist = this.wristDetector.process(timestamp, gx, gy, gz, ax, ay, az);
      const pinch = this.pinchDetector.process(timestamp, gx, gy, gz, ax, ay, az);
      const fist = this.settings.fistClenchEnabled
        ? this.fistDetector.process(timestamp, gx, gy, gz, ax, ay, az)
        : null;

      gesture = wrist || pinch || fist;
    }

    if (gesture) {
      this.handleDetectedGesture(gesture, timestamp);
    }
  }

  private handleDetectedGesture(gesture: GestureType, timestamp: number) {
    if (gesture === GestureType.ACTIVATE) {
      this.armingManager.activate(timestamp);
      this.lastGestureTime = timestamp;
      audioPlayer.triggerHaptic(120);
      this.dispatchGesture(gesture);
      return;
    }

    this.armingManager.touch(timestamp);
    this.lastGestureTime = timestamp;
    audioPlayer.triggerHaptic(50);

    // Dispatch media controls
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
        return '🔓 Активация';
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
    audioPlayer.triggerHaptic(45);
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
      audioPlayer.triggerHaptic(120);
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
