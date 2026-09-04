import React from 'react';
import { gestureManager } from '../../gesture/GestureManager';
import { audioPlayer } from '../../media/AudioPlayerService';
import { Power, Radio, Hand, Music, Sliders, ShieldCheck } from 'lucide-react';

interface QuickAccessTileCardProps {
  isRunning: boolean;
  leftHand: boolean;
  lastGesture: string;
  onOpenWatchTile: () => void;
  onOpenWatchControl: () => void;
  onOpenWatchPlayer: () => void;
}

export const QuickAccessTileCard: React.FC<QuickAccessTileCardProps> = ({
  isRunning,
  leftHand,
  lastGesture,
  onOpenWatchTile,
  onOpenWatchControl,
  onOpenWatchPlayer,
}) => {
  const isPlaying = audioPlayer.getPlaybackState();
  const currentTrack = audioPlayer.getTrack();

  const handleToggle = () => {
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
    <div className="bg-neutral-900/90 border border-white/10 rounded-2xl p-4 shadow-xl text-neutral-200">
      {/* Header */}
      <div className="flex items-center justify-between pb-3 border-b border-white/10">
        <div className="flex items-center gap-2">
          <div className={`w-2.5 h-2.5 rounded-full ${isRunning ? 'bg-cyan-400 animate-pulse' : 'bg-neutral-500'}`} />
          <h2 className="text-xs font-semibold uppercase tracking-wider text-neutral-200">
            Плитка быстрого доступа (Wear OS Tile)
          </h2>
        </div>
        <button
          type="button"
          onClick={onOpenWatchTile}
          className="text-[10px] text-cyan-400 hover:text-cyan-300 bg-cyan-950/60 hover:bg-cyan-900/60 border border-cyan-800/60 px-2 py-0.5 rounded-full transition-all"
        >
          Показать на часах
        </button>
      </div>

      {/* Main Action Banner */}
      <div className="mt-3 flex items-center justify-between gap-4 p-3 rounded-xl bg-neutral-950/70 border border-white/5">
        <div className="flex items-center gap-3 min-w-0">
          <div
            className={`w-12 h-12 rounded-xl flex items-center justify-center transition-colors ${
              isRunning
                ? 'bg-cyan-600/20 text-cyan-400 border border-cyan-500/40'
                : 'bg-neutral-800 text-neutral-500 border border-white/5'
            }`}
          >
            <Radio className={`w-6 h-6 ${isRunning ? 'animate-pulse text-cyan-400' : ''}`} />
          </div>
          <div className="min-w-0">
            <div className="text-sm font-bold text-white flex items-center gap-2">
              <span>Служба жестов</span>
              <span
                className={`text-[9px] font-semibold px-2 py-0.5 rounded-full uppercase tracking-wider ${
                  isRunning
                    ? 'bg-emerald-950 text-emerald-300 border border-emerald-800'
                    : 'bg-neutral-800 text-neutral-400'
                }`}
              >
                {isRunning ? 'Активна' : 'Отключена'}
              </span>
            </div>
            <p className="text-[11px] text-neutral-400 truncate">
              {isRunning
                ? 'Слушает повороты кисти, щипки и сжатия'
                : 'Нажмите кнопку для активации'}
            </p>
          </div>
        </div>

        {/* Big Quick Toggle Button */}
        <button
          type="button"
          onClick={handleToggle}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-xl font-semibold text-xs transition-all active:scale-95 shadow-md shrink-0 ${
            isRunning
              ? 'bg-rose-600 hover:bg-rose-500 text-white shadow-rose-900/30'
              : 'bg-cyan-600 hover:bg-cyan-500 text-white shadow-cyan-900/40'
          }`}
        >
          <Power className="w-4 h-4" />
          <span>{isRunning ? 'Выключить' : 'Включить'}</span>
        </button>
      </div>

      {/* Quick Controls Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 mt-3 text-xs">
        {/* Hand Toggle */}
        <button
          type="button"
          onClick={handleToggleHand}
          className="flex items-center justify-between p-2.5 rounded-xl bg-neutral-800/80 hover:bg-neutral-700/80 border border-white/5 transition-all text-left"
        >
          <div className="flex items-center gap-1.5">
            <Hand className="w-3.5 h-3.5 text-cyan-400" />
            <span className="text-[11px] text-neutral-300">Рука:</span>
          </div>
          <span className="font-semibold text-white text-[11px]">
            {leftHand ? 'Левая' : 'Правая'}
          </span>
        </button>

        {/* Quick Navigate to Player */}
        <button
          type="button"
          onClick={onOpenWatchPlayer}
          className="flex items-center justify-between p-2.5 rounded-xl bg-neutral-800/80 hover:bg-neutral-700/80 border border-white/5 transition-all text-left"
        >
          <div className="flex items-center gap-1.5">
            <Music className="w-3.5 h-3.5 text-cyan-400" />
            <span className="text-[11px] text-neutral-300">Плеер:</span>
          </div>
          <span className="font-semibold text-cyan-300 text-[11px] truncate max-w-[70px]">
            {isPlaying ? 'Играет' : 'Пауза'}
          </span>
        </button>

        {/* Quick Navigate to Settings */}
        <button
          type="button"
          onClick={onOpenWatchControl}
          className="flex items-center justify-between p-2.5 rounded-xl bg-neutral-800/80 hover:bg-neutral-700/80 border border-white/5 transition-all text-left col-span-2 sm:col-span-1"
        >
          <div className="flex items-center gap-1.5">
            <Sliders className="w-3.5 h-3.5 text-cyan-400" />
            <span className="text-[11px] text-neutral-300">Настройки:</span>
          </div>
          <span className="font-semibold text-neutral-300 text-[11px]">
            Открыть
          </span>
        </button>
      </div>

      {/* Last Detected Gesture Notification */}
      {lastGesture && (
        <div className="mt-3 flex items-center justify-between p-2 rounded-xl bg-cyan-950/40 border border-cyan-500/20 text-xs">
          <span className="text-neutral-400 text-[11px]">Последний распознанный жест:</span>
          <span className="font-bold text-cyan-300 text-[11px]">{lastGesture}</span>
        </div>
      )}
    </div>
  );
};
