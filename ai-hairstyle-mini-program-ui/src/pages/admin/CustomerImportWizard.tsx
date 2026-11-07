import React, { useState } from 'react';
import { FolderOpen, Loader2, Play, Eye } from 'lucide-react';
import { adminApi, CustomerImportPreview, CustomerImportResult } from '../../api/admin';
import { ErrorBanner, ToastStack, useConfirm, useToastStack } from './adminUi';

const DEFAULT_PATH = '/Users/wangguo/Desktop/VS/发型素材-ai测试用-刘经理提供';

/** 客户素材文件夹一键导入向导（31 款，不调 AI）。 */
export function CustomerImportWizard({ token }: { token: string }) {
  const [sourcePath, setSourcePath] = useState(DEFAULT_PATH);
  const [preview, setPreview] = useState<CustomerImportPreview | null>(null);
  const [result, setResult] = useState<CustomerImportResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { toasts, close: closeToast, toast } = useToastStack();
  const { confirm, dialog: confirmDialog } = useConfirm();

  const handlePreview = async () => {
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const p = await adminApi.previewCustomerImport(token, sourcePath.trim());
      setPreview(p);
      toast.success(`预览完成：约 ${p.estimatedStyleCount} 款`);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  };

  const handleExecute = async () => {
    const ok = await confirm({
      title: '确认一键导入',
      description: `将执行 v3 导入脚本，覆盖 data/*.json 并热加载。\n路径：${sourcePath}`,
      confirmText: '开始导入',
      danger: true,
    });
    if (!ok) return;
    setLoading(true);
    setError(null);
    try {
      const r = await adminApi.executeCustomerImport(token, sourcePath.trim());
      setResult(r);
      toast.success(`导入完成：${r.productCount} 款 / ${r.showcaseCount} 张陈列`);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      className="rounded-xl p-4 space-y-3"
      style={{ background: 'rgba(10,22,40,0.6)', border: '1px solid rgba(0,212,255,0.15)' }}
    >
      <div className="flex items-center gap-2">
        <FolderOpen className="w-5 h-5 text-cyan-400" />
        <div>
          <div className="text-white font-medium">客户素材一键导入</div>
          <div className="text-xs text-gray-400">扫描 V 系列文件夹 → 本体/陈列分离 → 不调 AI</div>
        </div>
      </div>

      <ErrorBanner message={error} onClose={() => setError(null)} />

      <label className="block text-xs text-gray-400">素材根目录（服务器本机路径）</label>
      <input
        value={sourcePath}
        onChange={(e) => setSourcePath(e.target.value)}
        className="admin-input w-full text-sm"
        placeholder={DEFAULT_PATH}
      />

      <div className="flex gap-2">
        <button
          type="button"
          onClick={handlePreview}
          disabled={loading}
          className="px-3 py-2 rounded-lg text-sm flex items-center gap-2 text-cyan-300 disabled:opacity-50"
          style={{ border: '1px solid rgba(0,212,255,0.3)', background: 'rgba(0,212,255,0.08)' }}
        >
          {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Eye className="w-4 h-4" />}
          预览
        </button>
        <button
          type="button"
          onClick={handleExecute}
          disabled={loading}
          className="px-3 py-2 rounded-lg text-sm flex items-center gap-2 text-white disabled:opacity-50"
          style={{ background: 'linear-gradient(135deg, #00d4ff, #b347ff)' }}
        >
          {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4" />}
          确认导入
        </button>
      </div>

      {preview && (
        <div className="text-xs text-gray-300 space-y-1 max-h-40 overflow-y-auto">
          <div>V 系列 {preview.vStyleCount} 款 · 预计总计 {preview.estimatedStyleCount} 款</div>
          {preview.warnings.slice(0, 5).map((w) => (
            <div key={w} className="text-amber-400/90">⚠ {w}</div>
          ))}
        </div>
      )}

      {result && (
        <div className="text-xs text-green-300/90 whitespace-pre-wrap">
          完成：{result.productCount} 商品 / {result.showcaseCount} 陈列 / {result.modelCount} 模特
        </div>
      )}

      {confirmDialog}
      <ToastStack toasts={toasts} onClose={closeToast} />
    </div>
  );
}
