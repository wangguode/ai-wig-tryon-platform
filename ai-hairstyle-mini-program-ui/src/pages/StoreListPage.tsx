import React, { useState } from 'react';
import { MapPin, ChevronDown, Search, X } from 'lucide-react';
import { PageHeader } from '../components/PageHeader';
import { StoreCard } from '../components/StoreCard';
import { stores, Store } from '../data/mockData';

interface StoreListPageProps {
  onBack: () => void;
  onViewDetail: (store: Store) => void;
}

const sortOptions = ['距离最近', '评分最高', '好评最多'];
const cityOptions = ['全部城市', '北京', '上海', '广州', '深圳'];

export const StoreListPage: React.FC<StoreListPageProps> = ({ onBack, onViewDetail }) => {
  const [sortBy, setSortBy] = useState('距离最近');
  const [city, setCity] = useState('全部城市');
  const [searchText, setSearchText] = useState('');

  const filteredStores = stores
    .filter((s) => {
      const searchMatch = !searchText || s.name.includes(searchText) || s.address.includes(searchText);
      return searchMatch;
    })
    .sort((a, b) => {
      if (sortBy === '距离最近') return parseFloat(a.distance) - parseFloat(b.distance);
      if (sortBy === '评分最高') return b.rating - a.rating;
      if (sortBy === '好评最多') return b.reviews - a.reviews;
      return 0;
    });

  return (
    <div className="page-enter pb-24">
      <PageHeader title="附近门店" onBack={onBack} />

      {/* Location bar */}
      <div
        className="flex items-center justify-between px-4 py-2.5"
        style={{ background: 'rgba(10,22,40,0.6)', borderBottom: '1px solid rgba(0,212,255,0.08)' }}
      >
        <div className="flex items-center gap-2">
          <div className="w-2 h-2 rounded-full pulse-dot" style={{ background: '#00d4ff' }} />
          <MapPin size={13} style={{ color: '#00d4ff' }} />
          <span className="text-xs font-medium text-white">当前位置：朝阳区</span>
        </div>
        <button className="text-xs flex items-center gap-1" style={{ color: '#00d4ff' }}>
          切换城市 <ChevronDown size={12} />
        </button>
      </div>

      {/* Search */}
      <div className="px-4 pt-3 pb-2">
        <div
          className="flex items-center gap-2 px-3 py-2 rounded-xl"
          style={{ background: 'rgba(10,22,40,0.8)', border: '1px solid rgba(0,212,255,0.12)' }}
        >
          <Search size={14} style={{ color: '#4b5563' }} />
          <input
            type="text"
            placeholder="搜索门店名称、地址..."
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            className="flex-1 bg-transparent text-sm outline-none"
            style={{ color: '#e2e8f0', caretColor: '#00d4ff' }}
          />
          {searchText && (
            <button onClick={() => setSearchText('')}><X size={13} style={{ color: '#4b5563' }} /></button>
          )}
        </div>
      </div>

      {/* Filter bar */}
      <div className="flex items-center gap-2 px-4 pb-3">
        {/* Sort */}
        <div className="relative">
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value)}
            className="appearance-none px-3 py-1.5 pr-7 rounded-lg text-xs font-medium outline-none cursor-pointer"
            style={{
              background: 'rgba(0,212,255,0.08)',
              border: '1px solid rgba(0,212,255,0.25)',
              color: '#00d4ff',
            }}
          >
            {sortOptions.map((o) => (
              <option key={o} value={o} style={{ background: '#0a1628' }}>{o} ▼</option>
            ))}
          </select>
          <ChevronDown size={10} className="absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none" style={{ color: '#00d4ff' }} />
        </div>

        {/* City */}
        <div className="relative">
          <select
            value={city}
            onChange={(e) => setCity(e.target.value)}
            className="appearance-none px-3 py-1.5 pr-7 rounded-lg text-xs font-medium outline-none cursor-pointer"
            style={{
              background: 'rgba(255,255,255,0.04)',
              border: '1px solid rgba(255,255,255,0.08)',
              color: '#6b7280',
            }}
          >
            {cityOptions.map((o) => (
              <option key={o} value={o} style={{ background: '#0a1628' }}>{o}</option>
            ))}
          </select>
          <ChevronDown size={10} className="absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none" style={{ color: '#4b5563' }} />
        </div>

        <span className="ml-auto text-xs" style={{ color: '#4b5563' }}>共 {filteredStores.length} 家门店</span>
      </div>

      {/* Divider */}
      <div className="px-4 mb-3">
        <div className="h-px" style={{ background: 'linear-gradient(90deg, rgba(0,212,255,0.3), transparent)' }} />
      </div>

      {/* Store list */}
      <div className="px-4 space-y-3">
        {filteredStores.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-3">
            <div className="text-4xl">🏪</div>
            <p className="text-sm" style={{ color: '#4b5563' }}>未找到相关门店</p>
          </div>
        ) : (
          filteredStores.map((store) => (
            <StoreCard
              key={store.id}
              store={store}
              onViewDetail={onViewDetail}
            />
          ))
        )}
      </div>

      {/* Map hint */}
      <div className="px-4 mt-4">
        <div
          className="relative h-24 rounded-2xl overflow-hidden flex items-center justify-center cursor-pointer"
          style={{
            background: 'linear-gradient(135deg, rgba(0,212,255,0.05), rgba(179,71,255,0.05))',
            border: '1px dashed rgba(0,212,255,0.2)',
          }}
        >
          <div className="text-center">
            <div className="text-2xl mb-1">🗺️</div>
            <p className="text-xs" style={{ color: '#4b5563' }}>点击查看地图视图</p>
          </div>
          <div className="absolute inset-0 tech-grid opacity-20" />
        </div>
      </div>
    </div>
  );
};
