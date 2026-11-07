import React, { useState } from 'react';
import { MapPin, Clock, Phone, Navigation, MessageCircle, ChevronLeft, ChevronRight, ExternalLink } from 'lucide-react';
import { PageHeader } from '../components/PageHeader';
import { Store } from '../data/mockData';
import { StarRating } from '../components/StarRating';

interface StoreDetailPageProps {
  store: Store;
  onBack: () => void;
}

const reviews = [
  { id: 1, user: '张小姐', avatar: '👩', rating: 5, date: '2024-01-15', text: '环境超棒，顾问很专业，试戴效果和实物几乎一模一样！强烈推荐！' },
  { id: 2, user: '李先生', avatar: '👨', rating: 4, date: '2024-01-12', text: '发型师态度很好，给了很多专业建议，最终选了一款非常适合自己的。' },
  { id: 3, user: '王女士', avatar: '👩‍🦱', rating: 5, date: '2024-01-10', text: '定制的发型质量真的很好，戴着非常舒适自然，已经是第二次来了。' },
];

export const StoreDetailPage: React.FC<StoreDetailPageProps> = ({ store, onBack }) => {
  const [currentImg, setCurrentImg] = useState(0);
  const [activeTab, setActiveTab] = useState<'info' | 'cases' | 'reviews'>('info');
  const images = [store.image, ...store.cases];

  return (
    <div className="page-enter pb-24">
      <PageHeader title="门店详情" onBack={onBack} />

      {/* Image carousel */}
      <div className="relative">
        <div className="relative h-52 overflow-hidden">
          <img
            src={images[currentImg]}
            alt="Store"
            className="w-full h-full object-cover transition-opacity duration-300"
          />
          <div
            className="absolute inset-0"
            style={{ background: 'linear-gradient(to bottom, rgba(5,10,20,0.2), rgba(5,10,20,0.6))' }}
          />

          {/* Navigation arrows */}
          {images.length > 1 && (
            <>
              <button
                onClick={() => setCurrentImg((c) => (c - 1 + images.length) % images.length)}
                className="absolute left-2 top-1/2 -translate-y-1/2 p-2 rounded-full"
                style={{ background: 'rgba(5,10,20,0.7)' }}
              >
                <ChevronLeft size={16} style={{ color: '#fff' }} />
              </button>
              <button
                onClick={() => setCurrentImg((c) => (c + 1) % images.length)}
                className="absolute right-2 top-1/2 -translate-y-1/2 p-2 rounded-full"
                style={{ background: 'rgba(5,10,20,0.7)' }}
              >
                <ChevronRight size={16} style={{ color: '#fff' }} />
              </button>
            </>
          )}

          {/* Dots */}
          <div className="absolute bottom-2 left-1/2 -translate-x-1/2 flex gap-1.5">
            {images.map((_, i) => (
              <div
                key={i}
                onClick={() => setCurrentImg(i)}
                className="rounded-full cursor-pointer transition-all duration-200"
                style={{
                  width: i === currentImg ? 16 : 6,
                  height: 6,
                  background: i === currentImg
                    ? 'linear-gradient(90deg, #00d4ff, #b347ff)'
                    : 'rgba(255,255,255,0.3)',
                }}
              />
            ))}
          </div>

          {/* Status overlay */}
          <div className="absolute top-3 right-3">
            <span
              className="px-2 py-0.5 rounded-full text-xs font-medium"
              style={{
                background: store.isOpen ? 'rgba(0,255,150,0.15)' : 'rgba(255,100,100,0.15)',
                border: store.isOpen ? '1px solid rgba(0,255,150,0.3)' : '1px solid rgba(255,100,100,0.3)',
                color: store.isOpen ? '#00ff96' : '#ff6464',
              }}
            >
              {store.isOpen ? '● 营业中' : '○ 已打烊'}
            </span>
          </div>
        </div>
      </div>

      {/* Store name & rating */}
      <div className="px-4 pt-4 pb-3">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-lg font-bold text-white mb-1">🏪 {store.name}</h1>
            <div className="flex items-center gap-2">
              <StarRating rating={store.rating} size={14} />
              <span className="text-sm font-bold" style={{ color: '#f59e0b' }}>{store.rating}分</span>
              <span className="text-xs" style={{ color: '#4b5563' }}>{store.reviews}条评价</span>
            </div>
          </div>
          <div className="flex flex-col items-end gap-1">
            {store.tags.map((tag) => (
              <span key={tag} className="chip chip-blue text-xs">{tag}</span>
            ))}
          </div>
        </div>
      </div>

      {/* Quick info */}
      <div
        className="mx-4 rounded-2xl p-3 mb-3"
        style={{ background: 'rgba(10,22,40,0.8)', border: '1px solid rgba(0,212,255,0.1)' }}
      >
        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <MapPin size={14} style={{ color: '#00d4ff', flexShrink: 0 }} />
            <span className="text-xs" style={{ color: '#94a3b8' }}>{store.address}</span>
          </div>
          <div className="flex items-center gap-2">
            <Clock size={14} style={{ color: '#00d4ff', flexShrink: 0 }} />
            <span className="text-xs" style={{ color: '#94a3b8' }}>周一至周日 {store.hours}</span>
          </div>
          <div className="flex items-center gap-2">
            <Phone size={14} style={{ color: '#00d4ff', flexShrink: 0 }} />
            <span className="text-xs" style={{ color: '#94a3b8' }}>{store.phone}</span>
          </div>
        </div>
      </div>

      {/* Action buttons */}
      <div className="grid grid-cols-3 gap-2 px-4 mb-4">
        {[
          { icon: Navigation, label: '导航', emoji: '📍', color: '#00d4ff' },
          { icon: Phone, label: '拨号', emoji: '☎️', color: '#b347ff' },
          { icon: MessageCircle, label: '微信', emoji: '💬', color: '#00ff96' },
        ].map((action) => (
          <button
            key={action.label}
            className="flex flex-col items-center gap-1.5 py-2.5 rounded-xl transition-all"
            style={{
              background: `${action.color}10`,
              border: `1px solid ${action.color}30`,
            }}
          >
            <span className="text-xl">{action.emoji}</span>
            <span className="text-xs font-medium" style={{ color: action.color }}>{action.label}</span>
          </button>
        ))}
      </div>

      {/* Tabs */}
      <div className="flex border-b mx-4 mb-4" style={{ borderColor: 'rgba(0,212,255,0.1)' }}>
        {(['info', 'cases', 'reviews'] as const).map((tab) => {
          const labels = { info: '顾问添加', cases: '客户案例', reviews: '用户评价' };
          return (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className="flex-1 py-2.5 text-xs font-medium transition-all"
              style={{
                color: activeTab === tab ? '#00d4ff' : '#4b5563',
                borderBottom: activeTab === tab ? '2px solid #00d4ff' : '2px solid transparent',
              }}
            >
              {labels[tab]}
            </button>
          );
        })}
      </div>

      {/* Tab content */}
      {activeTab === 'info' && (
        <div className="px-4">
          <div
            className="rounded-2xl p-4 text-center"
            style={{
              background: 'linear-gradient(135deg, rgba(0,212,255,0.05), rgba(179,71,255,0.05))',
              border: '1px solid rgba(179,71,255,0.2)',
            }}
          >
            {/* QR Code placeholder */}
            <div
              className="w-32 h-32 mx-auto mb-3 rounded-xl flex items-center justify-center"
              style={{
                background: 'rgba(255,255,255,0.03)',
                border: '2px dashed rgba(179,71,255,0.3)',
              }}
            >
              <div className="text-center">
                <div className="text-3xl mb-1">📱</div>
                <p className="text-xs" style={{ color: '#4b5563' }}>企业微信<br />二维码</p>
              </div>
            </div>
            <p className="text-sm font-semibold text-white mb-1">
              添加{store.advisor}专属顾问
            </p>
            <p className="text-xs mb-3" style={{ color: '#6b7280' }}>
              扫码添加 / 一键添加，专属顾问 1对1 服务
            </p>
            <button
              className="w-full py-2.5 rounded-xl text-sm font-bold btn-primary flex items-center justify-center gap-2"
              style={{ color: '#050a14' }}
            >
              <ExternalLink size={14} />
              一键添加微信顾问
            </button>
          </div>
        </div>
      )}

      {activeTab === 'cases' && (
        <div className="px-4">
          <div className="grid grid-cols-3 gap-2">
            {[...store.cases, ...store.cases].slice(0, 9).map((img, i) => (
              <div
                key={i}
                className="aspect-square rounded-xl overflow-hidden cursor-pointer card-hover"
                style={{ border: '1px solid rgba(0,212,255,0.1)' }}
              >
                <img src={img} alt={`Case ${i + 1}`} className="w-full h-full object-cover" />
              </div>
            ))}
          </div>
          <p className="text-center text-xs mt-3" style={{ color: '#374151' }}>点击查看大图</p>
        </div>
      )}

      {activeTab === 'reviews' && (
        <div className="px-4 space-y-3">
          {reviews.map((review) => (
            <div
              key={review.id}
              className="rounded-xl p-3"
              style={{ background: 'rgba(10,22,40,0.8)', border: '1px solid rgba(0,212,255,0.08)' }}
            >
              <div className="flex items-center gap-2 mb-2">
                <div
                  className="w-8 h-8 rounded-full flex items-center justify-center text-lg"
                  style={{ background: 'rgba(255,255,255,0.05)' }}
                >
                  {review.avatar}
                </div>
                <div>
                  <p className="text-xs font-semibold text-white">{review.user}</p>
                  <div className="flex items-center gap-1.5">
                    <StarRating rating={review.rating} size={11} />
                    <span className="text-xs" style={{ color: '#374151' }}>{review.date}</span>
                  </div>
                </div>
              </div>
              <p className="text-xs leading-relaxed" style={{ color: '#94a3b8' }}>{review.text}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
