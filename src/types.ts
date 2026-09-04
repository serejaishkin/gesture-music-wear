export enum GestureType {
  ACTIVATE = 'ACTIVATE',
  NEXT_TRACK = 'NEXT_TRACK',
  PREVIOUS_TRACK = 'PREVIOUS_TRACK',
  PLAY_PAUSE = 'PLAY_PAUSE',
}

export interface Settings {
  angleThreshold: number; // in degrees, default 28
  pinchThreshold: number; // in m/s^2, default 3.2
  fistClenchThreshold: number; // in m/s^2, default 3.2
  fistClenchEnabled: boolean; // default true
  minDuration: number; // in ms, default 160
  maxDuration: number; // in ms, default 600
  gestureCooldown: number; // in ms, default 1000
  leftHand: boolean; // default false
  vibrationEnabled: boolean; // default true
  vibrationIntensity: 'light' | 'medium' | 'strong'; // default 'medium'
  vibrationDuration: number; // in ms: 30, 60, 100
  speedTolerance: number; // in %, default 40
}

export interface IMUSample {
  timestamp: number;
  gx: number; // Gyroscope X (rad/s or deg/s converted)
  gy: number; // Gyroscope Y
  gz: number; // Gyroscope Z
  ax: number; // Linear Acceleration X (m/s^2)
  ay: number; // Linear Acceleration Y
  az: number; // Linear Acceleration Z
}

export interface TrainedGesture {
  gestureType: GestureType;
  samples: Array<{
    gx: number;
    gy: number;
    gz: number;
    ax: number;
    ay: number;
    az: number;
  }>;
  averageDurationMs?: number; // Average execution duration in milliseconds
  averageSpeed?: number; // Average angular/linear motion speed
  minDurationMs?: number; // Speed tolerance lower bound
  maxDurationMs?: number; // Speed tolerance upper bound
  tempoLabel?: string; // e.g. "Нормальный (320 мс)"
}

export interface TrackInfo {
  id: string;
  title: string;
  artist: string;
  album: string;
  duration: number; // seconds
}

export type EngineStrategy = 'RawSensor (universal)' | 'Samsung SDK (Galaxy Watch 4+)' | 'Web Motion API';
