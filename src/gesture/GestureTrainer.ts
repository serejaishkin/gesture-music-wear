import { GestureType, TrainedGesture } from '../types';

export interface Sample {
  gx: number;
  gy: number;
  gz: number;
  ax: number;
  ay: number;
  az: number;
}

export interface RepetitionStats {
  durationMs: number;
  averageSpeed: number;
  tempo: 'fast' | 'normal' | 'slow';
  tempoLabel: string;
}

export enum TrainingEvent {
  NONE = 'NONE',
  STARTED = 'STARTED',
  REPETITION_ACCEPTED = 'REPETITION_ACCEPTED',
  REPETITION_TOO_FAST = 'REPETITION_TOO_FAST',
  REPETITION_TOO_SLOW = 'REPETITION_TOO_SLOW',
}

/**
 * Five-repetition gesture training with automatic motion start/end detection,
 * execution speed/duration profiling, and Dynamic Time Warping (DTW) matching.
 * Ported directly from GestureTrainer.kt with speed awareness.
 */
export class GestureTrainer {
  private static readonly SAMPLE_SIZE = 90;
  private static readonly MIN_SAMPLES = 14;
  private static readonly TRAINING_REPETITIONS = 5;
  private static readonly DTW_THRESHOLD = 1.20;
  private static readonly TRAINING_VARIANCE_THRESHOLD = 1.45;
  private static readonly MIN_MOTION_ENERGY = 0.16;
  private static readonly START_MOTION_THRESHOLD = 0.42;
  private static readonly END_MOTION_THRESHOLD = 0.16;
  private static readonly QUIET_SAMPLES_TO_END = 8;
  private static readonly RECOGNITION_COOLDOWN_MS = 1000;
  private static readonly EVALUATION_INTERVAL_MS = 80;
  private static readonly STORAGE_KEY = 'gesture_music_trained_gestures';

  private trainingSession = false;
  private isRecording = false;
  private recordingStartTime = 0;
  private currentRecording: Sample[] = [];
  private repetitions: Sample[][] = [];
  private repetitionDurations: number[] = [];
  private repetitionSpeeds: number[] = [];
  private quietSamples = 0;
  private previousSample: Sample | null = null;
  private trainedGestures: Map<GestureType, TrainedGesture> = new Map();
  private lastRecognitionTime = 0;
  private lastEvaluationTime = 0;
  private candidateStartTime = 0;

  public lastRepetitionStats: RepetitionStats | null = null;
  public speedTolerancePercent = 40;

  constructor() {
    this.loadGestures();
  }

  public setSpeedTolerance(tolerancePercent: number) {
    this.speedTolerancePercent = Math.max(15, Math.min(80, tolerancePercent));
  }

  public startTraining() {
    this.trainingSession = true;
    this.isRecording = false;
    this.recordingStartTime = 0;
    this.currentRecording = [];
    this.repetitions = [];
    this.repetitionDurations = [];
    this.repetitionSpeeds = [];
    this.quietSamples = 0;
    this.previousSample = null;
    this.lastRepetitionStats = null;
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
        this.recordingStartTime = performance.now();
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
    const duration = Math.max(120, Math.round(performance.now() - this.recordingStartTime));
    this.currentRecording = [];
    this.isRecording = false;
    this.quietSamples = 0;

    const energy = this.motionEnergy(sample);
    if (sample.length < GestureTrainer.MIN_SAMPLES || energy < GestureTrainer.MIN_MOTION_ENERGY) {
      return TrainingEvent.NONE;
    }

    // Speed / Tempo categorization
    let tempo: 'fast' | 'normal' | 'slow' = 'normal';
    let tempoLabel = `🎯 ${duration} мс (Оптимально)`;
    if (duration < 220) {
      tempo = 'fast';
      tempoLabel = `⚡ ${duration} мс (Быстрый темп)`;
    } else if (duration > 650) {
      tempo = 'slow';
      tempoLabel = `🐢 ${duration} мс (Плавный темп)`;
    }

    this.lastRepetitionStats = {
      durationMs: duration,
      averageSpeed: Math.round(energy * 100) / 100,
      tempo,
      tempoLabel,
    };

    this.repetitionDurations.push(duration);
    this.repetitionSpeeds.push(energy);
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

    const avgDuration = Math.round(
      this.repetitionDurations.reduce((a, b) => a + b, 0) / this.repetitionDurations.length
    );
    const avgSpeed =
      Math.round(
        (this.repetitionSpeeds.reduce((a, b) => a + b, 0) / this.repetitionSpeeds.length) * 100
      ) / 100;

    const tolerance = this.speedTolerancePercent / 100;
    const minDuration = Math.max(100, Math.round(avgDuration * (1 - tolerance)));
    const maxDuration = Math.round(avgDuration * (1 + tolerance));

    let tempoLabel = `Норма (${avgDuration} мс)`;
    if (avgDuration < 240) {
      tempoLabel = `Быстрый (${avgDuration} мс)`;
    } else if (avgDuration > 600) {
      tempoLabel = `Плавный (${avgDuration} мс)`;
    }

    this.trainedGestures.set(gestureType, {
      gestureType,
      samples: template,
      averageDurationMs: avgDuration,
      averageSpeed: avgSpeed,
      minDurationMs: minDuration,
      maxDurationMs: maxDuration,
      tempoLabel,
    });

    this.saveGestures();
    this.trainingSession = false;
    this.isRecording = false;
    this.currentRecording = [];
    this.repetitions = [];
    this.repetitionDurations = [];
    this.repetitionSpeeds = [];
    this.previousSample = null;
    return true;
  }

  public cancelTraining() {
    this.trainingSession = false;
    this.isRecording = false;
    this.currentRecording = [];
    this.repetitions = [];
    this.repetitionDurations = [];
    this.repetitionSpeeds = [];
    this.previousSample = null;
    this.lastRepetitionStats = null;
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

    if (this.currentRecording.length === 0) {
      this.candidateStartTime = now;
    }

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

    const candidateDuration = Math.max(100, now - this.candidateStartTime);
    const candidate = this.normalize(this.currentRecording);
    let bestMatch: GestureType | null = null;
    let bestScore = Infinity;

    for (const [type, trained] of this.trainedGestures.entries()) {
      // Check execution speed constraint if trained model has speed profile
      if (trained.minDurationMs && trained.maxDurationMs) {
        // Allow slight wiggle room on the bounds (±15%) before hard rejecting
        const lowerBound = trained.minDurationMs * 0.85;
        const upperBound = trained.maxDurationMs * 1.15;
        if (candidateDuration < lowerBound || candidateDuration > upperBound) {
          // Speed mismatch - gesture performed either too fast or too slow compared to training
          continue;
        }
      }

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
      this.candidateStartTime = performance.now();
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
