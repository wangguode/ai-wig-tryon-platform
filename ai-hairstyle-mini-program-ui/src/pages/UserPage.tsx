import React, { useState } from 'react';
import { ChevronRight, Camera, History, Heart, Settings, Shield, Bell, HelpCircle, LogOut, Award, Zap, Cpu } from 'lucide-react';
import { historyItems } from '../data/mockData';
import { StarRating } from '../components/StarRating';
import { DEMO_AI_MODEL_KEY } from '../hooks/useTryonStudio';

interface UserPageProps {
  onNavigate: (page: string) => void;
}

/** 支持演示切换的 AI 模型列表。 */
const AI_MODELS = [
  { key: 'gpt-image-1',    label: 'GPT Image 1.0',  tag: '较快',       tagColor: '#60a5fa', providerColor: '#00d4ff', provider: 'OpenAI' },
  { key: 'gpt-image-1.5', label: 'GPT Image 1.5', tag: '最强编辑', tagColor: '#f59e0b', providerColor: '#00d4ff', provider: 'OpenAI' },
  { key: 'gpt-image-2',   label: 'GPT Image 2',   tag: '',         tagColor: '',         providerColor: '#00d4ff', provider: 'OpenAI' },
  { key: 'gemini-3-pro-image',     label: 'Gemini 3 Pro',   tag: '推理规划', tagColor: '#34d399', providerColor: '#4ade80', provider: 'Google' },
  { key: 'gemini-3.1-flash-image', label: 'Gemini 3.1 Flash', tag: '快速',   tagColor: '#34d399', providerColor: '#4ade80', provider: 'Google' },
  { key: 'seedream-4.5',  label: 'Seedream 4.5', tag: '中文 SOTA', tagColor: '#a78bfa', providerColor: '#a78bfa', provider: 'ByteDance' },
  { key: 'doubao-seedream-5',  label: 'Doubao Seedream 5.0', tag: '火山方舟',  tagColor: '#f472b6', providerColor: '#f472b6', provider: 'ByteDance' },
  { key: 'flux-2-max',    label: 'Flux 2 Max',   tag: '高性能',  tagColor: '#fb923c', providerColor: '#fb923c', provider: 'BFL' },
  { key: 'qwen',          label: 'Qwen 万象',    tag: '推荐·最快',   tagColor: '#60a5fa', providerColor: '#60a5fa', provider: '阿里云' },
  { key: 'qwen-yunwu',    label: 'Qwen 云雾版',  tag: '速度快',     tagColor: '#60a5fa', providerColor: '#38bdf8', provider: '阿里·云雾' },
] as const;

type ModelKey = typeof AI_MODELS[number]['key'];

const menuItems = [
  { icon: History, label: '试戴历史', sublabel: '查看所有试戴记录', page: 'user-history', color: '#00d4ff' },
  { icon: Heart, label: '收藏发型', sublabel: '已收藏 6 款发型', page: 'user-favorites', color: '#b347ff' },
  { icon: Bell, label: '消息通知', sublabel: '3条未读消息', page: '', color: '#ff9500' },
  { icon: Shield, label: '隐私设置', sublabel: '保护您的数据安全', page: '', color: '#00ff96' },
  { icon: HelpCircle, label: '帮助中心', sublabel: '常见问题解答', page: '', color: '#6b7280' },
  { icon: Settings, label: '设置', sublabel: '账号与偏好设置', page: '', color: '#6b7280' },
];

export const UserPage: React.FC<UserPageProps> = ({ onNavigate }) => {
  const [, setAvatarLoaded] = useState(false);
  const [selectedModel, setSelectedModel] = useState<ModelKey>(
    () => (localStorage.getItem(DEMO_AI_MODEL_KEY) as ModelKey | null) ?? 'qwen',
  );

  const handleSelectModel = (key: ModelKey) => {
    setSelectedModel(key);
    localStorage.setItem(DEMO_AI_MODEL_KEY, key);
  };

  return (
    <div className="page-enter pb-24">
      {/* Profile header */}
      <div
        className="relative overflow-hidden px-4 pt-12 pb-6"
        style={{ background: 'linear-gradient(135deg, #050a14, #0a1628)' }}
      >
        <div className="absolute inset-0 tech-grid opacity-20" />
        <div
          className="absolute -top-12 -right-12 w-48 h-48 rounded-full"
          style={{ background: 'radial-gradient(circle, rgba(179,71,255,0.12) 0%, transparent 70%)' }}
        />
        <div
          className="absolute top-0 left-0 right-0 h-0.5"
          style={{ background: 'linear-gradient(90deg, transparent, #b347ff, #00d4ff, transparent)' }}
        />

        <div className="relative flex items-center gap-4">
          {/* Avatar */}
          <div className="relative">
            <div
              className="w-18 h-18 rounded-full overflow-hidden flex items-center justify-center"
              style={{
                width: 72,
                height: 72,
                border: '2px solid rgba(0,212,255,0.4)',
                boxShadow: '0 0 20px rgba(0,212,255,0.2)',
                background: 'rgba(10,22,40,0.8)',
              }}
            >
              <img
                src="/images/avatar-placeholder.jpg"
                alt="Avatar"
                className="w-full h-full object-cover"
                onLoad={() => setAvatarLoaded(true)}
              />
            </div>
            {/* Camera button */}
            <button
              className="absolute -bottom-1 -right-1 w-6 h-6 rounded-full flex items-center justify-center"
              style={{ background: 'linear-gradient(135deg, #00d4ff, #b347ff)' }}
            >
              <Camera size={12} style={{ color: '#050a14' }} />
            </button>
          </div>

          <div className="flex-1">
            <div className="flex items-center gap-2 mb-0.5">
              <h1 className="text-lg font-bold text-white">用户_88661</h1>
              <span className="chip chip-purple text-xs">VIP</span>
            </div>
            <p className="text-xs mb-2" style={{ color: '#4b5563' }}>ID: 10086 · 加入于 2024年1月</p>
            <div className="flex gap-4">
              {[
                { label: '试戴次数', value: '23' },
                { label: '收藏款式', value: '6' },
                { label: '获得评分', value: '4.8' },
              ].map((stat) => (
                <div key={stat.label} className="text-center">
                  <p className="text-sm font-bold gradient-text">{stat.value}</p>
                  <p className="text-xs" style={{ color: '#374151' }}>{stat.label}</p>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Level bar */}
        <div className="relative mt-4">
          <div className="flex items-center justify-between mb-1.5">
            <div className="flex items-center gap-1.5">
              <Award size={13} style={{ color: '#f59e0b' }} />
              <span className="text-xs font-medium" style={{ color: '#f59e0b' }}>体验官 Lv.3</span>
            </div>
            <span className="text-xs" style={{ color: '#4b5563' }}>再试戴 7 次升级</span>
          </div>
          <div className="h-1.5 rounded-full overflow-hidden" style={{ background: 'rgba(255,255,255,0.06)' }}>
            <div
              className="h-full rounded-full progress-bar"
              style={{ width: '76%' }}
            />
          </div>
        </div>
      </div>

      {/* Quick actions */}
      <div className="px-4 mt-4">
        <div className="grid grid-cols-3 gap-2">
          {[
            { icon: Zap, label: '开始试戴', page: 'tryon-upload', color: '#00d4ff', bg: 'rgba(0,212,255,0.1)' },
            { icon: History, label: '历史记录', page: 'user-history', color: '#b347ff', bg: 'rgba(179,71,255,0.1)' },
            { icon: Heart, label: '我的收藏', page: 'user-favorites', color: '#ff47b3', bg: 'rgba(255,71,179,0.1)' },
          ].map((action) => {
            const Icon = action.icon;
            return (
              <button
                key={action.label}
                onClick={() => action.page && onNavigate(action.page)}
                className="flex flex-col items-center gap-1.5 py-3 rounded-xl transition-all card-hover"
                style={{
                  background: action.bg,
                  border: `1px solid ${action.color}30`,
                }}
              >
                <Icon size={20} style={{ color: action.color }} />
                <span className="text-xs font-medium" style={{ color: action.color }}>{action.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* AI 模型选择 */}
      <div className="px-4 mt-4">
        <div className="flex items-center gap-2 mb-3">
          <Cpu size={14} style={{ color: '#00d4ff' }} />
          <span className="text-sm font-semibold text-white">AI 模型</span>
          <span className="text-xs ml-auto" style={{ color: '#4b5563' }}>切换后下次试戴生效</span>
        </div>
        <div className="flex flex-col gap-2">
          {AI_MODELS.map((m) => {
            const active = selectedModel === m.key;
            return (
              <button
                key={m.key}
                onClick={() => handleSelectModel(m.key)}
                className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all text-left"
                style={{
                  background: active
                    ? `linear-gradient(135deg, ${m.providerColor}18, ${m.providerColor}08)`
                    : 'rgba(10,22,40,0.6)',
                  border: active
                    ? `1px solid ${m.providerColor}55`
                    : '1px solid rgba(0,212,255,0.06)',
                  boxShadow: active ? `0 0 12px ${m.providerColor}18` : undefined,
                }}
              >
                {/* 选中指示点 */}
                <div
                  className="w-4 h-4 rounded-full flex-shrink-0 flex items-center justify-center"
                  style={{
                    border: active ? `2px solid ${m.providerColor}` : '2px solid rgba(255,255,255,0.12)',
                    background: active ? m.providerColor : 'transparent',
                  }}
                >
                  {active && <div className="w-1.5 h-1.5 rounded-full bg-black" />}
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className={`text-sm font-medium ${active ? 'text-white' : ''}`} style={{ color: active ? undefined : '#9ca3af' }}>
                      {m.label}
                    </span>
                    {m.tag && (
                      <span
                        className="text-xs px-1.5 py-0.5 rounded-md"
                        style={{
                          background: `${m.tagColor}20`,
                          color: m.tagColor,
                          border: `1px solid ${m.tagColor}40`,
                          fontSize: 10,
                        }}
                      >
                        {m.tag}
                      </span>
                    )}
                  </div>
                  <span className="text-xs" style={{ color: '#374151' }}>{m.provider}</span>
                </div>

                {active && (
                  <span className="text-xs font-medium" style={{ color: m.providerColor }}>使用中</span>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* Recent history */}
      <div className="px-4 mt-4">
        <div className="flex items-center justify-between mb-2">
          <span className="text-sm font-semibold text-white">最近试戴</span>
          <button
            className="text-xs"
            style={{ color: '#00d4ff' }}
            onClick={() => onNavigate('user-history')}
          >
            全部 →
          </button>
        </div>
        <div className="flex gap-2.5 overflow-x-auto pb-1" style={{ scrollbarWidth: 'none' }}>
          {historyItems.map((item) => (
            <div
              key={item.id}
              className="flex-shrink-0 w-24 rounded-xl overflow-hidden"
              style={{ border: '1px solid rgba(0,212,255,0.1)' }}
            >
              <div className="relative h-28">
                <img src={item.image} alt="" className="w-full h-full object-cover" />
                <div className="absolute inset-0" style={{ background: 'linear-gradient(to top, rgba(5,10,20,0.85), transparent)' }} />
                <div className="absolute bottom-1 left-1 right-1">
                  <p className="text-xs font-medium text-white truncate" style={{ fontSize: 10 }}>{item.hairName}</p>
                  <StarRating rating={item.rating} size={9} />
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Menu */}
      <div className="px-4 mt-4">
        <div
          className="rounded-2xl overflow-hidden"
          style={{ background: 'rgba(10,22,40,0.8)', border: '1px solid rgba(0,212,255,0.08)' }}
        >
          {menuItems.map((item, i) => {
            const Icon = item.icon;
            return (
              <button
                key={item.label}
                className="w-full flex items-center gap-3 px-4 py-3 text-left transition-all"
                style={{
                  borderBottom: i < menuItems.length - 1 ? '1px solid rgba(0,212,255,0.06)' : 'none',
                  background: 'transparent',
                }}
                onClick={() => item.page && onNavigate(item.page)}
              >
                <div
                  className="w-8 h-8 rounded-xl flex items-center justify-center flex-shrink-0"
                  style={{ background: `${item.color}15` }}
                >
                  <Icon size={16} style={{ color: item.color }} />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-white">{item.label}</p>
                  <p className="text-xs truncate" style={{ color: '#374151' }}>{item.sublabel}</p>
                </div>
                <ChevronRight size={14} style={{ color: '#374151' }} />
              </button>
            );
          })}
        </div>
      </div>

      {/* Logout */}
      <div className="px-4 mt-3">
        <button
          className="w-full py-3 rounded-xl text-sm font-medium flex items-center justify-center gap-2 transition-all"
          style={{
            background: 'rgba(255,100,100,0.06)',
            border: '1px solid rgba(255,100,100,0.15)',
            color: '#ff6464',
          }}
        >
          <LogOut size={14} />
          退出登录
        </button>
      </div>
    </div>
  );
};
