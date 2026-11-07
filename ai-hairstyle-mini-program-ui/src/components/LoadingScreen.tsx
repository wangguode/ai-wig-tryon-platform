import React, { useEffect, useState } from 'react';

interface LoadingScreenProps {
  onComplete: () => void;
}

export const LoadingScreen: React.FC<LoadingScreenProps> = ({ onComplete }) => {
  const [progress, setProgress] = useState(0);
  const [phase, setPhase] = useState(0);

  const phases = [
    'INITIALIZING SYSTEM...',
    'LOADING AI ENGINE...',
    'PREPARING INTERFACE...',
    'READY',
  ];

  useEffect(() => {
    const interval = setInterval(() => {
      setProgress((p) => {
        const next = p + Math.random() * 8 + 4;
        if (next >= 100) {
          clearInterval(interval);
          setTimeout(onComplete, 300);
          return 100;
        }
        return next;
      });
    }, 80);

    const phaseInterval = setInterval(() => {
      setPhase((p) => Math.min(p + 1, phases.length - 1));
    }, 600);

    return () => {
      clearInterval(interval);
      clearInterval(phaseInterval);
    };
  }, [onComplete]);

  return (
    <div
      className="fixed inset-0 flex flex-col items-center justify-center z-50"
      style={{ background: '#050a14' }}
    >
      <div className="absolute inset-0 tech-grid opacity-30" />

      {/* Glow orbs */}
      <div
        className="absolute top-1/3 left-1/2 -translate-x-1/2 -translate-y-1/2 w-64 h-64 rounded-full"
        style={{ background: 'radial-gradient(circle, rgba(0,212,255,0.08) 0%, transparent 70%)' }}
      />
      <div
        className="absolute bottom-1/3 left-1/2 -translate-x-1/2 translate-y-1/2 w-48 h-48 rounded-full"
        style={{ background: 'radial-gradient(circle, rgba(179,71,255,0.08) 0%, transparent 70%)' }}
      />

      <div className="relative z-10 flex flex-col items-center">
        {/* Logo */}
        <div className="relative mb-8">
          <div
            className="w-24 h-24 rounded-2xl flex items-center justify-center"
            style={{
              background: 'linear-gradient(135deg, rgba(0,212,255,0.15), rgba(179,71,255,0.15))',
              border: '1px solid rgba(0,212,255,0.25)',
              boxShadow: '0 0 40px rgba(0,212,255,0.15)',
            }}
          >
            <span className="text-4xl">💇</span>
          </div>
          {/* Rotating rings */}
          <div
            className="absolute -inset-3 rounded-3xl spin-slow"
            style={{ border: '1px dashed rgba(0,212,255,0.25)' }}
          />
          <div
            className="absolute -inset-6 rounded-3xl"
            style={{
              border: '1px dashed rgba(179,71,255,0.15)',
              animation: 'rotate360 6s linear infinite reverse',
            }}
          />
        </div>

        {/* Brand name */}
        <h1
          className="text-2xl font-black mb-1 gradient-text"
          style={{ fontFamily: '"Orbitron", monospace', letterSpacing: '0.1em' }}
        >
          HAIR AI
        </h1>
        <p className="text-xs mb-8" style={{ color: '#4b5563', letterSpacing: '0.15em' }}>
          智能发型定制平台
        </p>

        {/* Progress */}
        <div className="w-64">
          <div className="flex justify-between items-center mb-2">
            <span className="text-xs font-medium" style={{ color: '#4b5563', letterSpacing: '0.08em' }}>
              {phases[phase]}
            </span>
            <span className="text-xs font-bold gradient-text">{Math.round(progress)}%</span>
          </div>
          <div
            className="h-1 rounded-full overflow-hidden"
            style={{ background: 'rgba(255,255,255,0.05)' }}
          >
            <div
              className="h-full rounded-full progress-bar transition-all duration-200"
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>

        {/* Dots */}
        <div className="flex gap-2 mt-6">
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className="w-1.5 h-1.5 rounded-full"
              style={{
                background: '#00d4ff',
                opacity: 0.3,
                animation: `pulseDot 1.2s ease-in-out ${i * 0.2}s infinite`,
              }}
            />
          ))}
        </div>
      </div>
    </div>
  );
};
