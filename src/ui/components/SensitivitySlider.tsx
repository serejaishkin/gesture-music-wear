import React from 'react';

interface SensitivitySliderProps {
  label: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  onValueChange: (val: number) => void;
  formatValue?: (val: number) => string;
}

export const SensitivitySlider: React.FC<SensitivitySliderProps> = ({
  label,
  value,
  min,
  max,
  step = 1,
  onValueChange,
  formatValue = (v) => v.toFixed(1),
}) => {
  const handleDecrement = () => {
    const next = Math.max(min, value - step);
    onValueChange(next);
  };

  const handleIncrement = () => {
    const next = Math.min(max, value + step);
    onValueChange(next);
  };

  return (
    <div className="w-full px-2 py-1 flex flex-col items-center select-none">
      <div className="text-xs text-neutral-300 font-medium tracking-tight text-center mb-1">
        {label}: <span className="text-cyan-400 font-semibold">{formatValue(value)}</span>
      </div>

      <div className="flex items-center gap-2 w-full max-w-[210px] justify-between">
        <button
          type="button"
          onClick={handleDecrement}
          disabled={value <= min}
          aria-label={`Decrease ${label}`}
          className="w-7 h-7 rounded-full bg-neutral-800 hover:bg-neutral-700 active:bg-cyan-600 disabled:opacity-30 disabled:pointer-events-none text-neutral-200 flex items-center justify-center font-bold text-sm transition-colors border border-white/5"
        >
          −
        </button>

        <div className="flex-1 px-1 flex items-center">
          <input
            type="range"
            min={min}
            max={max}
            step={step}
            value={value}
            onChange={(e) => onValueChange(parseFloat(e.target.value))}
            className="w-full h-1.5 bg-neutral-700 rounded-lg appearance-none cursor-pointer accent-cyan-400 focus:outline-none focus:ring-1 focus:ring-cyan-500/50"
          />
        </div>

        <button
          type="button"
          onClick={handleIncrement}
          disabled={value >= max}
          aria-label={`Increase ${label}`}
          className="w-7 h-7 rounded-full bg-neutral-800 hover:bg-neutral-700 active:bg-cyan-600 disabled:opacity-30 disabled:pointer-events-none text-neutral-200 flex items-center justify-center font-bold text-sm transition-colors border border-white/5"
        >
          +
        </button>
      </div>
    </div>
  );
};
