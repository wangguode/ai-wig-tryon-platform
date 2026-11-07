import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Upload, Trash2, RefreshCw, Edit3, Sparkles, Search, Image as ImageIcon } from 'lucide-react';
import { adminApi, WigAsset, WigAssetMeta } from '../../api/admin';
import {
  ErrorBanner,
  Field,
  EmptyState,
  LoadingOverlay,
  ToastStack,
  useConfirm,
  useToastStack,
} from './adminUi';

/** 假发本体管理（喂给 AI 的"参考图"来源）。 */
export function WigAssetTab({ token }: { token: string }) {
  const [assets, setAssets] = useState<WigAsset[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<WigAsset | null>(null);
  const [creating, setCreating] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<string>('all');
  const [regeneratingId, setRegeneratingId] = useState<number | null>(null);

  const { toasts, close: closeToast, toast } = useToastStack();
  const { confirm, dialog: confirmDialog } = useConfirm();

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setAssets(await adminApi.listWigAssets(token));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const handleSaved = (saved: WigAsset) => {
    setAssets((prev) => {
      const exists = prev.some((a) => a.id === saved.id);
      return exists ? prev.map((a) => (a.id === saved.id ? saved : a)) : [...prev, saved];
    });
    toast.success(`已保存：${saved.name}`);
  };

  const handleDelete = async (asset: WigAsset) => {
    const ok = await confirm({
      title: '删除假发本体',
      description: `将永久删除「${asset.name}」（#${asset.id}），此操作不可恢复。关联的成品图不会被自动删除。`,
      confirmText: '删除',
      cancelText: '取消',
      danger: true,
    });
    if (!ok) return;
    try {
      await adminApi.deleteWigAsset(token, asset.id);
      setAssets((prev) => prev.filter((a) => a.id !== asset.id));
      toast.success('已删除');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  /** 用 AI 文生图重画本体图。需要 30~60s，全屏 loading 蒙层。 */
  const handleRegenerateBare = async (asset: WigAsset) => {
    if (!asset.descriptionForAi || asset.descriptionForAi.trim().length < 8) {
      toast.error('请先在"编辑"里补全 AI 描述（至少 8 字），AI 才能据此画图');
      return;
    }
    const ok = await confirm({
      title: 'AI 重画本体图',
      description: `将根据 AI 描述重新生成一张白底纯本体图，覆盖当前图片（约 30~60 秒）。\n\n描述：${asset.descriptionForAi.slice(
        0,
        80,
      )}${asset.descriptionForAi.length > 80 ? '...' : ''}`,
      confirmText: '开始生成',
    });
    if (!ok) return;
    setRegeneratingId(asset.id);
    try {
      const updated = await adminApi.regenerateBareWig(token, asset.id);
      setAssets((prev) => prev.map((a) => (a.id === updated.id ? updated : a)));
      toast.success(`已重画：${updated.name}`);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setRegeneratingId(null);
    }
  };

  const categories = useMemo(() => {
    const set = new Set<string>();
    assets.forEach((a) => a.category && set.add(a.category));
    return Array.from(set);
  }, [assets]);

  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    return assets.filter((a) => {
      if (categoryFilter !== 'all' && a.category !== categoryFilter) return false;
      if (!kw) return true;
      return (
        a.name?.toLowerCase().includes(kw) ||
        a.code?.toLowerCase().includes(kw) ||
        a.colorFamily?.toLowerCase().includes(kw) ||
        a.descriptionForAi?.toLowerCase().includes(kw)
      );
    });
  }, [assets, keyword, categoryFilter]);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <div className="text-white font-medium text-lg">假发本体管理</div>
          <div className="text-xs text-gray-400">
            共 {assets.length} 条
            {filtered.length !== assets.length && ` · 筛选后 ${filtered.length} 条`} · 给 AI 试戴用的"参考图"来源
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={reload}
            disabled={loading}
            className="px-3 py-2 rounded-lg flex items-center gap-2 text-sm text-gray-200 disabled:opacity-50"
            style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
            刷新
          </button>
          <button
            onClick={() => setCreating(true)}
            className="px-4 py-2 rounded-lg flex items-center gap-2 text-sm text-white font-medium"
            style={{ background: 'linear-gradient(135deg, #00d4ff, #b347ff)' }}
          >
            <Upload className="w-4 h-4" />
            上传本体图
          </button>
        </div>
      </div>

      <div className="flex items-center gap-2 flex-wrap">
        <div className="relative flex-1 min-w-[220px]">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-white/40" />
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="搜索名称 / SKU / 发色 / 描述"
            className="admin-input"
            style={{ paddingLeft: 36 }}
          />
        </div>
        <select
          value={categoryFilter}
          onChange={(e) => setCategoryFilter(e.target.value)}
          className="admin-input"
          style={{ width: 140 }}
        >
          <option value="all">全部类型</option>
          {categories.map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>
      </div>

      <ErrorBanner message={error} onClose={() => setError(null)} />

      {!loading && filtered.length === 0 && (
        <EmptyState
          title={assets.length === 0 ? '还没有任何假发本体' : '没有匹配的本体'}
          hint={
            assets.length === 0
              ? '点击右上角「上传本体图」开始添加，或先用「AI 重画本体」把占位图替换为真本体'
              : '换个关键词或类型再试试'
          }
          icon={<ImageIcon className="w-7 h-7 text-white/40" />}
        />
      )}

      <div className="grid gap-4 admin-fade-in" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))' }}>
        {filtered.map((asset) => (
          <AssetCard
            key={asset.id}
            asset={asset}
            onEdit={() => setEditing(asset)}
            onDelete={() => handleDelete(asset)}
            onRegenerate={() => handleRegenerateBare(asset)}
          />
        ))}
      </div>

      {(creating || editing) && (
        <UploadModal
          token={token}
          existing={editing ?? null}
          onClose={() => {
            setCreating(false);
            setEditing(null);
          }}
          onSaved={(saved) => {
            handleSaved(saved);
            setCreating(false);
            setEditing(null);
          }}
        />
      )}

      <LoadingOverlay
        visible={regeneratingId != null}
        title="AI 正在重画本体图..."
        hint={'通常需要 30~60 秒，请勿关闭页面。\n生成完成后页面会自动刷新该卡片。'}
      />

      {confirmDialog}
      <ToastStack toasts={toasts} onClose={closeToast} />
    </div>
  );
}

function AssetCard({
  asset,
  onEdit,
  onDelete,
  onRegenerate,
}: {
  asset: WigAsset;
  onEdit: () => void;
  onDelete: () => void;
  onRegenerate: () => void;
}) {
  return (
    <div
      className="rounded-xl overflow-hidden flex flex-col group transition hover:translate-y-[-2px]"
      style={{
        background: 'rgba(10,22,40,0.6)',
        border: '1px solid rgba(255,255,255,0.06)',
        boxShadow: '0 4px 16px rgba(0,0,0,0.2)',
      }}
    >
      <div className="aspect-square bg-black flex items-center justify-center relative">
        <img
          src={asset.productImage}
          alt={asset.name}
          loading="lazy"
          className="w-full h-full object-cover transition-transform duration-200 group-hover:scale-[1.02]"
          onError={(e) => {
            (e.currentTarget as HTMLImageElement).style.opacity = '0.3';
          }}
        />
        <div
          className="absolute top-2 left-2 px-2 py-0.5 rounded text-[10px] font-medium"
          style={{ background: 'rgba(0,0,0,0.6)', color: 'rgba(255,255,255,0.85)' }}
        >
          #{asset.id}
        </div>
      </div>
      <div className="p-3 space-y-1">
        <div className="text-white text-sm font-medium truncate" title={asset.name}>
          {asset.name}
        </div>
        <div className="text-[11px] text-gray-500 truncate">
          {asset.code || '—'} · {asset.category || '—'} · {asset.colorFamily || '—'}
        </div>
        <div className="flex gap-1.5 pt-2">
          <button
            onClick={onRegenerate}
            title="用 AI 文生图根据描述重画一张白底纯本体图"
            className="flex-1 px-2 py-1.5 rounded-md text-xs flex items-center justify-center gap-1 text-purple-200 transition hover:brightness-125"
            style={{ background: 'rgba(179,71,255,0.12)', border: '1px solid rgba(179,71,255,0.3)' }}
          >
            <Sparkles className="w-3 h-3" />
            AI 重画
          </button>
          <button
            onClick={onEdit}
            title="替换图片或编辑元数据"
            className="px-2 py-1.5 rounded-md text-xs flex items-center justify-center text-cyan-300 transition hover:brightness-125"
            style={{ background: 'rgba(0,212,255,0.1)', border: '1px solid rgba(0,212,255,0.25)' }}
          >
            <Edit3 className="w-3 h-3" />
          </button>
          <button
            onClick={onDelete}
            title="删除"
            className="px-2 py-1.5 rounded-md text-xs flex items-center justify-center text-red-300 transition hover:brightness-125"
            style={{ background: 'rgba(255,80,80,0.08)', border: '1px solid rgba(255,80,80,0.2)' }}
          >
            <Trash2 className="w-3 h-3" />
          </button>
        </div>
      </div>
    </div>
  );
}

function UploadModal({
  token,
  existing,
  onClose,
  onSaved,
}: {
  token: string;
  existing: WigAsset | null;
  onClose: () => void;
  onSaved: (saved: WigAsset) => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(existing?.productImage ?? null);
  const [meta, setMeta] = useState<WigAssetMeta>({
    code: existing?.code ?? '',
    name: existing?.name ?? '',
    descriptionForAi: existing?.descriptionForAi ?? '',
    category: existing?.category ?? '中长发',
    colorFamily: existing?.colorFamily ?? '自然黑',
    tags: existing?.tags ?? [],
  });
  const [tagsInput, setTagsInput] = useState((existing?.tags ?? []).join(','));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = React.useRef<HTMLInputElement>(null);

  const openFilePicker = () => fileInputRef.current?.click();

  // ESC 关闭
  React.useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const onPickFile = (f: File | null) => {
    setFile(f);
    if (f) setPreview(URL.createObjectURL(f));
  };

  const finalMeta = (): WigAssetMeta => ({
    ...meta,
    tags: tagsInput.split(/[,，\s]+/).map((s) => s.trim()).filter(Boolean),
  });

  const handleSubmit = async () => {
    if (!file && !existing) {
      setError('请选择假发本体图');
      return;
    }
    if (!meta.name.trim()) {
      setError('请填写名称');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const saved = await adminApi.uploadWigAsset(
        token,
        file ?? new File([new Blob()], 'placeholder.png', { type: 'image/png' }),
        finalMeta(),
        existing?.id,
      );
      onSaved(saved);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmitMetaOnly = async () => {
    if (!existing) return;
    setSubmitting(true);
    setError(null);
    try {
      const saved = await adminApi.updateMeta(token, existing.id, finalMeta());
      onSaved(saved);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center px-4 admin-fade-in"
      style={{ background: 'rgba(0,0,0,0.65)', backdropFilter: 'blur(4px)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-2xl"
        style={{ background: '#0a1628', border: '1px solid rgba(255,255,255,0.08)' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="p-6 space-y-4">
          <div className="flex items-center justify-between">
            <div className="text-white font-medium text-lg">{existing ? `编辑：${existing.name}` : '上传新的假发本体'}</div>
            <button onClick={onClose} className="text-gray-400 text-sm hover:text-white">关闭 (Esc)</button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Field label="假发本体图（白底/透明 PNG 最佳）" hint="纯产品图，无模特、无人脸、单色背景。最大 10MB。">
              <div
                role="button"
                tabIndex={0}
                onClick={openFilePicker}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    openFilePicker();
                  }
                }}
                className="aspect-square rounded-lg flex flex-col items-center justify-center gap-2 overflow-hidden cursor-pointer transition-colors hover:border-cyan-400/40"
                style={{ background: 'rgba(0,0,0,0.4)', border: '1px dashed rgba(255,255,255,0.15)' }}
              >
                {preview ? (
                  <img src={preview} alt="preview" className="w-full h-full object-contain" />
                ) : (
                  <>
                    <Upload className="w-8 h-8 text-gray-500" />
                    <div className="text-xs text-gray-400">点击选择本体图</div>
                  </>
                )}
              </div>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif"
                onChange={(e) => onPickFile(e.target.files?.[0] ?? null)}
                className="hidden"
              />
              <button
                type="button"
                onClick={openFilePicker}
                className="mt-3 w-full py-2 rounded-lg text-xs text-cyan-300 transition hover:brightness-110"
                style={{ background: 'rgba(0,212,255,0.08)', border: '1px solid rgba(0,212,255,0.25)' }}
              >
                {preview ? '重新选择图片' : '选择图片'}
              </button>
            </Field>

            <div className="space-y-3">
              <Field label="名称">
                <input value={meta.name} onChange={(e) => setMeta({ ...meta, name: e.target.value })} className="admin-input" placeholder="例如：轻熟法式锁骨卷-本体" />
              </Field>
              <Field label="SKU 编码">
                <input value={meta.code} onChange={(e) => setMeta({ ...meta, code: e.target.value })} className="admin-input" placeholder="WA-009" />
              </Field>
              <div className="grid grid-cols-2 gap-3">
                <Field label="类型">
                  <select value={meta.category} onChange={(e) => setMeta({ ...meta, category: e.target.value })} className="admin-input">
                    <option>短发</option>
                    <option>中长发</option>
                    <option>长发</option>
                    <option>卷发</option>
                  </select>
                </Field>
                <Field label="发色">
                  <input value={meta.colorFamily} onChange={(e) => setMeta({ ...meta, colorFamily: e.target.value })} className="admin-input" placeholder="自然黑/巧克力棕..." />
                </Field>
              </div>
              <Field label="风格标签（逗号分隔）">
                <input value={tagsInput} onChange={(e) => setTagsInput(e.target.value)} className="admin-input" placeholder="自然,日常,通用" />
              </Field>
              <Field label="给 AI 的中文描述（发色/长度/卷直/分缝/刘海，勿写「奢华/软糯」等形容词）">
                <textarea value={meta.descriptionForAi} onChange={(e) => setMeta({ ...meta, descriptionForAi: e.target.value })} className="admin-input min-h-[120px]" placeholder="【发色】自然黑【长度】齐锁骨【卷直】直发【分缝】中分【刘海】无【发量】中等【要求】保留原图肤色毛孔，禁止磨皮" />
              </Field>
            </div>
          </div>

          <ErrorBanner message={error} onClose={() => setError(null)} />

          <div className="flex justify-end gap-3 pt-2">
            {existing && !file && (
              <button
                onClick={handleSubmitMetaOnly}
                disabled={submitting}
                className="px-4 py-2 rounded-lg text-sm text-gray-200 disabled:opacity-50"
                style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}
              >
                仅保存元数据
              </button>
            )}
            <button
              onClick={handleSubmit}
              disabled={submitting}
              className="px-5 py-2 rounded-lg text-sm text-white font-medium disabled:opacity-50"
              style={{ background: 'linear-gradient(135deg, #00d4ff, #b347ff)' }}
            >
              {submitting ? '提交中...' : existing ? '保存（含替换图）' : '上传'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
