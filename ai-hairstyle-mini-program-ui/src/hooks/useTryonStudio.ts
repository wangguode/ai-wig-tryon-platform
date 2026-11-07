import { useCallback, useRef, useState } from 'react';
import { HairStyle } from '../data/mockData';
import { api, pollTask } from '../api/client';
import { TryonHistoryItem } from './useTryonHistory';
import { priceRangeFull } from '../utils/price';

export type TryonStatus = 'pending' | 'processing' | 'success' | 'failed';

export interface TryonEntry {
  key: string;
  product: HairStyle;
  adjustNote?: string;
  status: TryonStatus;
  progress: number;
  resultImage?: string;
  aiMode?: 'real' | 'seed' | 'mock' | 'fallback';
  errorMessage?: string;
  retryCount?: number;
  changeScore?: number;
  qualityNote?: string;
  strategyUsed?: string;
}

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** localStorage key：演示模式下选择的 AI 模型 key。 */
export const DEMO_AI_MODEL_KEY = 'DEMO_AI_MODEL';

/**
 * onSuccess 回调：每次成功生成时触发，外层用来落历史记录。
 * 提供 userImage 是为了在历史记录中保留"原图"快照，便于后续对比基线展示。
 */
export interface TryonStudioOptions {
  userImage?: string | null;
  onSuccess?: (item: TryonHistoryItem) => void;
}

/**
 * 试戴工作台状态管理：支持同时为多款发型并行生成效果图、轮询进度、缓存结果。
 * - 有 sessionId：走真实后端（提交 + 轮询）
 * - 无 sessionId / 后端异常：离线降级，用发型图作为效果占位（保证演示不中断）
 */
export function useTryonStudio(sessionId: string | null, options: TryonStudioOptions = {}) {
  const { userImage, onSuccess } = options;
  const [entries, setEntries] = useState<Record<string, TryonEntry>>({});
  const startedRef = useRef<Set<string>>(new Set());

  const update = useCallback((key: string, patch: Partial<TryonEntry>) => {
    setEntries((prev) => ({ ...prev, [key]: { ...prev[key], ...patch } as TryonEntry }));
  }, []);

  const keyFor = useCallback(
    (product: HairStyle, adjustNote?: string) => {
      // cache key 里含 preferredModel，切换模型后同款发型重新生成而不是复用缓存
      const model = localStorage.getItem(DEMO_AI_MODEL_KEY) ?? '';
      const modelSuffix = model ? `::${model}` : '';
      return adjustNote ? `${product.id}::${adjustNote}${modelSuffix}` : `${product.id}${modelSuffix}`;
    },
    [],
  );

  const generate = useCallback(
    (product: HairStyle, adjustNote?: string): string => {
      const key = keyFor(product, adjustNote);
      // 已生成或正在生成则直接复用（点击切换零等待）
      if (startedRef.current.has(key)) return key;
      startedRef.current.add(key);

      setEntries((prev) => ({
        ...prev,
        [key]: { key, product, adjustNote, status: 'pending', progress: 5 },
      }));

      // 把成功结果落到外层历史记录（业界惯例：每次生成自动入库，时间倒序）
      const pushHistory = (taskId: string, resultImage: string, strategy?: string) => {
        if (!onSuccess) return;
        try {
          onSuccess({
            id: taskId,
            productId: product.id,
            productName: product.name,
            productPrice: priceRangeFull(product),
            resultImage,
            beforeImage: userImage || product.image,
            adjustNote,
            strategy,
            createdAt: Date.now(),
          });
        } catch {
          // 历史落库失败不影响试戴主流程
        }
      };

      (async () => {
        // 离线降级
        if (!sessionId) {
          for (let p = 25; p <= 90; p += 18) {
            await delay(320);
            update(key, { status: 'processing', progress: p });
          }
          await delay(300);
          update(key, { status: 'success', progress: 100, resultImage: product.image });
          pushHistory(`offline-${key}-${Date.now()}`, product.image, 'offline');
          return;
        }
        try {
          update(key, { status: 'processing', progress: 10 });
          const preferredModel = localStorage.getItem(DEMO_AI_MODEL_KEY) ?? undefined;
          const { taskId } = await api.submitTryon({
            sessionId,
            wigProductId: product.id,
            adjustmentNote: adjustNote,
            preferredModel: preferredModel || undefined,
          });
          const final = await pollTask(
            taskId,
            (s) => update(key, { status: 'processing', progress: Math.max(10, s.progress ?? 10) }),
            { intervalMs: 1500, timeoutMs: 180000 },
          );
          if (final.status === 'SUCCESS') {
            const resultImage = final.resultImageUrl ?? product.image;
            update(key, {
              status: 'success',
              progress: 100,
              resultImage,
              aiMode: final.aiMode ?? 'fallback',
              retryCount: final.retryCount ?? 0,
              changeScore: final.changeScore ?? undefined,
              qualityNote: final.qualityNote ?? undefined,
              strategyUsed: final.strategyUsed ?? undefined,
            });
            pushHistory(taskId, resultImage, final.strategyUsed ?? undefined);
          } else {
            update(key, {
              status: 'failed',
              progress: 100,
              aiMode: final.aiMode ?? 'fallback',
              errorMessage: final.errorMessage ?? final.qualityNote ?? '生成失败，请重试',
              retryCount: final.retryCount ?? 0,
              changeScore: final.changeScore ?? undefined,
              qualityNote: final.qualityNote ?? undefined,
              strategyUsed: final.strategyUsed ?? undefined,
            });
          }
        } catch {
          // 在线模式异常：明确失败，避免“看似成功但结果未变”的误解
          update(key, {
            status: 'failed',
            progress: 100,
            aiMode: 'fallback',
            errorMessage: '生成失败，请重试或切换款式',
          });
        }
      })();

      return key;
    },
    [sessionId, keyFor, update, onSuccess, userImage],
  );

  const generateMany = useCallback(
    (products: HairStyle[]) => products.forEach((p) => generate(p)),
    [generate],
  );

  const reset = useCallback(() => {
    startedRef.current = new Set();
    setEntries({});
  }, []);

  return { entries, generate, generateMany, keyFor, reset };
}
