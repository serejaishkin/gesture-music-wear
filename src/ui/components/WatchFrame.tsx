import React, { useRef } from 'react';

interface WatchFrameProps {
  children: React.ReactNode;
}

export const WatchFrame: React.FC<WatchFrameProps> = ({ children }) => {
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
        <div className="absolute -right-2.5 top-[30%] w-1.5 h-10 bg-neutral-600 rounded-r-md shadow border border-neutral-500/50" />
        <div className="absolute -right-2.5 top-[60%] w-1.5 h-8 bg-neutral-600 rounded-r-md shadow border border-neutral-500/50" />

        {/* Inner Display (Round AMOLED Screen) */}
        <div
          ref={scrollRef}
          className="w-full h-full rounded-full bg-black overflow-y-auto overflow-x-hidden relative flex flex-col items-center scroll-smooth focus:outline-none"
          style={{
            scrollbarWidth: 'none',
            msOverflowStyle: 'none',
          }}
        >
          {/* Top Status Bar Notch / Padding */}
          <div className="w-full pt-6 flex justify-center text-[10px] text-neutral-400 font-mono tracking-wider">
            <span>10:08</span>
          </div>

          {/* Screen Content */}
          <div className="w-full max-w-[270px] pb-10 flex flex-col items-center">
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
