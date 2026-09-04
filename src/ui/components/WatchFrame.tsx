import React, { useRef, useState, useEffect } from 'react';

export type WatchScreenType = 'tile' | 'control' | 'player' | 'training';

interface WatchFrameProps {
  children: React.ReactNode;
  currentScreen?: WatchScreenType;
  onSelectScreen?: (screen: WatchScreenType) => void;
  standalone?: boolean;
}

const SCREENS: Array<{ id: WatchScreenType; label: string; icon: string }> = [
  { id: 'tile', label: 'Главная', icon: '⌂' },
  { id: 'control', label: 'Настройки', icon: '⚙' },
  { id: 'player', label: 'Плеер', icon: '♫' },
  { id: 'training', label: 'Обучение', icon: '✦' },
];

export const WatchFrame: React.FC<WatchFrameProps> = ({
  children,
  currentScreen = 'tile',
  onSelectScreen,
  standalone = false,
}) => {
  const touchStartX = useRef<number | null>(null);
  const touchStartY = useRef<number | null>(null);
  const [timeStr, setTimeStr] = useState('10:08');

  useEffect(() => {
    const updateTime = () => {
      const d = new Date();
      setTimeStr(`${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`);
    };
    updateTime();
    const interval = setInterval(updateTime, 10000);
    return () => clearInterval(interval);
  }, []);

  const handleTouchStart = (e: React.TouchEvent) => {
    touchStartX.current = e.touches[0].clientX;
    touchStartY.current = e.touches[0].clientY;
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (touchStartX.current === null || touchStartY.current === null || !onSelectScreen) return;
    const deltaX = e.changedTouches[0].clientX - touchStartX.current;
    const deltaY = e.changedTouches[0].clientY - touchStartY.current;

    if (Math.abs(deltaX) > 45 && Math.abs(deltaX) > Math.abs(deltaY) * 1.4) {
      const currentIndex = SCREENS.findIndex((s) => s.id === currentScreen);
      if (deltaX < 0 && currentIndex < SCREENS.length - 1) onSelectScreen(SCREENS[currentIndex + 1].id);
      if (deltaX > 0 && currentIndex > 0) onSelectScreen(SCREENS[currentIndex - 1].id);
    }
    touchStartX.current = null;
    touchStartY.current = null;
  };

  return (
    <div className={`watch-shell flex flex-col items-center select-none relative ${standalone ? 'p-2' : ''}`}>
      <div className="w-28 h-5 bg-gradient-to-b from-[#171a1c] to-[#080a0b] rounded-t-xl border border-white/10 border-b-0 shadow-lg flex items-center justify-center">
        <div className="w-12 h-1 rounded-full bg-white/10" />
      </div>

      <div className="relative w-[330px] h-[330px] sm:w-[350px] sm:h-[350px] rounded-full p-[7px] bg-[radial-gradient(circle_at_35%_25%,#3a3f42,#111416_55%,#070809)] border border-white/15 flex items-center justify-center">
        <div className="absolute inset-[4px] rounded-full border border-white/10 pointer-events-none" />
        <div className="absolute inset-[9px] rounded-full border border-black/80 pointer-events-none" />

        <button
          type="button"
          onClick={() => onSelectScreen && onSelectScreen(currentScreen === 'tile' ? 'control' : 'tile')}
          title="Верхняя кнопка"
          className="watch-button absolute -right-[6px] top-[29%] w-[6px] h-11 bg-[#34393c] hover:bg-cyan-500 rounded-r-md border border-white/10 cursor-pointer active:scale-95 transition-colors z-10"
        />
        <button
          type="button"
          onClick={() => onSelectScreen && onSelectScreen(currentScreen === 'player' ? 'control' : 'player')}
          title="Нижняя кнопка"
          className="watch-button absolute -right-[6px] top-[59%] w-[6px] h-8 bg-[#34393c] hover:bg-cyan-500 rounded-r-md border border-white/10 cursor-pointer active:scale-95 transition-colors z-10"
        />

        <div
          onTouchStart={handleTouchStart}
          onTouchEnd={handleTouchEnd}
          className="watch-display w-full h-full rounded-full overflow-y-auto overflow-x-hidden relative flex flex-col items-center scroll-smooth focus:outline-none"
          style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
        >
          <div className="w-full pt-3 pb-1 flex flex-col items-center justify-center gap-1 shrink-0">
            <span className="text-[10px] text-neutral-500 font-mono tracking-[0.18em]">{timeStr}</span>
            {onSelectScreen && (
              <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-white/[0.035] border border-white/[0.06]">
                {SCREENS.map((s) => {
                  const isActive = currentScreen === s.id;
                  return (
                    <button
                      key={s.id}
                      type="button"
                      onClick={() => onSelectScreen(s.id)}
                      title={s.label}
                      className={`transition-all duration-200 rounded-full ${isActive ? 'w-4 h-1 bg-cyan-400 shadow-[0_0_8px_rgba(34,211,238,.55)]' : 'w-1 h-1 bg-neutral-700 hover:bg-neutral-400'}`}
                    />
                  );
                })}
              </div>
            )}
          </div>

          <div className="w-full max-w-[270px] pb-7 flex flex-col items-center">
            {children}
          </div>
        </div>
      </div>

      <div className="w-28 h-5 bg-gradient-to-t from-[#171a1c] to-[#080a0b] rounded-b-xl border border-white/10 border-t-0 shadow-lg flex items-center justify-center">
        <div className="w-12 h-1 rounded-full bg-white/10" />
      </div>
    </div>
  );
};
