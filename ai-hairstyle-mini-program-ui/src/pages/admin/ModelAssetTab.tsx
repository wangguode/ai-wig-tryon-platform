import React, { useCallback, useEffect, useState } from 'react';
import { Upload, Trash2, RefreshCw, Users } from 'lucide-react';
import { adminApi, ModelAsset, ModelAssetMeta } from '../../api/admin';
import {
  ErrorBanner,
  Field,
  EmptyState,
  ToastStack,
  useConfirm,
  useToastStack,
} from './adminUi';

/** 模特库管理：与 WigAssetTab 同结构。 */
export function ModelAssetTab({ token }: { token: string }) {
  const [models, setModels] = useState<ModelAsset[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const { toasts, close: closeToast, toast } = useToastStack();
  const { confirm, dialog: confirmDialog } = useConfirm();

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setModels(await adminApi.listModelAssets(token));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const handleSaved = (saved: ModelAsset) => {
    setModels((prev) => {
      const exists = prev.some((a) => a.id === saved.id);
      return exists ? prev.map((a) => (a.id === saved.id ? saved : a)) : [...prev, saved];
    });
    toast.success(`已保存：${saved.name}`);
  };

  const handleDelete = async (m: ModelAsset) => {
    const ok = await confirm({
      title: '删除模特',
      description: `将永久删除模特「${m.name}」（#${m.id}）。\n关联的成品图不会被自动删除，但今后不能再用此模特生成新图。`,
      confirmText: '删除',
      danger: true,
    });
    if (!ok) return;
    try {
      await adminApi.deleteModelAsset(token, m.id);
      setModels((prev) => prev.filter((a) => a.id !== m.id));
      toast.success('已删除');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <div className="text-white font-medium text-lg">模特库</div>
          <div className="text-xs text-gray-400">共 {models.length} 位 · 用于成品图工坊批量合成</div>
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
            添加模特
          </button>
        </div>
      </div>

      <ErrorBanner message={error} onClose={() => setError(null)} />

      {!loading && models.length === 0 && (
        <EmptyState
          title="暂无模特"
          hint="点击右上角「添加模特」上传一张胸像，作为成品图工坊的合成基底"
          icon={<Users className="w-7 h-7 text-white/40" />}
        />
      )}

      <div className="grid gap-4 admin-fade-in" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))' }}>
        {models.map((m) => (
          <ModelCard key={m.id} model={m} onDelete={() => handleDelete(m)} />
        ))}
      </div>

      {creating && <UploadModal token={token} onClose={() => setCreating(false)} onSaved={handleSaved} />}

      {confirmDialog}
      <ToastStack toasts={toasts} onClose={closeToast} />
    </div>
  );
}

function ModelCard({ model, onDelete }: { model: ModelAsset; onDelete: () => void }) {
  return (
    <div
      className="rounded-xl overflow-hidden flex flex-col group transition hover:translate-y-[-2px]"
      style={{
        background: 'rgba(10,22,40,0.6)',
        border: '1px solid rgba(255,255,255,0.06)',
        boxShadow: '0 4px 16px rgba(0,0,0,0.2)',
      }}
    >
      <div className="aspect-[3/4] bg-black flex items-center justify-center relative">
        <img
          src={model.portraitImage}
          alt={model.name}
          loading="lazy"
          className="w-full h-full object-cover transition-transform duration-200 group-hover:scale-[1.02]"
        />
        <div
          className="absolute top-2 left-2 px-2 py-0.5 rounded text-[10px] font-medium"
          style={{ background: 'rgba(0,0,0,0.6)', color: 'rgba(255,255,255,0.85)' }}
        >
          #{model.id}
        </div>
      </div>
      <div className="p-3 space-y-1">
        <div className="text-white text-sm font-medium truncate">{model.name}</div>
        <div className="text-[11px] text-gray-500 truncate">
          {model.gender === 'M' ? '男' : '女'} · {model.faceShape || '—'} · {model.ageGroup || '—'}
        </div>
        <div className="flex gap-2 pt-2">
          <button
            onClick={onDelete}
            className="flex-1 px-3 py-1.5 rounded-md text-xs flex items-center justify-center gap-1 text-red-300 transition hover:brightness-125"
            style={{ background: 'rgba(255,80,80,0.08)', border: '1px solid rgba(255,80,80,0.2)' }}
          >
            <Trash2 className="w-3 h-3" />
            删除
          </button>
        </div>
      </div>
    </div>
  );
}

function UploadModal({
  token,
  onClose,
  onSaved,
}: {
  token: string;
  onClose: () => void;
  onSaved: (saved: ModelAsset) => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [meta, setMeta] = useState<ModelAssetMeta>({
    name: '',
    gender: 'F',
    ageGroup: 'young',
    faceShape: '鹅蛋脸',
    tags: [],
  });
  const [tagsInput, setTagsInput] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

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

  const handleSubmit = async () => {
    if (!file) {
      setError('请选择模特肖像');
      return;
    }
    if (!meta.name.trim()) {
      setError('请填写模特命名');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const saved = await adminApi.uploadModelAsset(token, file, {
        ...meta,
        tags: tagsInput.split(/[,，\s]+/).map((s) => s.trim()).filter(Boolean),
      });
      onSaved(saved);
      onClose();
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
            <div className="text-white font-medium text-lg">添加模特</div>
            <button onClick={onClose} className="text-gray-400 text-sm hover:text-white">关闭 (Esc)</button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Field label="模特肖像（建议正面胸像）" hint="清晰正面照，背景简洁。最大 10MB。">
              <div
                className="aspect-[3/4] rounded-lg flex items-center justify-center overflow-hidden"
                style={{ background: 'rgba(0,0,0,0.4)', border: '1px dashed rgba(255,255,255,0.15)' }}
              >
                {preview ? <img src={preview} alt="preview" className="w-full h-full object-cover" /> : <div className="text-xs text-gray-500">尚未选择文件</div>}
              </div>
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif"
                onChange={(e) => onPickFile(e.target.files?.[0] ?? null)}
                className="block mt-3 text-xs text-gray-400"
              />
            </Field>

            <div className="space-y-3">
              <Field label="命名">
                <input value={meta.name} onChange={(e) => setMeta({ ...meta, name: e.target.value })} className="admin-input" placeholder="小雅 · 鹅蛋脸轻熟" />
              </Field>
              <div className="grid grid-cols-2 gap-3">
                <Field label="性别">
                  <select value={meta.gender} onChange={(e) => setMeta({ ...meta, gender: e.target.value })} className="admin-input">
                    <option value="F">女</option>
                    <option value="M">男</option>
                  </select>
                </Field>
                <Field label="年龄段">
                  <select value={meta.ageGroup} onChange={(e) => setMeta({ ...meta, ageGroup: e.target.value })} className="admin-input">
                    <option value="young">young (年轻)</option>
                    <option value="mid">mid (中年)</option>
                    <option value="elder">elder (银发)</option>
                  </select>
                </Field>
              </div>
              <Field label="脸型">
                <select value={meta.faceShape} onChange={(e) => setMeta({ ...meta, faceShape: e.target.value })} className="admin-input">
                  <option>圆脸</option>
                  <option>方脸</option>
                  <option>长脸</option>
                  <option>瓜子脸</option>
                  <option>鹅蛋脸</option>
                  <option>心形脸</option>
                </select>
              </Field>
              <Field label="标签（逗号分隔）">
                <input value={tagsInput} onChange={(e) => setTagsInput(e.target.value)} className="admin-input" placeholder="职场,轻熟" />
              </Field>
            </div>
          </div>

          <ErrorBanner message={error} onClose={() => setError(null)} />

          <div className="flex justify-end gap-3 pt-2">
            <button
              onClick={handleSubmit}
              disabled={submitting}
              className="px-5 py-2 rounded-lg text-sm text-white font-medium disabled:opacity-50"
              style={{ background: 'linear-gradient(135deg, #00d4ff, #b347ff)' }}
            >
              {submitting ? '提交中...' : '上传'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
