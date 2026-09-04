import React, { useState } from 'react';
import { gestureManager } from '../../gesture/GestureManager';
import { EngineStrategy } from '../../types';

interface GestureSimulatorProps {
  isRunning: boolean;
  leftHand: boolean;
}

export const GestureSimulator: React.FC<GestureSimulatorProps> = ({ isRunning, leftHand }) => {
  const [isSimulating, setIsSimulating] = useState(false);
  const [liveSensors, setLiveSensors] = useState({
    gyroX: 0,
    gyroY: 0,
    gyroZ: 0,
    linAccX: 0,
    linAccY: 0,
    linAccZ: 0,
  });

  // Emulate physical wrist rotation right
  const simulateRotateRight = () => {
    if (isSimulating) return;
    setIsSimulating(true);

    const startTime = performance.now();
    const duration = 240; // ms
    const direction = leftHand ? 1 : -1; // -2.8 rad/s creates negative angle in gyroX for right hand
    let step = 0;

    const interval = setInterval(() => {
      const elapsed = performance.now() - startTime;
      if (elapsed > duration) {
        clearInterval(interval);
        // Settle back to quiet
        gestureManager.processSample(performance.now(), 0, 0, 0, 0, 0, 0);
        setLiveSensors({ gyroX: 0, gyroY: 0, gyroZ: 0, linAccX: 0, linAccY: 0, linAccZ: 0 });
        setIsSimulating(false);
        return;
      }

      step++;
      const progress = elapsed / duration;
      const bell = Math.sin(progress * Math.PI); // peak in the middle
      const gx = direction * bell * 3.2; // 3.2 rad/s angular speed
      const linAccY = (Math.random() - 0.5) * 4;

      setLiveSensors({
        gyroX: Number(gx.toFixed(2)),
        gyroY: 0,
        gyroZ: 0,
        linAccX: 0,
        linAccY: Number(linAccY.toFixed(2)),
        linAccZ: 0,
      });

      gestureManager.processSample(
        performance.now(),
        gx,
        0,
        0,
        0,
        linAccY,
        0
      );
    }, 20);
  };

  // Emulate physical wrist rotation left
  const simulateRotateLeft = () => {
    if (isSimulating) return;
    setIsSimulating(true);

    const startTime = performance.now();
    const duration = 240;
    const direction = leftHand ? -1 : 1; // +3.2 rad/s
    let step = 0;

    const interval = setInterval(() => {
      const elapsed = performance.now() - startTime;
      if (elapsed > duration) {
        clearInterval(interval);
        gestureManager.processSample(performance.now(), 0, 0, 0, 0, 0, 0);
        setLiveSensors({ gyroX: 0, gyroY: 0, gyroZ: 0, linAccX: 0, linAccY: 0, linAccZ: 0 });
        setIsSimulating(false);
        return;
      }

      step++;
      const progress = elapsed / duration;
      const bell = Math.sin(progress * Math.PI);
      const gx = direction * bell * 3.2;
      const linAccY = (Math.random() - 0.5) * 4;

      setLiveSensors({
        gyroX: Number(gx.toFixed(2)),
        gyroY: 0,
        gyroZ: 0,
        linAccX: 0,
        linAccY: Number(linAccY.toFixed(2)),
        linAccZ: 0,
      });

      gestureManager.processSample(
        performance.now(),
        gx,
        0,
        0,
        0,
        linAccY,
        0
      );
    }, 20);
  };

  // Emulate physical double pinch
  const simulateDoublePinch = () => {
    if (isSimulating) return;
    setIsSimulating(true);

    const emitPinch = (callback: () => void) => {
      // 1. Z acceleration UP spike (4.2 m/s^2), quiet gyro
      gestureManager.processSample(performance.now(), 0.1, 0.1, 0.1, 0.3, 0.4, 4.2);
      setLiveSensors({ gyroX: 0.1, gyroY: 0.1, gyroZ: 0.1, linAccX: 0.3, linAccY: 0.4, linAccZ: 4.2 });

      setTimeout(() => {
        // 2. Z acceleration DOWN rebound (-3.1 m/s^2)
        gestureManager.processSample(performance.now(), 0.05, 0.05, 0.05, 0.2, 0.2, -3.1);
        setLiveSensors({ gyroX: 0.05, gyroY: 0.05, gyroZ: 0.05, linAccX: 0.2, linAccY: 0.2, linAccZ: -3.1 });

        setTimeout(() => {
          // 3. Calm
          gestureManager.processSample(performance.now(), 0, 0, 0, 0, 0, 0);
          setLiveSensors({ gyroX: 0, gyroY: 0, gyroZ: 0, linAccX: 0, linAccY: 0, linAccZ: 0 });
          callback();
        }, 60);
      }, 50);
    };

    // First pinch
    emitPinch(() => {
      setTimeout(() => {
        // Second pinch within window
        emitPinch(() => {
          setIsSimulating(false);
        });
      }, 160);
    });
  };

  // Emulate activation gesture
  const simulateActivate = () => {
    if (isSimulating) return;
    setIsSimulating(true);

    const startTime = performance.now();
    const duration = 300;

    const interval = setInterval(() => {
      const elapsed = performance.now() - startTime;
      if (elapsed > duration) {
        clearInterval(interval);
        gestureManager.processSample(performance.now(), 0, 0, 0, 0, 0, 0);
        setLiveSensors({ gyroX: 0, gyroY: 0, gyroZ: 0, linAccX: 0, linAccY: 0, linAccZ: 0 });
        setIsSimulating(false);
        return;
      }

      const progress = elapsed / duration;
      const bell = Math.sin(progress * Math.PI);
      const gz = bell * 2.8;
      const ax = bell * 2.2;

      setLiveSensors({
        gyroX: 0.2,
        gyroY: 0.2,
        gyroZ: Number(gz.toFixed(2)),
        linAccX: Number(ax.toFixed(2)),
        linAccY: 0.3,
        linAccZ: 0.5,
      });

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

      <p className="text-[11px] text-neutral-400 mt-2.5 leading-relaxed">
        Тестируйте алгоритмы распознавания в браузере (WristRotationDetector, DoublePinchDetector, DTW):
      </p>

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
          onClick={simulateActivate}
          disabled={isSimulating}
          className="flex flex-col items-center justify-center p-2.5 rounded-xl bg-neutral-800/90 hover:bg-neutral-700/90 active:scale-95 border border-white/5 transition-all text-center group disabled:opacity-50"
        >
          <span className="text-base mb-1 group-hover:rotate-12 transition-transform">🔓</span>
          <span className="text-xs font-medium text-neutral-100">Активация</span>
          <span className="text-[9px] text-cyan-400">Arm Guard (15s)</span>
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
