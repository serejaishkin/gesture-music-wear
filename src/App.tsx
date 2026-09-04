import { useState, useEffect } from 'react';
import { gestureManager } from './gesture/GestureManager';
import { ControlScreen } from './ui/screens/ControlScreen';
import { PlayerScreen } from './ui/screens/PlayerScreen';
import { TrainingScreen } from './ui/screens/TrainingScreen';
import { QuickTileScreen } from './ui/screens/QuickTileScreen';
import { WatchFrame, WatchScreenType } from './ui/components/WatchFrame';
import { GestureSimulator } from './ui/components/GestureSimulator';
import { QuickAccessTileCard } from './ui/components/QuickAccessTileCard';
import { audioPlayer } from './media/AudioPlayerService';
import { Watch, Music2, Radio, Info } from 'lucide-react';

export function App() {
  const [currentScreen, setCurrentScreen] = useState<WatchScreenType>('tile');
  const [isRunning, setIsRunning] = useState(gestureManager.isRunning);
  const [strategyName, setStrategyName] = useState(gestureManager.strategyName);
  const [lastGesture, setLastGesture] = useState(gestureManager.lastGesture);
  const [saveMessage, setSaveMessage] = useState(gestureManager.saveMessage);
  const [settings, setSettings] = useState({ ...gestureManager.settings });

  // Training state
  const [trainingProgress, setTrainingProgress] = useState(gestureManager.trainingProgress);
  const [trainingRepetitions, setTrainingRepetitions] = useState(gestureManager.trainingRepetitions);
  const [trainingDone, setTrainingDone] = useState(gestureManager.trainingDone);
  const [trainingSuccess, setTrainingSuccess] = useState(gestureManager.trainingSuccess);

  // Audio track state
  const [currentTrack, setCurrentTrack] = useState(audioPlayer.getTrack());
  const [isAudioPlaying, setIsAudioPlaying] = useState(audioPlayer.getPlaybackState());

  // Detect Android WebView / APK environment or allow user toggle
  const isAndroidApk =
    typeof window !== 'undefined' &&
    (Boolean((window as any).AndroidBridge) ||
      window.location.protocol === 'file:' ||
      navigator.userAgent.includes('wv') ||
      window.innerWidth < 768);

  const [watchOnlyMode, setWatchOnlyMode] = useState<boolean>(isAndroidApk);

  useEffect(() => {
    const unsubGesture = gestureManager.subscribe(() => {
      setIsRunning(gestureManager.isRunning);
      setStrategyName(gestureManager.strategyName);
      setLastGesture(gestureManager.lastGesture);
      setSaveMessage(gestureManager.saveMessage);
      setSettings({ ...gestureManager.settings });
      setTrainingProgress(gestureManager.trainingProgress);
      setTrainingRepetitions(gestureManager.trainingRepetitions);
      setTrainingDone(gestureManager.trainingDone);
      setTrainingSuccess(gestureManager.trainingSuccess);
    });

    const unsubAudio = audioPlayer.subscribe(() => {
      setCurrentTrack(audioPlayer.getTrack());
      setIsAudioPlaying(audioPlayer.getPlaybackState());
    });

    return () => {
      unsubGesture();
      unsubAudio();
    };
  }, []);

  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-100 flex flex-col items-center justify-start p-2 sm:p-4 lg:p-6 select-none">
      {/* Top Header */}
      <header className="w-full max-w-5xl flex flex-wrap items-center justify-between gap-3 pb-4 mb-2 border-b border-white/10">
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-xl bg-cyan-600/20 border border-cyan-500/30 flex items-center justify-center text-cyan-400 shrink-0">
            <Music2 className="w-4 h-4" />
          </div>
          <div>
            <h1 className="text-sm sm:text-base font-bold text-white flex items-center gap-2">
              Gesture Music Wear
              <span className="text-[9px] uppercase font-semibold px-2 py-0.5 rounded-full bg-cyan-950 text-cyan-300 border border-cyan-800/60">
                {isAndroidApk ? 'Wear OS APK' : 'Wear OS'}
              </span>
            </h1>
            <p className="text-[11px] text-neutral-400 hidden sm:block">
              Galaxy Watch 4+ Wrist Gesture Music Controller
            </p>
          </div>
        </div>

        {/* Global status pills & Mode Switcher */}
        <div className="flex items-center gap-2 text-xs flex-wrap">
          {/* Mode Switcher Button */}
          <button
            type="button"
            onClick={() => setWatchOnlyMode(!watchOnlyMode)}
            className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium border transition-all ${
              watchOnlyMode
                ? 'bg-cyan-950 text-cyan-300 border-cyan-700 shadow-sm'
                : 'bg-neutral-900 text-neutral-300 border-white/10 hover:border-cyan-500/50'
            }`}
          >
            <Watch className="w-3.5 h-3.5" />
            <span>{watchOnlyMode ? '⌚ Только часы' : '🖥️ Стенд + Часы'}</span>
          </button>

          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-neutral-900 border border-white/10 text-neutral-300">
            <Radio
              className={`w-3.5 h-3.5 ${isRunning ? 'text-cyan-400 animate-pulse' : 'text-neutral-500'}`}
            />
            <span className="font-medium text-[11px]">{isRunning ? 'АКТИВЕН' : 'СТОП'}</span>
          </div>

          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-neutral-900 border border-white/10 text-neutral-300">
            <Watch className="w-3.5 h-3.5 text-cyan-400" />
            <span className="font-medium text-[11px]">
              {settings.leftHand ? 'Левая рука' : 'Правая рука'}
            </span>
          </div>
        </div>
      </header>

      {/* Main Workspace Layout: Smartwatch View & Simulator / Companion Panel */}
      <main
        className={`w-full max-w-5xl ${
          watchOnlyMode
            ? 'flex flex-col items-center justify-center py-2'
            : 'grid grid-cols-1 lg:grid-cols-12 gap-6 items-start justify-center'
        }`}
      >
        {/* Left Column (or Centered Standalone): Smartwatch Hardware Frame */}
        <div
          className={`${
            watchOnlyMode
              ? 'w-full flex flex-col items-center justify-center'
              : 'lg:col-span-6 flex flex-col items-center justify-center'
          }`}
        >
          <div className="text-[11px] text-neutral-400 font-medium mb-2 flex items-center gap-1.5">
            <Watch className="w-3.5 h-3.5 text-cyan-400" />
            <span>Galaxy Watch 4+ (396×396 Circular AMOLED)</span>
          </div>

          <WatchFrame
            standalone={watchOnlyMode}
            currentScreen={currentScreen}
            onSelectScreen={setCurrentScreen}
          >
            {currentScreen === 'tile' && (
              <QuickTileScreen
                isRunning={isRunning}
                leftHand={settings.leftHand}
                lastGesture={lastGesture}
                onOpenSettings={() => setCurrentScreen('control')}
                onOpenPlayer={() => setCurrentScreen('player')}
              />
            )}

            {currentScreen === 'control' && (
              <ControlScreen
                onOpenTile={() => setCurrentScreen('tile')}
                onOpenPlayer={() => setCurrentScreen('player')}
                onOpenTraining={() => setCurrentScreen('training')}
                isRunning={isRunning}
                strategyName={strategyName}
                lastGesture={lastGesture}
                saveMessage={saveMessage}
                angleThreshold={settings.angleThreshold}
                pinchThreshold={settings.pinchThreshold}
                fistClenchThreshold={settings.fistClenchThreshold}
                fistClenchEnabled={settings.fistClenchEnabled}
                minDuration={settings.minDuration}
                maxDuration={settings.maxDuration}
                gestureCooldown={settings.gestureCooldown}
                leftHand={settings.leftHand}
                vibrationEnabled={settings.vibrationEnabled}
                vibrationIntensity={settings.vibrationIntensity}
                vibrationDuration={settings.vibrationDuration}
                speedTolerance={settings.speedTolerance}
              />
            )}

            {currentScreen === 'player' && (
              <PlayerScreen
                onBack={() => setCurrentScreen('control')}
                isRunning={isRunning}
              />
            )}

            {currentScreen === 'training' && (
              <TrainingScreen
                onBack={() => setCurrentScreen('control')}
                trainingProgress={trainingProgress}
                trainingRepetitions={trainingRepetitions}
                trainingDone={trainingDone}
                trainingSuccess={trainingSuccess}
              />
            )}
          </WatchFrame>

          <p className="text-[10px] text-neutral-500 mt-2 text-center max-w-[280px]">
            Свайп влево/вправо по экрану, верхние точки Wear OS или боковые кнопки для навигации
          </p>
        </div>

        {/* Right Column: Quick Access Tile & Interactive Test Suite (hidden in watchOnlyMode) */}
        {!watchOnlyMode && (
          <div className="lg:col-span-6 flex flex-col gap-4">
            {/* Quick Access Tile Card */}
            <QuickAccessTileCard
              isRunning={isRunning}
              leftHand={settings.leftHand}
              lastGesture={lastGesture}
              onOpenWatchTile={() => setCurrentScreen('tile')}
              onOpenWatchControl={() => setCurrentScreen('control')}
              onOpenWatchPlayer={() => setCurrentScreen('player')}
            />

            {/* Active Audio Playback Card */}
            <div className="bg-neutral-900/80 border border-white/10 rounded-2xl p-4 shadow-xl">
              <div className="flex items-center justify-between pb-3 border-b border-white/10">
                <div className="flex items-center gap-2">
                  <Music2 className="w-4 h-4 text-cyan-400" />
                  <h2 className="text-xs font-semibold uppercase tracking-wider text-neutral-200">
                    Активная аудиосессия (MediaSession)
                  </h2>
                </div>
                <span
                  className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${
                    isAudioPlaying
                      ? 'bg-emerald-950 text-emerald-300 border border-emerald-800'
                      : 'bg-neutral-800 text-neutral-400'
                  }`}
                >
                  {isAudioPlaying ? '▶ Играет' : '⏸ Пауза'}
                </span>
              </div>

              <div className="mt-3 flex items-center gap-3">
                <div className="w-12 h-12 rounded-xl bg-cyan-950/60 border border-cyan-500/30 flex items-center justify-center text-cyan-300 text-xl font-bold">
                  🎵
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-semibold text-white truncate">
                    {currentTrack.title}
                  </div>
                  <div className="text-xs text-neutral-400 truncate">
                    {currentTrack.artist} • {currentTrack.album}
                  </div>
                </div>
              </div>
            </div>

            {/* Gesture Simulator */}
            <GestureSimulator isRunning={isRunning} leftHand={settings.leftHand} />

            {/* Architecture and Features Card */}
            <div className="bg-neutral-900/40 border border-white/5 rounded-2xl p-4 text-xs text-neutral-400 leading-relaxed">
              <div className="flex items-center gap-2 text-neutral-300 font-semibold mb-2">
                <Info className="w-4 h-4 text-cyan-400" />
                <span>Алгоритмы распознавания и контроль темпа</span>
              </div>
              <ul className="list-disc list-inside space-y-1 text-[11px] text-neutral-400">
                <li>
                  <strong className="text-neutral-200">GestureTrainer (DTW + Speed)</strong>: 5-кратное обучение с замером длительности и скорости, автоматический фильтр по темпу (±{settings.speedTolerance}%).
                </li>
                <li>
                  <strong className="text-neutral-200">Виброотклик (Haptic Feedback)</strong>: Аппаратная вибрация часов через AndroidBridge ({settings.vibrationIntensity}, {settings.vibrationDuration} мс) с возможностью отключения.
                </li>
                <li>
                  <strong className="text-neutral-200">WristRotationDetector</strong>: Фильтр нижних частот, интегрирование гироскопа X ({settings.angleThreshold}°).
                </li>
                <li>
                  <strong className="text-neutral-200">DoublePinch & Fist</strong>: Конечно-автоматный детектор двойного щипка и сжатия кулака.
                </li>
              </ul>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
