import React, { useEffect, useState } from 'react';
import {
  LogOut,
  Layers,
  User as UserIcon,
  Sparkles,
  AlertCircle,
  LayoutDashboard,
  Wand2,
  ArrowRight,
  PanelLeftClose,
  PanelLeftOpen,
} from 'lucide-react';
import { useAdminAuth } from '../../hooks/useAdminAuth';
import { adminInputStyle } from './adminUi';
import { DashboardTab } from './DashboardTab';
import { WigAssetTab } from './WigAssetTab';
import { ModelAssetTab } from './ModelAssetTab';
import { ShowcaseStudioTab } from './ShowcaseStudioTab';

/**
 * 运营管理后台主入口。URL hash = #admin 时由 App.tsx 路由到这里。
 *
 * 品牌：Aura · 假发资产运营管理平台。
 * 三层资产 + AI 编排 + 商品主图绑定一站式。
 *
 * 见 docs/wig-asset-system-design.md。
 */
export function AdminPage() {
  const { token, authed, pinging, error, login, logout } = useAdminAuth();

  if (!authed) {
    return <AdminLoginScreen pinging={pinging} error={error} onSubmit={login} />;
  }
  return <AdminLayout token={token!} onLogout={logout} />;
}

/** 品牌 Logo：渐变背景 + 字母 A。统一登录页/侧边栏共用。 */
function BrandMark({ size = 40, withGlow = false }: { size?: number; withGlow?: boolean }) {
  return (
    <div
      className="rounded-xl flex items-center justify-center font-bold text-white relative"
      style={{
        width: size,
        height: size,
        fontSize: size * 0.5,
        background: 'linear-gradient(135deg, #00d4ff 0%, #6c5dff 50%, #b347ff 100%)',
        boxShadow: withGlow ? '0 8px 24px rgba(108,93,255,0.45), 0 0 0 1px rgba(255,255,255,0.06) inset' : undefined,
        letterSpacing: '-0.04em',
      }}
    >
      <span style={{ textShadow: '0 1px 2px rgba(0,0,0,0.3)' }}>A</span>
      {withGlow && (
        <span
          className="absolute -bottom-1 -right-1 w-3 h-3 rounded-full"
          style={{ background: '#00d4ff', boxShadow: '0 0 8px #00d4ff' }}
        />
      )}
    </div>
  );
}

// ---------- 登录 ----------

function AdminLoginScreen({
  pinging,
  error,
  onSubmit,
}: {
  pinging: boolean;
  error: string | null;
  onSubmit: (token: string) => Promise<boolean>;
}) {
  const [input, setInput] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLocalError(null);
    if (!input.trim()) {
      setLocalError('请输入管理员 token');
      return;
    }
    await onSubmit(input.trim());
  };

  return (
    <div
      className="min-h-screen flex items-center justify-center px-4 relative overflow-hidden"
      style={{ background: '#040810' }}
    >
      <style>{adminInputStyle}</style>
      {/* 装饰性背景光斑 */}
      <div
        className="absolute"
        style={{
          width: 600,
          height: 600,
          left: '-15%',
          top: '-20%',
          background: 'radial-gradient(circle, rgba(0,212,255,0.18), transparent 60%)',
          filter: 'blur(20px)',
          pointerEvents: 'none',
        }}
      />
      <div
        className="absolute"
        style={{
          width: 500,
          height: 500,
          right: '-10%',
          bottom: '-15%',
          background: 'radial-gradient(circle, rgba(179,71,255,0.18), transparent 60%)',
          filter: 'blur(20px)',
          pointerEvents: 'none',
        }}
      />

      <div className="grid lg:grid-cols-[1.1fr_1fr] gap-10 max-w-5xl w-full items-center relative">
        {/* 左侧：品牌介绍 */}
        <div className="hidden lg:block space-y-6 pl-2">
          <div className="flex items-center gap-3">
            <BrandMark size={48} withGlow />
            <div>
              <div className="text-white font-semibold text-xl tracking-tight">Aura</div>
              <div className="text-[11px] text-white/40 uppercase tracking-wider">Wig Ops Platform</div>
            </div>
          </div>
          <div>
            <div className="text-3xl text-white font-semibold leading-tight">
              假发资产运营
              <br />
              <span
                style={{
                  background: 'linear-gradient(90deg, #00d4ff, #b347ff)',
                  WebkitBackgroundClip: 'text',
                  WebkitTextFillColor: 'transparent',
                }}
              >
                管理平台
              </span>
            </div>
            <div className="text-sm text-white/60 mt-3 leading-relaxed">
              AI 驱动的三层资产编排：本体 · 模特 · 成品图。
              <br />
              一键打通从生产到 C 端的全链路。
            </div>
          </div>
          <div className="space-y-2 pt-2">
            {[
              { icon: <Wand2 className="w-4 h-4" />, label: 'AI 文生图按描述重画白底纯本体' },
              { icon: <UserIcon className="w-4 h-4" />, label: '亚洲面孔模特库 AI 自动生成' },
              { icon: <Sparkles className="w-4 h-4" />, label: '本体 + 模特 → 成品图智能合成 + 自动审批' },
              { icon: <Layers className="w-4 h-4" />, label: 'C 端商品主图全链路透明替换' },
            ].map((f, i) => (
              <div key={i} className="flex items-center gap-3 text-sm text-white/75">
                <span
                  className="w-7 h-7 rounded-md flex items-center justify-center"
                  style={{ background: 'rgba(0,212,255,0.1)', color: '#7dd3fc' }}
                >
                  {f.icon}
                </span>
                {f.label}
              </div>
            ))}
          </div>
        </div>

        {/* 右侧：登录卡 */}
        <div
          className="rounded-2xl p-8 w-full"
          style={{
            background: 'linear-gradient(135deg, rgba(20,28,44,0.92), rgba(12,18,32,0.92))',
            border: '1px solid rgba(255,255,255,0.08)',
            boxShadow: '0 30px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(0,212,255,0.04) inset',
            backdropFilter: 'blur(8px)',
          }}
        >
          <div className="flex items-center gap-3 mb-6 lg:hidden">
            <BrandMark size={40} withGlow />
            <div>
              <div className="text-white font-semibold">Aura</div>
              <div className="text-[11px] text-white/40">假发资产运营管理平台</div>
            </div>
          </div>
          <div className="hidden lg:block mb-6">
            <div className="text-white font-semibold text-lg">登录运营后台</div>
            <div className="text-xs text-white/40 mt-1">使用分配给你的管理员 token 进入</div>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="text-xs text-gray-400 mb-2 block">管理员 Token</label>
              <input
                type="password"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                placeholder="请输入分配给你的 token"
                autoComplete="off"
                className="admin-input"
              />
            </div>
            {(localError || error) && (
              <div
                className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm"
                style={{ background: 'rgba(255,80,80,0.1)', color: '#ff8080' }}
              >
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{localError || error}</span>
              </div>
            )}
            <button
              type="submit"
              disabled={pinging}
              className="w-full py-3 rounded-lg text-white font-medium disabled:opacity-50 flex items-center justify-center gap-2 transition-transform hover:scale-[1.01]"
              style={{
                background: pinging ? 'rgba(0,212,255,0.3)' : 'linear-gradient(135deg, #00d4ff, #b347ff)',
                boxShadow: '0 8px 24px rgba(108,93,255,0.4)',
              }}
            >
              {pinging ? '验证中...' : '进入管理平台'}
              {!pinging && <ArrowRight className="w-4 h-4" />}
            </button>
            <div className="text-[11px] text-gray-500 leading-relaxed pt-2">
              后端需启动时携带 <code className="text-gray-400">WIG_ADMIN_ENABLED=true</code> 与{' '}
              <code className="text-gray-400">WIG_ADMIN_TOKEN=…</code>。
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

// ---------- 主布局 ----------

type TabId = 'dashboard' | 'wig-assets' | 'model-assets' | 'showcase-studio';

/** 演示期：仅开放本体 + 成品手工导入，隐藏模特库 / AI 批量能力。 */
const DEMO_MANUAL_ONLY = true;

const ALL_TABS: { id: TabId; label: string; icon: React.ComponentType<{ className?: string }>; desc: string }[] = [
  { id: 'dashboard', label: '链路就绪', icon: LayoutDashboard, desc: '物料进度 · 上架校验' },
  { id: 'wig-assets', label: '假发本体', icon: Wand2, desc: '上传本体图 · 自动上架' },
  { id: 'model-assets', label: '模特库', icon: UserIcon, desc: '陈列合成的素材' },
  { id: 'showcase-studio', label: '成品图工坊', icon: Sparkles, desc: '导入现成图 · 设主图' },
];

const TABS = DEMO_MANUAL_ONLY
  ? ALL_TABS.filter((t) => t.id !== 'model-assets')
  : ALL_TABS;

const SIDEBAR_COLLAPSED_KEY = 'wig_admin_sidebar_collapsed';

function AdminLayout({ token, onLogout }: { token: string; onLogout: () => void }) {
  const [tab, setTab] = useState<TabId>('dashboard');
  // 录屏时把侧栏收起，给内容区腾空间。状态持久化到 localStorage 避免每次刷新都要再点。
  const [collapsed, setCollapsed] = useState<boolean>(
    () => localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1',
  );
  const toggleCollapsed = () => {
    setCollapsed((c) => {
      const next = !c;
      localStorage.setItem(SIDEBAR_COLLAPSED_KEY, next ? '1' : '0');
      return next;
    });
  };

  // Ctrl+B / Cmd+B 切换侧栏（VSCode 同款），方便录屏时盲操
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'b') {
        e.preventDefault();
        toggleCollapsed();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  return (
    <div className="min-h-screen flex" style={{ background: '#050a14' }}>
      <style>{adminInputStyle}</style>
      {/* 左侧导航：collapsed=true 时收为 64px 仅图标 */}
      <aside
        className="shrink-0 flex flex-col transition-all duration-300"
        style={{
          width: collapsed ? 64 : 256,
          background: 'linear-gradient(180deg, #0c1424 0%, #050a14 100%)',
          borderRight: '1px solid rgba(255,255,255,0.06)',
        }}
      >
        <div
          className={`flex items-center ${collapsed ? 'justify-center px-0' : 'px-5 gap-3'} py-5`}
          style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}
        >
          <BrandMark size={collapsed ? 32 : 36} withGlow />
          {!collapsed && (
            <div className="min-w-0 flex-1">
              <div className="text-white font-semibold text-sm tracking-tight">Aura</div>
              <div className="text-[10px] text-white/40 uppercase tracking-wider truncate">
                Wig Ops Platform
              </div>
            </div>
          )}
        </div>

        {/* 折叠按钮区 */}
        <div className={`${collapsed ? 'px-2' : 'px-3'} pt-3 pb-1`}>
          <button
            onClick={toggleCollapsed}
            title={collapsed ? '展开导航 (Ctrl+B)' : '收起导航 (Ctrl+B)'}
            className="w-full flex items-center justify-center gap-2 py-2 rounded-lg text-white/55 hover:text-white transition-colors"
            style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.06)' }}
          >
            {collapsed ? <PanelLeftOpen className="w-4 h-4" /> : <PanelLeftClose className="w-4 h-4" />}
            {!collapsed && <span className="text-[11px]">收起导航</span>}
          </button>
        </div>

        {!collapsed && (
          <div className="px-5 pt-3 pb-2">
            <div className="text-[10px] text-white/30 uppercase tracking-wider">运营管理</div>
          </div>
        )}

        <nav className={`flex-1 ${collapsed ? 'px-2' : 'px-3'} space-y-1 ${collapsed ? 'pt-1' : ''}`}>
          {TABS.map((t) => {
            const Icon = t.icon;
            const active = tab === t.id;
            return (
              <button
                key={t.id}
                onClick={() => setTab(t.id)}
                title={collapsed ? `${t.label} · ${t.desc}` : undefined}
                className={`w-full text-left rounded-lg flex transition-all ${
                  collapsed ? 'p-3 justify-center' : 'px-3 py-3 items-start gap-3'
                }`}
                style={{
                  background: active ? 'rgba(0,212,255,0.1)' : 'transparent',
                  border: active ? '1px solid rgba(0,212,255,0.3)' : '1px solid transparent',
                  boxShadow: active ? '0 4px 16px rgba(0,212,255,0.15)' : undefined,
                }}
              >
                <Icon
                  className={`w-5 h-5 ${collapsed ? '' : 'mt-0.5'} shrink-0 ${
                    active ? 'text-cyan-300' : 'text-gray-400'
                  }`}
                />
                {!collapsed && (
                  <div className="flex-1 min-w-0">
                    <div className={`text-sm ${active ? 'text-white font-medium' : 'text-gray-300'}`}>
                      {t.label}
                    </div>
                    <div className="text-[11px] text-gray-500 mt-0.5 truncate">{t.desc}</div>
                  </div>
                )}
              </button>
            );
          })}
        </nav>

        <div className={`${collapsed ? 'p-2' : 'p-3'} space-y-2`}>
          {!collapsed && (
            <div
              className="text-[10px] text-white/40 px-3 py-2 rounded-lg leading-relaxed"
              style={{ background: 'rgba(0,212,255,0.04)', border: '1px solid rgba(0,212,255,0.1)' }}
            >
              <div className="text-white/70 font-medium mb-0.5">v1.0 · MVP</div>
              资产分层架构 + AI 编排
            </div>
          )}
          <button
            onClick={onLogout}
            title={collapsed ? '退出登录' : undefined}
            className={`w-full rounded-lg flex items-center text-sm text-gray-300 hover:text-white transition-colors ${
              collapsed ? 'justify-center p-2.5' : 'px-3 py-2 gap-2'
            }`}
            style={{ background: 'rgba(255,80,80,0.06)', border: '1px solid rgba(255,80,80,0.18)' }}
          >
            <LogOut className="w-4 h-4" />
            {!collapsed && <span>退出登录</span>}
          </button>
        </div>
      </aside>

      {/* 右侧内容 */}
      <main className="flex-1 overflow-x-hidden flex flex-col">
        <header
          className="sticky top-0 z-10 px-8 py-4 flex items-center justify-between"
          style={{
            background: 'rgba(8,14,26,0.85)',
            borderBottom: '1px solid rgba(255,255,255,0.06)',
            backdropFilter: 'blur(12px)',
          }}
        >
          <div className="flex items-center gap-3">
            <div
              className="w-9 h-9 rounded-lg flex items-center justify-center"
              style={{
                background: 'rgba(0,212,255,0.1)',
                border: '1px solid rgba(0,212,255,0.25)',
                color: '#7dd3fc',
              }}
            >
              {(() => {
                const Icon = TABS.find((t) => t.id === tab)?.icon;
                return Icon ? <Icon className="w-5 h-5" /> : null;
              })()}
            </div>
            <div>
              <div className="text-[10px] text-cyan-300/70 uppercase tracking-[0.2em]">
                {TABS.find((t) => t.id === tab)?.label}
              </div>
              <div className="text-base text-white font-semibold leading-tight">
                {tab === 'dashboard' && '链路就绪 Dashboard'}
                {tab === 'wig-assets' && '假发本体管理'}
                {tab === 'model-assets' && '模特库'}
                {tab === 'showcase-studio' && '成品图工坊'}
              </div>
            </div>
          </div>
          <div className="flex items-center gap-3 text-[11px] text-white/40">
            <span
              className="px-2 py-1 rounded-md"
              style={{ background: 'rgba(34,197,94,0.1)', color: '#86efac', border: '1px solid rgba(34,197,94,0.25)' }}
            >
              <span className="inline-block w-1.5 h-1.5 rounded-full mr-1.5" style={{ background: '#86efac' }} />
              系统在线
            </span>
            <span>
              {new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })}
            </span>
          </div>
        </header>

        <div className="flex-1 px-8 py-8">
          <div className="mx-auto" style={{ maxWidth: 1400 }}>
            {tab === 'dashboard' && <DashboardTab token={token} />}
            {tab === 'wig-assets' && <WigAssetTab token={token} />}
            {tab === 'model-assets' && <ModelAssetTab token={token} />}
            {tab === 'showcase-studio' && <ShowcaseStudioTab token={token} />}
          </div>
        </div>
      </main>
    </div>
  );
}
