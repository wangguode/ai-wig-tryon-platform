import { useState, useCallback, useEffect } from 'react';
import { NavBar } from './components/NavBar';
import { LoadingScreen } from './components/LoadingScreen';
import { HomePage } from './pages/HomePage';
import { UploadPage } from './pages/UploadPage';
import { SelectHairPage } from './pages/SelectHairPage';
import { TryonWallPage } from './pages/TryonWallPage';
import { StoreListPage } from './pages/StoreListPage';
import { StoreDetailPage } from './pages/StoreDetailPage';
import { UserPage } from './pages/UserPage';
import { HistoryPage } from './pages/HistoryPage';
import { FavoritesPage } from './pages/FavoritesPage';
import { AdminPage } from './pages/admin/AdminPage';
import { HairStyle, Store } from './data/mockData';
import { AnalyzeResult } from './api/client';

type PageId =
  | 'home'
  | 'tryon-upload'
  | 'tryon-select'
  | 'tryon-wall'
  | 'store-list'
  | 'store-detail'
  | 'user'
  | 'user-history'
  | 'user-favorites';

interface PageState {
  page: PageId;
  data?: any;
}

const PAGES_WITH_NAV: PageId[] = ['home', 'tryon-upload', 'store-list', 'user'];

function App() {
  const [loading, setLoading] = useState(true);
  const [history, setHistory] = useState<PageState[]>([{ page: 'home' }]);
  const [focusProduct, setFocusProduct] = useState<HairStyle | null>(null);
  const [userImage, setUserImage] = useState<string | null>(null);
  const [selectedStore, setSelectedStore] = useState<Store | null>(null);
  // 与后端联动的状态
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [analysis, setAnalysis] = useState<AnalyzeResult | null>(null);

  // 运营后台入口：URL hash = #admin（与 C 端流程完全隔离，不走 mobile-container 包装）
  // 设计动机：项目硬约束不引入 React Router，用 hash 实现最低成本路由分发。
  const [isAdminRoute, setIsAdminRoute] = useState(() => window.location.hash === '#admin');
  useEffect(() => {
    const onHash = () => setIsAdminRoute(window.location.hash === '#admin');
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);
  // 用户误访问 /admin 或 /admin（登录）时，统一纠正到 #admin
  useEffect(() => {
    const path = window.location.pathname;
    if (path === '/admin' || path === '/admin/' || (path.startsWith('/admin') && !/^\/admin\/[\w-]+/.test(path))) {
      if (window.location.hash !== '#admin') {
        window.history.replaceState(null, '', '/#admin');
        setIsAdminRoute(true);
      }
    }
  }, []);

  const currentState = history[history.length - 1];
  const currentPage = currentState.page;

  const navigateTo = useCallback((page: string, data?: any) => {
    setHistory((prev) => [...prev, { page: page as PageId, data }]);
  }, []);

  const goBack = useCallback(() => {
    setHistory((prev) => (prev.length > 1 ? prev.slice(0, -1) : prev));
  }, []);

  const navigateFromNav = useCallback((page: string) => {
    setHistory([{ page: page as PageId }]);
  }, []);

  const showNav = PAGES_WITH_NAV.includes(currentPage) ||
    currentPage === 'tryon-select' ||
    currentPage === 'store-detail' ||
    currentPage === 'user-history' ||
    currentPage === 'user-favorites';

  const handleSelectHair = (hair: HairStyle) => {
    // 从"全部发型"选一款 → 进入效果墙，并把该款作为焦点款一起生成
    setFocusProduct(hair);
    navigateTo('tryon-wall');
  };

  const handleUploadComplete = (
    imageData: string,
    sid: string | null,
    faceCheckStatus?: 'PENDING' | 'PASSED' | 'FAILED',
  ) => {
    setUserImage(imageData);
    setSessionId(sid);
    setAnalysis(null);
    setFocusProduct(null);
    navigateTo('tryon-select', { faceCheckStatus });
  };

  const handleViewStoreDetail = (store: Store) => {
    setSelectedStore(store);
    navigateTo('store-detail');
  };

  const renderPage = () => {
    switch (currentPage) {
      case 'home':
        return <HomePage onNavigate={navigateTo} />;

      case 'tryon-upload':
        return (
          <UploadPage
            onBack={goBack}
            onNext={handleUploadComplete}
          />
        );

      case 'tryon-select':
        return (
          <SelectHairPage
            sessionId={sessionId}
            analysis={analysis}
            onAnalysis={setAnalysis}
            uploadFaceCheckStatus={currentState.data?.faceCheckStatus}
            onBack={goBack}
            onSelect={handleSelectHair}
          />
        );

      case 'tryon-wall':
        return (
          <TryonWallPage
            sessionId={sessionId}
            userImage={userImage}
            analysis={analysis}
            focusProduct={focusProduct}
            onAnalysis={setAnalysis}
            onBack={goBack}
            onViewStore={() => navigateTo('store-list')}
          />
        );

      case 'store-list':
        return (
          <StoreListPage
            onBack={goBack}
            onViewDetail={handleViewStoreDetail}
          />
        );

      case 'store-detail':
        return selectedStore ? (
          <StoreDetailPage
            store={selectedStore}
            onBack={goBack}
          />
        ) : null;

      case 'user':
        return <UserPage onNavigate={navigateTo} />;

      case 'user-history':
        return (
          <HistoryPage
            onBack={goBack}
            onNavigate={navigateTo}
          />
        );

      case 'user-favorites':
        return (
          <FavoritesPage
            onBack={goBack}
            onNavigate={navigateTo}
          />
        );

      default:
        return <HomePage onNavigate={navigateTo} />;
    }
  };

  // 运营后台：跳过 LoadingScreen 与 mobile-container，直接全屏渲染管理界面
  if (isAdminRoute) {
    return <AdminPage />;
  }

  if (loading) {
    return <LoadingScreen onComplete={() => setLoading(false)} />;
  }

  return (
    <div
      className="min-h-screen"
      style={{ background: '#050a14' }}
    >
      {/* Mobile frame */}
      <div className="mobile-container">
        {/* Scrollable content */}
        <div
          className="min-h-screen overflow-y-auto"
          style={{ scrollbarWidth: 'none' }}
        >
          {renderPage()}
        </div>

        {/* Bottom navigation */}
        {showNav && (
          <NavBar
            currentPage={currentPage}
            onNavigate={navigateFromNav}
          />
        )}
      </div>

      {/* Desktop side decoration */}
      <div
        className="fixed top-0 left-0 right-0 bottom-0 pointer-events-none"
        style={{ zIndex: 0 }}
      >
        {/* Left panel */}
        <div
          className="hidden lg:flex fixed left-0 top-0 bottom-0 items-center justify-center"
          style={{
            width: 'calc((100vw - 420px) / 2)',
            background: 'rgba(5,10,20,0.95)',
          }}
        >
          <div className="text-center px-8">
            <div
              className="text-5xl font-black mb-4"
              style={{ fontFamily: '"Orbitron", monospace' }}
            >
              <span className="gradient-text">AI</span>
            </div>
            <p className="text-sm font-semibold text-white mb-2">智能发型试戴</p>
            <p className="text-xs leading-relaxed" style={{ color: '#374151' }}>
              基于深度学习的<br />专业级发型体验
            </p>
            <div className="mt-8 space-y-3">
              {[
                { label: '脸型适配度', value: '96%' },
                { label: '发丝真实度', value: '94%' },
                { label: '款式丰富度', value: '92%' },
              ].map((s) => (
                <div key={s.label} className="text-left">
                  <div className="flex justify-between mb-1">
                    <span className="text-xs" style={{ color: '#4b5563' }}>{s.label}</span>
                    <span className="text-xs gradient-text font-bold">{s.value}</span>
                  </div>
                  <div className="h-1 rounded-full overflow-hidden" style={{ background: 'rgba(255,255,255,0.06)' }}>
                    <div className="h-full rounded-full progress-bar" style={{ width: s.value.replace('%', '').replace('+', '') + '%' }} />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Right panel */}
        <div
          className="hidden lg:flex fixed right-0 top-0 bottom-0 items-center justify-center"
          style={{
            width: 'calc((100vw - 420px) / 2)',
            background: 'rgba(5,10,20,0.95)',
          }}
        >
          <div className="text-center px-8">
            <div className="text-4xl mb-3">🔬</div>
            <p className="text-sm font-semibold text-white mb-1">技术特性</p>
            <div className="space-y-2.5 mt-4 text-left">
              {[
                { icon: '⚡', label: '秒级生成', desc: '15-30秒完成' },
                { icon: '🔒', label: '隐私保护', desc: '24h自动删除' },
                { icon: '🎯', label: '精准适配', desc: '脸型智能分析' },
                { icon: '🌟', label: '高质量输出', desc: '高清写实效果' },
                { icon: '📍', label: '就近体验', desc: '全国门店网络' },
              ].map((item) => (
                <div
                  key={item.label}
                  className="flex items-center gap-2.5 px-3 py-2 rounded-xl"
                  style={{ background: 'rgba(10,22,40,0.8)', border: '1px solid rgba(0,212,255,0.08)' }}
                >
                  <span className="text-lg">{item.icon}</span>
                  <div>
                    <p className="text-xs font-semibold text-white">{item.label}</p>
                    <p className="text-xs" style={{ color: '#374151' }}>{item.desc}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default App;
