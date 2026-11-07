import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Sparkles,
  Wand2,
  Users,
  Image as ImageIcon,
  RefreshCw,
  CheckCircle2,
  AlertTriangle,
  Loader2,
  XCircle,
  RotateCcw,
  Activity,
  Zap,
  ArrowRight,
} from 'lucide-react';
import { adminApi, BatchJob, BatchJobItem, DashboardData, ModelAsset } from '../../api/admin';
import { ErrorBanner, ToastStack, useConfirm, useToastStack } from './adminUi';
import { CustomerImportWizard } from './CustomerImportWizard';

/**
 * Dashboard：物料就绪进度卡 + 三个一键批量填充按钮。
 * <p>
 * 设计意图：让运营在 1 屏内看清楚"全链路是否打通"，并通过 3 个按钮
 * 把 16 个本体 + 5 个模特 + 16 个 showcase 全部 AI 填充到位。
 */
export function DashboardTab({ token }: { token: string }) {
  const [data, setData] = useState<DashboardData | null>(null);
  const [models, setModels] = useState<ModelAsset[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [activeJob, setActiveJob] = useState<BatchJob | null>(null);
  const [pickedModelId, setPickedModelId] = useState<number | null>(null);

  const { toasts, close: closeToast, toast } = useToastStack();
  const { confirm, dialog: confirmDialog } = useConfirm();

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [d, m] = await Promise.all([
        adminApi.dashboard(token),
        adminApi.listModelAssets(token),
      ]);
      setData(d);
      setModels(m);
      if (pickedModelId == null && m.length > 0) {
        setPickedModelId(m[0].id);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [token, pickedModelId]);

  useEffect(() => {
    void reload();
  }, [reload]);

  // 有活跃任务时每 2s 轮询一次进度 + dashboard 数据
  useEffect(() => {
    if (!activeJob || activeJob.status !== 'RUNNING') return;
    const t = setInterval(async () => {
      try {
        const updated = await adminApi.jobStatus(token, activeJob.jobId);
        setActiveJob(updated);
        if (updated.status !== 'RUNNING') {
          await reload();
          if (updated.failed === 0) toast.success(`${updated.description} 已完成`);
          else toast.error(`${updated.description} 完成（${updated.failed} 失败）`);
        }
      } catch (e) {
        // 网络抖动忽略，下一次轮询继续
      }
    }, 2000);
    return () => clearInterval(t);
  }, [activeJob, token, reload, toast]);

  const handleRegenerateBareWigs = async () => {
    if (!data) return;
    const missing = data.wigAssetTotal - data.wigAssetRealCount;
    const ok = await confirm({
      title: 'AI 批量重画本体图',
      description: `将用 AI 文生图重画 ${data.wigAssetTotal} 个本体（含 ${missing} 个还在用占位图的）。\n4 路并行约 5~10 分钟。每张约 0.05 元（共约 ${(data.wigAssetTotal * 0.05).toFixed(2)} 元）。`,
      confirmText: '开始',
    });
    if (!ok) return;
    try {
      const job = await adminApi.startBareWigBatch(token);
      setActiveJob(job);
      toast.info('批量任务已启动，正在并行生成');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const handleFillShowcases = async () => {
    if (!data || pickedModelId == null) return;
    const ok = await confirm({
      title: 'AI 批量生成 showcase + 自动设主图',
      description: `将用模特 #${pickedModelId} 给 ${data.wigAssetTotal} 个本体生成"模特戴假发图"，并自动审批 + 设为 C 端主图。\n约 8~15 分钟，共约 ${(data.wigAssetTotal * 0.08).toFixed(2)} 元。\n\n建议先确认本体图都已经是真本体（左侧切换到「假发本体」Tab 查看）。`,
      confirmText: '开始',
    });
    if (!ok) return;
    try {
      const job = await adminApi.startFillShowcases(token, pickedModelId);
      setActiveJob(job);
      toast.info('批量填充已启动');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const handleRegenerateModels = async () => {
    if (!data) return;
    const ok = await confirm({
      title: 'AI 批量生成模特照片',
      description: `将用 AI 文生图按性别/年龄/脸型生成 ${data.modelAssetTotal} 张亚洲面孔模特照片，覆盖现有占位图。\n约 3~5 分钟。`,
      confirmText: '开始',
    });
    if (!ok) return;
    try {
      const job = await adminApi.startModelPortraitBatch(token);
      setActiveJob(job);
      toast.info('已启动模特批量生成');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  // 演示模式：清空所有 showcase + 把 wigAsset/modelAsset 还原到 classpath 种子
  // 给录屏 / demo 用，不要在生产环境点
  const handleResetDemo = async () => {
    const ok = await confirm({
      title: '重置演示数据',
      description:
        '将清空全部已生成的成品图，并把假发本体 / 模特库还原成"占位图"状态。\n\n用于录屏 demo 后从 0 开始重跑一键填充。\n生产数据不可恢复，请谨慎。',
      confirmText: '我确认重置',
      danger: true,
    });
    if (!ok) return;
    try {
      setLoading(true);
      const result = await adminApi.resetDemoData(token);
      toast.success(result.message || '演示数据已重置');
      setActiveJob(null);
      await reload();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  };

  const cards = useMemo(() => {
    if (!data) return [];
    return [
      {
        key: 'wigs',
        index: '01',
        title: '假发本体',
        subtitle: 'Bare Wig Asset',
        icon: <Wand2 className="w-6 h-6" />,
        accent: '#00d4ff',
        ratio: data.wigAssetRealCount / Math.max(1, data.wigAssetTotal),
        value: `${data.wigAssetRealCount}`,
        denom: `/ ${data.wigAssetTotal}`,
        hint: data.wigAssetRealCount === data.wigAssetTotal ? '全部已是真本体图' : '其余还是占位图，建议批量重画',
      },
      {
        key: 'models',
        index: '02',
        title: '模特库',
        subtitle: 'Model Asset',
        icon: <Users className="w-6 h-6" />,
        accent: '#7dd3fc',
        ratio: data.modelAssetRealCount / Math.max(1, data.modelAssetTotal),
        value: `${data.modelAssetRealCount}`,
        denom: `/ ${data.modelAssetTotal}`,
        hint: data.modelAssetRealCount === data.modelAssetTotal ? '全部已是真模特照片' : '其余还是占位图',
      },
      {
        key: 'showcases',
        index: '03',
        title: '成品图',
        subtitle: 'Approved Showcase',
        icon: <ImageIcon className="w-6 h-6" />,
        accent: '#b347ff',
        ratio: data.showcaseApprovedCount / Math.max(1, data.wigAssetTotal),
        value: `${data.showcaseApprovedCount}`,
        denom: ``,
        hint: `已设为主图：${data.showcasePrimaryCount} 张`,
      },
      {
        key: 'products',
        index: '04',
        title: 'C 端商品',
        subtitle: 'Bound Products',
        icon: <Sparkles className="w-6 h-6" />,
        accent: '#ff6ad5',
        ratio: data.productWithPrimaryCount / Math.max(1, data.productTotal),
        value: `${data.productWithPrimaryCount}`,
        denom: `/ ${data.productTotal}`,
        hint:
          data.productWithPrimaryCount === data.productTotal
            ? 'C 端用户能看到完整合成图'
            : '其余商品 C 端会回退到本体图',
      },
    ];
  }, [data]);

  // Hero 横幅展示的整链路百分比：四项加权平均
  const overallPct = useMemo(() => {
    if (!data) return 0;
    const wigs = data.wigAssetRealCount / Math.max(1, data.wigAssetTotal);
    const models = data.modelAssetRealCount / Math.max(1, data.modelAssetTotal);
    const shows = data.showcaseApprovedCount / Math.max(1, data.wigAssetTotal);
    const prods = data.productWithPrimaryCount / Math.max(1, data.productTotal);
    return Math.round(((wigs + models + shows + prods) / 4) * 100);
  }, [data]);

  const jobBusy = !!activeJob && activeJob.status === 'RUNNING';

  return (
    <div className="space-y-8 admin-fade-in">
      {/* ─────── Hero 横幅 ─────── */}
      <section
        className="relative overflow-hidden rounded-3xl"
        style={{
          background:
            'linear-gradient(135deg, rgba(0,212,255,0.12) 0%, rgba(108,93,255,0.10) 50%, rgba(179,71,255,0.12) 100%), #0a1424',
          border: '1px solid rgba(0,212,255,0.18)',
          boxShadow: '0 20px 60px rgba(0,0,0,0.5), 0 0 0 1px rgba(255,255,255,0.04) inset',
        }}
      >
        {/* 背景装饰 */}
        <div
          className="absolute pointer-events-none"
          style={{
            width: 480,
            height: 480,
            right: -120,
            top: -180,
            background: 'radial-gradient(circle, rgba(0,212,255,0.18), transparent 60%)',
            filter: 'blur(20px)',
          }}
        />
        <div
          className="absolute pointer-events-none"
          style={{
            width: 380,
            height: 380,
            left: -80,
            bottom: -160,
            background: 'radial-gradient(circle, rgba(179,71,255,0.18), transparent 60%)',
            filter: 'blur(20px)',
          }}
        />

        <div className="relative grid lg:grid-cols-[1.4fr_1fr] gap-6 px-7 py-7">
          {/* 左：标题 + 大数字 + 状态 */}
          <div className="flex flex-col justify-between gap-5">
            <div>
              <div className="flex items-center gap-2 text-[11px] text-cyan-300/80 uppercase tracking-[0.2em] mb-2">
                <Activity className="w-3.5 h-3.5" />
                Operations Dashboard
              </div>
              <div className="text-white font-semibold text-2xl leading-tight">
                假发资产运营 · 全链路就绪监控
              </div>
              <div className="text-sm text-white/55 mt-2 leading-relaxed">
                本体 · 模特 · 成品图 · C 端主图 — 一屏掌握 AI 物料生产链路是否打通
              </div>
            </div>
            <div className="flex items-end gap-6">
              <div>
                <div className="text-[10px] text-white/40 uppercase tracking-[0.2em] mb-1">Overall Readiness</div>
                <div className="flex items-baseline gap-1">
                  <span
                    className="font-bold leading-none"
                    style={{
                      fontSize: 64,
                      letterSpacing: '-0.04em',
                      background: 'linear-gradient(135deg, #00d4ff, #b347ff)',
                      WebkitBackgroundClip: 'text',
                      WebkitTextFillColor: 'transparent',
                    }}
                  >
                    {overallPct}
                  </span>
                  <span className="text-2xl text-white/60 font-semibold">%</span>
                </div>
              </div>
              {data?.ready ? (
                <div
                  className="px-3 py-1.5 rounded-full flex items-center gap-2 text-xs font-medium mb-2"
                  style={{
                    background: 'rgba(34,197,94,0.15)',
                    border: '1px solid rgba(34,197,94,0.4)',
                    color: '#86efac',
                  }}
                >
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  链路 100% 就绪 · 可对外开放
                </div>
              ) : (
                <div
                  className="px-3 py-1.5 rounded-full flex items-center gap-2 text-xs font-medium mb-2"
                  style={{
                    background: 'rgba(250,204,21,0.12)',
                    border: '1px solid rgba(250,204,21,0.4)',
                    color: '#fde68a',
                  }}
                >
                  <AlertTriangle className="w-3.5 h-3.5" />
                  尚未就绪 · 请用一键填充补齐物料
                </div>
              )}
            </div>
          </div>

          {/* 右：流水线 4 步迷你视图 + 操作 */}
          <div className="flex flex-col gap-4">
            <div
              className="rounded-2xl p-4 flex-1"
              style={{ background: 'rgba(0,0,0,0.25)', border: '1px solid rgba(255,255,255,0.08)' }}
            >
              <div className="text-[10px] text-white/40 uppercase tracking-[0.2em] mb-3">Pipeline Status</div>
              <div className="grid grid-cols-4 gap-2">
                {cards.map((c) => {
                  const ok = c.ratio >= 1;
                  return (
                    <div key={c.key} className="flex flex-col items-center gap-1.5">
                      <div
                        className="w-9 h-9 rounded-lg flex items-center justify-center"
                        style={{
                          background: ok ? 'rgba(34,197,94,0.18)' : `${c.accent}1a`,
                          border: ok ? '1px solid rgba(34,197,94,0.5)' : `1px solid ${c.accent}55`,
                          color: ok ? '#86efac' : c.accent,
                        }}
                      >
                        {ok ? <CheckCircle2 className="w-4 h-4" /> : c.icon}
                      </div>
                      <div className="text-[10px] text-white/60 text-center">{c.title}</div>
                    </div>
                  );
                })}
              </div>
            </div>

            <div className="flex items-center gap-2">
              <button
                onClick={reload}
                disabled={loading}
                className="flex-1 px-3 py-2.5 rounded-lg flex items-center justify-center gap-2 text-sm text-white/85 disabled:opacity-50 hover:bg-white/10 transition-colors"
                style={{ background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.1)' }}
              >
                <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
                刷新
              </button>
              <button
                onClick={handleResetDemo}
                disabled={loading || jobBusy}
                className="flex-1 px-3 py-2.5 rounded-lg flex items-center justify-center gap-2 text-sm disabled:opacity-40 hover:bg-red-500/15 transition-colors"
                style={{
                  background: 'rgba(239,68,68,0.08)',
                  border: '1px solid rgba(239,68,68,0.3)',
                  color: '#fca5a5',
                }}
                title="清空 showcase 并把本体/模特还原为占位图，用于录屏 demo 重跑"
              >
                <RotateCcw className="w-4 h-4" />
                重置演示
              </button>
            </div>
          </div>
        </div>
      </section>

      <ErrorBanner message={error} onClose={() => setError(null)} />

      <CustomerImportWizard token={token} />

      {/* ─────── 章节 1：物料就绪概览 ─────── */}
      <section>
        <SectionHeader
          eyebrow="01 · Readiness"
          title="物料就绪概览"
          desc="四个维度的链路完整性，任一项缺失会导致 C 端体验降级"
        />
        <div className="grid gap-4 mt-4" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))' }}>
          {cards.map((c) => (
            <ReadinessCard key={c.key} card={c} />
          ))}
        </div>
      </section>

      {/* ─────── 章节 2：AI 物料生产线 ─────── */}
      <section>
        <SectionHeader
          eyebrow="02 · AI Pipeline"
          title="AI 物料生产线"
          desc={'按「模特 → 本体 → 成品图」顺序一键生成，DashScope 串行调用约 15~20 分钟全部就位'}
          right={
            jobBusy ? (
              <span
                className="px-2.5 py-1 rounded-full text-[11px] flex items-center gap-1.5"
                style={{ background: 'rgba(0,212,255,0.12)', color: '#7dd3fc', border: '1px solid rgba(0,212,255,0.3)' }}
              >
                <Loader2 className="w-3 h-3 animate-spin" />
                批量任务运行中 · 操作已锁
              </span>
            ) : (
              <span className="text-[11px] text-white/40">空闲 · 可以下发任务</span>
            )
          }
        />
        <div className="grid gap-4 mt-4" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))' }}>
          <PipelineStep
            number="①"
            title="模特照片"
            description="按性别/年龄/脸型 AI 生成亚洲面孔"
            count={data ? `${data.modelAssetRealCount}/${data.modelAssetTotal}` : '—'}
            done={!!data && data.modelAssetRealCount === data.modelAssetTotal}
            disabled={jobBusy}
            onClick={handleRegenerateModels}
            color="#00d4ff"
          />
          <PipelineStep
            number="②"
            title="假发本体"
            description="按描述 AI 重画白底纯本体图"
            count={data ? `${data.wigAssetRealCount}/${data.wigAssetTotal}` : '—'}
            done={!!data && data.wigAssetRealCount === data.wigAssetTotal}
            disabled={jobBusy}
            onClick={handleRegenerateBareWigs}
            color="#b347ff"
          />
          <ShowcaseFillStep
            data={data}
            models={models}
            pickedModelId={pickedModelId}
            setPickedModelId={setPickedModelId}
            disabled={jobBusy}
            onClick={handleFillShowcases}
          />
        </div>
      </section>

      {/* ─────── 章节 3：批量任务监控 ─────── */}
      <section>
        <SectionHeader
          eyebrow="03 · Job Monitor"
          title="批量任务监控"
          desc="实时进度 · 串行调度 · DashScope 节流策略"
        />
        <div className="mt-4">
          {activeJob ? (
            <JobPanel job={activeJob} />
          ) : (
            <div
              className="rounded-2xl px-6 py-10 text-center"
              style={{
                background: 'rgba(10,22,40,0.4)',
                border: '1px dashed rgba(255,255,255,0.1)',
              }}
            >
              <Zap className="w-8 h-8 text-white/20 mx-auto mb-3" />
              <div className="text-sm text-white/55">暂无运行中的批量任务</div>
              <div className="text-[11px] text-white/30 mt-1">点上面任意一步即可下发</div>
            </div>
          )}
        </div>
      </section>

      {confirmDialog}
      <ToastStack toasts={toasts} onClose={closeToast} />
    </div>
  );
}

// ───── 子组件 ─────

function SectionHeader({
  eyebrow,
  title,
  desc,
  right,
}: {
  eyebrow: string;
  title: string;
  desc?: string;
  right?: React.ReactNode;
}) {
  return (
    <div className="flex items-end justify-between gap-4 flex-wrap">
      <div>
        <div className="text-[10px] text-cyan-300/70 uppercase tracking-[0.25em]">{eyebrow}</div>
        <div className="text-white text-lg font-semibold mt-1">{title}</div>
        {desc && <div className="text-[12px] text-white/50 mt-1">{desc}</div>}
      </div>
      {right}
    </div>
  );
}

interface CardItem {
  key: string;
  index: string;
  title: string;
  subtitle: string;
  icon: React.ReactNode;
  accent: string;
  ratio: number;
  value: string;
  denom: string;
  hint: string;
}

function ReadinessCard({ card }: { card: CardItem }) {
  const pct = Math.round(card.ratio * 100);
  const ok = card.ratio >= 1;
  return (
    <div
      className="rounded-2xl p-5 relative overflow-hidden transition-transform hover:-translate-y-0.5"
      style={{
        background: 'linear-gradient(160deg, rgba(15,28,48,0.85), rgba(8,14,26,0.9))',
        border: ok ? '1px solid rgba(34,197,94,0.4)' : `1px solid ${card.accent}33`,
        boxShadow: ok
          ? '0 8px 24px rgba(34,197,94,0.08), 0 0 0 1px rgba(255,255,255,0.03) inset'
          : `0 8px 24px ${card.accent}14, 0 0 0 1px rgba(255,255,255,0.03) inset`,
      }}
    >
      {/* 角标编号 */}
      <div
        className="absolute -top-2 -right-2 text-5xl font-bold opacity-[0.06] select-none"
        style={{ color: card.accent }}
      >
        {card.index}
      </div>

      <div className="flex items-start justify-between mb-4 relative">
        <div>
          <div className="text-[10px] text-white/40 uppercase tracking-[0.18em]">{card.subtitle}</div>
          <div className="text-white font-semibold mt-0.5">{card.title}</div>
        </div>
        <div
          className="w-10 h-10 rounded-xl flex items-center justify-center"
          style={{
            background: ok ? 'rgba(34,197,94,0.15)' : `${card.accent}18`,
            color: ok ? '#86efac' : card.accent,
          }}
        >
          {ok ? <CheckCircle2 className="w-5 h-5" /> : card.icon}
        </div>
      </div>

      <div className="flex items-baseline gap-1 relative">
        <span
          className="font-bold text-white leading-none"
          style={{ fontSize: 38, letterSpacing: '-0.03em' }}
        >
          {card.value}
        </span>
        {card.denom && <span className="text-base text-white/40 font-medium">{card.denom}</span>}
      </div>

      <div className="mt-4 relative">
        <div className="h-1.5 rounded-full overflow-hidden" style={{ background: 'rgba(255,255,255,0.06)' }}>
          <div
            className="h-full transition-all duration-500"
            style={{
              width: `${pct}%`,
              background: ok
                ? 'linear-gradient(90deg, #22c55e, #4ade80)'
                : `linear-gradient(90deg, ${card.accent}, ${card.accent}cc)`,
            }}
          />
        </div>
        <div className="flex items-center justify-between mt-2">
          <div className="text-[11px] text-white/45">{card.hint}</div>
          <div className="text-[11px] font-medium" style={{ color: ok ? '#86efac' : card.accent }}>
            {pct}%
          </div>
        </div>
      </div>
    </div>
  );
}

function PipelineStep({
  number,
  title,
  description,
  count,
  done,
  disabled,
  onClick,
  color,
}: {
  number: string;
  title: string;
  description: string;
  count: string;
  done: boolean;
  disabled: boolean;
  onClick: () => void;
  color: string;
}) {
  return (
    <div
      className="rounded-2xl p-5 flex flex-col gap-3 transition-transform hover:-translate-y-0.5"
      style={{
        background: 'linear-gradient(160deg, rgba(15,28,48,0.8), rgba(8,14,26,0.9))',
        border: done ? '1px solid rgba(34,197,94,0.4)' : '1px solid rgba(255,255,255,0.08)',
        boxShadow: '0 8px 24px rgba(0,0,0,0.25)',
      }}
    >
      <div className="flex items-center gap-3">
        <div
          className="w-9 h-9 rounded-lg flex items-center justify-center font-bold text-base"
          style={{
            background: done ? 'rgba(34,197,94,0.15)' : `${color}18`,
            color: done ? '#86efac' : color,
            border: `1px solid ${done ? 'rgba(34,197,94,0.4)' : color + '40'}`,
          }}
        >
          {number}
        </div>
        <div className="flex-1 min-w-0">
          <div className="text-white font-medium">{title}</div>
          <div className="text-[11px] text-white/45 truncate">{description}</div>
        </div>
        {done && <CheckCircle2 className="w-5 h-5 text-green-400 shrink-0" />}
      </div>
      <button
        onClick={onClick}
        disabled={disabled}
        className="w-full px-3 py-2.5 rounded-lg text-sm text-white font-medium disabled:opacity-40 flex items-center justify-center gap-2 transition-transform hover:scale-[1.01]"
        style={{
          background: done
            ? 'rgba(34,197,94,0.15)'
            : `linear-gradient(135deg, ${color}, ${color}cc)`,
          border: done ? '1px solid rgba(34,197,94,0.4)' : 'none',
          color: done ? '#86efac' : 'white',
          boxShadow: done ? undefined : `0 8px 20px ${color}40`,
        }}
      >
        {done ? `已就绪（${count}）· 重新生成` : `开始填充（${count}）`}
        {!done && <ArrowRight className="w-4 h-4" />}
      </button>
    </div>
  );
}

function ShowcaseFillStep({
  data,
  models,
  pickedModelId,
  setPickedModelId,
  disabled,
  onClick,
}: {
  data: DashboardData | null;
  models: ModelAsset[];
  pickedModelId: number | null;
  setPickedModelId: (id: number | null) => void;
  disabled: boolean;
  onClick: () => void;
}) {
  const done = !!data && data.productWithPrimaryCount === data.productTotal;
  const color = '#ff6ad5';
  return (
    <div
      className="rounded-2xl p-5 flex flex-col gap-3"
      style={{
        background: 'linear-gradient(160deg, rgba(15,28,48,0.8), rgba(8,14,26,0.9))',
        border: done ? '1px solid rgba(34,197,94,0.4)' : '1px solid rgba(255,255,255,0.08)',
        boxShadow: '0 8px 24px rgba(0,0,0,0.25)',
      }}
    >
      <div className="flex items-center gap-3">
        <div
          className="w-9 h-9 rounded-lg flex items-center justify-center font-bold text-base"
          style={{
            background: done ? 'rgba(34,197,94,0.15)' : `${color}18`,
            color: done ? '#86efac' : color,
            border: `1px solid ${done ? 'rgba(34,197,94,0.4)' : color + '40'}`,
          }}
        >
          ③
        </div>
        <div className="flex-1 min-w-0">
          <div className="text-white font-medium">成品图填充</div>
          <div className="text-[11px] text-white/45 truncate">本体 + 模特合成 → 自动审批 + 设主图</div>
        </div>
        {done && <CheckCircle2 className="w-5 h-5 text-green-400 shrink-0" />}
      </div>
      <div className="flex flex-col gap-1">
        <label className="text-[10px] text-white/40 uppercase tracking-wider">选择基底模特</label>
        <select
          value={pickedModelId ?? ''}
          onChange={(e) => setPickedModelId(e.target.value ? Number(e.target.value) : null)}
          className="admin-input"
        >
          <option value="">— 选模特 —</option>
          {models.map((m) => (
            <option key={m.id} value={m.id}>
              #{m.id} {m.name} · {m.gender === 'M' ? '男' : '女'} · {m.faceShape}
            </option>
          ))}
        </select>
      </div>
      <button
        onClick={onClick}
        disabled={pickedModelId == null || !data || disabled}
        className="w-full px-3 py-2.5 rounded-lg text-sm text-white font-medium disabled:opacity-40 flex items-center justify-center gap-2 transition-transform hover:scale-[1.01]"
        style={{
          background: done
            ? 'rgba(34,197,94,0.15)'
            : 'linear-gradient(135deg, #ff6ad5, #b347ff)',
          border: done ? '1px solid rgba(34,197,94,0.4)' : 'none',
          color: done ? '#86efac' : 'white',
          boxShadow: done ? undefined : '0 8px 20px rgba(179,71,255,0.4)',
        }}
      >
        {done
          ? `已就绪（${data?.productWithPrimaryCount}/${data?.productTotal}）· 重跑`
          : `批量生成 + 自动审批`}
        {!done && <ArrowRight className="w-4 h-4" />}
      </button>
      <div className="text-[10px] text-white/40">
        {data ? `当前 ${data.productWithPrimaryCount}/${data.productTotal} 商品已绑主图` : ''}
      </div>
    </div>
  );
}

function JobPanel({ job }: { job: BatchJob }) {
  const pct = Math.round(((job.completed + job.failed) / Math.max(1, job.total)) * 100);
  const running = job.status === 'RUNNING';
  return (
    <div
      className="rounded-2xl p-6"
      style={{
        background: 'linear-gradient(160deg, rgba(15,28,48,0.85), rgba(8,14,26,0.9))',
        border: running ? '1px solid rgba(0,212,255,0.4)' : '1px solid rgba(34,197,94,0.4)',
        boxShadow: running
          ? '0 8px 24px rgba(0,212,255,0.12), 0 0 0 1px rgba(255,255,255,0.03) inset'
          : '0 8px 24px rgba(34,197,94,0.08), 0 0 0 1px rgba(255,255,255,0.03) inset',
      }}
    >
      <div className="flex items-center justify-between mb-4 gap-4">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 text-[10px] uppercase tracking-[0.2em]" style={{ color: running ? '#7dd3fc' : '#86efac' }}>
            {running ? <Loader2 className="w-3 h-3 animate-spin" /> : <CheckCircle2 className="w-3 h-3" />}
            {running ? 'Running' : 'Completed'}
          </div>
          <div className="text-white font-semibold mt-1 truncate">{job.description}</div>
          <div className="text-[11px] text-white/35 mt-0.5">jobId · {job.jobId}</div>
        </div>
        <div className="text-right shrink-0">
          <div className="flex items-baseline gap-1 justify-end">
            <span className="font-bold text-white leading-none" style={{ fontSize: 32, letterSpacing: '-0.03em' }}>
              {job.completed}
            </span>
            <span className="text-base text-white/40 font-medium">/ {job.total}</span>
          </div>
          {job.failed > 0 && (
            <div className="text-[11px] text-red-400 mt-1 flex items-center gap-1 justify-end">
              <AlertTriangle className="w-3 h-3" />
              {job.failed} 失败
            </div>
          )}
        </div>
      </div>
      <div className="h-2 rounded-full overflow-hidden mb-4" style={{ background: 'rgba(255,255,255,0.06)' }}>
        <div
          className="h-full transition-all duration-500"
          style={{
            width: `${pct}%`,
            background:
              job.failed > 0
                ? 'linear-gradient(90deg, #facc15, #f87171)'
                : running
                ? 'linear-gradient(90deg, #00d4ff, #6c5dff)'
                : 'linear-gradient(90deg, #22c55e, #4ade80)',
          }}
        />
      </div>
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2 max-h-64 overflow-y-auto pr-1" style={{ scrollbarWidth: 'thin' }}>
        {job.items.map((it) => (
          <ItemBadge key={it.entityId} item={it} />
        ))}
      </div>
    </div>
  );
}

function ItemBadge({ item }: { item: BatchJobItem }) {
  let icon: React.ReactNode;
  let bg: string;
  if (item.state === 'DONE') {
    icon = <CheckCircle2 className="w-3 h-3 text-green-400" />;
    bg = 'rgba(34,197,94,0.08)';
  } else if (item.state === 'FAILED') {
    icon = <XCircle className="w-3 h-3 text-red-400" />;
    bg = 'rgba(239,68,68,0.08)';
  } else if (item.state === 'RUNNING') {
    icon = <Loader2 className="w-3 h-3 animate-spin text-cyan-300" />;
    bg = 'rgba(0,212,255,0.08)';
  } else {
    icon = <span className="w-3 h-3 rounded-full inline-block" style={{ background: 'rgba(255,255,255,0.2)' }} />;
    bg = 'rgba(255,255,255,0.03)';
  }
  return (
    <div
      className="flex items-center gap-2 px-2 py-1.5 rounded-md text-[11px]"
      style={{ background: bg, border: '1px solid rgba(255,255,255,0.06)' }}
      title={item.reason ?? undefined}
    >
      {icon}
      <span className="text-white/70 truncate flex-1" title={item.entityName}>
        #{item.entityId} {item.entityName}
      </span>
    </div>
  );
}
