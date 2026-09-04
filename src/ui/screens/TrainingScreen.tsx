import React, { useEffect, useState } from 'react';
import { GestureType } from '../../types';
import { gestureManager } from '../../gesture/GestureManager';

interface TrainingScreenProps {
  onBack: () => void;
  trainingProgress: number;
  trainingRepetitions: number;
  trainingDone: boolean;
  trainingSuccess: boolean;
}

export const TrainingScreen: React.FC<TrainingScreenProps> = ({
  onBack,
  trainingProgress,
  trainingRepetitions,
  trainingDone,
  trainingSuccess,
}) => {
  const [activeGesture, setActiveGesture] = useState<GestureType | null>(null);

  useEffect(() => {
    if (trainingDone && !trainingSuccess) {
      setActiveGesture(null);
    }
    if (trainingDone && trainingSuccess) {
      const timer = setTimeout(() => {
        onBack();
      }, 1400);
      return () => clearTimeout(timer);
    }
  }, [trainingDone, trainingSuccess, onBack]);

  const sessionActive = activeGesture !== null && !trainingDone;

  const getGestureLabel = (type: GestureType | null): string => {
    switch (type) {
      case GestureType.NEXT_TRACK:
        return 'След. трек';
      case GestureType.PREVIOUS_TRACK:
        return 'Пред. трек';
      case GestureType.PLAY_PAUSE:
        return 'Play/Pause';
      case GestureType.ACTIVATE:
        return 'Активация';
      default:
        return '';
    }
  };

  const handleStartTraining = (type: GestureType) => {
    setActiveGesture(type);
    gestureManager.startTraining(type);
  };

  const handleCancel = () => {
    setActiveGesture(null);
    gestureManager.stopTraining();
  };

  const handleClear = () => {
    setActiveGesture(null);
    gestureManager.clearTraining();
  };

  return (
    <div className="w-full flex flex-col items-center gap-2 py-3 px-3 text-center">
      {/* Title */}
      <h1 className="text-sm font-semibold tracking-tight text-white mt-1">
        🎓 Обучение
      </h1>

      {/* Completion Status */}
      {trainingDone && trainingSuccess && (
        <div className="text-xs font-semibold text-emerald-400 bg-emerald-950/40 px-3 py-1 rounded-full border border-emerald-500/30">
          ✅ Сохранено!
        </div>
      )}

      {trainingDone && !trainingSuccess && (
        <div className="text-xs font-semibold text-rose-400 bg-rose-950/40 px-3 py-1 rounded-full border border-rose-500/30">
          ❌ Отменено / ошибка
        </div>
      )}

      {/* Active gesture title */}
      {sessionActive && (
        <div className="text-xs font-medium text-cyan-300">
          Обучается: {getGestureLabel(activeGesture)}
        </div>
      )}

      {/* Repetitions Counter */}
      <div className="text-xs font-medium text-neutral-300">
        Повторов: <span className="text-cyan-400 font-bold">{trainingRepetitions}</span>/5
      </div>

      {/* Circular Progress Gauge */}
      <div className="relative w-16 h-16 flex items-center justify-center my-1">
        <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
          <path
            className="text-neutral-800"
            strokeWidth="3.5"
            stroke="currentColor"
            fill="none"
            d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
          />
          <path
            className="text-cyan-400 transition-all duration-300 ease-out"
            strokeDasharray={`${trainingProgress}, 100`}
            strokeWidth="3.5"
            strokeLinecap="round"
            stroke="currentColor"
            fill="none"
            d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
          />
        </svg>
        <span className="absolute text-[11px] font-bold text-white">
          {trainingProgress}%
        </span>
      </div>

      {/* Prompt / Instruction */}
      <div className="text-[10px] text-neutral-400 min-h-[28px] flex items-center justify-center px-2">
        {sessionActive && trainingRepetitions === 0 && 'Сделайте жест...'}
        {sessionActive && trainingRepetitions > 0 && `Повторите ещё ${5 - trainingRepetitions}...`}
        {!sessionActive && 'Выберите жест и сделайте его 5 раз'}
      </div>

      {/* Gesture Choice Buttons */}
      <div className="w-full max-w-[210px] flex flex-col gap-1.5 mt-1">
        <button
          type="button"
          disabled={sessionActive}
          onClick={() => handleStartTraining(GestureType.NEXT_TRACK)}
          className="w-full py-1.5 px-3 rounded-full text-xs font-medium bg-neutral-800 hover:bg-neutral-700 disabled:opacity-40 disabled:pointer-events-none text-neutral-100 border border-white/5 active:scale-95 transition-all"
        >
          ➡️ След. трек
        </button>

        <button
          type="button"
          disabled={sessionActive}
          onClick={() => handleStartTraining(GestureType.PREVIOUS_TRACK)}
          className="w-full py-1.5 px-3 rounded-full text-xs font-medium bg-neutral-800 hover:bg-neutral-700 disabled:opacity-40 disabled:pointer-events-none text-neutral-100 border border-white/5 active:scale-95 transition-all"
        >
          ⬅️ Пред. трек
        </button>

        <button
          type="button"
          disabled={sessionActive}
          onClick={() => handleStartTraining(GestureType.PLAY_PAUSE)}
          className="w-full py-1.5 px-3 rounded-full text-xs font-medium bg-neutral-800 hover:bg-neutral-700 disabled:opacity-40 disabled:pointer-events-none text-neutral-100 border border-white/5 active:scale-95 transition-all"
        >
          ⏯️ Play/Pause
        </button>

        <button
          type="button"
          disabled={sessionActive}
          onClick={() => handleStartTraining(GestureType.ACTIVATE)}
          className="w-full py-1.5 px-3 rounded-full text-xs font-medium bg-neutral-800 hover:bg-neutral-700 disabled:opacity-40 disabled:pointer-events-none text-neutral-100 border border-white/5 active:scale-95 transition-all"
        >
          🔓 Активация
        </button>
      </div>

      {/* Control Actions */}
      <div className="w-full max-w-[210px] flex flex-col gap-1.5 mt-1">
        {sessionActive && (
          <button
            type="button"
            onClick={handleCancel}
            className="w-full py-1.5 px-3 rounded-full text-xs font-medium bg-rose-900/60 hover:bg-rose-800 text-rose-200 border border-rose-500/20 active:scale-95 transition-all"
          >
            ❌ Отмена
          </button>
        )}

        <button
          type="button"
          onClick={handleClear}
          className="w-full py-1.5 px-3 rounded-full text-xs font-medium bg-neutral-800 hover:bg-neutral-700 text-neutral-300 border border-white/5 active:scale-95 transition-all"
        >
          🗑 Очистить всё
        </button>

        <button
          type="button"
          onClick={onBack}
          className="w-full py-1.5 px-3 rounded-full text-xs font-medium bg-neutral-800 hover:bg-neutral-700 text-neutral-200 border border-white/5 active:scale-95 transition-all"
        >
          ← Назад
        </button>
      </div>
    </div>
  );
};
