import React from 'react';
import { gestureManager } from '../../gesture/GestureManager';
import { audioPlayer } from '../../media/AudioPlayerService';
import { Play, Pause, Settings, Music, Power, Radio, Hand } from 'lucide-react';

interface QuickTileScreenProps {
  isRunning: boolean;
  leftHand: boolean;
  lastGesture: string;
  onOpenSettings: () => void;
  onOpenPlayer: () => void;
}

export const QuickTileScreen: React.FC<QuickTileScreenProps> = ({
  isRunning,
  leftHand,
  lastGesture,
  onOpenSettings,
  onOpenPlayer,
}) => {
  const isPlaying = audioPlayer.getPlaybackState();
  const currentTrack = audioPlayer.getTrack();

  const handleToggleService = () => {
    if (isRunning) {
      gestureManager.stopService();
    } else {
      gestureManager.startService();
    }
    audioPlayer.triggerHaptic(60);
  };

  const handleToggleHand = () => {
    gestureManager.updateLeftHand(!leftHand);
    audioPlayer.triggerHaptic(35);
  };

  return (
    <div className="w-full flex flex-col items-center justify-between min-h-[260px] py-1 px-3 text-center select-none">
      {/* Top Tile Header */}
      <div className="flex flex-col items-center gap-0.5">
        <span className="text-[10px] uppercase font-bold tracking-widest text-cyan-400/90 flex items-center gap-1">
          <Radio className={`w-3 h-3 ${isRunning ? 'animate-pulse text-cyan-400' : 'text-neutral-500'}`} />
          Плитка жестов
        </span>
        <span className="text-[10px] text-neutral-400">
          {isRunning ? 'Служба работает' : 'Служба отключена'}
        </span>
      </div>

      {/* Main Circular Quick Toggle Button */}
      <div className="my-2 relative flex items-center justify-center">
        {/* Animated Glow Halo when Active */}
        {isRunning && (
          <div className="absolute inset-0 -m-2 rounded-full bg-cyan-500/20 blur-md animate-pulse pointer-events-none" />
        )}

        <button
          type="button"
          onClick={handleToggleService}
          aria-label={isRunning ? 'Выключить службу жестов' : 'Включить службу жестов'}
          className={`w-24 h-24 rounded-full flex flex-col items-center justify-center transition-all duration-200 active:scale-90 border-2 shadow-lg ${
            isRunning
              ? 'bg-gradient-to-b from-cyan-600 to-cyan-800 text-white border-cyan-400 shadow-cyan-900/50 hover:brightness-110'
              : 'bg-neutral-900 hover:bg-neutral-800 text-neutral-400 border-neutral-700 shadow-black/60 hover:text-neutral-200'
          }`}
        >
          <Power className={`w-7 h-7 mb-1 transition-transform ${isRunning ? 'text-white scale-105' : 'text-neutral-500'}`} />
          <span className="text-[11px] font-bold uppercase tracking-wider">
            {isRunning ? 'СТОП' : 'СТАРТ'}
          </span>
          <span className="text-[8px] font-medium opacity-80">
            {isRunning ? 'ВКЛ' : 'ВЫКЛ'}
          </span>
        </button>
      </div>

      {/* Hand Switcher & Last Gesture Pill */}
      <div className="w-full flex flex-col items-center gap-1.5 max-w-[210px]">
        <div className="flex items-center gap-2">
          {/* Hand quick toggle */}
          <button
            type="button"
            onClick={handleToggleHand}
            className="flex items-center gap-1 px-2.5 py-1 rounded-full text-[10px] font-medium bg-neutral-800/90 hover:bg-neutral-700 text-neutral-200 border border-white/10 active:scale-95 transition-all"
            title="Переключить руку"
          >
            <Hand className="w-3 h-3 text-cyan-400" />
            <span>{leftHand ? 'Левая' : 'Правая'}</span>
          </button>

          {/* Mini Play / Pause button */}
          <button
            type="button"
            onClick={() => {
              audioPlayer.playPause();
              audioPlayer.triggerHaptic(40);
            }}
            className="flex items-center gap-1 px-2.5 py-1 rounded-full text-[10px] font-medium bg-neutral-800/90 hover:bg-neutral-700 text-neutral-200 border border-white/10 active:scale-95 transition-all truncate max-w-[110px]"
            title="Воспроизведение / Пауза"
          >
            {isPlaying ? (
              <Pause className="w-3 h-3 text-cyan-400" />
            ) : (
              <Play className="w-3 h-3 text-cyan-400" />
            )}
            <span className="truncate">{currentTrack.title}</span>
          </button>
        </div>

        {/* Dynamic Last Gesture Badge */}
        {lastGesture ? (
          <div className="text-[10px] font-semibold text-cyan-300 bg-cyan-950/70 border border-cyan-500/30 px-3 py-0.5 rounded-full animate-fade-in truncate max-w-full">
            {lastGesture}
          </div>
        ) : (
          <div className="text-[9px] text-neutral-500 italic">
            {isRunning ? 'Поверните кисть или сделайте щипок' : 'Нажмите СТАРТ для включения'}
          </div>
        )}
      </div>

      {/* Bottom Quick Launch Icons */}
      <div className="w-full flex items-center justify-center gap-3 pt-2 border-t border-white/5 mt-1">
        <button
          type="button"
          onClick={onOpenPlayer}
          className="flex items-center gap-1 px-3 py-1 rounded-full text-[10px] font-medium bg-cyan-950/50 hover:bg-cyan-900/60 text-cyan-300 border border-cyan-800/50 active:scale-95 transition-all"
        >
          <Music className="w-3 h-3" />
          <span>Плеер</span>
        </button>

        <button
          type="button"
          onClick={onOpenSettings}
          className="flex items-center gap-1 px-3 py-1 rounded-full text-[10px] font-medium bg-neutral-800 hover:bg-neutral-700 text-neutral-300 border border-white/10 active:scale-95 transition-all"
        >
          <Settings className="w-3 h-3" />
          <span>Настройки</span>
        </button>
      </div>
    </div>
  );
};
