import React from 'react';
import { gestureManager } from '../../gesture/GestureManager';
import { audioPlayer } from '../../media/AudioPlayerService';
import { Play, Pause, Settings, Music, Power, Hand, GraduationCap } from 'lucide-react';

interface QuickTileScreenProps {
  isRunning: boolean;
  leftHand: boolean;
  lastGesture: string;
  onOpenSettings: () => void;
  onOpenPlayer: () => void;
  onOpenTraining?: () => void;
}

export const QuickTileScreen: React.FC<QuickTileScreenProps> = ({ isRunning, leftHand, lastGesture, onOpenSettings, onOpenPlayer, onOpenTraining }) => {
  const isPlaying = audioPlayer.getPlaybackState();
  const currentTrack = audioPlayer.getTrack();

  const handleToggleService = () => {
    if (isRunning) gestureManager.stopService();
    else gestureManager.startService();
    audioPlayer.triggerHaptic(60);
  };

  const handleToggleHand = () => {
    gestureManager.updateLeftHand(!leftHand);
    audioPlayer.triggerHaptic(35);
  };

  return (
    <div className="w-full flex flex-col items-center min-h-[260px] py-1 px-3 text-center select-none">
      <div className="flex flex-col items-center gap-0.5">
        <span className="text-[9px] uppercase font-semibold tracking-[0.22em] text-neutral-500 flex items-center gap-1.5">
          <span className={`w-1.5 h-1.5 rounded-full ${isRunning ? 'bg-cyan-400 shadow-[0_0_7px_rgba(34,211,238,.8)]' : 'bg-neutral-700'}`} />
          Gesture Control
        </span>
        <span className={`text-[10px] font-medium ${isRunning ? 'text-cyan-300' : 'text-neutral-500'}`}>
          {isRunning ? 'Жесты активны' : 'Жесты отключены'}
        </span>
      </div>

      <div className="my-3 relative flex items-center justify-center">
        {isRunning && <div className="absolute inset-0 -m-4 rounded-full bg-cyan-400/10 blur-xl soft-pulse pointer-events-none" />}
        <button
          type="button"
          onClick={handleToggleService}
          aria-label={isRunning ? 'Выключить службу жестов' : 'Включить службу жестов'}
          className={`relative w-[106px] h-[106px] rounded-full flex flex-col items-center justify-center transition-all duration-200 active:scale-90 border shadow-2xl ${isRunning ? 'bg-[radial-gradient(circle_at_50%_35%,#164e63,#082f3a_58%,#04191f)] text-white border-cyan-400/70 shadow-cyan-950/70' : 'bg-[radial-gradient(circle_at_50%_35%,#202428,#0d1012_65%)] text-neutral-400 border-white/10 shadow-black'}`}
        >
          <div className={`absolute inset-2 rounded-full border ${isRunning ? 'border-cyan-300/15' : 'border-white/[0.035]'}`} />
          <Power className={`w-7 h-7 mb-1 ${isRunning ? 'text-cyan-300' : 'text-neutral-500'}`} />
          <span className="text-[10px] font-bold uppercase tracking-[0.16em]">{isRunning ? 'АКТИВНО' : 'СТАРТ'}</span>
          <span className="text-[8px] text-neutral-500 mt-0.5">{isRunning ? '15s guard' : 'нажмите'}</span>
        </button>
      </div>

      <div className="w-full max-w-[218px] flex flex-col items-center gap-2">
        <div className="flex items-center gap-1.5">
          <button type="button" onClick={handleToggleHand} className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-full text-[9px] font-semibold bg-white/[0.045] hover:bg-white/[0.08] text-neutral-300 border border-white/[0.08] active:scale-95 transition-all" title="Переключить руку">
            <Hand className="w-3 h-3 text-cyan-400" />
            {leftHand ? 'Левая' : 'Правая'}
          </button>
          <button type="button" onClick={() => { audioPlayer.playPause(); audioPlayer.triggerHaptic(40); }} className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-full text-[9px] font-semibold bg-white/[0.045] hover:bg-white/[0.08] text-neutral-300 border border-white/[0.08] active:scale-95 transition-all max-w-[112px]" title="Воспроизведение / Пауза">
            {isPlaying ? <Pause className="w-3 h-3 text-cyan-400" /> : <Play className="w-3 h-3 text-cyan-400" />}
            <span className="truncate">{currentTrack.title}</span>
          </button>
        </div>

        <div className="min-h-[18px] flex items-center justify-center max-w-full">
          {lastGesture ? <div className="text-[9px] font-semibold text-cyan-200 bg-cyan-400/[0.07] border border-cyan-400/20 px-3 py-1 rounded-full truncate max-w-full">{lastGesture}</div> : <span className="text-[8px] text-neutral-600">{isRunning ? 'Поверните кисть или сделайте щипок' : 'Нажмите СТАРТ для включения'}</span>}
        </div>
      </div>

      <div className="w-full flex items-center justify-center gap-1.5 pt-2 mt-auto border-t border-white/[0.06]">
        <button type="button" onClick={onOpenPlayer} className="flex items-center gap-1 px-2.5 py-1.5 rounded-full text-[9px] font-semibold bg-cyan-400/[0.07] hover:bg-cyan-400/[0.12] text-cyan-300 border border-cyan-400/15 active:scale-95 transition-all">
          <Music className="w-3 h-3" /> Плеер
        </button>
        {onOpenTraining && (
          <button type="button" onClick={onOpenTraining} className="flex items-center gap-1 px-2.5 py-1.5 rounded-full text-[9px] font-semibold bg-cyan-500/10 hover:bg-cyan-500/20 text-cyan-200 border border-cyan-400/25 active:scale-95 transition-all" title="Обучение и калибровка жестов">
            <GraduationCap className="w-3 h-3 text-cyan-300" /> Обучение
          </button>
        )}
        <button type="button" onClick={onOpenSettings} className="flex items-center gap-1 px-2.5 py-1.5 rounded-full text-[9px] font-semibold bg-white/[0.045] hover:bg-white/[0.08] text-neutral-300 border border-white/[0.08] active:scale-95 transition-all">
          <Settings className="w-3 h-3" /> Опции
        </button>
      </div>
    </div>
  );
};
