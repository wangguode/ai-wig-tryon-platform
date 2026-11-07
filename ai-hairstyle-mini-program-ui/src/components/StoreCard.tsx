import React from 'react';
import { MapPin, Clock, Phone, ChevronRight, Star } from 'lucide-react';
import { Store } from '../data/mockData';

interface StoreCardProps {
  store: Store;
  onViewDetail: (store: Store) => void;
}

export const StoreCard: React.FC<StoreCardProps> = ({ store, onViewDetail }) => {
  return (
    <div
      className="rounded-2xl overflow-hidden card-hover cursor-pointer"
      style={{
        background: 'rgba(10, 22, 40, 0.8)',
        border: '1px solid rgba(0, 212, 255, 0.12)',
      }}
      onClick={() => onViewDetail(store)}
    >
      {/* Store image */}
      <div className="relative h-28 overflow-hidden">
        <img
          src={store.image}
          alt={store.name}
          className="w-full h-full object-cover"
        />
        <div
          className="absolute inset-0"
          style={{ background: 'linear-gradient(to top, rgba(5,10,20,0.9), transparent)' }}
        />
        {/* Distance badge */}
        <div
          className="absolute top-2 right-2 px-2 py-0.5 rounded-full text-xs font-medium"
          style={{
            background: 'rgba(0, 212, 255, 0.15)',
            border: '1px solid rgba(0, 212, 255, 0.3)',
            color: '#00d4ff',
          }}
        >
          📍 {store.distance}
        </div>
        {/* Status badge */}
        <div
          className="absolute bottom-2 left-2 px-2 py-0.5 rounded-full text-xs font-medium"
          style={{
            background: store.isOpen ? 'rgba(0, 255, 150, 0.15)' : 'rgba(255, 100, 100, 0.15)',
            border: store.isOpen ? '1px solid rgba(0, 255, 150, 0.3)' : '1px solid rgba(255, 100, 100, 0.3)',
            color: store.isOpen ? '#00ff96' : '#ff6464',
          }}
        >
          {store.isOpen ? '● 营业中' : '○ 已打烊'}
        </div>
      </div>

      {/* Content */}
      <div className="p-3">
        <div className="flex items-start justify-between gap-2 mb-1.5">
          <h3 className="text-sm font-semibold text-white flex-1">{store.name}</h3>
          <ChevronRight size={14} style={{ color: '#4b5563', flexShrink: 0, marginTop: 2 }} />
        </div>

        {/* Rating */}
        <div className="flex items-center gap-2 mb-2">
          <div className="flex items-center gap-1">
            <Star size={12} fill="#f59e0b" style={{ color: '#f59e0b' }} />
            <span className="text-xs font-bold" style={{ color: '#f59e0b' }}>{store.rating}</span>
          </div>
          <span className="text-xs" style={{ color: '#6b7280' }}>{store.reviews}条评价</span>
        </div>

        {/* Info */}
        <div className="space-y-1">
          <div className="flex items-center gap-1.5">
            <MapPin size={11} style={{ color: '#4b5563', flexShrink: 0 }} />
            <span className="text-xs truncate" style={{ color: '#6b7280' }}>{store.address}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <Clock size={11} style={{ color: '#4b5563', flexShrink: 0 }} />
            <span className="text-xs" style={{ color: '#6b7280' }}>{store.hours}</span>
          </div>
        </div>

        {/* Actions */}
        <div className="flex gap-2 mt-3">
          <button
            className="flex-1 flex items-center justify-center gap-1 py-1.5 rounded-lg text-xs font-medium transition-all"
            style={{
              background: 'rgba(0, 212, 255, 0.08)',
              border: '1px solid rgba(0, 212, 255, 0.25)',
              color: '#00d4ff',
            }}
            onClick={(e) => { e.stopPropagation(); }}
          >
            <Phone size={11} />
            立即致电
          </button>
          <button
            className="flex-1 flex items-center justify-center gap-1 py-1.5 rounded-lg text-xs font-medium transition-all"
            style={{
              background: 'linear-gradient(135deg, rgba(0,212,255,0.2), rgba(179,71,255,0.2))',
              border: '1px solid rgba(0, 212, 255, 0.3)',
              color: '#e2e8f0',
            }}
            onClick={(e) => { e.stopPropagation(); onViewDetail(store); }}
          >
            查看详情
          </button>
        </div>
      </div>
    </div>
  );
};
