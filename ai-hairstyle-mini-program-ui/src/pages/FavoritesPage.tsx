import React, { useState } from 'react';
import { Heart, Zap, Trash2, Search } from 'lucide-react';
import { PageHeader } from '../components/PageHeader';
import { hairStyles, HairStyle } from '../data/mockData';
import { StarRating } from '../components/StarRating';
import { priceRangeCompact } from '../utils/price';

interface FavoritesPageProps {
  onBack: () => void;
  onNavigate: (page: string, data?: any) => void;
}

export const FavoritesPage: React.FC<FavoritesPageProps> = ({ onBack, onNavigate }) => {
  const [favorites, setFavorites] = useState<HairStyle[]>(hairStyles.slice(0, 6));
  const [searchText, setSearchText] = useState('');

  const removeFromFavorites = (id: number) => {
    setFavorites((prev) => prev.filter((h) => h.id !== id));
  };

  const filtered = favorites.filter((h) =>
    !searchText || h.name.includes(searchText) || h.category.includes(searchText)
  );

  return (
    <div className="page-enter pb-24">
      <PageHeader
        title="收藏发型"
        onBack={onBack}
        rightContent={
          <span className="text-xs" style={{ color: '#4b5563' }}>
            {favorites.length} 款
          </span>
        }
      />

      {/* Search */}
      <div className="px-4 py-3">
        <div
          className="flex items-center gap-2 px-3 py-2 rounded-xl"
          style={{ background: 'rgba(10,22,40,0.8)', border: '1px solid rgba(0,212,255,0.12)' }}
        >
          <Search size={14} style={{ color: '#4b5563' }} />
          <input
            type="text"
            placeholder="搜索收藏的发型..."
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            className="flex-1 bg-transparent text-sm outline-none"
            style={{ color: '#e2e8f0', caretColor: '#00d4ff' }}
          />
        </div>
      </div>

      {/* Stats banner */}
      <div className="px-4 mb-3">
        <div
          className="flex items-center gap-3 px-3 py-2.5 rounded-xl"
          style={{
            background: 'linear-gradient(135deg, rgba(255,71,179,0.08), rgba(179,71,255,0.08))',
            border: '1px solid rgba(255,71,179,0.2)',
          }}
        >
          <Heart size={16} style={{ color: '#ff47b3' }} fill="#ff47b3" />
          <div>
            <p className="text-sm font-semibold text-white">我的心愿单</p>
            <p className="text-xs" style={{ color: '#6b7280' }}>已收藏 {favorites.length} 款，快去试戴吧！</p>
          </div>
        </div>
      </div>

      {/* Grid */}
      {filtered.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 gap-4">
          <Heart size={48} style={{ color: '#1a2d4f' }} />
          <div className="text-center">
            <p className="text-sm font-medium text-white mb-1">
              {searchText ? '未找到相关收藏' : '暂无收藏发型'}
            </p>
            <p className="text-xs" style={{ color: '#374151' }}>
              {searchText ? '换个关键词试试' : '去浏览发型，收藏喜欢的款式'}
            </p>
          </div>
          {!searchText && (
            <button
              onClick={() => onNavigate('tryon-select')}
              className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium btn-primary"
              style={{ color: '#050a14' }}
            >
              <Zap size={14} />
              浏览发型
            </button>
          )}
        </div>
      ) : (
        <div className="px-4 grid grid-cols-2 gap-3">
          {filtered.map((hair) => (
            <div
              key={hair.id}
              className="rounded-xl overflow-hidden card-hover relative"
              style={{
                background: 'rgba(10,22,40,0.8)',
                border: '1px solid rgba(255,71,179,0.15)',
              }}
            >
              {/* Image */}
              <div className="relative" style={{ aspectRatio: '3/4' }}>
                <img
                  src={hair.image}
                  alt={hair.name}
                  className="w-full h-full object-cover"
                />
                <div
                  className="absolute inset-0"
                  style={{ background: 'linear-gradient(to top, rgba(5,10,20,0.9), transparent 55%)' }}
                />
                {/* Remove button */}
                <button
                  onClick={() => removeFromFavorites(hair.id)}
                  className="absolute top-2 right-2 w-7 h-7 rounded-full flex items-center justify-center transition-all"
                  style={{
                    background: 'rgba(255,71,179,0.15)',
                    border: '1px solid rgba(255,71,179,0.4)',
                  }}
                >
                  <Heart size={13} fill="#ff47b3" style={{ color: '#ff47b3' }} />
                </button>

                {/* Category chip */}
                <div className="absolute top-2 left-2">
                  <span className="chip chip-purple" style={{ fontSize: 10 }}>{hair.category}</span>
                </div>
              </div>

              {/* Info */}
              <div className="p-2.5">
                <h3 className="text-xs font-semibold text-white mb-0.5 truncate">{hair.name}</h3>
                <div className="flex items-center gap-1 mb-1">
                  <StarRating rating={hair.rating} size={10} />
                  <span className="text-xs" style={{ color: '#4b5563', fontSize: 10 }}>({hair.reviews})</span>
                </div>
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-bold gradient-text">{priceRangeCompact(hair)}</span>
                </div>

                <div className="flex gap-1.5">
                  <button
                    onClick={() => removeFromFavorites(hair.id)}
                    className="flex-shrink-0 p-1.5 rounded-lg transition-all"
                    style={{
                      background: 'rgba(255,100,100,0.06)',
                      border: '1px solid rgba(255,100,100,0.15)',
                    }}
                  >
                    <Trash2 size={11} style={{ color: '#ff6464' }} />
                  </button>
                  <button
                    onClick={() => onNavigate('tryon-select')}
                    className="flex-1 py-1.5 rounded-lg text-xs font-semibold flex items-center justify-center gap-1 transition-all"
                    style={{
                      background: 'linear-gradient(135deg, rgba(0,212,255,0.12), rgba(179,71,255,0.12))',
                      border: '1px solid rgba(0,212,255,0.25)',
                      color: '#00d4ff',
                    }}
                  >
                    <Zap size={10} />
                    试戴
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
