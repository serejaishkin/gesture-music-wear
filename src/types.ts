export enum GestureType {
  ACTIVATE = 'ACTIVATE',
  NEXT_TRACK = 'NEXT_TRACK',
  PREVIOUS_TRACK = 'PREVIOUS_TRACK',
  PLAY_PAUSE = 'PLAY_PAUSE',
}

export interface Settings {
  angleThreshold: number; // in degrees, default 28
  pinchThreshold: number; // in m/s^2, default 3.5
  minDuration: number; // in ms, default 160
  maxDuration: number; // in ms, default 600
  gestureCooldown: number; // in ms, default 1200
  leftHand: boolean; // default false
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
}

export interface TrackInfo {
  id: string;
  title: string;
  artist: string;
  album: string;
  duration: number; // seconds
}

export type EngineStrategy = 'RawSensor (universal)' | 'Samsung SDK (Galaxy Watch 4+)' | 'Web Motion API';
