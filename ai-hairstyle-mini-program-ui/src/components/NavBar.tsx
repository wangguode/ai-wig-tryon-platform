import React from 'react';
import { Home, Sparkles, Store, User } from 'lucide-react';

interface NavBarProps {
  currentPage: string;
  onNavigate: (page: string) => void;
}

const navItems = [
  { id: 'home', label: '首页', icon: Home },
  { id: 'tryon-upload', label: '试戴', icon: Sparkles },
  { id: 'store-list', label: '门店', icon: Store },
  { id: 'user', label: '我的', icon: User },
];

export const NavBar: React.FC<NavBarProps> = ({ currentPage, onNavigate }) => {
  const isActive = (id: string) => {
    if (id === 'home') return currentPage === 'home';
    if (id === 'tryon-upload') return currentPage.startsWith('tryon');
    if (id === 'store-list') return currentPage.startsWith('store');
    if (id === 'user') return currentPage.startsWith('user');
    return false;
  };

  return (
    <div className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-[420px] z-50">
      <div
        className="mx-3 mb-3 rounded-2xl flex items-center justify-around py-2"
        style={{
          background: 'rgba(5, 10, 20, 0.92)',
          backdropFilter: 'blur(20px)',
          border: '1px solid rgba(0, 212, 255, 0.15)',
          boxShadow: '0 -4px 30px rgba(0, 0, 0, 0.5), 0 0 20px rgba(0, 212, 255, 0.05)',
        }}
      >
        {navItems.map((item) => {
          const active = isActive(item.id);
          const Icon = item.icon;
          return (
            <button
              key={item.id}
              onClick={() => onNavigate(item.id)}
              className="flex flex-col items-center gap-1 px-4 py-1.5 rounded-xl transition-all duration-300 relative"
              style={{
                background: active ? 'rgba(0, 212, 255, 0.1)' : 'transparent',
              }}
            >
              {active && (
                <div
                  className="absolute top-0 left-1/2 -translate-x-1/2 w-8 h-0.5 rounded-full"
                  style={{ background: 'linear-gradient(90deg, #00d4ff, #b347ff)' }}
                />
              )}
              <Icon
                size={20}
                style={{
                  color: active ? '#00d4ff' : '#4b5563',
                  filter: active ? 'drop-shadow(0 0 6px rgba(0, 212, 255, 0.6))' : 'none',
                }}
              />
              <span
                className="text-xs font-medium"
                style={{
                  color: active ? '#00d4ff' : '#4b5563',
                  fontFamily: active ? 'inherit' : 'inherit',
                }}
              >
                {item.label}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
};
