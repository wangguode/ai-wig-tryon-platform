import React, { useCallback, useRef, useState } from 'react';
import { ChevronRight } from 'lucide-react';

interface CompareSliderProps {
  /** 原图（左） */
  before: string;
  /** 效果图（右） */
  after: string;
  height?: number;
  beforeLabel?: string;
  afterLabel?: string;
  compact?: boolean;
}

/**
 * 原图 / 效果图 滑动对比组件（左右拖动分割线）。
 */
export const CompareSlider: React.FC<CompareSliderProps> = ({
  before,
  after,
  height = 300,
  beforeLabel = '原图',
  afterLabel = '✨ 效果图',
  compact = false,
}) => {
  const [sliderPos, setSliderPos] = useState(50);
  const [isDragging, setIsDragging] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const updateByClientX = useCallback((clientX: number) => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const x = Math.max(0, Math.min(clientX - rect.left, rect.width));
    setSliderPos((x / rect.width) * 100);
  }, []);

  const handlePointerDown = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    setIsDragging(true);
    e.currentTarget.setPointerCapture(e.pointerId);
    updateByClientX(e.clientX);
  }, [updateByClientX]);

  const handlePointerMove = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    if (!isDragging) return;
    updateByClientX(e.clientX);
  }, [isDragging, updateByClientX]);

  const handlePointerUp = useCallback(() => {
    setIsDragging(false);
  }, []);

  return (
    <div
      ref={containerRef}
      className="compare-slider rounded-2xl overflow-hidden select-none"
      style={{ height, border: '1px solid rgba(0,212,255,0.15)', position: 'relative', touchAction: 'none', background: '#050a14' }}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerCancel={handlePointerUp}
    >
      {/* 效果图（右，铺满） */}
      <div className="absolute inset-0">
        <img src={after} alt="after" className="w-full h-full object-contain" />
        <div
          className="absolute top-2 right-2 px-2 py-0.5 rounded-full text-xs font-medium"
          style={{ background: 'rgba(0,212,255,0.15)', border: '1px solid rgba(0,212,255,0.3)', color: '#00d4ff' }}
        >
          {afterLabel}
        </div>
      </div>

      {/* 原图（左，按分割线裁切） */}
      <div className="absolute inset-0 overflow-hidden" style={{ clipPath: `inset(0 ${100 - sliderPos}% 0 0)` }}>
        <img src={before} alt="before" className="w-full h-full object-contain" />
        <div
          className="absolute top-2 left-2 px-2 py-0.5 rounded-full text-xs font-medium"
          style={{ background: 'rgba(255,255,255,0.15)', border: '1px solid rgba(255,255,255,0.25)', color: '#fff' }}
        >
          {beforeLabel}
        </div>
      </div>

      {/* 分割线 + 手柄 */}
      <div
        className="absolute top-0 bottom-0 w-0.5"
        style={{
          left: `${sliderPos}%`,
          background: 'linear-gradient(to bottom, #00d4ff, #b347ff)',
          boxShadow: '0 0 10px rgba(0,212,255,0.6)',
        }}
      >
        <div
          className="absolute top-1/2 -translate-y-1/2 -translate-x-1/2 rounded-full flex items-center justify-center cursor-col-resize"
          style={{
            width: compact ? 28 : 36,
            height: compact ? 28 : 36,
            background: 'linear-gradient(135deg, #00d4ff, #b347ff)',
            boxShadow: '0 0 15px rgba(0,212,255,0.5)',
          }}
        >
          <div className="flex items-center gap-0.5">
            <ChevronRight size={compact ? 7 : 8} style={{ color: '#050a14', transform: 'rotate(180deg)' }} />
            <ChevronRight size={compact ? 7 : 8} style={{ color: '#050a14' }} />
          </div>
        </div>
      </div>

      {!compact && (
        <div
          className="absolute bottom-2 left-1/2 -translate-x-1/2 flex items-center gap-1 px-2 py-1 rounded-full"
          style={{ background: 'rgba(5,10,20,0.7)' }}
        >
          <span className="text-xs" style={{ color: '#94a3b8' }}>← 左右拖动对比 →</span>
        </div>
      )}
    </div>
  );
};
