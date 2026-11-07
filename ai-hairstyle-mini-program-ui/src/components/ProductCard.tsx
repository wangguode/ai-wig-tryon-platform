import React from 'react';
import { Star, Zap } from 'lucide-react';
import { HairStyle } from '../data/mockData';
import { priceRangeCompact } from '../utils/price';

interface ProductCardProps {
  hair: HairStyle;
  onSelect: (hair: HairStyle) => void;
  onDetail?: (hair: HairStyle) => void;
}

export const ProductCard: React.FC<ProductCardProps> = ({ hair, onSelect, onDetail }) => {
  return (
    <div
      className="rounded-xl overflow-hidden card-hover"
      style={{
        background: 'rgba(10, 22, 40, 0.8)',
        border: '1px solid rgba(0, 212, 255, 0.1)',
      }}
    >
      {/* Image */}
      <div className="relative aspect-[3/4] overflow-hidden">
        <img
          src={hair.image}
          alt={hair.name}
          className="w-full h-full object-cover transition-transform duration-500 hover:scale-105"
          onClick={() => onDetail?.(hair)}
        />
        <div
          className="absolute inset-0"
          style={{ background: 'linear-gradient(to top, rgba(5,10,20,0.95) 0%, rgba(5,10,20,0.2) 50%, transparent 100%)' }}
        />
        {/* Badges */}
        <div className="absolute top-2 left-2 flex gap-1 flex-wrap">
          {hair.isHot && (
            <span className="chip chip-blue text-xs">🔥 热门</span>
          )}
          {hair.isNew && (
            <span className="chip chip-purple text-xs">✨ 新品</span>
          )}
        </div>
        {/* Rating */}
        <div className="absolute top-2 right-2 flex items-center gap-1 px-1.5 py-0.5 rounded-full"
          style={{ background: 'rgba(5,10,20,0.7)', border: '1px solid rgba(245,158,11,0.3)' }}>
          <Star size={10} fill="#f59e0b" style={{ color: '#f59e0b' }} />
          <span className="text-xs font-bold" style={{ color: '#f59e0b' }}>{hair.rating}</span>
        </div>
      </div>

      {/* Info */}
      <div className="p-2.5">
        <h3 className="text-xs font-semibold text-white mb-0.5 truncate">{hair.name}</h3>
        <p className="text-xs mb-1 truncate" style={{ color: '#6b7280' }}>{hair.material}</p>

        <div className="flex items-center justify-between mb-2">
          <span className="text-xs font-bold gradient-text">{priceRangeCompact(hair)}</span>
          <span className="text-xs" style={{ color: '#4b5563' }}>{hair.reviews}人试戴</span>
        </div>

        <button
          onClick={() => onSelect(hair)}
          className="w-full py-1.5 rounded-lg text-xs font-semibold transition-all duration-200 flex items-center justify-center gap-1"
          style={{
            background: 'linear-gradient(135deg, #00d4ff22, #b347ff22)',
            border: '1px solid rgba(0, 212, 255, 0.35)',
            color: '#00d4ff',
          }}
        >
          <Zap size={11} />
          选择试戴
        </button>
      </div>
    </div>
  );
};
