import React from 'react';
import { SensitivitySlider } from '../components/SensitivitySlider';
import { gestureManager } from '../../gesture/GestureManager';

interface ControlScreenProps {
  onOpenPlayer: () => void;
  onOpenTraining: () => void;
  onOpenTile?: () => void;
  isRunning: boolean;
  strategyName: string;
  lastGesture: string;
  saveMessage: string;
  angleThreshold: number;
  pinchThreshold: number;
  fistClenchThreshold: number;
  fistClenchEnabled: boolean;
  minDuration: number;
  maxDuration: number;
  gestureCooldown: number;
  leftHand: boolean;
}

export const ControlScreen: React.FC<ControlScreenProps> = ({
  onOpenPlayer,
  onOpenTraining,
  onOpenTile,
  isRunning,
  strategyName,
  lastGesture,
  saveMessage,
  angleThreshold,
  pinchThreshold,
  fistClenchThreshold,
  fistClenchEnabled,
  minDuration,
  maxDuration,
  gestureCooldown,
  leftHand,
}) => {
  return (
    <div className="w-full flex flex-col items-center gap-2 py-3 px-3 text-center">
      {/* Title */}
      <h1 className="text-sm font-semibold tracking-tight text-white mt-1">
        🎵 Gesture Music
      </h1>

      {/* Running State */}
      <div
        className={`text-xs font-medium transition-colors ${
          isRunning ? 'text-cyan-400 font-semibold' : 'text-neutral-400'
        }`}
      >
        {isRunning ? '● Слушаю жесты' : '○ Остановлено'}
      </div>

      {/* Strategy Engine Info */}
      {isRunning && strategyName && (
        <div className="text-[10px] text-neutral-400 bg-neutral-900/60 px-2 py-0.5 rounded-full border border-white/5 max-w-[200px] truncate">
          Движок: {strategyName}
        </div>
      )}

      {/* Start / Stop Button */}
      <button
        type="button"
        onClick={() => {
          if (isRunning) {
            gestureManager.stopService();
          } else {
            gestureManager.startService();
          }
        }}
        className={`w-full max-w-[210px] py-2 px-4 rounded-full text-xs font-semibold shadow-sm transition-all active:scale-95 ${
          isRunning
            ? 'bg-rose-600 hover:bg-rose-500 text-white'
            : 'bg-cyan-600 hover:bg-cyan-500 text-white'
        }`}
      >
        {isRunning ? '⏹ Стоп' : '▶️ Старт'}
      </button>

      {/* Hand Switcher */}
      <button
        type="button"
        onClick={() => gestureManager.updateLeftHand(!leftHand)}
        className="w-full max-w-[210px] py-1.5 px-3 rounded-full text-xs font-medium bg-neutral-800 hover:bg-neutral-700 text-neutral-200 border border-white/5 active:scale-95 transition-all"
      >
        {leftHand ? '⌚ Левая рука' : '⌚ Правая рука'}
      </button>

      {/* Sensitivity Sliders */}
      <div className="w-full flex flex-col gap-1 py-1">
        <SensitivitySlider
          label="Поворот"
          value={angleThreshold}
          min={15}
          max={35}
          step={1}
          onValueChange={(v) => gestureManager.updateAngleThreshold(v)}
          formatValue={(v) => `${Math.round(v)}°`}
        />

        <SensitivitySlider
          label="Щипок (аксель)"
          value={pinchThreshold}
          min={1.8}
          max={5.0}
          step={0.1}
          onValueChange={(v) => gestureManager.updatePinchThreshold(v)}
          formatValue={(v) => v.toFixed(1)}
        />

        <SensitivitySlider
          label="Сжатие в кулак"
          value={fistClenchThreshold}
          min={1.8}
          max={5.5}
          step={0.1}
          onValueChange={(v) => gestureManager.updateFistClenchThreshold(v)}
          formatValue={(v) => v.toFixed(1)}
        />

        <div className="w-full max-w-[210px] flex items-center justify-between py-1 px-2 text-xs">
          <span className="text-neutral-300 text-[11px]">Жест сжатия кулака:</span>
          <button
            type="button"
            onClick={() => gestureManager.updateFistClenchEnabled(!fistClenchEnabled)}
            className={`px-2 py-0.5 rounded-full text-[10px] font-semibold border transition-all ${
              fistClenchEnabled
                ? 'bg-cyan-950 text-cyan-300 border-cyan-700'
                : 'bg-neutral-800 text-neutral-400 border-neutral-700'
            }`}
          >
            {fistClenchEnabled ? 'ВКЛ' : 'ВЫКЛ'}
          </button>
        </div>

        <SensitivitySlider
          label="Мин. время"
          value={minDuration}
          min={50}
          max={300}
          step={10}
          onValueChange={(v) => gestureManager.updateMinDuration(Math.round(v))}
          formatValue={(v) => `${Math.round(v)}мс`}
        />

        <SensitivitySlider
          label="Макс. время"
          value={maxDuration}
          min={300}
          max={1000}
          step={20}
          onValueChange={(v) => gestureManager.updateMaxDuration(Math.round(v))}
          formatValue={(v) => `${Math.round(v)}мс`}
        />

        <SensitivitySlider
          label="Пауза между жестами"
          value={gestureCooldown}
          min={500}
          max={2000}
          step={50}
          onValueChange={(v) => gestureManager.updateGestureCooldown(Math.round(v))}
          formatValue={(v) => `${Math.round(v)}мс`}
        />
      </div>

      {/* Settings Persistence Controls */}
      <div className="w-full max-w-[210px] flex flex-col gap-1.5 pt-1">
        <button
          type="button"
          onClick={() => gestureManager.saveSettings()}
          className="w-full py-1.5 px-3 rounded-full text-xs font-medium bg-neutral-800 hover:bg-neutral-700 text-neutral-200 border border-white/5 active:scale-95 transition-all"
        >
          💾 Сохранить
        </button>

        <button
          type="button"
          onClick={() => gestureManager.restoreDefaults()}
          className="w-full py-1.5 px-3 rounded-full text-xs font-medium bg-neutral-800 hover:bg-neutral-700 text-neutral-200 border border-white/5 active:scale-95 transition-all"
        >
          ↺ Сбросить
        </button>
      </div>

      {/* Screen Navigation */}
      <div className="w-full max-w-[210px] flex flex-col gap-1.5 pt-1">
        {onOpenTile && (
          <button
            type="button"
            onClick={onOpenTile}
            className="w-full py-1.5 px-3 rounded-full text-xs font-semibold bg-neutral-800 hover:bg-neutral-700 text-cyan-300 border border-cyan-500/30 active:scale-95 transition-all shadow-sm flex items-center justify-center gap-1.5"
          >
            <span>📱</span>
            <span>Быстрая плитка (Tile)</span>
          </button>
        )}

        <button
          type="button"
          onClick={onOpenPlayer}
          className="w-full py-2 px-3 rounded-full text-xs font-semibold bg-cyan-600 hover:bg-cyan-500 text-white active:scale-95 transition-all shadow-sm"
        >
          🎶 Плеер
        </button>

        <button
          type="button"
          onClick={onOpenTraining}
          className="w-full py-1.5 px-3 rounded-full text-xs font-medium bg-neutral-800 hover:bg-neutral-700 text-neutral-200 border border-white/5 active:scale-95 transition-all"
        >
          🎓 Обучение
        </button>

        <a
          href="/gesture-music-wear.apk"
          download="gesture-music-wear.apk"
          className="w-full py-1.5 px-3 rounded-full text-xs font-medium bg-emerald-700 hover:bg-emerald-600 text-white flex items-center justify-center gap-1 active:scale-95 transition-all shadow-sm"
        >
          📥 Скачать APK (Wear OS)
        </a>
      </div>

      {/* Toast Feedback */}
      {saveMessage && (
        <div className="text-[11px] text-cyan-400 font-medium px-2 py-0.5 rounded-full bg-cyan-950/40 border border-cyan-500/20 animate-fade-in">
          {saveMessage}
        </div>
      )}

      {lastGesture && (
        <div className="text-[11px] text-cyan-300 font-medium px-2.5 py-1 rounded-full bg-cyan-900/30 border border-cyan-500/30">
          {lastGesture}
        </div>
      )}

      {/* Guide Footer */}
      <div className="text-[10px] leading-relaxed text-neutral-400 px-2 py-2 whitespace-pre-line border-t border-white/5 mt-2">
        Поворот кисти вправо ➡️ next{'\n'}
        Поворот влево ⬅️ prev{'\n'}
        (для левой руки — наоборот){'\n'}
        👌 двойной щипок: play/pause{'\n'}
        ✊ сжатие в кулак: play/pause
      </div>
    </div>
  );
};
