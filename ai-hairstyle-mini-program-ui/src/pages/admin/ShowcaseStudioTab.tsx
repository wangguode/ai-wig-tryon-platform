import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Sparkles, RefreshCw, CheckCircle2, XCircle, Star, Trash2, Wand2, Upload, FolderOpen } from 'lucide-react';
import { adminApi, ModelAsset, WigAsset, WigShowcase, ShowcaseIntegrity } from '../../api/admin';
import { CustomerImportWizard } from './CustomerImportWizard';
import {
  ErrorBanner,
  EmptyState,
  ToastStack,
  useConfirm,
  useToastStack,
} from './adminUi';

/** 演示期仅手工导入成品图，隐藏 AI 生成与批量向导。 */
const DEMO_HIDE_AI = true;

/**
 * 成品图工坊：选 1 个本体 + N 个模特 → 后台批量 AI 生成 → 审核入库。
 * <p>
 * 这是用户提出的资产分层架构的"运营生产入口"——
 * 它产出的图既不直接给 C 端看，也不会被喂给 AI（C 端 AI 永远只看 WigAsset 本体图）。
 * 它的作用是给 C 端用户在选品页提供一个"模特戴假发的成品图"（视觉预览）。
 */
export function ShowcaseStudioTab({ token }: { token: string }) {
  const [wigAssets, setWigAssets] = useState<WigAsset[]>([]);
  const [models, setModels] = useState<ModelAsset[]>([]);
  const [selectedAsset, setSelectedAsset] = useState<WigAsset | null>(null);
  const [selectedModelIds, setSelectedModelIds] = useState<Set<number>>(new Set());
  const [showcases, setShowcases] = useState<WigShowcase[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [polling, setPolling] = useState(false);
  const [mode, setMode] = useState<'import' | 'ai'>('import');
  const [importFiles, setImportFiles] = useState<File[]>([]);
  const [importing, setImporting] = useState(false);
  const [integrity, setIntegrity] = useState<ShowcaseIntegrity | null>(null);
  const [showWizard, setShowWizard] = useState(false);

  const { toasts, close: closeToast, toast } = useToastStack();
  const { confirm, dialog: confirmDialog } = useConfirm();
  const candidatesRef = useRef<HTMLDivElement>(null);
  const importInputRef = useRef<HTMLInputElement>(null);

  const openImportPicker = () => importInputRef.current?.click();

  // 初次加载所有本体 + 所有模特
  const reloadIndex = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [w, m] = await Promise.all([
        adminApi.listWigAssets(token),
        adminApi.listModelAssets(token),
      ]);
      setWigAssets(w);
      setModels(m);
      // 初次进入或上次选的资产被删，自动选首条
      if (!selectedAsset || !w.find((a) => a.id === selectedAsset.id)) {
        setSelectedAsset(w[0] ?? null);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [token, selectedAsset]);

  useEffect(() => {
    void reloadIndex();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 加载某 wigAsset 的所有 showcase
  const reloadShowcases = useCallback(async () => {
    if (!selectedAsset) {
      setShowcases([]);
      return;
    }
    try {
      setShowcases(await adminApi.listShowcases(token, selectedAsset.id));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [token, selectedAsset]);

  useEffect(() => {
    void reloadShowcases();
  }, [reloadShowcases]);

  const reloadIntegrity = useCallback(async () => {
    if (!selectedAsset) {
      setIntegrity(null);
      return;
    }
    try {
      setIntegrity(await adminApi.showcaseIntegrity(token, selectedAsset.id));
    } catch {
      setIntegrity(null);
    }
  }, [token, selectedAsset]);

  useEffect(() => {
    void reloadIntegrity();
  }, [reloadIntegrity, showcases]);

  // 自动轮询：只要还有 DRAFT 且 resultImage 为 null 的 showcase，就 3s 轮一次
  useEffect(() => {
    const pending = showcases.some((s) => s.status === 'DRAFT' && !s.resultImage && !s.errorMessage);
    if (!pending) {
      setPolling(false);
      return;
    }
    setPolling(true);
    const t = setTimeout(() => {
      void reloadShowcases();
    }, 3000);
    return () => clearTimeout(t);
  }, [showcases, reloadShowcases]);

  const toggleModel = (id: number) => {
    setSelectedModelIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleImport = async () => {
    if (!selectedAsset || importFiles.length === 0) {
      setError('请选择本体并至少 1 张成品图');
      return;
    }
    setImporting(true);
    setError(null);
    try {
      await adminApi.importShowcases(token, selectedAsset.id, importFiles, { setFirstPrimary: true });
      toast.success(`已导入 ${importFiles.length} 张现成图`);
      setImportFiles([]);
      await reloadShowcases();
      await reloadIntegrity();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setImporting(false);
    }
  };

  const handleGenerate = async () => {
    if (!selectedAsset) {
      setError('请先选择一款假发本体');
      return;
    }
    if (selectedModelIds.size === 0) {
      setError('至少选 1 个模特');
      return;
    }
    setGenerating(true);
    setError(null);
    try {
      const created = await adminApi.generateShowcases(token, selectedAsset.id, Array.from(selectedModelIds));
      toast.success(`已提交 ${created.length} 个生成任务`);
      setShowcases((prev) => [...created, ...prev]);
      setSelectedModelIds(new Set());
      window.requestAnimationFrame(() => {
        candidatesRef.current?.scrollTo({ top: 0, behavior: 'smooth' });
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setGenerating(false);
    }
  };

  const handleApprove = async (s: WigShowcase, isPrimary: boolean) => {
    try {
      const saved = await adminApi.approveShowcase(token, s.id, isPrimary);
      if (isPrimary) {
        await reloadShowcases();
      } else {
        setShowcases((prev) => prev.map((x) => (x.id === saved.id ? saved : x)));
      }
      toast.success(isPrimary ? '已通过并设为主图' : '已通过');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const handleReject = async (s: WigShowcase) => {
    try {
      const saved = await adminApi.rejectShowcase(token, s.id);
      setShowcases((prev) => prev.map((x) => (x.id === saved.id ? saved : x)));
      toast.info('已驳回');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const handleDelete = async (s: WigShowcase) => {
    const ok = await confirm({
      title: '删除候选成品图',
      description: `将永久删除 #${s.id}（${s.status === 'APPROVED' ? '已通过' : '待审核'}${
        s.isPrimary ? ' · 主图' : ''
      }）。如果它正是当前主图，C 端将回退到本体图展示。`,
      confirmText: '删除',
      danger: true,
    });
    if (!ok) return;
    try {
      await adminApi.deleteShowcase(token, s.id);
      setShowcases((prev) => prev.filter((x) => x.id !== s.id));
      toast.success('已删除');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <div>
          <div className="text-white font-medium text-lg">成品图工坊</div>
          <div className="text-xs text-gray-400">
            演示模式：手工导入现成佩戴图 → 设主图（C 端选款封面）。AI 生成 / 批量向导已暂时关闭。
          </div>
        </div>
        <div className="flex items-center gap-2">
          {!DEMO_HIDE_AI && (
            <div className="flex rounded-lg overflow-hidden text-xs" style={{ border: '1px solid rgba(255,255,255,0.1)' }}>
              <button
                type="button"
                onClick={() => setMode('import')}
                className="px-3 py-1.5"
                style={{
                  background: mode === 'import' ? 'rgba(0,212,255,0.2)' : 'transparent',
                  color: mode === 'import' ? '#00d4ff' : '#6b7280',
                }}
              >
                导入现成图
              </button>
              <button
                type="button"
                onClick={() => setMode('ai')}
                className="px-3 py-1.5"
                style={{
                  background: mode === 'ai' ? 'rgba(179,71,255,0.2)' : 'transparent',
                  color: mode === 'ai' ? '#b347ff' : '#6b7280',
                }}
              >
                AI 生成
              </button>
            </div>
          )}
          {!DEMO_HIDE_AI && (
            <button
              type="button"
              onClick={() => setShowWizard((v) => !v)}
              className="px-3 py-2 rounded-lg flex items-center gap-1 text-xs text-gray-200"
              style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}
            >
              <FolderOpen className="w-3.5 h-3.5" />
              批量向导
            </button>
          )}
          <button
            onClick={reloadIndex}
            disabled={loading}
            className="px-3 py-2 rounded-lg flex items-center gap-2 text-sm text-gray-200 disabled:opacity-50"
            style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
            刷新
          </button>
        </div>
      </div>

      {showWizard && <CustomerImportWizard token={token} />}

      {integrity && selectedAsset && (
        <div
          className="text-xs px-3 py-2 rounded-lg flex flex-wrap gap-3"
          style={{
            background: integrity.ok ? 'rgba(0,200,100,0.08)' : 'rgba(255,180,0,0.08)',
            border: `1px solid ${integrity.ok ? 'rgba(0,200,100,0.2)' : 'rgba(255,180,0,0.25)'}`,
            color: integrity.ok ? '#86efac' : '#fcd34d',
          }}
        >
          <span>#{integrity.wigAssetId} {integrity.assetName}</span>
          <span>{integrity.hasProduct ? '✓ 已上架' : '✗ 缺商品'}</span>
          <span>陈列 {integrity.approvedCount} 张</span>
          <span>{integrity.hasPrimary ? '✓ 有主图' : '✗ 无主图'}</span>
          <span>{integrity.bareEqualsShowcase ? '⚠ 本体与陈列 URL 相同' : '✓ 本体≠陈列'}</span>
        </div>
      )}

      <ErrorBanner message={error} onClose={() => setError(null)} />

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        {/* 左：本体选择 */}
        <div className="lg:col-span-3 space-y-2">
          <div className="text-xs text-gray-400 mb-2">① 选择假发本体（{wigAssets.length}）</div>
          <div className="space-y-2 max-h-[60vh] overflow-y-auto pr-1" style={{ scrollbarWidth: 'thin' }}>
            {wigAssets.map((a) => (
              <button
                key={a.id}
                onClick={() => setSelectedAsset(a)}
                className="w-full flex items-center gap-3 p-2 rounded-lg text-left"
                style={{
                  background: selectedAsset?.id === a.id ? 'rgba(0,212,255,0.12)' : 'rgba(10,22,40,0.5)',
                  border: selectedAsset?.id === a.id ? '1px solid rgba(0,212,255,0.5)' : '1px solid rgba(255,255,255,0.06)',
                }}
              >
                <img src={a.productImage} alt={a.name} className="w-12 h-12 rounded object-cover" />
                <div className="flex-1 min-w-0">
                  <div className="text-white text-sm truncate">{a.name}</div>
                  <div className="text-[11px] text-gray-500">#{a.id} · {a.category}</div>
                </div>
              </button>
            ))}
          </div>
        </div>

        {/* 中：导入 or AI 模特选择 */}
        <div className="lg:col-span-3 space-y-2">
          {mode === 'import' ? (
            <>
              <div className="text-xs text-gray-400 mb-2">② 上传客户成品棚拍图（最多 15 张）</div>
              <div
                role="button"
                tabIndex={0}
                onClick={openImportPicker}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    openImportPicker();
                  }
                }}
                className="rounded-lg p-6 flex flex-col items-center justify-center gap-2 cursor-pointer transition-colors hover:border-cyan-400/40 min-h-[140px]"
                style={{ background: 'rgba(0,0,0,0.35)', border: '1px dashed rgba(255,255,255,0.15)' }}
              >
                <Upload className="w-7 h-7 text-gray-500" />
                <div className="text-xs text-gray-400 text-center">
                  {importFiles.length > 0
                    ? `已选 ${importFiles.length} 张，点击可重新选择`
                    : '点击选择佩戴图（模特戴假发的成品图）'}
                </div>
              </div>
              <input
                ref={importInputRef}
                type="file"
                accept="image/*"
                multiple
                onChange={(e) => setImportFiles(Array.from(e.target.files ?? []).slice(0, 15))}
                className="hidden"
              />
              <button
                type="button"
                onClick={openImportPicker}
                className="w-full py-2 rounded-lg text-xs text-cyan-300"
                style={{ background: 'rgba(0,212,255,0.08)', border: '1px solid rgba(0,212,255,0.25)' }}
              >
                {importFiles.length > 0 ? '重新选择图片' : '选择图片'}
              </button>
              <button
                type="button"
                onClick={handleImport}
                disabled={importing || !selectedAsset || importFiles.length === 0}
                className="w-full py-3 rounded-lg text-sm text-white font-medium flex items-center justify-center gap-2 disabled:opacity-50"
                style={{ background: 'linear-gradient(135deg, #00d4ff, #0891b2)' }}
              >
                <Upload className="w-4 h-4" />
                {importing ? '上传中...' : `上传并关联 ${importFiles.length || 0} 张`}
              </button>
              <p className="text-[10px] text-gray-500 leading-relaxed">
                若尚无主图，首张自动设为主图；已有主图则新图作为非主图（款式素材）。试戴 AI 仍用本体图。
              </p>
            </>
          ) : (
            <>
          <div className="text-xs text-gray-400 mb-2">② 选择模特（已选 {selectedModelIds.size}/{models.length}）</div>
          <div className="grid grid-cols-2 gap-2 max-h-[60vh] overflow-y-auto pr-1" style={{ scrollbarWidth: 'thin' }}>
            {models.map((m) => {
              const checked = selectedModelIds.has(m.id);
              return (
                <button
                  key={m.id}
                  onClick={() => toggleModel(m.id)}
                  className="rounded-lg overflow-hidden text-left relative"
                  style={{
                    border: checked ? '2px solid #00d4ff' : '1px solid rgba(255,255,255,0.08)',
                    background: 'rgba(10,22,40,0.5)',
                  }}
                >
                  <div className="aspect-[3/4] bg-black">
                    <img src={m.portraitImage} alt={m.name} className="w-full h-full object-cover" />
                  </div>
                  <div className="p-2">
                    <div className="text-white text-xs truncate">{m.name}</div>
                    <div className="text-[10px] text-gray-500">{m.faceShape} · {m.ageGroup}</div>
                  </div>
                  {checked && (
                    <div className="absolute top-1 right-1 w-5 h-5 rounded-full bg-cyan-400 flex items-center justify-center">
                      <CheckCircle2 className="w-4 h-4 text-black" />
                    </div>
                  )}
                </button>
              );
            })}
          </div>
          <button
            onClick={handleGenerate}
            disabled={generating || !selectedAsset || selectedModelIds.size === 0}
            className="w-full py-3 rounded-lg text-sm text-white font-medium flex items-center justify-center gap-2 disabled:opacity-50"
            style={{ background: 'linear-gradient(135deg, #00d4ff, #b347ff)' }}
          >
            <Sparkles className="w-4 h-4" />
            {generating ? '提交中...' : `AI 生成 ${selectedModelIds.size} 张成品图`}
          </button>
            </>
          )}
        </div>

        {/* 右：候选/已审核成品图 */}
        <div className="lg:col-span-6 space-y-2">
          <div className="text-xs text-gray-400 mb-2 flex items-center gap-2">
            ③ 候选成品图（{showcases.length}）
            {polling && (
              <span className="text-cyan-300 flex items-center gap-1">
                <RefreshCw className="w-3 h-3 animate-spin" />
                自动刷新中
              </span>
            )}
          </div>
          <div
            ref={candidatesRef}
            className="grid grid-cols-2 sm:grid-cols-3 gap-3 max-h-[65vh] overflow-y-auto pr-1"
            style={{ scrollbarWidth: 'thin' }}
          >
            {showcases.map((s) => (
              <ShowcaseCard
                key={s.id}
                showcase={s}
                models={models}
                onApprove={(primary) => handleApprove(s, primary)}
                onReject={() => handleReject(s)}
                onDelete={() => handleDelete(s)}
              />
            ))}
            {showcases.length === 0 && (
              <div className="col-span-full">
                <EmptyState
                  title={selectedAsset ? '此款本体尚未生成任何成品图' : '请先选择左侧任意一款假发本体'}
                  hint={selectedAsset ? (mode === 'import' ? '在中间栏上传客户成品图，或点顶部「批量向导」' : '在左中两栏分别选好本体和模特后，点击下方蓝紫色按钮开始生成') : undefined}
                  icon={<Wand2 className="w-7 h-7 text-white/40" />}
                />
              </div>
            )}
          </div>
        </div>
      </div>

      {confirmDialog}
      <ToastStack toasts={toasts} onClose={closeToast} />
    </div>
  );
}

function ShowcaseCard({
  showcase,
  models,
  onApprove,
  onReject,
  onDelete,
}: {
  showcase: WigShowcase;
  models: ModelAsset[];
  onApprove: (isPrimary: boolean) => void;
  onReject: () => void;
  onDelete: () => void;
}) {
  const model = models.find((m) => m.id === showcase.modelAssetId) ?? null;
  const pending = showcase.status === 'DRAFT' && !showcase.resultImage && !showcase.errorMessage;
  const failed = showcase.status === 'REJECTED' && showcase.errorMessage;

  const statusBadge =
    showcase.status === 'APPROVED' ? '已通过' : showcase.status === 'REJECTED' ? '已驳回' : '待审核';
  const statusColor =
    showcase.status === 'APPROVED'
      ? { background: 'rgba(0,200,100,0.15)', color: '#00ff96' }
      : showcase.status === 'REJECTED'
      ? { background: 'rgba(255,80,80,0.15)', color: '#ff8080' }
      : { background: 'rgba(255,200,0,0.15)', color: '#ffd84d' };

  return (
    <div
      className="rounded-lg overflow-hidden flex flex-col"
      style={{ background: 'rgba(10,22,40,0.6)', border: '1px solid rgba(255,255,255,0.06)' }}
    >
      <div className="aspect-square bg-black flex items-center justify-center relative">
        {showcase.resultImage ? (
          <img src={showcase.resultImage} alt={`#${showcase.id}`} className="w-full h-full object-cover" />
        ) : pending ? (
          <div className="text-xs text-gray-500 flex flex-col items-center gap-2">
            <RefreshCw className="w-5 h-5 animate-spin" />
            生成中...
          </div>
        ) : (
          <div className="text-xs text-red-400 px-3 text-center">
            生成失败
            {showcase.errorMessage && <div className="text-[10px] text-gray-500 mt-1 line-clamp-3">{showcase.errorMessage}</div>}
          </div>
        )}
        {showcase.isPrimary && (
          <div
            className="absolute top-1 left-1 px-2 py-0.5 rounded-full flex items-center gap-1 text-[10px] font-medium"
            style={{ background: 'rgba(0,212,255,0.95)', color: '#000' }}
          >
            <Star className="w-3 h-3 fill-current" />
            主图
          </div>
        )}
        <div
          className="absolute top-1 right-1 px-2 py-0.5 rounded-full text-[10px]"
          style={statusColor}
        >
          {statusBadge}
        </div>
      </div>
      <div className="p-2 space-y-1.5">
        <div className="text-[11px] text-gray-400 truncate">
          模特：{model?.name ?? (showcase.modelAssetId == null ? '未绑定' : `#${showcase.modelAssetId}`)}
        </div>
        {showcase.changeScore != null && (
          <div className="text-[10px] text-gray-500">变化分 {showcase.changeScore.toFixed(2)}</div>
        )}
        {!pending && !failed && showcase.status !== 'APPROVED' && (
          <div className="grid grid-cols-2 gap-1 pt-1">
            <button
              onClick={() => onApprove(false)}
              className="px-2 py-1 rounded text-[11px] text-green-300"
              style={{ background: 'rgba(0,200,100,0.1)', border: '1px solid rgba(0,200,100,0.2)' }}
            >
              <CheckCircle2 className="w-3 h-3 inline mr-1" />通过
            </button>
            <button
              onClick={onReject}
              className="px-2 py-1 rounded text-[11px] text-red-300"
              style={{ background: 'rgba(255,80,80,0.08)', border: '1px solid rgba(255,80,80,0.2)' }}
            >
              <XCircle className="w-3 h-3 inline mr-1" />驳回
            </button>
            <button
              onClick={() => onApprove(true)}
              className="col-span-2 px-2 py-1 rounded text-[11px] text-cyan-300"
              style={{ background: 'rgba(0,212,255,0.08)', border: '1px solid rgba(0,212,255,0.25)' }}
            >
              <Star className="w-3 h-3 inline mr-1" />通过 + 设为主图
            </button>
          </div>
        )}
        {showcase.status === 'APPROVED' && !showcase.isPrimary && (
          <button
            onClick={() => onApprove(true)}
            className="w-full px-2 py-1 rounded text-[11px] text-cyan-300"
            style={{ background: 'rgba(0,212,255,0.08)', border: '1px solid rgba(0,212,255,0.25)' }}
          >
            <Star className="w-3 h-3 inline mr-1" />设为主图
          </button>
        )}
        <button
          onClick={onDelete}
          className="w-full px-2 py-1 rounded text-[11px] text-gray-400 mt-1"
          style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.06)' }}
        >
          <Trash2 className="w-3 h-3 inline mr-1" />删除
        </button>
      </div>
    </div>
  );
}
