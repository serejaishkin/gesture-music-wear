import React, { useRef } from 'react';

export type WatchScreenType = 'tile' | 'control' | 'player' | 'training';

interface WatchFrameProps {
  children: React.ReactNode;
  currentScreen?: WatchScreenType;
  onSelectScreen?: (screen: WatchScreenType) => void;
}

const SCREENS: Array<{ id: WatchScreenType; label: string; icon: string }> = [
  { id: 'tile', label: 'Плитка', icon: '📱' },
  { id: 'control', label: 'Настройки', icon: '⚙️' },
  { id: 'player', label: 'Плеер', icon: '🎵' },
  { id: 'training', label: 'Обучение', icon: '🎓' },
];

export const WatchFrame: React.FC<WatchFrameProps> = ({
  children,
  currentScreen,
  onSelectScreen,
}) => {
  const scrollRef = useRef<HTMLDivElement>(null);

  return (
    <div className="flex flex-col items-center select-none relative">
      {/* Top Watch Band Lug */}
      <div className="w-32 h-6 bg-gradient-to-b from-neutral-800 to-neutral-900 rounded-t-lg border-t border-neutral-700 shadow-md flex items-center justify-center">
        <div className="w-16 h-1 bg-neutral-700/50 rounded-full" />
      </div>

      {/* Watch Body Outer Bezel (Galaxy Watch 4+ circular casing) */}
      <div className="relative w-[340px] h-[340px] rounded-full p-2.5 bg-gradient-to-tr from-neutral-900 via-neutral-800 to-neutral-900 shadow-2xl shadow-black/80 border-4 border-neutral-700 flex items-center justify-center">
        {/* Metallic Bezel Ring */}
        <div className="absolute inset-1 rounded-full border border-neutral-600/40 pointer-events-none" />

        {/* Physical Button Accents (right side of watch) */}
        <button
          type="button"
          onClick={() => onSelectScreen && onSelectScreen(currentScreen === 'tile' ? 'control' : 'tile')}
          title="Верхняя физическая кнопка (Домой / Плитка)"
          className="absolute -right-2.5 top-[30%] w-1.5 h-10 bg-neutral-600 hover:bg-cyan-500 rounded-r-md shadow border border-neutral-500/50 cursor-pointer active:scale-95 transition-colors"
        />
        <button
          type="button"
          onClick={() => onSelectScreen && onSelectScreen(currentScreen === 'player' ? 'control' : 'player')}
          title="Нижняя физическая кнопка (Назад / Плеер)"
          className="absolute -right-2.5 top-[60%] w-1.5 h-8 bg-neutral-600 hover:bg-cyan-500 rounded-r-md shadow border border-neutral-500/50 cursor-pointer active:scale-95 transition-colors"
        />

        {/* Inner Display (Round AMOLED Screen) */}
        <div
          ref={scrollRef}
          className="w-full h-full rounded-full bg-black overflow-y-auto overflow-x-hidden relative flex flex-col items-center scroll-smooth focus:outline-none"
          style={{
            scrollbarWidth: 'none',
            msOverflowStyle: 'none',
          }}
        >
          {/* Top Status Bar & Wear OS Page Indicator */}
          <div className="w-full pt-4 pb-1 flex flex-col items-center justify-center gap-1">
            <span className="text-[10px] text-neutral-400 font-mono tracking-wider">
              10:08
            </span>

            {/* Wear OS Page Indicator Dots */}
            {onSelectScreen && currentScreen && (
              <div className="flex items-center gap-1.5 bg-neutral-900/60 px-2.5 py-0.5 rounded-full border border-white/5">
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
      <div className="w-32 h-6 bg-gradient-to-t from-neutral-800 to-neutral-900 rounded-b-lg border-b border-neutral-700 shadow-md flex items-center justify-center">
        <div className="w-16 h-1 bg-neutral-700/50 rounded-full" />
      </div>
    </div>
  );
};
