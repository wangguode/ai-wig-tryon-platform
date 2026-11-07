import React, { useState } from 'react';
import { Trash2, RefreshCw, Calendar } from 'lucide-react';
import { PageHeader } from '../components/PageHeader';
import { StarRating } from '../components/StarRating';

interface HistoryPageProps {
  onBack: () => void;
  onNavigate: (page: string, data?: any) => void;
}

const historyData = [
  {
    id: 1,
    date: '2024-01-15',
    hairName: '柔光自然直发套',
    image: '/images/hair-natural.jpg',
    resultImage: '/images/hair-natural.jpg',
    rating: 5,
    material: '100% 真人发丝',
    price: '¥3,800 起',
    status: 'success',
  },
  {
    id: 2,
    date: '2024-01-10',
    hairName: '甜美大波浪卷发',
    image: '/images/hair-curly.jpg',
    resultImage: '/images/hair-curly.jpg',
    rating: 4,
    material: '混合材质',
    price: '¥2,000 起',
    status: 'success',
  },
  {
    id: 3,
    date: '2024-01-05',
    hairName: '都市精英短发套',
    image: '/images/hair-short.jpg',
    resultImage: '/images/hair-short.jpg',
    rating: 4,
    material: '优质合成发丝',
    price: '¥1,500 起',
    status: 'success',
  },
  {
    id: 4,
    date: '2023-12-28',
    hairName: '高贵御姐长直发',
    image: '/images/hair-business.jpg',
    resultImage: '/images/hair-business.jpg',
    rating: 5,
    material: '100% 真人发丝',
    price: '¥5,000 起',
    status: 'success',
  },
];

export const HistoryPage: React.FC<HistoryPageProps> = ({ onBack, onNavigate }) => {
  const [selectedItems, setSelectedItems] = useState<number[]>([]);
  const [editMode, setEditMode] = useState(false);

  const toggleSelect = (id: number) => {
    setSelectedItems((prev) =>
      prev.includes(id) ? prev.filter((i) => i !== id) : [...prev, id]
    );
  };

  return (
    <div className="page-enter pb-24">
      <PageHeader
        title="试戴历史"
        onBack={onBack}
        rightContent={
          <button
            onClick={() => { setEditMode(!editMode); setSelectedItems([]); }}
            className="text-xs px-2.5 py-1.5 rounded-lg"
            style={{
              background: editMode ? 'rgba(255,100,100,0.1)' : 'rgba(255,255,255,0.05)',
              border: `1px solid ${editMode ? 'rgba(255,100,100,0.25)' : 'rgba(255,255,255,0.08)'}`,
              color: editMode ? '#ff6464' : '#6b7280',
            }}
          >
            {editMode ? '取消' : '管理'}
          </button>
        }
      />

      {/* Stats */}
      <div className="grid grid-cols-3 gap-2 px-4 mt-4 mb-4">
        {[
          { label: '试戴总次数', value: '23', icon: '🎯' },
          { label: '本月试戴', value: '4', icon: '📅' },
          { label: '平均评分', value: '4.8', icon: '⭐' },
        ].map((stat) => (
          <div
            key={stat.label}
            className="flex flex-col items-center py-2.5 rounded-xl"
            style={{ background: 'rgba(10,22,40,0.8)', border: '1px solid rgba(0,212,255,0.08)' }}
          >
            <span className="text-lg mb-0.5">{stat.icon}</span>
            <span className="text-sm font-bold gradient-text">{stat.value}</span>
            <span className="text-xs text-center" style={{ color: '#374151', fontSize: 10 }}>{stat.label}</span>
          </div>
        ))}
      </div>

      {/* Edit mode actions */}
      {editMode && selectedItems.length > 0 && (
        <div
          className="mx-4 mb-3 flex items-center justify-between px-3 py-2 rounded-xl"
          style={{ background: 'rgba(255,100,100,0.08)', border: '1px solid rgba(255,100,100,0.2)' }}
        >
          <span className="text-xs" style={{ color: '#ff6464' }}>已选择 {selectedItems.length} 项</span>
          <button className="flex items-center gap-1 text-xs" style={{ color: '#ff6464' }}>
            <Trash2 size={12} />
            删除选中
          </button>
        </div>
      )}

      {/* History list */}
      <div className="px-4 space-y-3">
        {historyData.map((item) => (
          <div
            key={item.id}
            className="rounded-2xl overflow-hidden transition-all duration-200"
            style={{
              background: 'rgba(10,22,40,0.8)',
              border: selectedItems.includes(item.id)
                ? '1px solid rgba(255,100,100,0.4)'
                : '1px solid rgba(0,212,255,0.1)',
            }}
            onClick={() => editMode && toggleSelect(item.id)}
          >
            <div className="flex gap-3 p-3">
              {/* Edit checkbox */}
              {editMode && (
                <div
                  className="w-5 h-5 rounded-md flex items-center justify-center flex-shrink-0 mt-1"
                  style={{
                    background: selectedItems.includes(item.id) ? 'rgba(255,100,100,0.2)' : 'rgba(255,255,255,0.04)',
                    border: `1px solid ${selectedItems.includes(item.id) ? 'rgba(255,100,100,0.5)' : 'rgba(255,255,255,0.1)'}`,
                  }}
                >
                  {selectedItems.includes(item.id) && (
                    <span style={{ color: '#ff6464', fontSize: 10 }}>✓</span>
                  )}
                </div>
              )}

              {/* Before / After thumbnails */}
              <div className="flex gap-1.5 flex-shrink-0">
                <div className="w-14 h-18 rounded-lg overflow-hidden" style={{ height: 72 }}>
                  <img src={item.image} alt="Before" className="w-full h-full object-cover" />
                </div>
                <div className="w-14 rounded-lg overflow-hidden relative" style={{ height: 72 }}>
                  <img src={item.resultImage} alt="After" className="w-full h-full object-cover" />
                  <div
                    className="absolute top-0.5 right-0.5 text-xs px-1 rounded"
                    style={{ background: 'rgba(0,212,255,0.2)', color: '#00d4ff', fontSize: 9 }}
                  >
                    AI
                  </div>
                </div>
              </div>

              {/* Info */}
              <div className="flex-1 min-w-0">
                <h3 className="text-sm font-semibold text-white truncate mb-0.5">{item.hairName}</h3>
                <p className="text-xs mb-1 truncate" style={{ color: '#6b7280' }}>{item.material}</p>
                <div className="flex items-center gap-1.5 mb-1">
                  <StarRating rating={item.rating} size={11} />
                  <span className="text-xs" style={{ color: '#4b5563' }}>{item.rating}.0</span>
                </div>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1">
                    <Calendar size={10} style={{ color: '#374151' }} />
                    <span className="text-xs" style={{ color: '#374151' }}>{item.date}</span>
                  </div>
                  <span className="text-xs font-bold gradient-text">{item.price}</span>
                </div>
              </div>
            </div>

            {/* Actions */}
            {!editMode && (
              <div
                className="flex items-center justify-end gap-2 px-3 py-2"
                style={{ borderTop: '1px solid rgba(0,212,255,0.06)' }}
              >
                <button
                  className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs transition-all"
                  style={{
                    background: 'rgba(0,212,255,0.06)',
                    border: '1px solid rgba(0,212,255,0.15)',
                    color: '#00d4ff',
                  }}
                  onClick={() => onNavigate('tryon-select')}
                >
                  <RefreshCw size={10} />
                  重新试戴
                </button>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};
