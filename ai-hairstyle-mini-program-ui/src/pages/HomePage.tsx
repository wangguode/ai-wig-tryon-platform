import React, { useState } from 'react';
import { Search, MapPin, ChevronRight, Sparkles, Zap, TrendingUp, Bell } from 'lucide-react';
import { hairStyles, stores } from '../data/mockData';
import { StoreCard } from '../components/StoreCard';
import { priceRangeCompact } from '../utils/price';

interface HomePageProps {
  onNavigate: (page: string, data?: any) => void;
}

const ParticlesBg = () => (
  <div className="absolute inset-0 overflow-hidden pointer-events-none">
    {[...Array(12)].map((_, i) => (
      <div
        key={i}
        className="absolute rounded-full"
        style={{
          width: Math.random() * 4 + 2,
          height: Math.random() * 4 + 2,
          left: `${Math.random() * 100}%`,
          top: `${Math.random() * 100}%`,
          background: i % 2 === 0 ? '#00d4ff' : '#b347ff',
          opacity: Math.random() * 0.4 + 0.1,
          animation: `float ${Math.random() * 4 + 4}s ease-in-out ${Math.random() * 2}s infinite alternate`,
        }}
      />
    ))}
  </div>
);

export const HomePage: React.FC<HomePageProps> = ({ onNavigate }) => {
  const [searchText, setSearchText] = useState('');
  const [activeTab, setActiveTab] = useState('全部');
  const categories = ['全部', '短发', '中长发', '长发', '卷发'];

  const filteredHair = hairStyles
    .filter(h => activeTab === '全部' || h.category === activeTab)
    .filter(h => !searchText || h.name.includes(searchText))
    .slice(0, 4);

  const nearbyStores = stores.slice(0, 2);

  return (
    <div className="page-enter pb-24">
      {/* Hero Header */}
      <div
        className="relative overflow-hidden"
        style={{
          background: 'linear-gradient(135deg, #050a14 0%, #0a1628 50%, #0d0a20 100%)',
          minHeight: 220,
        }}
      >
        <ParticlesBg />
        {/* Grid overlay */}
        <div className="absolute inset-0 tech-grid opacity-40" />

        {/* Glow orbs */}
        <div className="absolute -top-20 -right-20 w-64 h-64 rounded-full"
          style={{ background: 'radial-gradient(circle, rgba(179,71,255,0.15) 0%, transparent 70%)' }} />
        <div className="absolute -bottom-10 -left-10 w-48 h-48 rounded-full"
          style={{ background: 'radial-gradient(circle, rgba(0,212,255,0.12) 0%, transparent 70%)' }} />

        {/* Top bar */}
        <div className="relative flex items-center justify-between px-4 pt-12 pb-2">
          <div className="flex items-center gap-1.5">
            <div className="w-1.5 h-1.5 rounded-full bg-cyan-400 pulse-dot" />
            <span className="text-xs font-medium" style={{ color: '#00d4ff' }}>AI 试戴系统 · 在线</span>
          </div>
          <button className="relative p-2 rounded-full" style={{ background: 'rgba(255,255,255,0.05)' }}>
            <Bell size={18} style={{ color: '#6b7280' }} />
            <div className="absolute top-1 right-1 w-2 h-2 rounded-full bg-red-500" />
          </button>
        </div>

        {/* Brand title */}
        <div className="relative px-4 pt-2 pb-4">
          <div className="flex items-center gap-2 mb-1">
            <Sparkles size={16} style={{ color: '#00d4ff' }} />
            <span className="text-xs font-medium" style={{ color: '#00d4ff', letterSpacing: '0.1em' }}>SMART HAIR AI</span>
          </div>
          <h1
            className="text-2xl font-black text-white mb-1"
            style={{ fontFamily: '"Orbitron", monospace', letterSpacing: '0.02em' }}
          >
            智能发型<br />
            <span className="gradient-text">定制平台</span>
          </h1>
          <p className="text-xs" style={{ color: '#6b7280' }}>AI 驱动 · 秒级试戴 · 精准匹配</p>
        </div>

        {/* Search bar */}
        <div className="relative px-4 pb-5">
          <div
            className="flex items-center gap-2 px-3 py-2.5 rounded-xl"
            style={{
              background: 'rgba(255,255,255,0.05)',
              border: '1px solid rgba(0,212,255,0.15)',
            }}
          >
            <Search size={15} style={{ color: '#4b5563' }} />
            <input
              type="text"
              placeholder="搜索发型、门店..."
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              className="flex-1 bg-transparent text-sm outline-none"
              style={{ color: '#e2e8f0', caretColor: '#00d4ff' }}
            />
            {searchText && (
              <button onClick={() => setSearchText('')} className="text-xs" style={{ color: '#4b5563' }}>✕</button>
            )}
          </div>
          {/* Location */}
          <div className="flex items-center gap-1.5 mt-2">
            <MapPin size={12} style={{ color: '#00d4ff' }} />
            <span className="text-xs" style={{ color: '#6b7280' }}>北京市朝阳区</span>
            <span className="text-xs" style={{ color: '#1a2d4f' }}>|</span>
            <button className="text-xs" style={{ color: '#00d4ff' }}>切换城市</button>
          </div>
        </div>
      </div>

      {/* AI Try-on Banner */}
      <div className="px-4 -mt-1">
        <div
          className="relative overflow-hidden rounded-2xl p-4 cursor-pointer card-hover"
          style={{
            background: 'linear-gradient(135deg, #0a1628, #130a2a)',
            border: '1px solid rgba(179, 71, 255, 0.3)',
            boxShadow: '0 0 30px rgba(179, 71, 255, 0.1)',
          }}
          onClick={() => onNavigate('tryon-upload')}
        >
          {/* Background decoration */}
          <div className="absolute right-0 top-0 bottom-0 w-32 opacity-20">
            <img src="/images/hair-natural.jpg" alt="" className="w-full h-full object-cover" />
            <div className="absolute inset-0" style={{ background: 'linear-gradient(to right, #0a1628, transparent)' }} />
          </div>
          <div className="absolute inset-0 tech-grid opacity-30" />

          {/* Glow line */}
          <div
            className="absolute top-0 left-0 right-0 h-0.5"
            style={{ background: 'linear-gradient(90deg, transparent, #b347ff, #00d4ff, transparent)' }}
          />

          <div className="relative">
            <div className="flex items-center gap-2 mb-1.5">
              <div
                className="px-2 py-0.5 rounded-full text-xs font-bold"
                style={{ background: 'rgba(179,71,255,0.2)', color: '#b347ff', border: '1px solid rgba(179,71,255,0.3)' }}
              >
                AI POWERED
              </div>
            </div>
            <h2 className="text-lg font-black text-white mb-1">
              立即 <span className="gradient-text">AI 试戴</span>
            </h2>
            <p className="text-xs mb-3" style={{ color: '#94a3b8' }}>
              上传一张照片，看看<br />新发型的你有多美！
            </p>
            <button
              className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-bold transition-all btn-primary"
              style={{ color: '#050a14' }}
            >
              <Zap size={14} />
              开始试戴
              <ChevronRight size={14} />
            </button>
          </div>

          {/* Corner decoration */}
          <div
            className="absolute bottom-3 right-3 w-16 h-16 rounded-full opacity-10 spin-slow"
            style={{ border: '2px solid #00d4ff', borderStyle: 'dashed' }}
          />
        </div>
      </div>

      {/* Stats row */}
      <div className="grid grid-cols-3 gap-2 px-4 mt-4">
        {[
          { label: '试戴用户', value: '50W+', icon: '👥' },
          { label: '发型款式', value: '2000+', icon: '💇' },
          { label: '满意度', value: '98%', icon: '⭐' },
        ].map((stat) => (
          <div
            key={stat.label}
            className="flex flex-col items-center py-2.5 px-2 rounded-xl"
            style={{ background: 'rgba(10,22,40,0.6)', border: '1px solid rgba(0,212,255,0.08)' }}
          >
            <span className="text-base mb-0.5">{stat.icon}</span>
            <span className="text-sm font-bold gradient-text">{stat.value}</span>
            <span className="text-xs" style={{ color: '#4b5563' }}>{stat.label}</span>
          </div>
        ))}
      </div>

      {/* Hot hairstyles */}
      <div className="px-4 mt-5">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <TrendingUp size={16} style={{ color: '#00d4ff' }} />
            <h2 className="text-sm font-bold text-white">热门发型推荐</h2>
          </div>
          <button
            className="flex items-center gap-1 text-xs"
            style={{ color: '#00d4ff' }}
            onClick={() => onNavigate('tryon-select')}
          >
            更多 <ChevronRight size={12} />
          </button>
        </div>

        {/* Category tabs */}
        <div className="flex gap-2 mb-3 overflow-x-auto pb-1" style={{ scrollbarWidth: 'none' }}>
          {categories.map((cat) => (
            <button
              key={cat}
              onClick={() => setActiveTab(cat)}
              className="flex-shrink-0 px-3 py-1 rounded-full text-xs font-medium transition-all duration-200"
              style={
                activeTab === cat
                  ? {
                      background: 'linear-gradient(135deg, #00d4ff22, #b347ff22)',
                      border: '1px solid #00d4ff',
                      color: '#00d4ff',
                    }
                  : {
                      background: 'rgba(255,255,255,0.04)',
                      border: '1px solid rgba(255,255,255,0.08)',
                      color: '#6b7280',
                    }
              }
            >
              {cat}
            </button>
          ))}
        </div>

        {/* Hair grid */}
        <div className="grid grid-cols-2 gap-2.5">
          {filteredHair.map((hair) => (
            <div
              key={hair.id}
              className="relative rounded-xl overflow-hidden cursor-pointer card-hover"
              style={{
                background: 'rgba(10,22,40,0.8)',
                border: '1px solid rgba(0,212,255,0.1)',
                aspectRatio: '3/4',
              }}
              onClick={() => onNavigate('tryon-select', { category: hair.category })}
            >
              <img
                src={hair.image}
                alt={hair.name}
                className="w-full h-full object-cover"
              />
              <div
                className="absolute inset-0"
                style={{ background: 'linear-gradient(to top, rgba(5,10,20,0.9) 0%, transparent 55%)' }}
              />
              {(hair.isHot || hair.isNew) && (
                <div className="absolute top-2 left-2">
                  <span
                    className="text-xs px-1.5 py-0.5 rounded-full font-medium"
                    style={hair.isHot
                      ? { background: 'rgba(0,212,255,0.2)', border: '1px solid rgba(0,212,255,0.4)', color: '#00d4ff' }
                      : { background: 'rgba(179,71,255,0.2)', border: '1px solid rgba(179,71,255,0.4)', color: '#b347ff' }
                    }
                  >
                    {hair.isHot ? '🔥 热门' : '✨ 新品'}
                  </span>
                </div>
              )}
              <div className="absolute bottom-0 left-0 right-0 p-2">
                <p className="text-xs font-semibold text-white truncate">{hair.name}</p>
                <p className="text-xs gradient-text font-bold">{priceRangeCompact(hair)}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Nearby stores */}
      <div className="px-4 mt-5">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <MapPin size={16} style={{ color: '#00d4ff' }} />
            <h2 className="text-sm font-bold text-white">📍 附近门店</h2>
          </div>
          <button
            className="flex items-center gap-1 text-xs"
            style={{ color: '#00d4ff' }}
            onClick={() => onNavigate('store-list')}
          >
            更多 <ChevronRight size={12} />
          </button>
        </div>

        <div className="space-y-2.5">
          {nearbyStores.map((store) => (
            <StoreCard
              key={store.id}
              store={store}
              onViewDetail={(s) => onNavigate('store-detail', { store: s })}
            />
          ))}
        </div>
      </div>
    </div>
  );
};
