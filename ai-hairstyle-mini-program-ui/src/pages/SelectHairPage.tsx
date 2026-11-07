import React, { useEffect, useMemo, useState } from 'react';
import { Search, SlidersHorizontal, X, ChevronDown, Star, Zap, Info, Sparkles } from 'lucide-react';
import { PageHeader } from '../components/PageHeader';
import { hairStyles, categories, HairStyle } from '../data/mockData';
import { api, AnalyzeResult, RecommendationMatch, pollFaceCheck } from '../api/client';
import { priceRangeFull, priceRangeCompact } from '../utils/price';
import { buildDemoAnalyzeResult, RECOMMEND_LOADING_STEPS, RECOMMEND_MIN_REVEAL_MS } from '../utils/demoRecommend';

interface SelectHairPageProps {
  sessionId: string | null;
  analysis: AnalyzeResult | null;
  onAnalysis: (result: AnalyzeResult) => void;
  /** 上传接口返回的人脸状态；PASSED 时跳过轮询 */
  uploadFaceCheckStatus?: 'PENDING' | 'PASSED' | 'FAILED';
  onBack: () => void;
  onSelect: (hair: HairStyle) => void;
}

const materialOptions = ['全部材质', '100% 真人发丝', '混合材质', '优质合成发丝'];
const priceOptions = ['全部价格', '¥2000以下', '¥2000-5000', '¥5000以上'];


const DetailModal: React.FC<{ hair: HairStyle; onClose: () => void; onSelect: (h: HairStyle) => void }> = ({ hair, onClose, onSelect }) => (
  <div
    className="fixed inset-0 z-50 flex items-end justify-center"
    style={{ background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(4px)' }}
    onClick={onClose}
  >
    <div
      className="w-full max-w-[420px] rounded-t-3xl overflow-hidden"
      style={{ background: '#0a1628', border: '1px solid rgba(0,212,255,0.2)', borderBottom: 'none' }}
      onClick={(e) => e.stopPropagation()}
    >
      {/* Top bar */}
      <div
        className="w-10 h-1 rounded-full mx-auto mt-3 mb-4"
        style={{ background: 'rgba(255,255,255,0.2)' }}
      />
      {/* Image */}
      <div className="relative h-56">
        <img src={hair.image} alt={hair.name} className="w-full h-full object-cover" />
        <div className="absolute inset-0" style={{ background: 'linear-gradient(to top, #0a1628, transparent)' }} />
        <button
          onClick={onClose}
          className="absolute top-3 right-3 p-1.5 rounded-full"
          style={{ background: 'rgba(5,10,20,0.8)', border: '1px solid rgba(255,255,255,0.1)' }}
        >
          <X size={16} style={{ color: '#94a3b8' }} />
        </button>
      </div>

      <div className="px-4 pb-8">
        <div className="flex items-start justify-between mb-3">
          <div>
            <h2 className="text-lg font-bold text-white">{hair.name}</h2>
            <div className="flex items-center gap-2 mt-1">
              <Star size={12} fill="#f59e0b" style={{ color: '#f59e0b' }} />
              <span className="text-xs font-bold" style={{ color: '#f59e0b' }}>{hair.rating}</span>
              <span className="text-xs" style={{ color: '#4b5563' }}>{hair.reviews}人试戴</span>
            </div>
          </div>
          <div className="text-right">
            <p className="text-base font-black gradient-text">{priceRangeFull(hair)}</p>
            <p className="text-[10px]" style={{ color: '#4b5563' }}>价格区间</p>
          </div>
        </div>

        {/* Details grid */}
        <div className="grid grid-cols-2 gap-2 mb-4">
          {[
            { label: '材质', value: hair.material },
            { label: '工艺', value: hair.craft },
            { label: '重量', value: hair.weight },
            { label: '适合脸型', value: hair.suitable },
          ].map((item) => (
            <div
              key={item.label}
              className="px-3 py-2 rounded-xl"
              style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(0,212,255,0.08)' }}
            >
              <p className="text-xs mb-0.5" style={{ color: '#4b5563' }}>{item.label}</p>
              <p className="text-xs font-medium text-white">{item.value}</p>
            </div>
          ))}
        </div>

        {/* Tags */}
        <div className="flex gap-1.5 mb-4 flex-wrap">
          {hair.tags.map((tag) => (
            <span key={tag} className="chip chip-blue">{tag}</span>
          ))}
        </div>

        <button
          onClick={() => { onSelect(hair); onClose(); }}
          className="w-full py-3.5 rounded-xl text-sm font-bold btn-primary flex items-center justify-center gap-2"
          style={{ color: '#050a14' }}
        >
          <Zap size={16} />
          选择此款，开始 AI 试戴
        </button>
      </div>
    </div>
  </div>
);

export const SelectHairPage: React.FC<SelectHairPageProps> = ({
  sessionId,
  analysis,
  onAnalysis,
  uploadFaceCheckStatus,
  onBack,
  onSelect,
}) => {
  const [activeCategory, setActiveCategory] = useState('全部');
  const [searchText, setSearchText] = useState('');
  const [selectedMaterial, setSelectedMaterial] = useState('全部材质');
  const [selectedPrice, setSelectedPrice] = useState('全部价格');
  const [showFilters, setShowFilters] = useState(false);
  const [detailHair, setDetailHair] = useState<HairStyle | null>(null);
  const [products, setProducts] = useState<HairStyle[]>(hairStyles);
  const [analysisState, setAnalysisState] = useState<AnalyzeResult | null>(analysis);
  const [analyzing, setAnalyzing] = useState(false);
  const [recommendReady, setRecommendReady] = useState(false);
  const [loadingStep, setLoadingStep] = useState(0);
  const [faceChecking, setFaceChecking] = useState(false);
  const [faceCheckError, setFaceCheckError] = useState<string | null>(null);
  const [selectingHair, setSelectingHair] = useState<HairStyle | null>(null);

  const [faceReady, setFaceReady] = useState(uploadFaceCheckStatus === 'PASSED');

  // 拉取后端发型库（失败则保留内置 mock 数据，保证离线可用）
  useEffect(() => {
    api
      .products()
      .then((list) => {
        if (Array.isArray(list) && list.length > 0) setProducts(list);
      })
      .catch(() => {});
  }, []);

  // 无 sessionId（离线）：延迟后再展示固定推荐，避免「秒出」显得假
  useEffect(() => {
    if (sessionId || products.length === 0) return;
    let cancelled = false;
    setRecommendReady(false);
    setAnalyzing(true);
    setLoadingStep(0);

    const stepTimer = window.setInterval(() => {
      setLoadingStep((s) => Math.min(s + 1, RECOMMEND_LOADING_STEPS.length - 1));
    }, 420);

    const reveal = window.setTimeout(() => {
      if (cancelled) return;
      const demo = buildDemoAnalyzeResult(products, activeCategory);
      if (demo) {
        setAnalysisState(demo);
        onAnalysis(demo);
      }
      setRecommendReady(true);
      setAnalyzing(false);
    }, RECOMMEND_MIN_REVEAL_MS);

    return () => {
      cancelled = true;
      window.clearInterval(stepTimer);
      window.clearTimeout(reveal);
    };
  }, [sessionId, products, activeCategory, onAnalysis]);

  // 人脸预检：上传已 PASSED / 演示模式跳过 / 否则轮询
  useEffect(() => {
    if (!sessionId) {
      setFaceReady(true);
      return;
    }
    if (uploadFaceCheckStatus === 'PASSED') {
      setFaceReady(true);
      setFaceChecking(false);
      return;
    }

    let cancelled = false;
    setFaceChecking(true);
    setFaceCheckError(null);
    setFaceReady(false);

    api
      .aiStatus()
      .then((st) => {
        if (cancelled) return;
        if (st.demoFastFaceCheck) {
          setFaceChecking(false);
          setFaceReady(true);
          return;
        }
        return pollFaceCheck(sessionId, { intervalMs: 400, timeoutMs: 15000 });
      })
      .then((fc) => {
        if (cancelled || !fc) return;
        setFaceChecking(false);
        if (fc.status === 'FAILED') {
          setFaceCheckError(fc.message || '未检测到清晰的正面人脸，请重新拍摄');
          return;
        }
        setFaceReady(true);
      })
      .catch(() => {
        if (!cancelled) {
          setFaceChecking(false);
          // 演示兜底：轮询失败仍展示固定推荐，避免白屏
          setFaceReady(true);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [sessionId, uploadFaceCheckStatus]);

  // 有 sessionId：走后端 demo-fast-recommend；至少转 1.6s 再展示文案与推荐款
  useEffect(() => {
    if (!sessionId || !faceReady || faceCheckError) return;
    let cancelled = false;
    setAnalyzing(true);
    setRecommendReady(false);
    setAnalysisState(null);
    setLoadingStep(0);

    const stepTimer = window.setInterval(() => {
      setLoadingStep((s) => Math.min(s + 1, RECOMMEND_LOADING_STEPS.length - 1));
    }, 420);

    const minDelay = new Promise<void>((resolve) => {
      window.setTimeout(resolve, RECOMMEND_MIN_REVEAL_MS);
    });

    const fetchAnalyze = api
      .analyze(sessionId, '', activeCategory)
      .then((res) => res)
      .catch(() => buildDemoAnalyzeResult(products, activeCategory, sessionId));

    Promise.all([minDelay, fetchAnalyze])
      .then(([, res]) => {
        if (cancelled || !res) return;
        setAnalysisState(res);
        onAnalysis(res);
        setRecommendReady(true);
      })
      .catch(() => {
        if (cancelled) return;
        const fallback = buildDemoAnalyzeResult(products, activeCategory, sessionId);
        if (fallback) {
          setAnalysisState(fallback);
          onAnalysis(fallback);
          setRecommendReady(true);
        } else {
          setFaceCheckError('推荐加载失败，请下拉刷新或重新进入');
        }
      })
      .finally(() => {
        if (!cancelled) setAnalyzing(false);
      });

    return () => {
      cancelled = true;
      window.clearInterval(stepTimer);
    };
  }, [sessionId, faceReady, faceCheckError, activeCategory, onAnalysis, products]);

  const recommendedIds = analysisState?.faceAnalysis?.recommendedIds ?? [];

  // 推荐匹配信息（匹配分 / 理由），来自后端两阶段打分
  const matchMap = useMemo(() => {
    const m = new Map<number, RecommendationMatch>();
    (analysisState?.recommendationMatches || []).forEach((x) => m.set(x.wigProductId, x));
    return m;
  }, [analysisState?.recommendationMatches]);

  // "为你推荐"大卡数据：优先用后端推荐款，回退到按推荐ID从全库匹配
  const recommendedCards = useMemo(() => {
    const recs = analysisState?.recommendedProducts?.length
      ? analysisState.recommendedProducts
      : products.filter((p) => recommendedIds.includes(p.id));
    return recs.slice(0, 2).map((hair) => ({ hair, match: matchMap.get(hair.id) }));
  }, [analysisState?.recommendedProducts, products, recommendedIds, matchMap]);

  const startTryon = (hair: HairStyle) => {
    if (faceCheckError) return;
    setSelectingHair(hair);
    window.setTimeout(() => {
      onSelect(hair);
      setSelectingHair(null);
    }, 900);
  };

  const filteredHairs = products
    .filter((h) => {
      const catMatch = activeCategory === '全部' || h.category === activeCategory;
      const searchMatch = !searchText || h.name.includes(searchText) || h.material.includes(searchText);
      const matMatch = selectedMaterial === '全部材质' || h.material === selectedMaterial;
      const priceMatch = (() => {
        if (selectedPrice === '全部价格') return true;
        if (selectedPrice === '¥2000以下') return h.priceMin < 2000;
        if (selectedPrice === '¥2000-5000') return h.priceMin >= 2000 && h.priceMax <= 5000;
        if (selectedPrice === '¥5000以上') return h.priceMax > 5000;
        return true;
      })();
      return catMatch && searchMatch && matMatch && priceMatch;
    })
    // AI 推荐的款式排在前面
    .sort((a, b) => {
      const ra = recommendedIds.includes(a.id) ? 0 : 1;
      const rb = recommendedIds.includes(b.id) ? 0 : 1;
      return ra - rb;
    });

  return (
    <div className="page-enter pb-24">
      <PageHeader title="选择发型" onBack={onBack} />

      {faceChecking && (
        <div className="px-4 pt-3">
          <div
            className="rounded-xl px-3 py-2 flex items-center gap-2"
            style={{ background: 'rgba(0,212,255,0.08)', border: '1px solid rgba(0,212,255,0.2)' }}
          >
            <div
              className="w-3.5 h-3.5 rounded-full border-2 border-t-transparent animate-spin flex-shrink-0"
              style={{ borderColor: '#00d4ff', borderTopColor: 'transparent' }}
            />
            <span className="text-xs" style={{ color: '#94a3b8' }}>AI 人脸检测中，请稍候…</span>
          </div>
        </div>
      )}

      {faceCheckError && (
        <div className="px-4 pt-3">
          <div
            className="rounded-xl px-3 py-2.5"
            style={{ background: 'rgba(255,100,100,0.1)', border: '1px solid rgba(255,100,100,0.25)' }}
          >
            <p className="text-xs" style={{ color: '#ff6464' }}>{faceCheckError}</p>
            <p className="text-xs mt-1" style={{ color: '#94a3b8' }}>请返回重新上传正脸照片</p>
          </div>
        </div>
      )}

      {/* AI 分析与推荐横幅：加载中先转圈，再展示完整话术 */}
      {faceReady && !faceCheckError && (analyzing || recommendReady) && (
        <div className="px-4 pt-3">
          <div
            className="rounded-2xl p-3"
            style={{
              background: 'linear-gradient(135deg, rgba(0,212,255,0.08), rgba(179,71,255,0.08))',
              border: '1px solid rgba(0,212,255,0.2)',
            }}
          >
            <div className="flex items-center gap-2 mb-1.5">
              <Sparkles size={14} style={{ color: '#00d4ff' }} />
              <span className="text-xs font-semibold" style={{ color: '#00d4ff' }}>
                AI 智能分析{recommendReady && (analysisState?.aiMode === 'demo-fast' || analysisState?.mock) ? '（演示模式）' : ''}
              </span>
            </div>
            {analyzing || !recommendReady ? (
              <div className="flex items-start gap-2.5">
                <div
                  className="w-4 h-4 mt-0.5 rounded-full border-2 border-t-transparent animate-spin flex-shrink-0"
                  style={{ borderColor: '#00d4ff', borderTopColor: 'transparent' }}
                />
                <div>
                  <p className="text-xs font-medium text-white">{RECOMMEND_LOADING_STEPS[loadingStep]}</p>
                  <p className="text-[11px] mt-1 leading-relaxed" style={{ color: '#64748b' }}>
                    正在结合素材库为您筛选合适款式，请稍候…
                  </p>
                </div>
              </div>
            ) : (
              <>
                <p className="text-xs leading-relaxed" style={{ color: '#94a3b8' }}>
                  {analysisState?.faceAnalysis?.analysisText}
                </p>
                <div className="flex gap-1.5 mt-2 flex-wrap">
                  {analysisState?.faceAnalysis?.faceShape && (
                    <span className="chip chip-blue">脸型 · {analysisState.faceAnalysis.faceShape}</span>
                  )}
                  {analysisState?.faceAnalysis?.hairlineType && (
                    <span className="chip chip-purple">发际线 · {analysisState.faceAnalysis.hairlineType}</span>
                  )}
                  {analysisState?.faceAnalysis?.skinTone && (
                    <span className="chip chip-blue">肤色 · {analysisState.faceAnalysis.skinTone}</span>
                  )}
                  {analysisState?.faceAnalysis?.hasWhiteHair && (
                    <span className="chip chip-blue">需遮盖白发</span>
                  )}
                </div>
                {(analysisState?.faceAnalysis?.suggestions?.length ?? 0) > 0 && (
                  <ul className="mt-2 space-y-1">
                    {analysisState!.faceAnalysis!.suggestions!.slice(0, 3).map((tip) => (
                      <li key={tip} className="text-[11px] leading-relaxed flex gap-1.5" style={{ color: '#64748b' }}>
                        <span style={{ color: '#00d4ff' }}>·</span>
                        <span>{tip}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </>
            )}
          </div>
        </div>
      )}

      {/* 为你推荐：加载完成后再展示 2 款大卡 */}
      {recommendReady && recommendedCards.length > 0 && !faceCheckError && (
        <div className="px-4 pt-3">
          <div className="flex items-center gap-2 mb-2">
            <Sparkles size={14} style={{ color: '#b347ff' }} />
            <span className="text-sm font-bold text-white">为你推荐</span>
            <span className="text-xs" style={{ color: '#6b7280' }}>· {activeCategory === '全部' ? '精选' : activeCategory} Top 2</span>
          </div>
          <div className="overflow-x-auto" style={{ scrollbarWidth: 'none' }}>
            <div className="flex gap-3 min-w-max pb-1">
              {recommendedCards.map(({ hair, match }) => {
                const reason = match?.matchReasons?.[0] || '与你的脸型和风格匹配度较高';
                return (
                  <div
                    key={hair.id}
                    className="w-[180px] rounded-2xl overflow-hidden flex flex-col"
                    style={{ background: 'rgba(10,22,40,0.85)', border: '1px solid rgba(179,71,255,0.28)' }}
                  >
                    <div className="relative" style={{ aspectRatio: '4/5' }}>
                      <img
                        src={hair.image}
                        alt={hair.name}
                        className="w-full h-full object-cover cursor-pointer"
                        onClick={() => setDetailHair(hair)}
                      />
                      <div className="absolute inset-0" style={{ background: 'linear-gradient(to top, rgba(5,10,20,0.85), transparent 55%)' }} />
                      <span className="absolute top-2 left-2 chip chip-purple text-xs">
                        {match?.matchScore ? `${match.matchScore}分` : '推荐'}
                      </span>
                    </div>
                    <div className="p-2.5 flex flex-col flex-1">
                      <h3 className="text-xs font-semibold text-white truncate">{hair.name}</h3>
                      <p className="text-[11px] font-bold mt-0.5 gradient-text">{priceRangeCompact(hair)}</p>
                      <p className="text-[11px] mt-1 leading-relaxed flex-1 line-clamp-2" style={{ color: '#94a3b8' }}>{reason}</p>
                      <button
                        onClick={() => startTryon(hair)}
                        className="mt-2 w-full py-1.5 rounded-lg text-xs font-semibold flex items-center justify-center gap-1"
                        style={{ background: 'linear-gradient(135deg, rgba(0,212,255,0.18), rgba(179,71,255,0.18))', border: '1px solid rgba(0,212,255,0.35)', color: '#00d4ff' }}
                      >
                        <Zap size={11} /> 试戴这款
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* Search */}
      <div className="px-4 pt-3 pb-2">
        {recommendedCards.length > 0 && (
          <div className="flex items-center gap-2 mb-2">
            <span className="text-sm font-bold text-white">全部假发</span>
            <span className="text-xs" style={{ color: '#6b7280' }}>· 也可以自己挑</span>
          </div>
        )}
        <div
          className="flex items-center gap-2 px-3 py-2.5 rounded-xl"
          style={{ background: 'rgba(10,22,40,0.8)', border: '1px solid rgba(0,212,255,0.15)' }}
        >
          <Search size={14} style={{ color: '#4b5563' }} />
          <input
            type="text"
            placeholder="搜索发型名称..."
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

      {/* Category tabs */}
      <div className="px-4 pb-2 overflow-x-auto" style={{ scrollbarWidth: 'none' }}>
        <div className="flex gap-2">
          {categories.map((cat) => (
            <button
              key={cat}
              onClick={() => setActiveCategory(cat)}
              className="flex-shrink-0 px-3 py-1 rounded-full text-xs font-medium transition-all duration-200"
              style={
                activeCategory === cat
                  ? {
                      background: 'linear-gradient(135deg, rgba(0,212,255,0.2), rgba(179,71,255,0.2))',
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
      </div>

      {/* Filter bar */}
      <div className="px-4 pb-3">
        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowFilters(!showFilters)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-all"
            style={{
              background: showFilters ? 'rgba(0,212,255,0.1)' : 'rgba(255,255,255,0.04)',
              border: `1px solid ${showFilters ? 'rgba(0,212,255,0.4)' : 'rgba(255,255,255,0.08)'}`,
              color: showFilters ? '#00d4ff' : '#6b7280',
            }}
          >
            <SlidersHorizontal size={12} />
            筛选
          </button>

          {[
            { label: selectedMaterial === '全部材质' ? '材质' : selectedMaterial, options: materialOptions, value: selectedMaterial, setValue: setSelectedMaterial },
            { label: selectedPrice === '全部价格' ? '价格' : selectedPrice, options: priceOptions, value: selectedPrice, setValue: setSelectedPrice },
          ].map((filter) => (
            <div key={filter.label} className="relative">
              <select
                value={filter.value}
                onChange={(e) => filter.setValue(e.target.value)}
                className="appearance-none flex items-center gap-1 px-3 py-1.5 pr-7 rounded-lg text-xs font-medium outline-none cursor-pointer"
                style={{
                  background: filter.value !== filter.options[0] ? 'rgba(0,212,255,0.08)' : 'rgba(255,255,255,0.04)',
                  border: `1px solid ${filter.value !== filter.options[0] ? 'rgba(0,212,255,0.3)' : 'rgba(255,255,255,0.08)'}`,
                  color: filter.value !== filter.options[0] ? '#00d4ff' : '#6b7280',
                }}
              >
                {filter.options.map((o) => (
                  <option key={o} value={o} style={{ background: '#0a1628' }}>{o}</option>
                ))}
              </select>
              <ChevronDown
                size={10}
                className="absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none"
                style={{ color: '#4b5563' }}
              />
            </div>
          ))}

          <span className="ml-auto text-xs" style={{ color: '#4b5563' }}>{filteredHairs.length}款</span>
        </div>
      </div>

      {/* Results count */}
      <div className="px-4 mb-3">
        <div className="h-px" style={{ background: 'linear-gradient(90deg, rgba(0,212,255,0.3), transparent)' }} />
      </div>

      {/* Hair grid */}
      <div className="px-4">
        {filteredHairs.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-3">
            <div className="text-4xl">🔍</div>
            <p className="text-sm" style={{ color: '#4b5563' }}>未找到匹配的发型</p>
            <button onClick={() => { setSearchText(''); setActiveCategory('全部'); }} className="text-xs" style={{ color: '#00d4ff' }}>
              清除筛选条件
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-3">
            {filteredHairs.map((hair) => (
              <div
                key={hair.id}
                className="rounded-xl overflow-hidden card-hover"
                style={{
                  background: 'rgba(10,22,40,0.8)',
                  border: '1px solid rgba(0,212,255,0.1)',
                }}
              >
                {/* Image */}
                <div className="relative" style={{ aspectRatio: '3/4' }}>
                  <img
                    src={hair.image}
                    alt={hair.name}
                    className="w-full h-full object-cover cursor-pointer"
                    onClick={() => setDetailHair(hair)}
                  />
                  <div
                    className="absolute inset-0"
                    style={{ background: 'linear-gradient(to top, rgba(5,10,20,0.95) 0%, rgba(5,10,20,0.1) 55%, transparent 100%)' }}
                  />
                  {/* Badges */}
                  <div className="absolute top-2 left-2 flex gap-1 flex-wrap">
                    {recommendedIds.includes(hair.id) && (
                      <span className="chip chip-purple text-xs">✨AI推荐</span>
                    )}
                    {hair.isHot && <span className="chip chip-blue text-xs">🔥</span>}
                    {hair.isNew && <span className="chip chip-purple text-xs">✨</span>}
                  </div>
                  {/* Info button */}
                  <button
                    className="absolute top-2 right-2 p-1 rounded-full"
                    style={{ background: 'rgba(5,10,20,0.7)' }}
                    onClick={() => setDetailHair(hair)}
                  >
                    <Info size={12} style={{ color: '#6b7280' }} />
                  </button>
                  {/* Rating */}
                  <div className="absolute bottom-14 left-2 flex items-center gap-1">
                    <Star size={10} fill="#f59e0b" style={{ color: '#f59e0b' }} />
                    <span className="text-xs font-bold" style={{ color: '#f59e0b' }}>{hair.rating}</span>
                    <span className="text-xs" style={{ color: '#6b7280' }}>({hair.reviews})</span>
                  </div>
                </div>

                {/* Info */}
                <div className="p-2">
                  <h3 className="text-xs font-semibold text-white mb-0.5 truncate">{hair.name}</h3>
                  <p className="text-xs truncate mb-1" style={{ color: '#6b7280' }}>{hair.material}</p>
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold gradient-text">{priceRangeCompact(hair)}</span>
                  </div>
                  <button
                    onClick={() => startTryon(hair)}
                    className="w-full py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center justify-center gap-1"
                    style={{
                      background: 'linear-gradient(135deg, rgba(0,212,255,0.15), rgba(179,71,255,0.15))',
                      border: '1px solid rgba(0,212,255,0.3)',
                      color: '#00d4ff',
                    }}
                  >
                    <Zap size={10} />
                    选择试戴
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {detailHair && (
        <DetailModal
          hair={detailHair}
          onClose={() => setDetailHair(null)}
          onSelect={startTryon}
        />
      )}

      {selectingHair && (
        <div
          className="fixed inset-0 z-[70] flex items-center justify-center px-6"
          style={{ background: 'rgba(5,10,20,0.78)', backdropFilter: 'blur(4px)' }}
        >
          <div
            className="w-full max-w-[340px] rounded-2xl p-4"
            style={{ background: '#0a1628', border: '1px solid rgba(0,212,255,0.22)' }}
          >
            <p className="text-sm font-semibold text-white mb-2">正在进入 AI 试戴台…</p>
            <div className="rounded-xl overflow-hidden mb-2" style={{ border: '1px solid rgba(0,212,255,0.14)' }}>
              <img src={selectingHair.image} alt={selectingHair.name} className="w-full h-44 object-cover" />
            </div>
            <p className="text-xs mb-2" style={{ color: '#94a3b8' }}>{selectingHair.name} · 进入后开始生成效果图</p>
            <div className="h-1.5 rounded-full overflow-hidden" style={{ background: 'rgba(255,255,255,0.08)' }}>
              <div className="h-full progress-bar rounded-full" style={{ width: '70%' }} />
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
