import { TrackInfo } from '../types';

export const SAMPLE_TRACKS: TrackInfo[] = [
  {
    id: '1',
    title: 'Midnight Motion',
    artist: 'Galaxy Beats',
    album: 'Wear OS Sessions Vol. 1',
    duration: 194,
  },
  {
    id: '2',
    title: 'Wrist Rotation Echoes',
    artist: 'Pinch & Flow',
    album: 'Sensory Horizons',
    duration: 228,
  },
  {
    id: '3',
    title: 'Cybernetic Pulse',
    artist: 'One UI Sound',
    album: 'Galaxy Groove',
    duration: 182,
  },
  {
    id: '4',
    title: 'Starlight Cadence',
    artist: 'Wearable Waves',
    album: 'Nocturne Drive',
    duration: 215,
  },
];

export class AudioPlayerService {
  private audioCtx: AudioContext | null = null;
  private isPlaying = false;
  private currentTrackIndex = 0;
  private playbackTimer: number | null = null;
  private currentTime = 0;
  private listeners: Array<() => void> = [];
  private oscNode: OscillatorNode | null = null;
  private gainNode: GainNode | null = null;

  constructor() {
    this.setupMediaSession();
  }

  public subscribe(listener: () => void): () => void {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter((l) => l !== listener);
    };
  }

  private notify() {
    for (const listener of this.listeners) {
      listener();
    }
  }

  public getTrack(): TrackInfo {
    return SAMPLE_TRACKS[this.currentTrackIndex];
  }

  public getPlaybackState(): boolean {
    return this.isPlaying;
  }

  public getCurrentTime(): number {
    return this.currentTime;
  }

  public playPause() {
    if (this.isPlaying) {
      this.pause();
    } else {
      this.play();
    }
  }

  public play() {
    if (this.isPlaying) return;
    this.initAudioContext();
    this.isPlaying = true;
    this.startSynthPlayback();
    this.startTimer();
    this.updateMediaSession();
    this.notify();
  }

  public pause() {
    if (!this.isPlaying) return;
    this.isPlaying = false;
    this.stopSynthPlayback();
    this.stopTimer();
    this.updateMediaSession();
    this.notify();
  }

  public nextTrack() {
    this.currentTrackIndex = (this.currentTrackIndex + 1) % SAMPLE_TRACKS.length;
    this.currentTime = 0;
    if (this.isPlaying) {
      this.stopSynthPlayback();
      this.startSynthPlayback();
    }
    this.updateMediaSession();
    this.notify();
  }

  public previousTrack() {
    if (this.currentTime > 3) {
      this.currentTime = 0;
    } else {
      this.currentTrackIndex =
        (this.currentTrackIndex - 1 + SAMPLE_TRACKS.length) % SAMPLE_TRACKS.length;
      this.currentTime = 0;
    }
    if (this.isPlaying) {
      this.stopSynthPlayback();
      this.startSynthPlayback();
    }
    this.updateMediaSession();
    this.notify();
  }

  private initAudioContext() {
    if (!this.audioCtx) {
      const AudioContextClass =
        window.AudioContext ||
        (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (AudioContextClass) {
        this.audioCtx = new AudioContextClass();
      }
    }
    if (this.audioCtx && this.audioCtx.state === 'suspended') {
      this.audioCtx.resume();
    }
  }

  private startSynthPlayback() {
    if (!this.audioCtx) return;
    try {
      const notes = [220, 261.63, 293.66, 329.63, 392.0, 440];
      const baseFreq = notes[this.currentTrackIndex % notes.length];

      this.oscNode = this.audioCtx.createOscillator();
      this.gainNode = this.audioCtx.createGain();

      this.oscNode.type = 'sine';
      this.oscNode.frequency.setValueAtTime(baseFreq, this.audioCtx.currentTime);

      // Smooth rhythmic modulation
      this.gainNode.gain.setValueAtTime(0.01, this.audioCtx.currentTime);
      this.gainNode.gain.exponentialRampToValueAtTime(0.06, this.audioCtx.currentTime + 0.8);

      this.oscNode.connect(this.gainNode);
      this.gainNode.connect(this.audioCtx.destination);
      this.oscNode.start();
    } catch {
      // Audio autoplay policy catch
    }
  }

  private stopSynthPlayback() {
    if (this.gainNode && this.audioCtx) {
      try {
        this.gainNode.gain.exponentialRampToValueAtTime(0.0001, this.audioCtx.currentTime + 0.05);
        setTimeout(() => {
          this.oscNode?.stop();
          this.oscNode?.disconnect();
          this.gainNode?.disconnect();
          this.oscNode = null;
          this.gainNode = null;
        }, 60);
      } catch {
        this.oscNode = null;
        this.gainNode = null;
      }
    }
  }

  private startTimer() {
    this.stopTimer();
    this.playbackTimer = window.setInterval(() => {
      const track = this.getTrack();
      this.currentTime += 1;
      if (this.currentTime >= track.duration) {
        this.nextTrack();
      } else {
        this.notify();
      }
    }, 1000);
  }

  private stopTimer() {
    if (this.playbackTimer !== null) {
      clearInterval(this.playbackTimer);
      this.playbackTimer = null;
    }
  }

  public triggerHaptic(durationMs = 45) {
    if ('vibrate' in navigator) {
      try {
        navigator.vibrate(durationMs);
      } catch {
        // ignore
      }
    }
    // Web Audio haptic click tone
    this.playHapticTone(durationMs > 80 ? 320 : 540, durationMs / 1000);
  }

  private playHapticTone(freq: number, duration: number) {
    if (!this.audioCtx) this.initAudioContext();
    if (!this.audioCtx) return;
    try {
      const osc = this.audioCtx.createOscillator();
      const gain = this.audioCtx.createGain();
      osc.type = 'triangle';
      osc.frequency.setValueAtTime(freq, this.audioCtx.currentTime);
      gain.gain.setValueAtTime(0.05, this.audioCtx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, this.audioCtx.currentTime + duration);
      osc.connect(gain);
      gain.connect(this.audioCtx.destination);
      osc.start();
      osc.stop(this.audioCtx.currentTime + duration);
    } catch {
      // ignore
    }
  }

  private setupMediaSession() {
    if ('mediaSession' in navigator) {
      navigator.mediaSession.setActionHandler('play', () => this.play());
      navigator.mediaSession.setActionHandler('pause', () => this.pause());
      navigator.mediaSession.setActionHandler('previoustrack', () => this.previousTrack());
      navigator.mediaSession.setActionHandler('nexttrack', () => this.nextTrack());
    }
  }

  private updateMediaSession() {
    if ('mediaSession' in navigator) {
      const track = this.getTrack();
      navigator.mediaSession.metadata = new MediaMetadata({
        title: track.title,
        artist: track.artist,
        album: track.album,
      });
      navigator.mediaSession.playbackState = this.isPlaying ? 'playing' : 'paused';
    }
  }
}

export const audioPlayer = new AudioPlayerService();
