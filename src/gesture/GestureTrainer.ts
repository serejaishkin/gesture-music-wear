import { GestureType, TrainedGesture } from '../types';

export interface Sample {
  gx: number;
  gy: number;
  gz: number;
  ax: number;
  ay: number;
  az: number;
}

export enum TrainingEvent {
  NONE = 'NONE',
  STARTED = 'STARTED',
  REPETITION_ACCEPTED = 'REPETITION_ACCEPTED',
}

/**
 * Five-repetition gesture training with automatic motion start/end detection
 * and Dynamic Time Warping (DTW) matching.
 * Ported directly from GestureTrainer.kt
 */
export class GestureTrainer {
  private static readonly SAMPLE_SIZE = 90;
  private static readonly MIN_SAMPLES = 18;
  private static readonly TRAINING_REPETITIONS = 5;
  private static readonly DTW_THRESHOLD = 1.15;
  private static readonly TRAINING_VARIANCE_THRESHOLD = 1.4;
  private static readonly MIN_MOTION_ENERGY = 0.18;
  private static readonly START_MOTION_THRESHOLD = 0.45;
  private static readonly END_MOTION_THRESHOLD = 0.18;
  private static readonly QUIET_SAMPLES_TO_END = 8;
  private static readonly RECOGNITION_COOLDOWN_MS = 1200;
  private static readonly EVALUATION_INTERVAL_MS = 100;
  private static readonly STORAGE_KEY = 'gesture_music_trained_gestures';

  private trainingSession = false;
  private isRecording = false;
  private currentRecording: Sample[] = [];
  private repetitions: Sample[][] = [];
  private quietSamples = 0;
  private previousSample: Sample | null = null;
  private trainedGestures: Map<GestureType, TrainedGesture> = new Map();
  private lastRecognitionTime = 0;
  private lastEvaluationTime = 0;

  constructor() {
    this.loadGestures();
  }

  public startTraining() {
    this.trainingSession = true;
    this.isRecording = false;
    this.currentRecording = [];
    this.repetitions = [];
    this.quietSamples = 0;
    this.previousSample = null;
  }

  public startRecording() {
    if (!this.trainingSession) this.startTraining();
  }

  public addSample(
    gx: number,
    gy: number,
    gz: number,
    ax: number,
    ay: number,
    az: number
  ): TrainingEvent {
    if (!this.trainingSession || this.repetitions.length >= GestureTrainer.TRAINING_REPETITIONS) {
      return TrainingEvent.NONE;
    }

    const sample: Sample = { gx, gy, gz, ax, ay, az };
    const delta = this.previousSample ? this.distance(this.previousSample, sample) : 0;
    this.previousSample = sample;

    if (!this.isRecording) {
      if (delta >= GestureTrainer.START_MOTION_THRESHOLD) {
        this.isRecording = true;
        this.currentRecording = [sample];
        this.quietSamples = 0;
        return TrainingEvent.STARTED;
      }
      return TrainingEvent.NONE;
    }

    if (this.currentRecording.length < GestureTrainer.SAMPLE_SIZE) {
      this.currentRecording.push(sample);
    }

    if (delta < GestureTrainer.END_MOTION_THRESHOLD) {
      this.quietSamples++;
    } else {
      this.quietSamples = 0;
    }

    if (
      this.quietSamples >= GestureTrainer.QUIET_SAMPLES_TO_END ||
      this.currentRecording.length >= GestureTrainer.SAMPLE_SIZE
    ) {
      return this.finishAutoRepetition();
    }

    return TrainingEvent.NONE;
  }

  private finishAutoRepetition(): TrainingEvent {
    const sample = [...this.currentRecording];
    this.currentRecording = [];
    this.isRecording = false;
    this.quietSamples = 0;

    if (
      sample.length < GestureTrainer.MIN_SAMPLES ||
      this.motionEnergy(sample) < GestureTrainer.MIN_MOTION_ENERGY
    ) {
      return TrainingEvent.NONE;
    }

    this.repetitions.push(this.normalize(sample));
    return TrainingEvent.REPETITION_ACCEPTED;
  }

  public finishRepetition(): boolean {
    return this.finishAutoRepetition() === TrainingEvent.REPETITION_ACCEPTED;
  }

  public saveTraining(gestureType: GestureType): boolean {
    if (this.repetitions.length < GestureTrainer.TRAINING_REPETITIONS) return false;
    const template = this.chooseTemplate();
    if (!template) {
      return false;
    }

    this.trainedGestures.set(gestureType, {
      gestureType,
      samples: template,
    });
    this.saveGestures();
    this.trainingSession = false;
    this.isRecording = false;
    this.currentRecording = [];
    this.repetitions = [];
    this.previousSample = null;
    return true;
  }

  public cancelTraining() {
    this.trainingSession = false;
    this.isRecording = false;
    this.currentRecording = [];
    this.repetitions = [];
    this.previousSample = null;
  }

  public getTrainingRepetitionCount(): number {
    return this.repetitions.length;
  }

  public getRequiredRepetitions(): number {
    return GestureTrainer.TRAINING_REPETITIONS;
  }

  public getRecordingProgress(): number {
    if (!this.isRecording) return 0;
    return Math.min(100, Math.max(0, Math.round((this.currentRecording.length * 100) / GestureTrainer.SAMPLE_SIZE)));
  }

  public isCurrentlyRecording(): boolean {
    return this.isRecording;
  }

  public recognize(
    gx: number,
    gy: number,
    gz: number,
    ax: number,
    ay: number,
    az: number
  ): GestureType | null {
    if (this.trainingSession || this.isRecording || this.trainedGestures.size === 0) return null;
    const now = performance.now();
    if (now - this.lastRecognitionTime < GestureTrainer.RECOGNITION_COOLDOWN_MS) return null;

    this.currentRecording.push({ gx, gy, gz, ax, ay, az });
    if (this.currentRecording.length > GestureTrainer.SAMPLE_SIZE) {
      this.currentRecording.shift();
    }

    if (now - this.lastEvaluationTime < GestureTrainer.EVALUATION_INTERVAL_MS) return null;
    this.lastEvaluationTime = now;

    if (
      this.currentRecording.length < GestureTrainer.MIN_SAMPLES ||
      this.motionEnergy(this.currentRecording) < GestureTrainer.MIN_MOTION_ENERGY
    ) {
      return null;
    }

    const candidate = this.normalize(this.currentRecording);
    let bestMatch: GestureType | null = null;
    let bestScore = Infinity;

    for (const [type, trained] of this.trainedGestures.entries()) {
      const score = this.dtwDistance(candidate, trained.samples);
      if (score < bestScore) {
        bestScore = score;
        bestMatch = type;
      }
    }

    if (bestMatch !== null && bestScore <= GestureTrainer.DTW_THRESHOLD) {
      this.currentRecording = [];
      this.lastRecognitionTime = performance.now();
      return bestMatch;
    }

    if (this.currentRecording.length >= GestureTrainer.SAMPLE_SIZE) {
      this.currentRecording.splice(0, Math.floor(GestureTrainer.SAMPLE_SIZE / 2));
    }

    return null;
  }

  public clearAll() {
    this.trainedGestures.clear();
    this.currentRecording = [];
    this.repetitions = [];
    this.trainingSession = false;
    this.isRecording = false;
    this.previousSample = null;
    try {
      localStorage.removeItem(GestureTrainer.STORAGE_KEY);
    } catch {
      // ignore
    }
  }

  public hasTrainedGesture(type: GestureType): boolean {
    return this.trainedGestures.has(type);
  }

  public getTrainedGestures(): GestureType[] {
    return Array.from(this.trainedGestures.keys());
  }

  private chooseTemplate(): Sample[] | null {
    if (this.repetitions.length < GestureTrainer.TRAINING_REPETITIONS) return null;
    let best = this.repetitions[0];
    let bestScore = Infinity;

    for (const candidate of this.repetitions) {
      let sum = 0;
      for (const rep of this.repetitions) {
        sum += this.dtwDistance(candidate, rep);
      }
      const score = sum / this.repetitions.length;
      if (score < bestScore) {
        bestScore = score;
        best = candidate;
      }
    }

    return bestScore <= GestureTrainer.TRAINING_VARIANCE_THRESHOLD ? best : null;
  }

  private normalize(input: Sample[]): Sample[] {
    if (input.length === 0) return [];
    const len = input.length;

    const mean = (fn: (s: Sample) => number) => input.reduce((acc, s) => acc + fn(s), 0) / len;

    const m = [
      mean((s) => s.gx),
      mean((s) => s.gy),
      mean((s) => s.gz),
      mean((s) => s.ax),
      mean((s) => s.ay),
      mean((s) => s.az),
    ];

    const rms = (channelIdx: number, fn: (s: Sample) => number) => {
      let sum = 0;
      for (const s of input) {
        const d = fn(s) - m[channelIdx];
        sum += d * d;
      }
      return Math.max(0.001, Math.sqrt(sum / len));
    };

    const scale = [
      rms(0, (s) => s.gx),
      rms(1, (s) => s.gy),
      rms(2, (s) => s.gz),
      rms(3, (s) => s.ax),
      rms(4, (s) => s.ay),
      rms(5, (s) => s.az),
    ];

    return input.map((s) => ({
      gx: (s.gx - m[0]) / scale[0],
      gy: (s.gy - m[1]) / scale[1],
      gz: (s.gz - m[2]) / scale[2],
      ax: (s.ax - m[3]) / scale[3],
      ay: (s.ay - m[4]) / scale[4],
      az: (s.az - m[5]) / scale[5],
    }));
  }

  private distance(a: Sample, b: Sample): number {
    const dgx = b.gx - a.gx;
    const dgy = b.gy - a.gy;
    const dgz = b.gz - a.gz;
    const dax = b.ax - a.ax;
    const day = b.ay - a.ay;
    const daz = b.az - a.az;
    return Math.sqrt(dgx * dgx + dgy * dgy + dgz * dgz + dax * dax + day * day + daz * daz);
  }

  private motionEnergy(s: Sample[]): number {
    if (s.length < 2) return 0;
    let total = 0;
    for (let i = 1; i < s.length; i++) {
      total += this.distance(s[i - 1], s[i]);
    }
    return total / (s.length - 1);
  }

  private dtwDistance(a: Sample[], b: Sample[]): number {
    const n = a.length;
    const m = b.length;
    const dp: number[][] = Array.from({ length: n + 1 }, () =>
      Array.from({ length: m + 1 }, () => Infinity)
    );
    dp[0][0] = 0;

    for (let i = 1; i <= n; i++) {
      for (let j = 1; j <= m; j++) {
        const cost = this.distance(a[i - 1], b[j - 1]);
        dp[i][j] = cost + Math.min(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]);
      }
    }

    return dp[n][m] / (n + m);
  }

  private saveGestures() {
    try {
      const arr = Array.from(this.trainedGestures.values());
      localStorage.setItem(GestureTrainer.STORAGE_KEY, JSON.stringify(arr));
    } catch {
      // ignore
    }
  }

  private loadGestures() {
    try {
      const data = localStorage.getItem(GestureTrainer.STORAGE_KEY);
      if (!data) return;
      const list = JSON.parse(data) as TrainedGesture[];
      for (const item of list) {
        if (item && item.gestureType && Array.isArray(item.samples)) {
          this.trainedGestures.set(item.gestureType, item);
        }
      }
    } catch {
      // ignore
    }
  }
}
