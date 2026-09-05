import React, { useState, useRef } from 'react';
import { gestureManager } from '../../gesture/GestureManager';
import { EngineStrategy } from '../../types';

interface GestureSimulatorProps {
  isRunning: boolean;
  leftHand: boolean;
}

export const GestureSimulator: React.FC<GestureSimulatorProps> = ({ isRunning, leftHand }) => {
  const [isSimulating, setIsSimulating] = useState(false);
  const [activeGestureNote, setActiveGestureNote] = useState('');
  const [liveSensors, setLiveSensors] = useState({
    gyroX: 0,
    gyroY: 0,
    gyroZ: 0,
    linAccX: 0,
    linAccY: 0,
    linAccZ: 0,
  });

  const lastDisplayTimeRef = useRef(0);

  // Smoothly throttled UI sensor update (16 FPS UI refresh while physical sensor loop stays 50Hz)
  const updateSensorsUI = (
    gx: number,
    gy: number,
    gz: number,
    ax: number,
    ay: number,
    az: number,
    force = false
  ) => {
    const now = performance.now();
    if (force || now - lastDisplayTimeRef.current >= 60) {
      lastDisplayTimeRef.current = now;
      setLiveSensors({
        gyroX: Number(gx.toFixed(2)),
        gyroY: Number(gy.toFixed(2)),
        gyroZ: Number(gz.toFixed(2)),
        linAccX: Number(ax.toFixed(2)),
        linAccY: Number(ay.toFixed(2)),
        linAccZ: Number(az.toFixed(2)),
      });
    }
  };

  const showNote = (msg: string) => {
    setActiveGestureNote(msg);
    setTimeout(() => setActiveGestureNote(''), 1800);
  };

  // 1. Emulate physical wrist rotation right
  const simulateRotateRight = () => {
    if (isSimulating) return;
    setIsSimulating(true);
    showNote('Поворот кисти вправо...');

    const startTime = performance.now();
    const duration = 240;
    const direction = leftHand ? 1 : -1;

    const interval = setInterval(() => {
      const elapsed = performance.now() - startTime;
      if (elapsed > duration) {
        clearInterval(interval);
        gestureManager.processSample(performance.now(), 0, 0, 0, 0, 0, 0);
        updateSensorsUI(0, 0, 0, 0, 0, 0, true);
        setIsSimulating(false);
        return;
      }

      const progress = elapsed / duration;
      const bell = Math.sin(progress * Math.PI);
      const gx = direction * bell * 3.4; // 3.4 rad/s
      const linAccY = (Math.random() - 0.5) * 3;

      updateSensorsUI(gx, 0, 0, 0, linAccY, 0);
      gestureManager.processSample(performance.now(), gx, 0, 0, 0, linAccY, 0);
    }, 20);
  };

  // 2. Emulate physical wrist rotation left
  const simulateRotateLeft = () => {
    if (isSimulating) return;
    setIsSimulating(true);
    showNote('Поворот кисти влево...');

    const startTime = performance.now();
    const duration = 240;
    const direction = leftHand ? -1 : 1;

    const interval = setInterval(() => {
      const elapsed = performance.now() - startTime;
      if (elapsed > duration) {
        clearInterval(interval);
        gestureManager.processSample(performance.now(), 0, 0, 0, 0, 0, 0);
        updateSensorsUI(0, 0, 0, 0, 0, 0, true);
        setIsSimulating(false);
        return;
      }

      const progress = elapsed / duration;
      const bell = Math.sin(progress * Math.PI);
      const gx = direction * bell * 3.4;
      const linAccY = (Math.random() - 0.5) * 3;

      updateSensorsUI(gx, 0, 0, 0, linAccY, 0);
      gestureManager.processSample(performance.now(), gx, 0, 0, 0, linAccY, 0);
    }, 20);
  };

  // 3. Emulate physical double pinch (👌👌)
  const simulateDoublePinch = () => {
    if (isSimulating) return;
    setIsSimulating(true);
    showNote('Двойной щипок пальцами...');

    const emitPinchCycle = (callback: () => void) => {
      let t = 0;
      const pinchInterval = setInterval(() => {
        t += 20;
        if (t <= 50) {
          // Sharp upward Z spike + minor lateral impulse
          const az = 4.6;
          gestureManager.processSample(performance.now(), 0.05, 0.05, 0.05, 0.3, 0.4, az);
          updateSensorsUI(0.05, 0.05, 0.05, 0.3, 0.4, az);
        } else if (t <= 110) {
          // Rebound down
          const az = -2.6;
          gestureManager.processSample(performance.now(), 0.02, 0.02, 0.02, 0.1, 0.1, az);
          updateSensorsUI(0.02, 0.02, 0.02, 0.1, 0.1, az);
        } else {
          clearInterval(pinchInterval);
          gestureManager.processSample(performance.now(), 0, 0, 0, 0, 0, 0);
          updateSensorsUI(0, 0, 0, 0, 0, 0, true);
          callback();
        }
      }, 20);
    };

    // First pinch
    emitPinchCycle(() => {
      // Natural gap between pinches (180ms)
      setTimeout(() => {
        // Second pinch
        emitPinchCycle(() => {
          setIsSimulating(false);
        });
      }, 160);
    });
  };

  // 4. Emulate physical fist clench (✊ Сжатие в кулак — жест активации)
  const simulateFistClench = () => {
    if (isSimulating) return;
    setIsSimulating(true);
    showNote('Сжатие в кулак (активация)...');

    const emitClenchImpulse = (callback: () => void) => {
      let t = 0;
      const clenchInterval = setInterval(() => {
        t += 20;
        if (t <= 60) {
          // Micro-shockwave across multiple axes (high jerk, low gyro)
          const ax = 3.2;
          const ay = 3.0;
          const az = 3.8;
          gestureManager.processSample(performance.now(), 0.1, 0.1, 0.1, ax, ay, az);
          updateSensorsUI(0.1, 0.1, 0.1, ax, ay, az);
        } else if (t <= 120) {
          // Relaxation
          gestureManager.processSample(performance.now(), 0.05, 0.05, 0.05, 0.4, 0.4, 0.6);
          updateSensorsUI(0.05, 0.05, 0.05, 0.4, 0.4, 0.6);
        } else {
          clearInterval(clenchInterval);
          gestureManager.processSample(performance.now(), 0, 0, 0, 0, 0, 0);
          updateSensorsUI(0, 0, 0, 0, 0, 0, true);
          callback();
        }
      }, 20);
    };

    emitClenchImpulse(() => {
      setIsSimulating(false);
    });
  };

  // 5. Emulate activation gesture
  const simulateActivate = () => {
    if (isSimulating) return;
    setIsSimulating(true);
    showNote('Жест активации...');

    const startTime = performance.now();
    const duration = 300;

    const interval = setInterval(() => {
      const elapsed = performance.now() - startTime;
      if (elapsed > duration) {
        clearInterval(interval);
        gestureManager.processSample(performance.now(), 0, 0, 0, 0, 0, 0);
        updateSensorsUI(0, 0, 0, 0, 0, 0, true);
        setIsSimulating(false);
        return;
      }

      const progress = elapsed / duration;
      const bell = Math.sin(progress * Math.PI);
      const gz = bell * 2.8;
      const ax = bell * 2.2;

      updateSensorsUI(0.2, 0.2, gz, ax, 0.3, 0.5);
      gestureManager.processSample(performance.now(), 0.2, 0.2, gz, ax, 0.3, 0.5);
    }, 25);
  };

  const handleStrategyChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    gestureManager.setStrategyName(e.target.value as EngineStrategy);
  };

  return (
    <div className="w-full max-w-md bg-neutral-900/80 backdrop-blur border border-white/10 rounded-2xl p-4 shadow-xl text-neutral-200">
      <div className="flex items-center justify-between pb-3 border-b border-white/10">
        <div className="flex items-center gap-2">
          <span className="w-2.5 h-2.5 rounded-full bg-cyan-400 animate-pulse" />
          <h2 className="text-xs font-semibold uppercase tracking-wider text-neutral-200">
            Эмулятор жестов & IMU сенсоров
          </h2>
        </div>
        <span className="text-[10px] text-neutral-400 bg-neutral-800 px-2 py-0.5 rounded-full">
          Watch 4+ IMU
        </span>
      </div>

      <div className="flex items-center justify-between mt-2.5">
        <p className="text-[11px] text-neutral-400 leading-relaxed">
          Тестируйте алгоритмы распознавания в реальном времени:
        </p>
        {activeGestureNote && (
          <span className="text-[10px] font-semibold text-cyan-300 bg-cyan-950/60 px-2 py-0.5 rounded-full border border-cyan-500/30 animate-fade-in">
            {activeGestureNote}
          </span>
        )}
      </div>

      {/* Simulator Buttons */}
      <div className="grid grid-cols-2 gap-2 mt-3">
        <button
          type="button"
          onClick={simulateRotateRight}
          disabled={isSimulating}
          className="flex flex-col items-center justify-center p-2.5 rounded-xl bg-neutral-800/90 hover:bg-neutral-700/90 active:scale-95 border border-white/5 transition-all text-center group disabled:opacity-50"
        >
          <span className="text-base mb-1 group-hover:translate-x-0.5 transition-transform">➡️</span>
          <span className="text-xs font-medium text-neutral-100">Поворот вправо</span>
          <span className="text-[9px] text-cyan-400">Next Track</span>
        </button>

        <button
          type="button"
          onClick={simulateRotateLeft}
          disabled={isSimulating}
          className="flex flex-col items-center justify-center p-2.5 rounded-xl bg-neutral-800/90 hover:bg-neutral-700/90 active:scale-95 border border-white/5 transition-all text-center group disabled:opacity-50"
        >
          <span className="text-base mb-1 group-hover:-translate-x-0.5 transition-transform">⬅️</span>
          <span className="text-xs font-medium text-neutral-100">Поворот влево</span>
          <span className="text-[9px] text-cyan-400">Prev Track</span>
        </button>

        <button
          type="button"
          onClick={simulateDoublePinch}
          disabled={isSimulating}
          className="flex flex-col items-center justify-center p-2.5 rounded-xl bg-neutral-800/90 hover:bg-neutral-700/90 active:scale-95 border border-white/5 transition-all text-center group disabled:opacity-50"
        >
          <span className="text-base mb-1 group-hover:scale-110 transition-transform">👌👌</span>
          <span className="text-xs font-medium text-neutral-100">Двойной щипок</span>
          <span className="text-[9px] text-cyan-400">Play / Pause</span>
        </button>

        <button
          type="button"
          onClick={simulateFistClench}
          disabled={isSimulating}
          className="flex flex-col items-center justify-center p-2.5 rounded-xl bg-neutral-800/90 hover:bg-neutral-700/90 active:scale-95 border border-white/5 transition-all text-center group disabled:opacity-50"
        >
          <span className="text-base mb-1 group-hover:scale-110 transition-transform">✊</span>
          <span className="text-xs font-medium text-neutral-100">Сжатие в кулак</span>
          <span className="text-[9px] text-cyan-400">🔓 Активация (15 с)</span>
        </button>
      </div>

      <div className="mt-2">
        <button
          type="button"
          onClick={simulateActivate}
          disabled={isSimulating}
          className="w-full flex items-center justify-center gap-2 p-2 rounded-xl bg-neutral-800/70 hover:bg-neutral-700/70 active:scale-95 border border-white/5 transition-all text-center group disabled:opacity-50"
        >
          <span className="text-sm group-hover:rotate-12 transition-transform">🔓</span>
          <span className="text-xs font-medium text-neutral-200">Быстрая активация приложения</span>
          <span className="text-[9px] text-cyan-400">(Защита от ложных)</span>
        </button>
      </div>

      {/* Live IMU Readings */}
      <div className="mt-3 p-2.5 rounded-xl bg-neutral-950/60 border border-white/5 flex flex-col gap-1.5">
        <div className="flex justify-between items-center text-[10px] text-neutral-400">
          <span>Сенсоры (Гироскоп & Акселерометр)</span>
          <span className={`font-mono text-[9px] ${isRunning ? 'text-emerald-400' : 'text-neutral-500'}`}>
            {isRunning ? '● АКТИВЕН' : '○ ОСТАНОВЛЕН'}
          </span>
        </div>

        <div className="grid grid-cols-2 gap-2 text-[10px] font-mono text-neutral-300">
          <div>
            <div className="text-neutral-400 text-[9px]">Gyro (X, Y, Z rad/s):</div>
            <div>
              X: {liveSensors.gyroX} | Y: {liveSensors.gyroY} | Z: {liveSensors.gyroZ}
            </div>
          </div>
          <div>
            <div className="text-neutral-400 text-[9px]">LinAcc (X, Y, Z m/s²):</div>
            <div>
              X: {liveSensors.linAccX} | Y: {liveSensors.linAccY} | Z: {liveSensors.linAccZ}
            </div>
          </div>
        </div>
      </div>

      {/* Strategy Switcher */}
      <div className="mt-3 flex items-center justify-between gap-2 pt-2 border-t border-white/5">
        <label htmlFor="strategy-select" className="text-[11px] text-neutral-400">
          Движок распознавания:
        </label>
        <select
          id="strategy-select"
          value={gestureManager.strategyName}
          onChange={handleStrategyChange}
          className="text-xs bg-neutral-800 text-neutral-200 rounded-lg px-2 py-1 border border-white/10 focus:outline-none focus:ring-1 focus:ring-cyan-500"
        >
          <option value="RawSensor (universal)">RawSensor (universal)</option>
          <option value="Samsung SDK (Galaxy Watch 4+)">Samsung SDK (Galaxy Watch 4+)</option>
          <option value="Web Motion API">Web Motion API</option>
        </select>
      </div>
    </div>
  );
};
