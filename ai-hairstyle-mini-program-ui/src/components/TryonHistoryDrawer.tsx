import React, { useMemo, useState } from 'react';
import { X, Heart, Trash2, GitCompareArrows, CheckCircle2, Clock, Sparkles } from 'lucide-react';
import { TryonHistoryItem } from '../hooks/useTryonHistory';

interface Props {
  items: TryonHistoryItem[];
  beforeImage: string | null;          // 用户原图，用于对比基线
  onClose: () => void;
  onRemove: (id: string) => void;
  onRemoveMany: (ids: string[]) => void;
  onClear: () => void;
  onToggleLike: (id: string) => void;
}

type Filter = 'all' | 'liked';

const formatRelative = (ms: number): string => {
  const diff = Date.now() - ms;
  const sec = Math.round(diff / 1000);
  if (sec < 60) return `${sec} 秒前`;
  const min = Math.round(sec / 60);
  if (min < 60) return `${min} 分钟前`;
  const hour = Math.round(min / 60);
  if (hour < 24) return `${hour} 小时前`;
  const day = Math.round(hour / 24);
  if (day < 30) return `${day} 天前`;
  return new Date(ms).toLocaleDateString('zh-CN');
};

/**
 * 试戴历史抽屉：3 列网格、单击放大、多选对比、长按进入多选模式。
 * 业界惯例：时间倒序、空态友好、删除二次确认、最多 4 张对比。
 */
export const TryonHistoryDrawer: React.FC<Props> = ({
  items,
  beforeImage,
  onClose,
  onRemove,
  onRemoveMany,
  onClear,
  onToggleLike,
}) => {
  const [filter, setFilter] = useState<Filter>('all');
  const [selectMode, setSelectMode] = useState(false);
  const [selected, setSelected] = useState<string[]>([]);
  const [preview, setPreview] = useState<TryonHistoryItem | null>(null);
  const [confirmClear, setConfirmClear] = useState(false);
  const [showCompare, setShowCompare] = useState(false);
  const [includeBaseline, setIncludeBaseline] = useState(true);

  const visible = useMemo(
    () => items.filter((x) => (filter === 'liked' ? x.liked : true)),
    [items, filter],
  );

  const liftSelectMode = () => {
    setSelectMode(false);
    setSelected([]);
  };

  const onTilePress = (item: TryonHistoryItem) => {
    if (selectMode) {
      setSelected((prev) => {
        if (prev.includes(item.id)) return prev.filter((x) => x !== item.id);
        if (prev.length >= 4) return prev;
        return [...prev, item.id];
      });
      return;
    }
    setPreview(item);
  };

  // 长按进入多选模式（移动端原生惯例）
  const longPressTimer = React.useRef<number | null>(null);
  const startLongPress = (id: string) => {
    longPressTimer.current = window.setTimeout(() => {
      setSelectMode(true);
      setSelected([id]);
    }, 450);
  };
  const cancelLongPress = () => {
    if (longPressTimer.current) {
      window.clearTimeout(longPressTimer.current);
      longPressTimer.current = null;
    }
  };

  const compareItems = useMemo(
    () => selected.map((id) => items.find((x) => x.id === id)).filter(Boolean) as TryonHistoryItem[],
    [selected, items],
  );

  return (
    <div
      className="fixed inset-0 z-[60] flex items-end justify-center"
      style={{ background: 'rgba(0,0,0,0.78)', backdropFilter: 'blur(6px)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-[420px] rounded-t-3xl flex flex-col"
        style={{
          background: '#0a1628',
          border: '1px solid rgba(0,212,255,0.22)',
          borderBottom: 'none',
          height: '88vh',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* drag handle */}
        <div className="w-10 h-1 rounded-full mx-auto mt-3 mb-2 shrink-0" style={{ background: 'rgba(255,255,255,0.2)' }} />

        {/* 顶部标题 + 操作 */}
        <div className="px-4 pb-2 shrink-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Sparkles size={14} style={{ color: '#00d4ff' }} />
              <h3 className="text-sm font-bold text-white">我的试戴记录</h3>
              <span className="text-[11px] px-1.5 py-0.5 rounded-full" style={{ background: 'rgba(0,212,255,0.12)', color: '#00d4ff' }}>{items.length}</span>
            </div>
            <button onClick={onClose}><X size={18} style={{ color: '#94a3b8' }} /></button>
          </div>

          <div className="flex items-center gap-2 mt-3">
            {(['all', 'liked'] as Filter[]).map((f) => (
              <button
                key={f}
                onClick={() => setFilter(f)}
                className="px-3 py-1 rounded-full text-[11px] font-medium transition-all"
                style={{
                  background: filter === f ? 'rgba(0,212,255,0.14)' : 'rgba(255,255,255,0.04)',
                  border: `1px solid ${filter === f ? 'rgba(0,212,255,0.4)' : 'rgba(255,255,255,0.08)'}`,
                  color: filter === f ? '#00d4ff' : '#94a3b8',
                }}
              >
                {f === 'all' ? '全部' : '收藏'}
              </button>
            ))}

            <div className="ml-auto flex items-center gap-1.5">
              {selectMode ? (
                <>
                  <span className="text-[11px]" style={{ color: '#94a3b8' }}>已选 {selected.length}/4</span>
                  {selected.length >= 2 && (
                    <button
                      onClick={() => setShowCompare(true)}
                      className="px-2.5 py-1 rounded-lg text-[11px] font-semibold flex items-center gap-1"
                      style={{ background: 'linear-gradient(135deg, rgba(0,212,255,0.3), rgba(179,71,255,0.3))', border: '1px solid rgba(0,212,255,0.5)', color: '#00d4ff' }}
                    >
                      <GitCompareArrows size={11} />对比
                    </button>
                  )}
                  {selected.length > 0 && (
                    <button
                      onClick={() => {
                        onRemoveMany(selected);
                        liftSelectMode();
                      }}
                      className="px-2.5 py-1 rounded-lg text-[11px] font-semibold"
                      style={{ background: 'rgba(255,77,141,0.12)', border: '1px solid rgba(255,77,141,0.35)', color: '#ff6b9e' }}
                    >
                      删除
                    </button>
                  )}
                  <button onClick={liftSelectMode} className="px-2.5 py-1 rounded-lg text-[11px]" style={{ color: '#94a3b8' }}>取消</button>
                </>
              ) : (
                <>
                  {items.length > 0 && (
                    <button
                      onClick={() => setSelectMode(true)}
                      className="px-2.5 py-1 rounded-lg text-[11px] font-semibold flex items-center gap-1"
                      style={{ background: 'rgba(0,212,255,0.08)', border: '1px solid rgba(0,212,255,0.3)', color: '#00d4ff' }}
                    >
                      <GitCompareArrows size={11} />选择对比
                    </button>
                  )}
                  {items.length > 0 && (
                    <button
                      onClick={() => setConfirmClear(true)}
                      className="px-2.5 py-1 rounded-lg text-[11px]"
                      style={{ color: '#94a3b8' }}
                    >
                      <Trash2 size={11} className="inline mr-0.5" />清空
                    </button>
                  )}
                </>
              )}
            </div>
          </div>
        </div>

        {/* 网格 / 空态 */}
        <div className="flex-1 overflow-y-auto px-4 pb-6 pt-1" style={{ scrollbarWidth: 'none' }}>
          {visible.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-24 gap-3">
              <div className="text-4xl opacity-50">📷</div>
              <p className="text-xs" style={{ color: '#6b7280' }}>
                {filter === 'liked' ? '还没有收藏的试戴记录' : '还没有试戴记录'}
              </p>
              <p className="text-[11px]" style={{ color: '#4b5563' }}>试戴成功后会自动保存到这里</p>
            </div>
          ) : (
            <div className="grid grid-cols-3 gap-2">
              {visible.map((item) => {
                const isSel = selected.includes(item.id);
                return (
                  <div
                    key={item.id}
                    onClick={() => onTilePress(item)}
                    onTouchStart={() => startLongPress(item.id)}
                    onTouchEnd={cancelLongPress}
                    onTouchCancel={cancelLongPress}
                    onMouseDown={() => startLongPress(item.id)}
                    onMouseUp={cancelLongPress}
                    onMouseLeave={cancelLongPress}
                    className="relative rounded-xl overflow-hidden cursor-pointer"
                    style={{
                      aspectRatio: '3/4',
                      border: isSel ? '2px solid #00d4ff' : '1px solid rgba(0,212,255,0.12)',
                      background: 'rgba(10,22,40,0.8)',
                    }}
                  >
                    <img src={item.resultImage} alt={item.productName} className="w-full h-full object-cover" />
                    <div className="absolute inset-0" style={{ background: 'linear-gradient(to top, rgba(5,10,20,0.92), transparent 55%)' }} />

                    {/* 调整角标 */}
                    {item.adjustNote && (
                      <span className="absolute top-1.5 left-1.5 chip chip-purple" style={{ fontSize: 9, padding: '1px 5px' }}>
                        {item.adjustNote}
                      </span>
                    )}

                    {/* 收藏按钮 */}
                    {!selectMode && (
                      <button
                        onClick={(e) => { e.stopPropagation(); onToggleLike(item.id); }}
                        className="absolute top-1.5 right-1.5 p-1 rounded-full"
                        style={{ background: 'rgba(5,10,20,0.7)' }}
                      >
                        <Heart
                          size={11}
                          fill={item.liked ? '#ff4d8d' : 'none'}
                          style={{ color: item.liked ? '#ff4d8d' : '#94a3b8' }}
                        />
                      </button>
                    )}

                    {/* 多选模式：勾选标记 */}
                    {selectMode && (
                      <div
                        className="absolute top-1.5 right-1.5 w-5 h-5 rounded-full flex items-center justify-center"
                        style={{
                          background: isSel ? 'linear-gradient(135deg,#00d4ff,#b347ff)' : 'rgba(5,10,20,0.75)',
                          border: '1px solid rgba(0,212,255,0.45)',
                        }}
                      >
                        {isSel && <CheckCircle2 size={11} style={{ color: '#050a14' }} />}
                      </div>
                    )}

                    {/* 文案 */}
                    <div className="absolute bottom-0 left-0 right-0 p-1.5">
                      <p className="text-[10px] font-semibold text-white truncate">{item.productName}</p>
                      <p className="text-[9px] flex items-center gap-1" style={{ color: '#94a3b8' }}>
                        <Clock size={8} />{formatRelative(item.createdAt)}
                      </p>
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {items.length > 0 && (
            <p className="text-[10px] text-center mt-3" style={{ color: '#4b5563' }}>
              本地保存，最多 30 条 · 长按图片可多选
            </p>
          )}
        </div>

        {/* 单图预览 */}
        {preview && (
          <div
            className="fixed inset-0 z-[70] flex items-center justify-center px-4"
            style={{ background: 'rgba(0,0,0,0.92)' }}
            onClick={() => setPreview(null)}
          >
            <div className="w-full max-w-[380px]" onClick={(e) => e.stopPropagation()}>
              <img src={preview.resultImage} alt={preview.productName} className="w-full rounded-2xl"
                   style={{ border: '1px solid rgba(0,212,255,0.2)' }} />
              <div className="mt-3 px-1">
                <div className="flex items-center justify-between">
                  <h4 className="text-sm font-bold text-white truncate">{preview.productName}</h4>
                  <span className="text-xs gradient-text font-bold whitespace-nowrap">{preview.productPrice}</span>
                </div>
                <p className="text-[11px] mt-1" style={{ color: '#94a3b8' }}>
                  {formatRelative(preview.createdAt)}
                  {preview.adjustNote ? ` · ${preview.adjustNote}` : ''}
                </p>
                <div className="flex items-center gap-2 mt-3">
                  <button
                    onClick={() => onToggleLike(preview.id)}
                    className="flex-1 py-2 rounded-lg text-xs font-semibold flex items-center justify-center gap-1"
                    style={{
                      background: preview.liked ? 'rgba(255,77,141,0.14)' : 'rgba(255,255,255,0.04)',
                      border: `1px solid ${preview.liked ? 'rgba(255,77,141,0.4)' : 'rgba(255,255,255,0.1)'}`,
                      color: preview.liked ? '#ff4d8d' : '#94a3b8',
                    }}
                  >
                    <Heart size={12} fill={preview.liked ? '#ff4d8d' : 'none'} />
                    {preview.liked ? '已收藏' : '收藏'}
                  </button>
                  <button
                    onClick={() => { onRemove(preview.id); setPreview(null); }}
                    className="flex-1 py-2 rounded-lg text-xs font-semibold"
                    style={{ background: 'rgba(255,77,141,0.08)', border: '1px solid rgba(255,77,141,0.25)', color: '#ff6b9e' }}
                  >
                    <Trash2 size={12} className="inline mr-1" />删除
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* 清空确认 */}
        {confirmClear && (
          <div
            className="fixed inset-0 z-[70] flex items-center justify-center px-6"
            style={{ background: 'rgba(0,0,0,0.85)' }}
            onClick={() => setConfirmClear(false)}
          >
            <div
              className="w-full max-w-[300px] rounded-2xl p-4"
              style={{ background: '#0a1628', border: '1px solid rgba(255,77,141,0.3)' }}
              onClick={(e) => e.stopPropagation()}
            >
              <p className="text-sm font-bold text-white mb-1">清空所有记录？</p>
              <p className="text-[11px] mb-4" style={{ color: '#94a3b8' }}>共 {items.length} 条，删除后不可恢复</p>
              <div className="flex gap-2">
                <button onClick={() => setConfirmClear(false)} className="flex-1 py-2 rounded-lg text-xs font-medium"
                        style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.1)', color: '#94a3b8' }}>
                  取消
                </button>
                <button onClick={() => { onClear(); setConfirmClear(false); }}
                        className="flex-1 py-2 rounded-lg text-xs font-bold"
                        style={{ background: 'rgba(255,77,141,0.18)', border: '1px solid rgba(255,77,141,0.5)', color: '#ff4d8d' }}>
                  全部清空
                </button>
              </div>
            </div>
          </div>
        )}

        {/* 对比浮层 */}
        {showCompare && compareItems.length >= 2 && (
          <CompareOverlay
            items={compareItems}
            beforeImage={includeBaseline ? beforeImage : null}
            includeBaseline={includeBaseline}
            onToggleBaseline={() => setIncludeBaseline((v) => !v)}
            onClose={() => setShowCompare(false)}
          />
        )}
      </div>
    </div>
  );
};

/**
 * 对比浮层：N 张并排或 2×2 网格，可选叠加用户原图作为基线。
 * 业界做法（Replicate/美图等）：
 *   2 张 → 等宽并排撑满屏（最直观）
 *   3 张 → 三等分窄屏并排
 *   4 张 → 2×2 网格（再窄就糊了）
 * 关键：
 *   - 用内联 style 写百分比宽度，避免 Tailwind 任意值类未编译时降级到 auto
 *   - 图片固定 3/4 比例 + object-position top，保留人脸不被裁
 */
const CompareOverlay: React.FC<{
  items: TryonHistoryItem[];
  beforeImage: string | null;
  includeBaseline: boolean;
  onToggleBaseline: () => void;
  onClose: () => void;
}> = ({ items, beforeImage, includeBaseline, onToggleBaseline, onClose }) => {
  const showBaseline = includeBaseline && !!beforeImage;
  const totalCount = items.length + (showBaseline ? 1 : 0);

  // 布局策略：2/3 张并排一行，4+ 张 2 列网格
  const useGrid = totalCount >= 4;
  const colsPerRow = useGrid ? 2 : totalCount;

  // 一个 tile 的 React 节点（基线 / 试戴结果通用）
  const Tile = ({
    image,
    title,
    subtitle,
    badge,
    badgeStyle = 'cyan',
    extra,
  }: {
    image: string;
    title: string;
    subtitle?: string;
    badge?: string;
    badgeStyle?: 'cyan' | 'white' | 'purple';
    extra?: string;
  }) => {
    const badgeColor =
      badgeStyle === 'white'
        ? { background: 'rgba(255,255,255,0.92)', color: '#050a14', fontWeight: 700 }
        : badgeStyle === 'purple'
        ? { background: 'rgba(179,71,255,0.85)', color: '#fff' }
        : { background: 'rgba(0,212,255,0.85)', color: '#050a14', fontWeight: 700 };
    return (
      <div
        className="rounded-2xl overflow-hidden flex flex-col"
        style={{
          border: '1px solid rgba(0,212,255,0.18)',
          background: 'rgba(10,22,40,0.9)',
        }}
      >
        {/* 图：固定 3:4 比例 + 顶部对齐（保住人脸） */}
        <div className="relative w-full" style={{ aspectRatio: '3 / 4', background: '#050a14' }}>
          <img
            src={image}
            alt={title}
            className="w-full h-full"
            style={{ objectFit: 'cover', objectPosition: 'center top' }}
          />
          {badge && (
            <span
              className="absolute top-1.5 left-1.5 rounded-full"
              style={{ ...badgeColor, fontSize: 10, padding: '2px 8px' }}
            >
              {badge}
            </span>
          )}
        </div>
        <div className="p-2 shrink-0">
          <p className="text-xs font-semibold text-white truncate">{title}</p>
          {subtitle && <p className="text-[11px] gradient-text font-bold truncate">{subtitle}</p>}
          {extra && <p className="text-[10px] mt-0.5 truncate" style={{ color: '#6b7280' }}>{extra}</p>}
        </div>
      </div>
    );
  };

  return (
    <div className="fixed inset-0 z-[80] flex flex-col" style={{ background: 'rgba(5,10,20,0.97)' }}>
      <div
        className="flex items-center justify-between px-4 py-3 shrink-0"
        style={{ borderBottom: '1px solid rgba(0,212,255,0.12)' }}
      >
        <div className="flex items-center gap-2">
          <GitCompareArrows size={14} style={{ color: '#00d4ff' }} />
          <span className="text-sm font-bold text-white">对比 {items.length} 款</span>
        </div>
        <button onClick={onClose}>
          <X size={20} style={{ color: '#94a3b8' }} />
        </button>
      </div>

      {beforeImage && (
        <div className="px-4 pt-2 pb-1 shrink-0">
          <button
            onClick={onToggleBaseline}
            className="text-[11px] flex items-center gap-1.5 px-2.5 py-1 rounded-full"
            style={{
              background: includeBaseline ? 'rgba(0,212,255,0.12)' : 'rgba(255,255,255,0.04)',
              border: `1px solid ${includeBaseline ? 'rgba(0,212,255,0.35)' : 'rgba(255,255,255,0.08)'}`,
              color: includeBaseline ? '#00d4ff' : '#94a3b8',
            }}
          >
            <CheckCircle2 size={10} />
            {includeBaseline ? '已加入原图基线' : '加入原图基线'}
          </button>
        </div>
      )}

      <div className="flex-1 overflow-y-auto p-3">
        <div
          className="grid gap-2"
          style={{
            // 用内联 style 写 grid-template-columns，绕过 Tailwind JIT 任意值类未编译的坑
            gridTemplateColumns: `repeat(${colsPerRow}, minmax(0, 1fr))`,
          }}
        >
          {showBaseline && (
            <Tile
              image={beforeImage}
              title="未试戴"
              subtitle="对比基线"
              badge="原图"
              badgeStyle="white"
            />
          )}
          {items.map((it) => (
            <Tile
              key={it.id}
              image={it.resultImage}
              title={it.productName}
              subtitle={it.productPrice}
              badge={it.adjustNote}
              badgeStyle="purple"
              extra={formatRelative(it.createdAt)}
            />
          ))}
        </div>

        <p className="text-[10px] text-center mt-3" style={{ color: '#4b5563' }}>
          {useGrid ? `2×${Math.ceil(totalCount / 2)} 网格 · 上下滑动查看更多` : '同屏并排对比'}
        </p>
      </div>
    </div>
  );
};
