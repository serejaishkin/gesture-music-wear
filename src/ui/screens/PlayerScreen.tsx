import React, { useEffect, useState } from 'react';
import { audioPlayer } from '../../media/AudioPlayerService';
import { gestureManager } from '../../gesture/GestureManager';
import { TrackInfo } from '../../types';

interface PlayerScreenProps {
  onBack: () => void;
  isRunning: boolean;
}

export const PlayerScreen: React.FC<PlayerScreenProps> = ({ onBack, isRunning }) => {
  const [track, setTrack] = useState<TrackInfo>(audioPlayer.getTrack());
  const [isPlaying, setIsPlaying] = useState<boolean>(audioPlayer.getPlaybackState());
  const [currentTime, setCurrentTime] = useState<number>(audioPlayer.getCurrentTime());

  useEffect(() => {
    const unsub = audioPlayer.subscribe(() => {
      setTrack(audioPlayer.getTrack());
      setIsPlaying(audioPlayer.getPlaybackState());
      setCurrentTime(audioPlayer.getCurrentTime());
    });
    return unsub;
  }, []);

  const formatSeconds = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
  };

  const progressPercent = Math.min(100, Math.max(0, (currentTime / (track.duration || 1)) * 100));

  return (
    <div className="w-full flex flex-col items-center gap-2 py-3 px-3 text-center">
      {/* Header */}
      <h1 className="text-sm font-semibold tracking-tight text-white mt-1">
        🎵 Плеер
      </h1>

      {/* Track info */}
      <div className="w-full max-w-[200px] flex flex-col items-center px-1">
        <div className="text-xs font-semibold text-white truncate max-w-full">
          {track.title}
        </div>
        <div className="text-[11px] text-neutral-400 truncate max-w-full">
          {track.artist}
        </div>

        {/* Progress Bar */}
        <div className="w-full mt-2">
          <div className="w-full bg-neutral-800 rounded-full h-1 overflow-hidden">
            <div
              className="bg-cyan-400 h-full transition-all duration-300"
              style={{ width: `${progressPercent}%` }}
            />
          </div>
          <div className="flex justify-between text-[9px] text-neutral-400 mt-1">
            <span>{formatSeconds(currentTime)}</span>
            <span>{formatSeconds(track.duration)}</span>
          </div>
        </div>
      </div>

      {/* Hero Play/Pause Button */}
      <div className="my-1">
        <button
          type="button"
          onClick={() => audioPlayer.playPause()}
          aria-label={isPlaying ? 'Pause' : 'Play'}
          className="w-16 h-16 rounded-full bg-cyan-600 hover:bg-cyan-500 active:scale-95 text-white flex items-center justify-center text-2xl shadow-lg shadow-cyan-950/50 transition-all border border-cyan-400/30"
        >
          {isPlaying ? '⏸' : '▶'}
        </button>
      </div>

      {/* Transport Controls */}
      <div className="flex items-center justify-center gap-3">
        <button
          type="button"
          onClick={() => audioPlayer.previousTrack()}
          aria-label="Previous Track"
          className="w-11 h-11 rounded-full bg-neutral-800 hover:bg-neutral-700 active:scale-95 text-neutral-200 flex items-center justify-center text-sm border border-white/5 transition-all"
        >
          ⏮
        </button>

        <button
          type="button"
          onClick={() => audioPlayer.playPause()}
          aria-label={isPlaying ? 'Pause' : 'Play'}
          className="w-12 h-12 rounded-full bg-neutral-800 hover:bg-neutral-700 active:scale-95 text-cyan-400 font-bold flex items-center justify-center text-base border border-cyan-500/20 transition-all"
        >
          {isPlaying ? '⏸' : '▶'}
        </button>

        <button
          type="button"
          onClick={() => audioPlayer.nextTrack()}
          aria-label="Next Track"
          className="w-11 h-11 rounded-full bg-neutral-800 hover:bg-neutral-700 active:scale-95 text-neutral-200 flex items-center justify-center text-sm border border-white/5 transition-all"
        >
          ⏭
        </button>
      </div>

      {/* Gesture Controls Hint */}
      <div className="mt-1 flex flex-col items-center">
        <div className="text-[10px] text-neutral-400">Управление жестами:</div>
        <div className="text-[10px] text-neutral-300 leading-relaxed mt-0.5">
          👌 Щипок → Play/Pause
          <br />
          🔄 Поворот → Next/Prev
        </div>
      </div>

      {/* Gesture Service Toggle Button */}
      <button
        type="button"
        onClick={() => {
          if (isRunning) {
            gestureManager.stopService();
          } else {
            gestureManager.startService();
          }
        }}
        className={`w-full max-w-[210px] py-1.5 px-3 rounded-full text-xs font-medium transition-all active:scale-95 mt-1 border border-white/5 ${
          isRunning
            ? 'bg-neutral-800 hover:bg-neutral-700 text-rose-400'
            : 'bg-cyan-600 hover:bg-cyan-500 text-white'
        }`}
      >
        {isRunning ? '⏹ Стоп' : '▶️ Жесты ВКЛ'}
      </button>

      {/* Back Button */}
      <button
        type="button"
        onClick={onBack}
        className="w-full max-w-[210px] py-1.5 px-3 rounded-full text-xs font-medium bg-neutral-800 hover:bg-neutral-700 text-neutral-200 border border-white/5 active:scale-95 transition-all mt-1"
      >
        ← Назад
      </button>
    </div>
  );
};
