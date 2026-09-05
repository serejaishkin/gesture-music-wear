import React, { useRef, useState, useEffect } from 'react';

export type WatchScreenType = 'tile' | 'control' | 'player' | 'training';

interface WatchFrameProps {
  children: React.ReactNode;
  currentScreen?: WatchScreenType;
  onSelectScreen?: (screen: WatchScreenType) => void;
  standalone?: boolean;
}

const SCREENS: Array<{ id: WatchScreenType; label: string; icon: string }> = [
  { id: 'tile', label: 'Плитка', icon: '📱' },
  { id: 'control', label: 'Настройки', icon: '⚙️' },
  { id: 'player', label: 'Плеер', icon: '🎵' },
  { id: 'training', label: 'Обучение', icon: '🎓' },
];

export const WatchFrame: React.FC<WatchFrameProps> = ({
  children,
  currentScreen = 'tile',
  onSelectScreen,
  standalone = false,
}) => {
  const scrollRef = useRef<HTMLDivElement>(null);
  const touchStartX = useRef<number | null>(null);
  const touchStartY = useRef<number | null>(null);
  const [timeStr, setTimeStr] = useState('10:08');

  useEffect(() => {
    const updateTime = () => {
      const d = new Date();
      const h = String(d.getHours()).padStart(2, '0');
      const m = String(d.getMinutes()).padStart(2, '0');
      setTimeStr(`${h}:${m}`);
    };
    updateTime();
    const interval = setInterval(updateTime, 10000);
    return () => clearInterval(interval);
  }, []);

  // Listen for rotary bezel events from native Android on Galaxy Watch
  useEffect(() => {
    const handleRotary = (e: any) => {
      if (scrollRef.current && e?.detail?.delta) {
        scrollRef.current.scrollBy({ top: e.detail.delta * 80, behavior: 'smooth' });
      }
    };
    window.addEventListener('wearRotary', handleRotary as EventListener);
    (window as any).onWearRotary = (delta: number) => {
      if (scrollRef.current) {
        scrollRef.current.scrollBy({ top: delta * 80, behavior: 'smooth' });
      }
    };
    return () => {
      window.removeEventListener('wearRotary', handleRotary as EventListener);
      delete (window as any).onWearRotary;
    };
  }, []);

  const handleTouchStart = (e: React.TouchEvent) => {
    touchStartX.current = e.touches[0].clientX;
    touchStartY.current = e.touches[0].clientY;
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (touchStartX.current === null || touchStartY.current === null || !onSelectScreen) return;
    const deltaX = e.changedTouches[0].clientX - touchStartX.current;
    const deltaY = e.changedTouches[0].clientY - touchStartY.current;

    // Only trigger swipe if horizontal movement is dominant and > 35px
    if (Math.abs(deltaX) > 35 && Math.abs(deltaX) > Math.abs(deltaY) * 1.2) {
      const currentIndex = SCREENS.findIndex((s) => s.id === currentScreen);
      if (deltaX < 0 && currentIndex < SCREENS.length - 1) {
        // Swipe left -> next screen
        onSelectScreen(SCREENS[currentIndex + 1].id);
      } else if (deltaX > 0 && currentIndex > 0) {
        // Swipe right -> previous screen
        onSelectScreen(SCREENS[currentIndex - 1].id);
      }
    }
    touchStartX.current = null;
    touchStartY.current = null;
  };

  // Pure Fullscreen Wear OS Layout for standalone / APK on Galaxy Watch 4
  if (standalone) {
    return (
      <div
        ref={scrollRef}
        onTouchStart={handleTouchStart}
        onTouchEnd={handleTouchEnd}
        className="w-full h-full min-h-screen bg-black text-neutral-100 overflow-y-auto overflow-x-hidden relative flex flex-col items-center justify-start scroll-smooth select-none focus:outline-none"
        style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
      >
        {/* Sticky Top Status Bar & Wear OS Screen Dots */}
        <header className="w-full pt-2 pb-1 flex flex-col items-center justify-center gap-1 shrink-0 sticky top-0 z-30 bg-black/90 backdrop-blur-xs">
          <div className="flex items-center gap-2">
            <span className="text-[11px] text-neutral-400 font-mono tracking-wider font-semibold">
              {timeStr}
            </span>
          </div>

          {/* Wear OS Page Indicator Dots */}
          {onSelectScreen && currentScreen && (
            <div className="flex items-center gap-1.5 bg-neutral-900/90 px-3 py-0.5 rounded-full border border-white/10 shadow-xs">
              {SCREENS.map((s) => {
                const isActive = currentScreen === s.id;
                return (
                  <button
                    key={s.id}
                    type="button"
                    onClick={() => onSelectScreen(s.id)}
                    title={`${s.label} (${s.icon})`}
                    className={`transition-all rounded-full ${
                      isActive
                        ? 'w-4 h-1.5 bg-cyan-400 shadow-sm shadow-cyan-500/50'
                        : 'w-1.5 h-1.5 bg-neutral-600 hover:bg-neutral-400'
                    }`}
                  />
                );
              })}
            </div>
          )}
        </header>

        {/* Circular AMOLED Safe-zone Content Container */}
        <main className="w-full max-w-[340px] px-3 pb-12 flex flex-col items-center">
          {children}
        </main>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center select-none relative">
      {/* Top Watch Band Lug */}
      <div className="w-32 h-5 sm:h-6 bg-gradient-to-b from-neutral-800 to-neutral-900 rounded-t-lg border-t border-neutral-700 shadow-md flex items-center justify-center">
        <div className="w-16 h-1 bg-neutral-700/50 rounded-full" />
      </div>

      {/* Watch Body Outer Bezel (Galaxy Watch 4+ circular casing) */}
      <div className="relative w-[330px] h-[330px] sm:w-[350px] sm:h-[350px] rounded-full p-2 sm:p-2.5 bg-gradient-to-tr from-neutral-900 via-neutral-800 to-neutral-900 shadow-2xl shadow-black/80 border-4 border-neutral-700 flex items-center justify-center transition-all">
        {/* Metallic Bezel Ring */}
        <div className="absolute inset-1 rounded-full border border-neutral-600/40 pointer-events-none" />

        {/* Physical Button Accents (right side of watch) */}
        <button
          type="button"
          onClick={() => onSelectScreen && onSelectScreen(currentScreen === 'tile' ? 'control' : 'tile')}
          title="Верхняя кнопка (Домой / Настройки)"
          className="absolute -right-2.5 top-[30%] w-2 h-10 bg-neutral-600 hover:bg-cyan-500 rounded-r-md shadow border border-neutral-500/50 cursor-pointer active:scale-95 transition-colors"
        />
        <button
          type="button"
          onClick={() => onSelectScreen && onSelectScreen(currentScreen === 'player' ? 'control' : 'player')}
          title="Нижняя кнопка (Назад / Плеер)"
          className="absolute -right-2.5 top-[60%] w-2 h-8 bg-neutral-600 hover:bg-cyan-500 rounded-r-md shadow border border-neutral-500/50 cursor-pointer active:scale-95 transition-colors"
        />

        {/* Inner Display (Round AMOLED Screen) */}
        <div
          ref={scrollRef}
          onTouchStart={handleTouchStart}
          onTouchEnd={handleTouchEnd}
          className="w-full h-full rounded-full bg-black overflow-y-auto overflow-x-hidden relative flex flex-col items-center scroll-smooth focus:outline-none"
          style={{
            scrollbarWidth: 'none',
            msOverflowStyle: 'none',
          }}
        >
          {/* Top Status Bar & Wear OS Page Indicator */}
          <div className="w-full pt-3 sm:pt-4 pb-1 flex flex-col items-center justify-center gap-1 shrink-0">
            <span className="text-[10px] text-neutral-400 font-mono tracking-wider">
              {timeStr}
            </span>

            {/* Wear OS Page Indicator Dots */}
            {onSelectScreen && currentScreen && (
              <div className="flex items-center gap-1.5 bg-neutral-900/80 px-2.5 py-0.5 rounded-full border border-white/5">
                {SCREENS.map((s) => {
                  const isActive = currentScreen === s.id;
                  return (
                    <button
                      key={s.id}
                      type="button"
                      onClick={() => onSelectScreen(s.id)}
                      title={`${s.label} (${s.icon})`}
                      className={`transition-all rounded-full ${
                        isActive
                          ? 'w-4 h-1.5 bg-cyan-400'
                          : 'w-1.5 h-1.5 bg-neutral-600 hover:bg-neutral-400'
                      }`}
                    />
                  );
                })}
              </div>
            )}
          </div>

          {/* Screen Content */}
          <div className="w-full max-w-[270px] pb-8 flex flex-col items-center">
            {children}
          </div>
        </div>
      </div>

      {/* Bottom Watch Band Lug */}
      <div className="w-32 h-5 sm:h-6 bg-gradient-to-t from-neutral-800 to-neutral-900 rounded-b-lg border-b border-neutral-700 shadow-md flex items-center justify-center">
        <div className="w-16 h-1 bg-neutral-700/50 rounded-full" />
      </div>
    </div>
  );
};
