import React from 'react';
import { ChevronLeft } from 'lucide-react';

interface PageHeaderProps {
  title: string;
  onBack?: () => void;
  rightContent?: React.ReactNode;
  transparent?: boolean;
}

export const PageHeader: React.FC<PageHeaderProps> = ({
  title,
  onBack,
  rightContent,
  transparent = false,
}) => {
  return (
    <div
      className="sticky top-0 z-40 flex items-center justify-between px-4 py-3"
      style={{
        background: transparent
          ? 'transparent'
          : 'rgba(5, 10, 20, 0.9)',
        backdropFilter: transparent ? 'none' : 'blur(16px)',
        borderBottom: transparent ? 'none' : '1px solid rgba(0, 212, 255, 0.08)',
      }}
    >
      <div className="flex items-center gap-2 min-w-[80px]">
        {onBack && (
          <button
            onClick={onBack}
            className="flex items-center gap-1 text-sm transition-all duration-200 hover:text-cyan-400"
            style={{ color: '#94a3b8' }}
          >
            <ChevronLeft size={20} />
            <span>返回</span>
          </button>
        )}
      </div>

      <h1
        className="text-base font-semibold text-center"
        style={{
          color: '#e2e8f0',
          fontFamily: '"Orbitron", monospace',
          letterSpacing: '0.05em',
        }}
      >
        {title}
      </h1>

      <div className="min-w-[80px] flex justify-end">
        {rightContent}
      </div>
    </div>
  );
};
