import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Sparkles, X, Star, MessageCircle, Navigation,
  RefreshCw, CheckCircle, Zap, Search, ChevronDown, Heart, Copy,
  History,
} from 'lucide-react';
import { PageHeader } from '../components/PageHeader';
import { CompareSlider } from '../components/CompareSlider';
import { StarRating } from '../components/StarRating';
import { TryonHistoryDrawer } from '../components/TryonHistoryDrawer';
import { hairStyles, stores, HairStyle } from '../data/mockData';
import { api, AnalyzeResult, FunnelStats, AiStatus, RecommendationMatch } from '../api/client';
import { useTryonStudio, TryonEntry } from '../hooks/useTryonStudio';
import { useTryonHistory } from '../hooks/useTryonHistory';
import { priceRangeFull, priceRangeCompact } from '../utils/price';
import { resolveAssetUrl } from '../utils/assetUrl';

interface TryonWallPageProps {
  sessionId: string | null;
  userImage: string | null;
  analysis: AnalyzeResult | null;
  focusProduct?: HairStyle | null;
  onAnalysis: (result: AnalyzeResult) => void;
  onBack: () => void;
  onViewStore: () => void;
}

// 局部调整项（对应客户痛点：刘海 / 发际线 / 白发）
const ADJUSTS = [
  { label: '加刘海', note: '齐刘海' },
  { label: '修发际线', note: '修复M型发际线' },
  { label: '遮白发', note: '遮盖白发' },
  { label: '更蓬松', note: '头顶更蓬松' },
];

/**
 * 把后端 strategyUsed 翻译成给客户看的友好文案。
 * 客户不需要知道是 yunwu / dashscope / chat / edits / 模型名，
 * 但要能区分"真 AI 生成"还是"演示兜底"。
 */
const friendlyStrategyLabel = (strategy?: string, aiMode?: string): { text: string; tone: 'real' | 'seed' | 'mock' } => {
  if (!strategy && !aiMode) return { text: 'AI 高清精准生成', tone: 'real' };
  const s = (strategy || '').toLowerCase();
  if (s.startsWith('yunwu') || s.startsWith('dashscope') || s.includes('wan2') || s.includes('gpt-image')) {
    return { text: 'AI 高清精准生成', tone: 'real' };
  }
  if (s.includes('seed')) {
    return { text: '演示效果（AI 暂不可用）', tone: 'seed' };
  }
  if (s.includes('mock') || aiMode === 'mock') {
    return { text: '演示模式', tone: 'mock' };
  }
  return { text: 'AI 高清精准生成', tone: 'real' };
};

/**
 * 进度阶段文案：根据进度百分比返回当前阶段提示。
 * 配合后端伪进度推进器，给用户连续可感知的"AI 在做什么"。
 */
const progressStage = (pct: number): string => {
  if (pct < 15) return 'AI 正在仔细看你的脸…';
  if (pct < 35) return '正在为你试戴这款假发…';
  if (pct < 60) return '正在合成发丝细节…';
  if (pct < 85) return '正在做高清渲染…';
  if (pct < 100) return '即将完成…';
  return '已完成';
};

/**
 * 大型等待动画：双层脉冲圆环 + 假发缩略图 + 阶段文案 + 进度条。
 * 给用户"AI 真在工作"的可感知反馈，配合后端伪进度推进器使用。
 */
const WaitingAnimation: React.FC<{ progress: number; productName: string; productImage: string }> = ({
  progress,
  productName,
  productImage,
}) => {
  const pct = Math.max(2, Math.min(100, progress));
  return (
    <div
      className="relative rounded-2xl overflow-hidden flex flex-col items-center justify-center px-4"
      style={{
        height: 300,
        border: '1px solid rgba(0,212,255,0.18)',
        background: 'radial-gradient(circle at 50% 35%, rgba(0,212,255,0.10), rgba(5,10,20,0.8) 60%)',
      }}
    >
      {/* 双层脉冲环（外层慢、内层快） */}
      <div className="relative" style={{ width: 120, height: 120 }}>
        <div
          className="absolute inset-0 rounded-full pulse-slow"
          style={{ border: '2px solid rgba(0,212,255,0.35)', boxShadow: '0 0 28px rgba(0,212,255,0.25)' }}
        />
        <div
          className="absolute rounded-full pulse-fast"
          style={{ inset: 14, border: '2px dashed rgba(179,71,255,0.55)' }}
        />
        {/* 中心：假发缩略图 */}
        <div className="absolute rounded-full overflow-hidden" style={{ inset: 24, border: '1px solid rgba(0,212,255,0.4)' }}>
          <img src={productImage} alt={productName} className="w-full h-full object-cover" />
        </div>
        {/* 旋转点缀 */}
        <div
          className="absolute inset-0 spin-slow"
          style={{
            background:
              'conic-gradient(from 0deg, transparent 0deg, transparent 270deg, rgba(0,212,255,0.65) 320deg, transparent 360deg)',
            borderRadius: '50%',
            mask: 'radial-gradient(circle, transparent 50%, black 51%)',
            WebkitMask: 'radial-gradient(circle, transparent 50%, black 51%)',
          }}
        />
      </div>

      {/* 阶段文案（随 progress 切换） */}
      <p className="text-sm font-semibold mt-4" style={{ color: '#00d4ff' }}>{progressStage(pct)}</p>
      <p className="text-[11px] mt-1 text-center" style={{ color: '#94a3b8' }}>
        预计 60-100 秒 · 高清模型生成需要一点时间
      </p>

      {/* 进度条 */}
      <div className="w-full max-w-[260px] mt-3">
        <div className="h-1.5 rounded-full overflow-hidden" style={{ background: 'rgba(255,255,255,0.08)' }}>
          <div
            className="h-full rounded-full progress-bar transition-all duration-700"
            style={{ width: `${pct}%` }}
          />
        </div>
        <p className="text-[10px] text-right mt-1" style={{ color: '#6b7280' }}>{pct}%</p>
      </div>
    </div>
  );
};

const SHEET_CATEGORIES = ['全部', '短发', '中长发', '长发', '卷发'];
const STYLE_LINES = ['全部系列', 'V系列', '设计款', 'Art款'];
const SHEET_PRICES = ['全部价格', '¥2000以下', '¥2000-5000', '¥5000以上'];
const SHEET_NONE = '全部';

const priceStart = (p: HairStyle) => priceRangeCompact(p);

const hitPriceBucket = (p: HairStyle, bucket: string) => {
  if (bucket === '全部价格') return true;
  if (bucket === '¥2000以下') return p.priceMin < 2000;
  if (bucket === '¥2000-5000') return p.priceMin >= 2000 && p.priceMax <= 5000;
  if (bucket === '¥5000以上') return p.priceMax > 5000;
  return true;
};

export const TryonWallPage: React.FC<TryonWallPageProps> = ({
  sessionId,
  userImage,
  analysis,
  focusProduct,
  onAnalysis,
  onBack,
  onViewStore,
}) => {
  const FAVORITES_KEY = 'tryon_favorites';
  const history = useTryonHistory();
  const studio = useTryonStudio(sessionId, {
    userImage,
    onSuccess: (item) => history.add(item),
  });
  const [showHistory, setShowHistory] = useState(false);
  const [analysisState, setAnalysisState] = useState<AnalyzeResult | null>(analysis);
  const [allProducts, setAllProducts] = useState<HairStyle[]>(hairStyles);
  const [wallProducts, setWallProducts] = useState<HairStyle[]>([]);
  const [analyzing, setAnalyzing] = useState(false);

  const [compareMode, setCompareMode] = useState(false);
  const [compareSel, setCompareSel] = useState<string[]>([]);
  const [showCompare, setShowCompare] = useState(false);
  const [showSheet, setShowSheet] = useState(false);
  const [sheetSearch, setSheetSearch] = useState('');
  const [sheetCategory, setSheetCategory] = useState('全部');
  const [sheetStyleLine, setSheetStyleLine] = useState('全部系列');
  const [sheetPrice, setSheetPrice] = useState('全部价格');
  const [sheetCrowd, setSheetCrowd] = useState(SHEET_NONE);
  const [sheetScene, setSheetScene] = useState(SHEET_NONE);
  const [sheetStore, setSheetStore] = useState(SHEET_NONE);
  const [needHairlineFix, setNeedHairlineFix] = useState(false);
  const [needWhiteCover, setNeedWhiteCover] = useState(false);
  const [favoriteIds, setFavoriteIds] = useState<number[]>([]);
  const [showConsult, setShowConsult] = useState(false);
  const [consultCopied, setConsultCopied] = useState(false);
  const [funnel, setFunnel] = useState<FunnelStats | null>(null);
  const [aiStatus, setAiStatus] = useState<AiStatus | null>(null);

  const [enlarge, setEnlarge] = useState<{ product: HairStyle; key: string } | null>(null);
  const [rating, setRating] = useState(0);
  const [feedbackDone, setFeedbackDone] = useState(false);

  const initRef = useRef(false);
  const focusStartedRef = useRef(false);
  const nearbyStore = stores[0];

  // 初始化：拉全量发型库 + 获取推荐结果（不自动触发合成）
  useEffect(() => {
    if (initRef.current) return;
    initRef.current = true;

    api.products().then((list) => {
      if (Array.isArray(list) && list.length) setAllProducts(list);
    }).catch(() => {});

    const withFocus = (recs: HairStyle[]) => {
      const list = [...recs];
      if (focusProduct && !list.find((x) => x.id === focusProduct.id)) {
        list.unshift(focusProduct);
      }
      return list.slice(0, 6);
    };
    const fallback = () => withFocus(hairStyles.slice(0, 4));

    const start = (recs: HairStyle[]) => {
      const list = recs.length ? withFocus(recs) : fallback();
      setWallProducts(list);
    };

    if (analysisState?.recommendedProducts?.length) {
      start(analysisState.recommendedProducts);
    } else if (sessionId) {
      setAnalyzing(true);
      api.analyze(sessionId)
        .then((res) => {
          setAnalysisState(res);
          onAnalysis(res);
          start(res.recommendedProducts || []);
        })
        .catch(() => start([]))
        .finally(() => setAnalyzing(false));
    } else {
      start([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    trackEvent('enter_wall');
    api.funnel().then(setFunnel).catch(() => {});
    api.aiStatus().then(setAiStatus).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(FAVORITES_KEY);
      const parsed = raw ? (JSON.parse(raw) as number[]) : [];
      if (Array.isArray(parsed)) setFavoriteIds(parsed);
    } catch {
      setFavoriteIds([]);
    }
  }, []);

  const trackEvent = (eventType: string, productId?: number, note?: string) => {
    api.submitFeedback({
      sessionId: sessionId ?? undefined,
      productId,
      eventType,
      sentiment: 'event',
      note,
    }).catch(() => {});
    if (['upload_photo', 'enter_wall', 'open_compare', 'open_consult', 'consult_copy_wechat'].includes(eventType)) {
      api.funnel().then(setFunnel).catch(() => {});
    }
  };

  const doneCount = wallProducts.filter(
    (p) => studio.entries[studio.keyFor(p)]?.status === 'success',
  ).length;
  // 推荐选款已移到选款页；本页只保留对比浮层需要的匹配信息（matchMap）。
  const matchMap = useMemo(() => {
    const map = new Map<number, RecommendationMatch>();
    (analysisState?.recommendationMatches || []).forEach((m) => map.set(m.wigProductId, m));
    return map;
  }, [analysisState?.recommendationMatches]);
  const crowdOptions = useMemo(() => {
    const list = new Set<string>();
    allProducts.forEach((p) => (p.targetCrowds || []).forEach((c) => list.add(c)));
    return [SHEET_NONE, ...Array.from(list)];
  }, [allProducts]);

  const sceneOptions = useMemo(() => {
    const list = new Set<string>();
    allProducts.forEach((p) => (p.sceneTags || []).forEach((c) => list.add(c)));
    return [SHEET_NONE, ...Array.from(list)];
  }, [allProducts]);

  const storeOptions = useMemo(() => {
    return [SHEET_NONE, ...stores.map((s) => s.name)];
  }, []);

  const selectedStoreId = useMemo(() => {
    if (sheetStore === SHEET_NONE) return null;
    return stores.find((s) => s.name === sheetStore)?.id ?? null;
  }, [sheetStore]);

  const sheetProducts = allProducts.filter((p) => {
    const catMatch = sheetCategory === '全部' || p.category === sheetCategory;
    const priceMatch = hitPriceBucket(p, sheetPrice);
    const search = sheetSearch.trim();
    const searchMatch =
      !search
      || p.name.includes(search)
      || p.tags.some((t) => t.includes(search))
      || p.material.includes(search)
      || (p.targetCrowds || []).some((c) => c.includes(search))
      || (p.sceneTags || []).some((c) => c.includes(search));
    const crowdMatch = sheetCrowd === SHEET_NONE || (p.targetCrowds || []).includes(sheetCrowd);
    const sceneMatch = sheetScene === SHEET_NONE || (p.sceneTags || []).includes(sheetScene);
    const storeMatch = selectedStoreId == null || (p.storeIds || []).includes(selectedStoreId);
    const lineMatch = sheetStyleLine === '全部系列' || (p.tags || []).includes(sheetStyleLine);
    const hairlineMatch = !needHairlineFix || (p.hairlineCoverage ?? 0) >= 4;
    const whiteMatch = !needWhiteCover || (p.whiteHairCoverage ?? 0) >= 4;
    return catMatch && priceMatch && searchMatch && crowdMatch && sceneMatch && storeMatch && lineMatch && hairlineMatch && whiteMatch;
  });

  const addToWall = (p: HairStyle) => {
    setWallProducts((prev) => (prev.find((x) => x.id === p.id) ? prev : [...prev, p]));
    trackEvent('add_to_wall', p.id);
    setShowSheet(false);
  };

  const startTryon = (product: HairStyle, note?: string) => {
    const key = studio.generate(product, note);
    trackEvent('submit_tryon', product.id, note);
    return key;
  };

  // 三步漏斗③：从选款页带着 focusProduct 进来时，自动开始生成并弹出结果对比层
  useEffect(() => {
    if (focusStartedRef.current || !focusProduct) return;
    focusStartedRef.current = true;
    const key = startTryon(focusProduct);
    setEnlarge({ product: focusProduct, key });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusProduct]);

  const onTileClick = (p: HairStyle) => {
    const key = studio.keyFor(p);
    if (compareMode) {
      setCompareSel((prev) => {
        if (prev.includes(key)) return prev.filter((k) => k !== key);
        if (prev.length >= 2) return prev;
        return [...prev, key];
      });
      return;
    }
    setEnlarge({ product: p, key });
    setRating(0);
    setFeedbackDone(false);
  };

  const applyAdjust = (note: string) => {
    if (!enlarge) return;
    const key = startTryon(enlarge.product, note);
    trackEvent('adjust_tryon', enlarge.product.id, note);
    setEnlarge({ product: enlarge.product, key });
  };

  const submitFeedback = (type: 'like' | 'neutral' | 'dislike') => {
    api.submitFeedback({
      sentiment: type,
      rating: rating || undefined,
      sessionId: sessionId ?? undefined,
      productId: enlarge?.product.id,
      eventType: 'result_feedback',
    }).catch(() => {});
    setFeedbackDone(true);
  };

  const toggleFavorite = (productId: number) => {
    setFavoriteIds((prev) => {
      const next = prev.includes(productId)
        ? prev.filter((id) => id !== productId)
        : [...prev, productId];
      localStorage.setItem(FAVORITES_KEY, JSON.stringify(next));
      trackEvent(next.includes(productId) ? 'favorite_add' : 'favorite_remove', productId);
      return next;
    });
  };

  const Tile: React.FC<{ product: HairStyle }> = ({ product }) => {
    const key = studio.keyFor(product);
    const entry = studio.entries[key];
    const selected = compareSel.includes(key);
    const success = entry?.status === 'success' && entry.resultImage;
    const processing = entry?.status === 'pending' || entry?.status === 'processing';
    const failed = entry?.status === 'failed';
    return (
      <div
        className="relative rounded-xl overflow-hidden card-hover cursor-pointer"
        style={{
          background: 'rgba(10,22,40,0.8)',
          border: selected ? '2px solid #00d4ff' : '1px solid rgba(0,212,255,0.12)',
          aspectRatio: '3/4',
        }}
        onClick={() => onTileClick(product)}
      >
        {success ? (
          <img src={entry!.resultImage} alt={product.name} className="w-full h-full object-cover" />
        ) : (
          <div className="w-full h-full relative">
            <img src={product.image} alt={product.name} className="w-full h-full object-cover" />
            <div className="absolute inset-0" style={{ background: 'linear-gradient(to top, rgba(5,10,20,0.88), transparent 60%)' }} />
            <div className="absolute left-2 right-2 bottom-11">
              {processing && (
                <div className="w-full">
                  <div className="h-1.5 rounded-full overflow-hidden" style={{ background: 'rgba(255,255,255,0.1)' }}>
                    <div className="h-full rounded-full progress-bar transition-all duration-500" style={{ width: `${entry?.progress ?? 8}%` }} />
                  </div>
                  <p className="text-[10px] mt-1" style={{ color: '#00d4ff' }}>AI 生成中 {entry?.progress ?? 0}%</p>
                </div>
              )}
              {!processing && !failed && (
                <span className="chip chip-blue text-xs">待试戴</span>
              )}
              {failed && <span className="chip chip-purple text-xs">生成失败，点开重试</span>}
            </div>
          </div>
        )}

        {/* 渐变 + 文案 */}
        <div className="absolute inset-0 pointer-events-none" style={{ background: 'linear-gradient(to top, rgba(5,10,20,0.92) 0%, transparent 55%)' }} />
        <div className="absolute bottom-0 left-0 right-0 p-2">
          <p className="text-xs font-semibold text-white truncate">{product.name}</p>
          <p className="text-xs gradient-text font-bold">{priceStart(product)}</p>
        </div>

        {!compareMode && !processing && !success && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              const k = startTryon(product);
              setEnlarge({ product, key: k });
            }}
            className="absolute bottom-10 left-2 right-2 py-1 rounded-lg text-[11px] font-semibold"
            style={{ background: 'rgba(0,212,255,0.16)', border: '1px solid rgba(0,212,255,0.45)', color: '#00d4ff' }}
          >
            试戴这款
          </button>
        )}

        {/* AI 推荐角标 */}
        {(analysisState?.faceAnalysis?.recommendedIds ?? []).includes(product.id) && (
          <span className="absolute top-2 left-2 chip chip-purple text-xs">✨AI推荐</span>
        )}

        {!compareMode && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              toggleFavorite(product.id);
            }}
            className="absolute top-2 right-2 p-1.5 rounded-full"
            style={{ background: 'rgba(5,10,20,0.72)', border: '1px solid rgba(255,255,255,0.12)' }}
          >
            <Heart
              size={13}
              fill={favoriteIds.includes(product.id) ? '#ff4d8d' : 'none'}
              style={{ color: favoriteIds.includes(product.id) ? '#ff4d8d' : '#94a3b8' }}
            />
          </button>
        )}

        {/* 对比勾选 */}
        {compareMode && (
          <div
            className="absolute top-2 right-2 w-6 h-6 rounded-full flex items-center justify-center"
            style={{
              background: selected ? 'linear-gradient(135deg,#00d4ff,#b347ff)' : 'rgba(5,10,20,0.7)',
              border: '1px solid rgba(0,212,255,0.4)',
            }}
          >
            {selected && <CheckCircle size={14} style={{ color: '#050a14' }} />}
          </div>
        )}
      </div>
    );
  };

  const compareEntries: TryonEntry[] = compareSel
    .map((k) => {
      const entry = studio.entries[k];
      if (entry) return entry;
      const product = wallProducts.find((p) => studio.keyFor(p) === k);
      if (!product) return null;
      return { key: k, product, status: 'pending', progress: 0, resultImage: product.image };
    })
    .filter(Boolean) as TryonEntry[];

  return (
    <div className="page-enter pb-28">
      <PageHeader
        title="AI 试戴效果墙"
        onBack={onBack}
        rightContent={
          <div className="flex items-center gap-1.5">
            <button
              onClick={() => setShowHistory(true)}
              className="relative px-2.5 py-1.5 rounded-lg text-xs font-medium transition-all flex items-center gap-1"
              style={{
                background: 'rgba(179,71,255,0.08)',
                border: '1px solid rgba(179,71,255,0.3)',
                color: '#b347ff',
              }}
              aria-label="我的试戴记录"
            >
              <History size={12} />
              <span>记录</span>
              {history.items.length > 0 && (
                <span
                  className="absolute -top-1.5 -right-1.5 min-w-[16px] h-4 px-1 rounded-full text-[9px] font-bold flex items-center justify-center"
                  style={{ background: '#ff4d8d', color: '#fff' }}
                >
                  {history.items.length > 99 ? '99+' : history.items.length}
                </span>
              )}
            </button>
            <button
              onClick={() => { setCompareMode((v) => !v); setCompareSel([]); }}
              className="px-2.5 py-1.5 rounded-lg text-xs font-medium transition-all"
              style={{
                background: compareMode ? 'rgba(0,212,255,0.15)' : 'rgba(255,255,255,0.05)',
                border: `1px solid ${compareMode ? 'rgba(0,212,255,0.4)' : 'rgba(255,255,255,0.1)'}`,
                color: compareMode ? '#00d4ff' : '#6b7280',
              }}
            >
              {compareMode ? '退出' : '⇄ 对比'}
            </button>
          </div>
        }
      />

      {/* AI 分析横幅 */}
      {(analyzing || analysisState) && (
        <div className="px-4 pt-3">
          <div className="rounded-2xl p-3" style={{ background: 'linear-gradient(135deg, rgba(0,212,255,0.08), rgba(179,71,255,0.08))', border: '1px solid rgba(0,212,255,0.2)' }}>
            <div className="flex items-center gap-2 mb-1.5">
              <Sparkles size={14} style={{ color: '#00d4ff' }} />
              <span className="text-xs font-semibold" style={{ color: '#00d4ff' }}>
                AI 智能分析{analysisState?.mock ? '（演示模式）' : ''}
              </span>
              {aiStatus?.effectiveMode && (
                <span className="chip chip-blue text-xs">
                  模式 {aiStatus.effectiveMode.toUpperCase()}
                </span>
              )}
            </div>
            {analyzing ? (
              <p className="text-xs" style={{ color: '#94a3b8' }}>正在分析你的脸型与气质，为你匹配多款发型…</p>
            ) : (
              <>
                <p className="text-xs leading-relaxed" style={{ color: '#94a3b8' }}>{analysisState?.faceAnalysis?.analysisText}</p>
                <div className="flex gap-1.5 mt-2 flex-wrap">
                  {analysisState?.faceAnalysis?.faceShape && <span className="chip chip-blue">脸型 · {analysisState.faceAnalysis.faceShape}</span>}
                  {analysisState?.faceAnalysis?.hairlineType && <span className="chip chip-purple">发际线 · {analysisState.faceAnalysis.hairlineType}</span>}
                  {analysisState?.faceAnalysis?.hasWhiteHair && <span className="chip chip-blue">需遮盖白发</span>}
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* 状态提示 */}
      <div className="px-4 mt-3 flex items-center justify-between">
        <p className="text-xs" style={{ color: '#94a3b8' }}>
          {compareMode ? `勾选 2 款进行对比（已选 ${compareSel.length}/2）` : `已试戴 ${doneCount}/${wallProducts.length} 款（先选再生成）`}
        </p>
        <span className="text-xs" style={{ color: '#4b5563' }}>点击查看大图</span>
      </div>

      {/* 风格系列快筛：V系列 / 设计款 / Art款 */}
      {!compareMode && (
        <div className="px-4 mt-3 flex gap-2 overflow-x-auto pb-1" style={{ scrollbarWidth: 'none' }}>
          {STYLE_LINES.map((line) => (
            <button
              key={line}
              onClick={() => setSheetStyleLine(line)}
              className="flex-shrink-0 px-3 py-1.5 rounded-full text-xs font-medium transition-all"
              style={{
                background: sheetStyleLine === line ? 'rgba(0,212,255,0.15)' : 'rgba(255,255,255,0.04)',
                border: sheetStyleLine === line ? '1px solid rgba(0,212,255,0.45)' : '1px solid rgba(255,255,255,0.08)',
                color: sheetStyleLine === line ? '#00d4ff' : '#94a3b8',
              }}
            >
              {line}
            </button>
          ))}
          {SHEET_CATEGORIES.filter((c) => c !== '全部').map((cat) => (
            <button
              key={cat}
              onClick={() => setSheetCategory(sheetCategory === cat ? '全部' : cat)}
              className="flex-shrink-0 px-3 py-1.5 rounded-full text-xs font-medium transition-all"
              style={{
                background: sheetCategory === cat ? 'rgba(179,71,255,0.15)' : 'rgba(255,255,255,0.04)',
                border: sheetCategory === cat ? '1px solid rgba(179,71,255,0.45)' : '1px solid rgba(255,255,255,0.08)',
                color: sheetCategory === cat ? '#b347ff' : '#94a3b8',
              }}
            >
              {cat}
            </button>
          ))}
        </div>
      )}

      {/* 转化漏斗（演示/汇报可直接展示） */}
      {!compareMode && funnel && (
        <div className="px-4 mt-2">
          <div
            className="rounded-xl p-2.5"
            style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(0,212,255,0.12)' }}
          >
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold" style={{ color: '#00d4ff' }}>实时转化漏斗</span>
              <span className="text-[11px]" style={{ color: '#6b7280' }}>事件 {funnel.totalEvents}</span>
            </div>
            <div className="grid grid-cols-4 gap-2 mt-2">
              {[
                { label: '上传', value: funnel.uploadPhoto },
                { label: '对比', value: funnel.openCompare },
                { label: '咨询', value: funnel.openConsult },
                { label: '加微', value: funnel.copyWechat },
              ].map((i) => (
                <div key={i.label} className="rounded-lg px-2 py-1.5 text-center" style={{ background: 'rgba(10,22,40,0.7)' }}>
                  <p className="text-[11px] font-semibold text-white">{i.value}</p>
                  <p className="text-[10px]" style={{ color: '#6b7280' }}>{i.label}</p>
                </div>
              ))}
            </div>
            <p className="text-[11px] mt-2" style={{ color: '#94a3b8' }}>
              对比率 {funnel.compareRate} · 咨询率 {funnel.consultRate}
            </p>
            {aiStatus?.effectiveMode !== 'real' && (
              <p className="text-[11px] mt-1" style={{ color: '#b347ff' }}>
                当前非真实模型：{aiStatus?.suggestion}
              </p>
            )}
          </div>
        </div>
      )}

      {/* 候选清单：让用户有“已收藏待决策”的沉淀感 */}
      {!compareMode && favoriteIds.length > 0 && (
        <div className="px-4 mt-2 overflow-x-auto" style={{ scrollbarWidth: 'none' }}>
          <div className="flex items-center gap-2 mb-1.5">
            <Heart size={13} style={{ color: '#ff4d8d' }} />
            <span className="text-xs font-semibold" style={{ color: '#ff4d8d' }}>候选清单（{favoriteIds.length}）</span>
          </div>
          <div className="flex gap-2 min-w-max">
            {favoriteIds
              .map((id) => allProducts.find((p) => p.id === id))
              .filter(Boolean)
              .map((p) => {
                const product = p as HairStyle;
                const entry = studio.entries[studio.keyFor(product)];
                return (
                  <button
                    key={product.id}
                    onClick={() => setEnlarge({ product, key: studio.keyFor(product) })}
                    className="w-[96px] rounded-xl overflow-hidden text-left"
                    style={{ border: '1px solid rgba(255,77,141,0.26)' }}
                  >
                    <div style={{ aspectRatio: '1/1' }}>
                      <img src={entry?.resultImage || product.image} alt={product.name} className="w-full h-full object-cover" />
                    </div>
                    <p className="text-[10px] px-1.5 py-1 truncate" style={{ color: '#e2e8f0', background: 'rgba(10,22,40,0.88)' }}>{product.name}</p>
                  </button>
                );
              })}
          </div>
        </div>
      )}

      {/* 效果墙网格 */}
      <div className="px-4 mt-2 grid grid-cols-2 gap-3">
        {wallProducts.map((p) => <Tile key={p.id} product={p} />)}

        {/* 添加更多 */}
        <button
          onClick={() => setShowSheet(true)}
          className="rounded-xl flex flex-col items-center justify-center gap-2"
          style={{ aspectRatio: '3/4', background: 'rgba(10,22,40,0.5)', border: '1px dashed rgba(0,212,255,0.3)' }}
        >
          <div className="w-10 h-10 rounded-full flex items-center justify-center text-2xl" style={{ background: 'rgba(0,212,255,0.1)', color: '#00d4ff' }}>＋</div>
          <span className="text-xs" style={{ color: '#00d4ff' }}>添加更多发型</span>
        </button>
      </div>

      {/* 对比模式底部操作条 */}
      {compareMode && compareSel.length === 2 && (
        <div className="fixed bottom-4 left-1/2 -translate-x-1/2 z-40 w-[92%] max-w-[400px]">
          <button
            onClick={() => {
              trackEvent('open_compare', undefined, compareSel.join(','));
              setShowCompare(true);
            }}
            className="w-full py-3 rounded-xl text-sm font-bold btn-primary flex items-center justify-center gap-2"
            style={{ color: '#050a14' }}
          >
            <Zap size={16} /> 并排对比这 2 款
          </button>
        </div>
      )}

      {/* ===== 添加发型 底部弹层 ===== */}
      {showSheet && (
        <div className="fixed inset-0 z-50 flex items-end justify-center" style={{ background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(4px)' }} onClick={() => setShowSheet(false)}>
          <div className="w-full max-w-[420px] rounded-t-3xl overflow-hidden" style={{ background: '#0a1628', border: '1px solid rgba(0,212,255,0.2)', borderBottom: 'none', maxHeight: '72vh' }} onClick={(e) => e.stopPropagation()}>
            <div className="w-10 h-1 rounded-full mx-auto mt-3 mb-2" style={{ background: 'rgba(255,255,255,0.2)' }} />
            <div className="flex items-center justify-between px-4 pb-2">
              <h3 className="text-sm font-bold text-white">选择发型加入试戴</h3>
              <button onClick={() => setShowSheet(false)}><X size={18} style={{ color: '#6b7280' }} /></button>
            </div>
            <div className="px-4 pb-2">
              <div
                className="flex items-center gap-2 px-3 py-2 rounded-xl"
                style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(0,212,255,0.12)' }}
              >
                <Search size={13} style={{ color: '#6b7280' }} />
                <input
                  value={sheetSearch}
                  onChange={(e) => setSheetSearch(e.target.value)}
                  placeholder="搜发型名/标签/材质"
                  className="flex-1 bg-transparent text-xs outline-none"
                  style={{ color: '#e2e8f0', caretColor: '#00d4ff' }}
                />
              </div>
              <div className="mt-2 flex items-center gap-2">
                <div className="relative">
                  <select
                    value={sheetCategory}
                    onChange={(e) => setSheetCategory(e.target.value)}
                    className="appearance-none text-xs px-2.5 py-1.5 pr-6 rounded-lg outline-none"
                    style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', color: '#94a3b8' }}
                  >
                    {SHEET_CATEGORIES.map((c) => <option key={c} value={c} style={{ background: '#0a1628' }}>{c}</option>)}
                  </select>
                  <ChevronDown size={10} className="absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none" style={{ color: '#4b5563' }} />
                </div>
                <div className="relative">
                  <select
                    value={sheetPrice}
                    onChange={(e) => setSheetPrice(e.target.value)}
                    className="appearance-none text-xs px-2.5 py-1.5 pr-6 rounded-lg outline-none"
                    style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', color: '#94a3b8' }}
                  >
                    {SHEET_PRICES.map((c) => <option key={c} value={c} style={{ background: '#0a1628' }}>{c}</option>)}
                  </select>
                  <ChevronDown size={10} className="absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none" style={{ color: '#4b5563' }} />
                </div>
                <span className="ml-auto text-xs" style={{ color: '#6b7280' }}>{sheetProducts.length} 款</span>
              </div>
              <div className="mt-2 flex items-center gap-2">
                <div className="relative">
                  <select
                    value={sheetCrowd}
                    onChange={(e) => setSheetCrowd(e.target.value)}
                    className="appearance-none text-xs px-2.5 py-1.5 pr-6 rounded-lg outline-none"
                    style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', color: '#94a3b8' }}
                  >
                    {crowdOptions.map((c) => <option key={c} value={c} style={{ background: '#0a1628' }}>{c === SHEET_NONE ? '人群' : c}</option>)}
                  </select>
                  <ChevronDown size={10} className="absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none" style={{ color: '#4b5563' }} />
                </div>
                <div className="relative">
                  <select
                    value={sheetScene}
                    onChange={(e) => setSheetScene(e.target.value)}
                    className="appearance-none text-xs px-2.5 py-1.5 pr-6 rounded-lg outline-none"
                    style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', color: '#94a3b8' }}
                  >
                    {sceneOptions.map((c) => <option key={c} value={c} style={{ background: '#0a1628' }}>{c === SHEET_NONE ? '场景' : c}</option>)}
                  </select>
                  <ChevronDown size={10} className="absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none" style={{ color: '#4b5563' }} />
                </div>
                <div className="relative">
                  <select
                    value={sheetStore}
                    onChange={(e) => setSheetStore(e.target.value)}
                    className="appearance-none text-xs px-2.5 py-1.5 pr-6 rounded-lg outline-none"
                    style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', color: '#94a3b8' }}
                  >
                    {storeOptions.map((c) => <option key={c} value={c} style={{ background: '#0a1628' }}>{c === SHEET_NONE ? '门店' : c}</option>)}
                  </select>
                  <ChevronDown size={10} className="absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none" style={{ color: '#4b5563' }} />
                </div>
              </div>
              <div className="mt-2 flex gap-2 flex-wrap">
                <button
                  onClick={() => setNeedHairlineFix((v) => !v)}
                  className="px-2.5 py-1.5 rounded-lg text-xs font-medium"
                  style={{
                    background: needHairlineFix ? 'rgba(179,71,255,0.12)' : 'rgba(255,255,255,0.04)',
                    border: `1px solid ${needHairlineFix ? 'rgba(179,71,255,0.35)' : 'rgba(255,255,255,0.08)'}`,
                    color: needHairlineFix ? '#b347ff' : '#94a3b8',
                  }}
                >
                  修发际线优先
                </button>
                <button
                  onClick={() => setNeedWhiteCover((v) => !v)}
                  className="px-2.5 py-1.5 rounded-lg text-xs font-medium"
                  style={{
                    background: needWhiteCover ? 'rgba(0,212,255,0.12)' : 'rgba(255,255,255,0.04)',
                    border: `1px solid ${needWhiteCover ? 'rgba(0,212,255,0.35)' : 'rgba(255,255,255,0.08)'}`,
                    color: needWhiteCover ? '#00d4ff' : '#94a3b8',
                  }}
                >
                  遮白发优先
                </button>
                <button
                  onClick={() => {
                    setSheetSearch('');
                    setSheetCategory('全部');
                    setSheetPrice('全部价格');
                    setSheetCrowd(SHEET_NONE);
                    setSheetScene(SHEET_NONE);
                    setSheetStore(SHEET_NONE);
                    setNeedHairlineFix(false);
                    setNeedWhiteCover(false);
                  }}
                  className="ml-auto px-2.5 py-1.5 rounded-lg text-xs font-medium"
                  style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', color: '#94a3b8' }}
                >
                  重置筛选
                </button>
              </div>
            </div>
            <div className="px-4 pb-8 overflow-y-auto grid grid-cols-3 gap-2.5" style={{ maxHeight: '52vh', scrollbarWidth: 'none' }}>
              {sheetProducts.length === 0 && (
                <div className="col-span-3 py-10 text-center text-xs" style={{ color: '#6b7280' }}>没有符合条件的发型，试试放宽筛选</div>
              )}
              {sheetProducts.map((p) => {
                const added = wallProducts.find((x) => x.id === p.id);
                return (
                  <button key={p.id} disabled={!!added} onClick={() => addToWall(p)} className="rounded-xl overflow-hidden text-left relative" style={{ border: '1px solid rgba(0,212,255,0.1)', opacity: added ? 0.5 : 1 }}>
                    <div style={{ aspectRatio: '3/4' }}>
                      <img src={p.image} alt={p.name} className="w-full h-full object-cover" />
                    </div>
                    <div className="absolute inset-0" style={{ background: 'linear-gradient(to top, rgba(5,10,20,0.9), transparent 60%)' }} />
                    <div className="absolute bottom-0 left-0 right-0 p-1.5">
                      <p className="text-xs font-medium text-white truncate">{p.name}</p>
                      <p className="text-xs gradient-text">{priceRangeCompact(p)}</p>
                    </div>
                    {added && <span className="absolute top-1 right-1 chip chip-blue text-xs">已添加</span>}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* ===== 并排对比 浮层 ===== */}
      {showCompare && compareEntries.length === 2 && (
        <div className="fixed inset-0 z-50 flex flex-col" style={{ background: 'rgba(5,10,20,0.96)' }}>
          <div className="flex items-center justify-between px-4 py-3" style={{ borderBottom: '1px solid rgba(0,212,255,0.1)' }}>
            <span className="text-sm font-bold text-white">发型对比</span>
            <button onClick={() => setShowCompare(false)}><X size={20} style={{ color: '#94a3b8' }} /></button>
          </div>
          <div className="flex-1 grid grid-cols-2 gap-2 p-3 overflow-y-auto">
            {compareEntries.map((e) => {
              const match = matchMap.get(e.product.id);
              const generated = e.status === 'success' && !!e.resultImage;
              return (
                <div key={e.key} className="rounded-2xl overflow-hidden flex flex-col" style={{ border: '1px solid rgba(0,212,255,0.15)' }}>
                  {generated && userImage ? (
                    <div className="px-2 pt-2">
                      <CompareSlider
                        before={userImage}
                        after={e.resultImage!}
                        height={160}
                        compact
                        beforeLabel="原图"
                        afterLabel="效果"
                      />
                    </div>
                  ) : (
                    <div className="relative">
                      <img src={e.resultImage || e.product.image} alt={e.product.name} className="w-full h-48 object-cover" />
                      <div className="absolute inset-0" style={{ background: 'linear-gradient(to top, rgba(5,10,20,0.65), transparent 55%)' }} />
                      <span className="absolute top-2 left-2 chip chip-purple text-xs">
                        {generated ? '已生成' : '待生成'}
                      </span>
                    </div>
                  )}
                  <div className="p-2.5 flex-1" style={{ background: 'rgba(10,22,40,0.9)' }}>
                    <p className="text-sm font-semibold text-white truncate">{e.product.name}</p>
                    <div className="flex items-center justify-between mt-0.5">
                      <span className="text-xs gradient-text font-bold">{priceStart(e.product)}</span>
                      <span className="flex items-center gap-0.5 text-xs" style={{ color: '#f59e0b' }}>
                        <Star size={10} fill="#f59e0b" />{e.product.rating}
                      </span>
                    </div>
                    <div className="mt-2 space-y-1">
                      <div className="text-[11px]" style={{ color: '#94a3b8' }}>匹配分：<span style={{ color: '#00d4ff' }}>{match?.matchScore ?? '-'}分</span></div>
                      <div className="text-[11px]" style={{ color: '#94a3b8' }}>发际线遮盖：<span style={{ color: '#b347ff' }}>{match?.hairlineCoverage ?? '-'} / 5</span></div>
                      <div className="text-[11px]" style={{ color: '#94a3b8' }}>白发遮盖：<span style={{ color: '#00d4ff' }}>{match?.whiteHairCoverage ?? '-'} / 5</span></div>
                      <div className="text-[11px] truncate" style={{ color: '#94a3b8' }}>门店可试：{match?.storeIds?.length ? `${match.storeIds.length} 家` : '待确认'}</div>
                    </div>
                    {match?.matchReasons?.[0] && (
                      <p className="text-[11px] mt-2 leading-relaxed" style={{ color: '#6b7280' }}>
                        {match.matchReasons[0]}
                      </p>
                    )}
                    {!generated && (
                      <button
                        onClick={() => {
                          const key = startTryon(e.product);
                          setCompareSel((prev) => prev.map((k) => (k === e.key ? key : k)));
                        }}
                        className="mt-2 w-full py-1.5 rounded-lg text-xs font-semibold"
                        style={{ background: 'rgba(0,212,255,0.14)', border: '1px solid rgba(0,212,255,0.35)', color: '#00d4ff' }}
                      >
                        先生成这款
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
          {userImage && (
            <div className="px-3 pb-3 flex items-center gap-2">
              <img src={userImage} alt="原图" className="w-12 h-12 rounded-lg object-cover" style={{ border: '1px solid rgba(255,255,255,0.15)' }} />
              <span className="text-xs" style={{ color: '#6b7280' }}>← 你的原图，两款效果对比哪个更适合你？</span>
            </div>
          )}
        </div>
      )}

      {/* ===== 放大 / 工作台 浮层 ===== */}
      {enlarge && (() => {
        const entry = studio.entries[enlarge.key];
        const compareBefore = userImage || '/images/avatar-placeholder.jpg';
        const ready = entry?.status === 'success' && entry.resultImage;
        const processing = entry?.status === 'pending' || entry?.status === 'processing';
        const failed = entry?.status === 'failed';

        // 生成中走全屏沉浸式 modal（业界最佳实践：让用户全部注意力聚焦在"AI 在工作"），
        // 生成完 / 失败 / 待生成走底部 sheet，方便用户上下滑动查看详情/局部调整/门店 CTA
        if (processing) {
          return (
            <div
              className="fixed inset-0 z-50 flex flex-col"
              style={{
                background: 'radial-gradient(circle at 50% 30%, rgba(0,212,255,0.10), #050a14 60%)',
              }}
            >
              {/* 顶部：关闭按钮 + 款式名（不让用户感觉自己在等"未知"，明确告诉他在试什么） */}
              <div className="flex items-center justify-between px-4 py-3 shrink-0">
                <button
                  onClick={() => setEnlarge(null)}
                  className="p-1.5 rounded-full"
                  style={{ background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.1)' }}
                >
                  <X size={18} style={{ color: '#94a3b8' }} />
                </button>
                <div className="flex flex-col items-center min-w-0 flex-1 mx-3">
                  <span className="text-[11px]" style={{ color: '#6b7280' }}>正在为你试戴</span>
                  <span className="text-sm font-bold text-white truncate max-w-full">{enlarge.product.name}</span>
                </div>
                <div style={{ width: 32 }} />
              </div>

              {/* 主体：垂直居中的等待动画 */}
              <div className="flex-1 flex flex-col items-center justify-center px-6">
                <WaitingAnimation
                  progress={entry?.progress ?? 0}
                  productName={enlarge.product.name}
                  productImage={enlarge.product.image}
                />

                {/* 原图 vs 假发 小预览：让用户看着自己照片想象效果，缓解等待焦虑 */}
                {userImage && (
                  <div className="mt-6 flex items-center gap-3">
                    <div className="flex flex-col items-center">
                      <div className="w-14 h-14 rounded-full overflow-hidden" style={{ border: '1.5px solid rgba(255,255,255,0.25)' }}>
                        <img src={userImage} alt="原图" className="w-full h-full object-cover" />
                      </div>
                      <span className="text-[10px] mt-1" style={{ color: '#94a3b8' }}>你的照片</span>
                    </div>
                    <div className="text-[#00d4ff] text-base">＋</div>
                    <div className="flex flex-col items-center">
                      <div className="w-14 h-14 rounded-full overflow-hidden" style={{ border: '1.5px solid rgba(0,212,255,0.4)' }}>
                        <img src={enlarge.product.image} alt="假发" className="w-full h-full object-cover" />
                      </div>
                      <span className="text-[10px] mt-1" style={{ color: '#00d4ff' }}>{enlarge.product.name.length > 5 ? enlarge.product.name.slice(0, 5) + '…' : enlarge.product.name}</span>
                    </div>
                  </div>
                )}
              </div>

              {/* 底部：温馨提示 */}
              <div className="px-6 pb-8 shrink-0 text-center">
                <p className="text-[11px]" style={{ color: '#6b7280' }}>
                  AI 正在仔细处理 · 关闭页面不影响生成
                </p>
              </div>
            </div>
          );
        }

        // ready / failed / 待生成：底部 sheet
        return (
          <div className="fixed inset-0 z-50 flex items-end justify-center" style={{ background: 'rgba(0,0,0,0.75)', backdropFilter: 'blur(4px)' }} onClick={() => setEnlarge(null)}>
            <div className="w-full max-w-[420px] rounded-t-3xl overflow-hidden" style={{ background: '#0a1628', border: '1px solid rgba(0,212,255,0.2)', borderBottom: 'none', maxHeight: '92vh', overflowY: 'auto' }} onClick={(e) => e.stopPropagation()}>
              <div className="w-10 h-1 rounded-full mx-auto mt-3 mb-3" style={{ background: 'rgba(255,255,255,0.2)' }} />

              <div className="px-4">
                {ready ? (
                  <CompareSlider before={compareBefore} after={entry!.resultImage!} height={300} />
                ) : processing ? (
                  <WaitingAnimation
                    progress={entry?.progress ?? 0}
                    productName={enlarge.product.name}
                    productImage={enlarge.product.image}
                  />
                ) : (
                  <div className="rounded-2xl overflow-hidden" style={{ height: 300, border: '1px solid rgba(0,212,255,0.15)', background: 'rgba(5,10,20,0.6)' }}>
                    <img src={resolveAssetUrl(enlarge.product.image)} alt={enlarge.product.name} className="w-full h-full object-cover" />
                  </div>
                )}
              </div>

              {/* 名称 + 价格区间（突出） */}
              <div className="px-4 mt-3">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <h3 className="text-base font-bold text-white truncate">{enlarge.product.name}</h3>
                    <p className="text-[11px] mt-0.5 truncate" style={{ color: '#94a3b8' }}>
                      {enlarge.product.material} · {enlarge.product.craft}
                    </p>
                  </div>
                  <div className="text-right shrink-0">
                    <p className="text-sm font-black gradient-text whitespace-nowrap">{priceRangeFull(enlarge.product)}</p>
                    <p className="text-[10px]" style={{ color: '#4b5563' }}>价格区间</p>
                  </div>
                </div>
                {/* 单一友好标签：替代过去的"结果模式·SEED / 策略·yunwu|... / 变化分 / 重试" */}
                {(() => {
                  const label = friendlyStrategyLabel(entry?.strategyUsed, entry?.aiMode);
                  if (!ready && !processing && !failed) return null;
                  const palette = label.tone === 'real'
                    ? { bg: 'rgba(0,212,255,0.10)', bd: 'rgba(0,212,255,0.32)', fg: '#00d4ff' }
                    : { bg: 'rgba(179,71,255,0.10)', bd: 'rgba(179,71,255,0.32)', fg: '#b347ff' };
                  return (
                    <div className="mt-2 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold"
                         style={{ background: palette.bg, border: `1px solid ${palette.bd}`, color: palette.fg }}>
                      <Sparkles size={11} />{label.text}
                    </div>
                  );
                })()}
              </div>
              {/* 款式素材画廊：每款 10~15 张客户实拍 */}
              {(enlarge.product.galleryImages?.length ?? 0) > 0 && (
                <div className="px-4 mt-3">
                  <p className="text-[11px] mb-2" style={{ color: '#6b7280' }}>
                    款式素材 · {enlarge.product.galleryImages!.length} 张
                  </p>
                  <div className="flex gap-2 overflow-x-auto pb-1" style={{ scrollbarWidth: 'none' }}>
                    {enlarge.product.galleryImages!.map((url, idx) => (
                      <div
                        key={url}
                        className="flex-shrink-0 w-16 h-20 rounded-lg overflow-hidden"
                        style={{ border: idx === 0 ? '2px solid rgba(0,212,255,0.5)' : '1px solid rgba(255,255,255,0.08)' }}
                      >
                        <img src={resolveAssetUrl(url)} alt="" className="w-full h-full object-cover" />
                      </div>
                    ))}
                  </div>
                </div>
              )}
              {ready && entry?.qualityNote && (
                <div className="px-4 mt-2">
                  <div className="rounded-xl px-3 py-2 text-xs" style={{ background: 'rgba(179,71,255,0.08)', border: '1px solid rgba(179,71,255,0.25)', color: '#b347ff' }}>
                    {entry.qualityNote}
                  </div>
                </div>
              )}
              {ready && entry?.resultImage === compareBefore && (
                <div className="px-4 mt-2">
                  <div className="rounded-xl px-3 py-2 text-xs" style={{ background: 'rgba(179,71,255,0.08)', border: '1px solid rgba(179,71,255,0.25)', color: '#b347ff' }}>
                    本次结果与原图基本一致，建议切换款式或重新生成一次。
                  </div>
                </div>
              )}
              {failed && entry?.errorMessage && (
                <div className="px-4 mt-2">
                  <div className="rounded-xl px-3 py-2 text-xs" style={{ background: 'rgba(255,77,141,0.08)', border: '1px solid rgba(255,77,141,0.25)', color: '#ff6b9e' }}>
                    {entry.errorMessage}
                  </div>
                </div>
              )}

              {!ready && !processing && (
                <div className="px-4 mt-3">
                  <button
                    onClick={() => {
                      const key = startTryon(enlarge.product);
                      setEnlarge({ product: enlarge.product, key });
                    }}
                    className="w-full py-3 rounded-xl text-sm font-bold btn-primary"
                    style={{ color: '#050a14' }}
                  >
                    {failed ? '重新生成这款效果' : '开始生成这款效果'}
                  </button>
                </div>
              )}

              {/* 局部调整 + 评分 + 门店 整合到一个 sticky bottom 操作区，减少视觉层数 */}
              {ready && (
                <>
                  <div className="px-4 mt-4">
                    <p className="text-[11px] mb-1.5" style={{ color: '#6b7280' }}>不满意？基于这款重新生成</p>
                    <div className="flex gap-1.5 flex-wrap">
                      {ADJUSTS.map((a) => (
                        <button key={a.label} onClick={() => applyAdjust(a.note)}
                                className="flex-1 min-w-[72px] py-1.5 rounded-lg text-[11px] font-medium"
                                style={{ background: 'rgba(179,71,255,0.08)', border: '1px solid rgba(179,71,255,0.28)', color: '#b347ff' }}>
                          {a.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* 评分（更紧凑：表情 + 星级一行） */}
                  <div className="px-4 mt-3">
                    {!feedbackDone ? (
                      <div className="flex items-center justify-between gap-3 px-3 py-2 rounded-xl"
                           style={{ background: 'rgba(10,22,40,0.7)', border: '1px solid rgba(0,212,255,0.1)' }}>
                        <span className="text-[11px] shrink-0" style={{ color: '#94a3b8' }}>喜欢吗？</span>
                        <div className="flex items-center gap-2">
                          {[{ t: 'like' as const, e: '😍' }, { t: 'neutral' as const, e: '😐' }, { t: 'dislike' as const, e: '😞' }].map((x) => (
                            <button key={x.t} onClick={() => submitFeedback(x.t)} className="text-base leading-none">{x.e}</button>
                          ))}
                        </div>
                        <StarRating rating={rating} interactive onChange={setRating} size={14} />
                      </div>
                    ) : (
                      <p className="text-xs text-center py-2 rounded-xl"
                         style={{ color: '#00ff96', background: 'rgba(0,255,150,0.06)', border: '1px solid rgba(0,255,150,0.2)' }}>
                        🎉 感谢反馈，已记录
                      </p>
                    )}
                  </div>

                  {/* 门店 + CTA：单卡 3 按钮一行，文字行更紧凑 */}
                  <div className="px-4 mt-3 pb-6">
                    <div className="rounded-2xl px-3 py-2.5"
                         style={{ background: 'rgba(10,22,40,0.85)', border: '1px solid rgba(179,71,255,0.18)' }}>
                      <div className="flex items-center justify-between mb-2">
                        <div className="flex items-center gap-1.5 min-w-0">
                          <span className="text-sm">🏪</span>
                          <p className="text-xs font-semibold text-white truncate">{nearbyStore.name}</p>
                        </div>
                        <span className="text-[11px] shrink-0" style={{ color: '#6b7280' }}>📍 {nearbyStore.distance} · ⭐ {nearbyStore.rating}</span>
                      </div>
                      <div className="grid grid-cols-3 gap-1.5">
                        <button onClick={onViewStore} className="flex items-center justify-center gap-1 py-2 rounded-lg text-[11px] font-semibold"
                                style={{ background: 'rgba(0,212,255,0.08)', border: '1px solid rgba(0,212,255,0.25)', color: '#00d4ff' }}>
                          <Navigation size={12} />导航
                        </button>
                        <button onClick={() => setEnlarge(null)} className="flex items-center justify-center gap-1 py-2 rounded-lg text-[11px] font-semibold"
                                style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.1)', color: '#94a3b8' }}>
                          <RefreshCw size={12} />换一款
                        </button>
                        <button
                          onClick={() => {
                            setConsultCopied(false);
                            setShowConsult(true);
                            trackEvent('open_consult', enlarge.product.id, 'wechat');
                          }}
                          className="flex items-center justify-center gap-1 py-2 rounded-lg text-[11px] font-semibold btn-primary"
                          style={{ color: '#050a14' }}
                        >
                          <MessageCircle size={12} />加微信
                        </button>
                      </div>
                    </div>
                  </div>
                </>
              )}

              {/* 还在生成 / 还未生成时，下方留白即可（避免双层"局部调整"） */}
              {!ready && (
                <div className="pb-6" />
              )}
            </div>
          </div>
        );
      })()}

      {showConsult && enlarge && (
        <div
          className="fixed inset-0 z-[60] flex items-center justify-center px-6"
          style={{ background: 'rgba(0,0,0,0.78)', backdropFilter: 'blur(4px)' }}
          onClick={() => setShowConsult(false)}
        >
          <div
            className="w-full max-w-[340px] rounded-2xl p-4"
            style={{ background: '#0a1628', border: '1px solid rgba(0,212,255,0.2)' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between mb-2">
              <p className="text-sm font-bold text-white">专属顾问咨询</p>
              <button onClick={() => setShowConsult(false)}><X size={16} style={{ color: '#6b7280' }} /></button>
            </div>
            <p className="text-xs mb-2" style={{ color: '#94a3b8' }}>
              已为你锁定款式：{enlarge.product.name}
            </p>
            <div className="rounded-xl p-3 mb-3" style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)' }}>
              <p className="text-xs mb-1" style={{ color: '#6b7280' }}>顾问微信号</p>
              <p className="text-sm font-semibold text-white">HairAI_Advisor01</p>
            </div>
            <button
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText('HairAI_Advisor01');
                  setConsultCopied(true);
                  trackEvent('consult_copy_wechat', enlarge.product.id);
                } catch {
                  setConsultCopied(false);
                }
              }}
              className="w-full py-2.5 rounded-xl text-xs font-semibold"
              style={{ background: 'rgba(0,212,255,0.12)', border: '1px solid rgba(0,212,255,0.32)', color: '#00d4ff' }}
            >
              <Copy size={13} className="inline mr-1" />复制微信号
            </button>
            {consultCopied && (
              <p className="text-xs text-center mt-2" style={{ color: '#00ff96' }}>
                已复制，可直接去微信搜索添加
              </p>
            )}
          </div>
        </div>
      )}

      {/* ===== 试戴历史抽屉 + 多张对比浮层（统一入口） ===== */}
      {showHistory && (
        <TryonHistoryDrawer
          items={history.items}
          beforeImage={userImage}
          onClose={() => setShowHistory(false)}
          onRemove={history.remove}
          onRemoveMany={history.removeMany}
          onClear={history.clear}
          onToggleLike={history.toggleLike}
        />
      )}
    </div>
  );
};
