import React from 'react';
import { AlertCircle, CheckCircle2, Inbox, Loader2, X, AlertTriangle } from 'lucide-react';

/** 运营后台共享 UI：Field / Toast / 错误条 / 空状态 / 加载蒙层 / 二次确认弹窗。 */

export function Field({ label, children, hint }: { label: string; children: React.ReactNode; hint?: string }) {
  return (
    <div>
      <label className="text-xs text-gray-400 mb-1.5 block">{label}</label>
      {children}
      {hint && <div className="text-[11px] text-gray-500 mt-1">{hint}</div>}
    </div>
  );
}

export function ErrorBanner({ message, onClose }: { message: string | null; onClose?: () => void }) {
  if (!message) return null;
  return (
    <div
      className="px-3 py-2 rounded-lg flex items-center gap-2 text-sm"
      style={{ background: 'rgba(255,80,80,0.12)', color: '#ff9999', border: '1px solid rgba(255,80,80,0.3)' }}
    >
      <AlertCircle className="w-4 h-4 shrink-0" />
      <span className="flex-1">{message}</span>
      {onClose && (
        <button onClick={onClose} className="text-white/40 hover:text-white/80 transition">
          <X className="w-3.5 h-3.5" />
        </button>
      )}
    </div>
  );
}

type ToastKind = 'success' | 'error' | 'info';
export interface ToastItem {
  id: number;
  message: string;
  kind: ToastKind;
}

export function ToastStack({ toasts, onClose }: { toasts: ToastItem[]; onClose: (id: number) => void }) {
  if (toasts.length === 0) return null;
  return (
    <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-[60] flex flex-col gap-2 items-center pointer-events-none">
      {toasts.map((t) => {
        const colorMap: Record<ToastKind, { bg: string; fg: string; icon: React.ReactNode }> = {
          success: { bg: 'rgba(34,197,94,0.95)', fg: 'white', icon: <CheckCircle2 className="w-4 h-4" /> },
          error: { bg: 'rgba(239,68,68,0.95)', fg: 'white', icon: <AlertCircle className="w-4 h-4" /> },
          info: { bg: 'rgba(59,130,246,0.95)', fg: 'white', icon: <AlertTriangle className="w-4 h-4" /> },
        };
        const c = colorMap[t.kind];
        return (
          <div
            key={t.id}
            className="px-4 py-2 rounded-full flex items-center gap-2 text-sm pointer-events-auto admin-toast-in"
            style={{ background: c.bg, color: c.fg, boxShadow: '0 8px 24px rgba(0,0,0,0.4)' }}
            onClick={() => onClose(t.id)}
          >
            {c.icon}
            <span className="max-w-[60vw] break-words">{t.message}</span>
          </div>
        );
      })}
    </div>
  );
}

/**
 * useToast 升级版：支持 success/error/info 三档，自动堆叠不互相覆盖。
 * 旧的 useToast 保持兼容（show(msg) 等价于 toast.success(msg)）。
 */
export function useToastStack() {
  const [toasts, setToasts] = React.useState<ToastItem[]>([]);
  const counterRef = React.useRef(0);

  const push = React.useCallback((kind: ToastKind, message: string, ttlMs = 2800) => {
    const id = ++counterRef.current;
    setToasts((prev) => [...prev, { id, kind, message }]);
    window.setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, ttlMs);
  }, []);

  const close = React.useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const api = React.useMemo(
    () => ({
      success: (msg: string) => push('success', msg),
      error: (msg: string) => push('error', msg, 4000),
      info: (msg: string) => push('info', msg),
    }),
    [push],
  );

  return { toasts, close, toast: api };
}

/** 旧 API 保留以兼容；新代码请用 useToastStack。 */
export function useToast(): [string | null, (msg: string) => void] {
  const [toast, setToast] = React.useState<string | null>(null);
  const show = React.useCallback((msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(null), 2500);
  }, []);
  return [toast, show];
}

export function Toast({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div
      className="fixed bottom-6 left-1/2 -translate-x-1/2 px-4 py-2 rounded-full flex items-center gap-2 text-sm text-white z-50"
      style={{ background: 'rgba(0, 200, 100, 0.9)', boxShadow: '0 8px 24px rgba(0,0,0,0.4)' }}
    >
      <CheckCircle2 className="w-4 h-4" />
      {message}
    </div>
  );
}

/** 空状态：用于列表查询为空时。 */
export function EmptyState({
  title,
  hint,
  icon,
  action,
}: {
  title: string;
  hint?: string;
  icon?: React.ReactNode;
  action?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center py-16 gap-3 text-center">
      <div
        className="w-16 h-16 rounded-2xl flex items-center justify-center"
        style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)' }}
      >
        {icon ?? <Inbox className="w-7 h-7 text-white/40" />}
      </div>
      <div className="text-sm text-white/80 font-medium">{title}</div>
      {hint && <div className="text-xs text-white/40 max-w-xs">{hint}</div>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

/**
 * 全屏 loading 蒙层。content 可选，多用于"AI 生成中"——告诉用户大概等多久。
 * 不允许点击穿透；按 Esc 不关闭（避免长任务被误中断）。
 */
export function LoadingOverlay({
  visible,
  title,
  hint,
  progress,
}: {
  visible: boolean;
  title: string;
  hint?: string;
  /** 0~1 之间的进度比例；不传时显示无限旋转。 */
  progress?: number;
}) {
  if (!visible) return null;
  const pct = progress != null ? Math.round(Math.min(1, Math.max(0, progress)) * 100) : null;
  return (
    <div
      className="fixed inset-0 z-[55] flex items-center justify-center"
      style={{ background: 'rgba(0,0,0,0.65)', backdropFilter: 'blur(6px)' }}
    >
      <div
        className="px-8 py-6 rounded-2xl flex flex-col items-center gap-3 max-w-sm"
        style={{ background: 'rgba(20,24,32,0.95)', border: '1px solid rgba(255,255,255,0.08)' }}
      >
        <Loader2 className="w-9 h-9 text-cyan-400 animate-spin" />
        <div className="text-base text-white font-medium">{title}</div>
        {hint && <div className="text-xs text-white/50 text-center">{hint}</div>}
        {pct != null && (
          <div className="w-56 mt-1">
            <div className="h-1.5 rounded-full overflow-hidden" style={{ background: 'rgba(255,255,255,0.1)' }}>
              <div
                className="h-full transition-all"
                style={{ width: `${pct}%`, background: 'linear-gradient(90deg, #00d4ff, #4cc9ff)' }}
              />
            </div>
            <div className="text-[11px] text-white/40 mt-1 text-right">{pct}%</div>
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * 二次确认弹窗：替代原生 confirm()，支持危险操作红色按钮 + 自定义文案。
 * 通过 useConfirm hook 命令式调用：const ok = await confirm({ title, ... });
 */
export interface ConfirmOptions {
  title: string;
  description?: string;
  confirmText?: string;
  cancelText?: string;
  danger?: boolean;
}

export function useConfirm() {
  const [state, setState] = React.useState<{
    options: ConfirmOptions;
    resolve: (v: boolean) => void;
  } | null>(null);

  const confirm = React.useCallback(
    (options: ConfirmOptions) =>
      new Promise<boolean>((resolve) => {
        setState({ options, resolve });
      }),
    [],
  );

  const handle = React.useCallback(
    (ok: boolean) => {
      if (state) {
        state.resolve(ok);
        setState(null);
      }
    },
    [state],
  );

  React.useEffect(() => {
    if (!state) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') handle(false);
      if (e.key === 'Enter') handle(true);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [state, handle]);

  const dialog = state ? (
    <div
      className="fixed inset-0 z-[58] flex items-center justify-center px-4"
      style={{ background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(4px)' }}
      onClick={() => handle(false)}
    >
      <div
        className="rounded-2xl p-5 w-full max-w-sm"
        style={{ background: 'rgba(20,24,32,0.98)', border: '1px solid rgba(255,255,255,0.1)' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="text-base text-white font-semibold mb-2">{state.options.title}</div>
        {state.options.description && (
          <div className="text-sm text-white/60 mb-4 leading-relaxed">{state.options.description}</div>
        )}
        <div className="flex gap-2 justify-end">
          <button
            onClick={() => handle(false)}
            className="px-4 py-1.5 text-sm rounded-lg transition"
            style={{ background: 'rgba(255,255,255,0.06)', color: 'white' }}
          >
            {state.options.cancelText ?? '取消'}
          </button>
          <button
            onClick={() => handle(true)}
            className="px-4 py-1.5 text-sm rounded-lg transition font-medium"
            style={{
              background: state.options.danger ? 'rgba(239,68,68,0.9)' : 'linear-gradient(90deg,#00d4ff,#4cc9ff)',
              color: state.options.danger ? 'white' : '#001520',
            }}
          >
            {state.options.confirmText ?? '确认'}
          </button>
        </div>
      </div>
    </div>
  ) : null;

  return { confirm, dialog };
}

/**
 * 全局注入运营后台输入控件 / Toast 动效样式。所有 Tab 共用，AdminLayout 注入一次。
 */
export const adminInputStyle = `
  .admin-input {
    display: block;
    width: 100%;
    padding: 8px 12px;
    background: rgba(0,0,0,0.4);
    color: white;
    border: 1px solid rgba(255,255,255,0.1);
    border-radius: 8px;
    outline: none;
    font-size: 14px;
  }
  .admin-input:focus { border-color: rgba(0,212,255,0.5); }

  @keyframes admin-toast-in {
    from { opacity: 0; transform: translateY(8px) scale(0.96); }
    to { opacity: 1; transform: translateY(0) scale(1); }
  }
  .admin-toast-in { animation: admin-toast-in 180ms ease-out; }

  @keyframes admin-fade-in {
    from { opacity: 0; }
    to { opacity: 1; }
  }
  .admin-fade-in { animation: admin-fade-in 220ms ease-out; }
`;
