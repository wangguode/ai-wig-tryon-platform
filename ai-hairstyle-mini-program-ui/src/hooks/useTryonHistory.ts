import { useCallback, useEffect, useState } from 'react';

const STORAGE_KEY = 'tryon_history_v1';
// localStorage 单 key 上限 5MB，每条记录主要存 url 字符串，30 条足够 demo 用
// 超过则按 FIFO 丢弃最早的，防止长时间使用导致超限
const MAX_HISTORY = 30;

export interface TryonHistoryItem {
  id: string;              // taskId（在线模式）或 client uuid（离线降级）
  productId: number;
  productName: string;
  productPrice: string;    // 价格区间快照（避免商品库下架后展示空白）
  resultImage: string;     // 后端 url 或本地图（离线时）
  beforeImage: string;     // 用户原图 url
  adjustNote?: string;     // 局部调整文案，如"齐刘海"
  strategy?: string;       // 后端 strategyUsed 原值（仅记录用，不展示）
  createdAt: number;       // ms timestamp
  liked?: boolean;
  rating?: number;
}

const readStorage = (): TryonHistoryItem[] => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((x): x is TryonHistoryItem =>
      x && typeof x.id === 'string' && typeof x.resultImage === 'string',
    );
  } catch {
    return [];
  }
};

const writeStorage = (list: TryonHistoryItem[]) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
  } catch (e) {
    // 超出 5MB 时浏览器抛 QuotaExceededError，砍半重试一次保住一部分
    try {
      const half = list.slice(0, Math.floor(list.length / 2));
      localStorage.setItem(STORAGE_KEY, JSON.stringify(half));
    } catch {
      // 二次失败则放弃；下次启动会以空列表重建
    }
  }
};

/**
 * 试戴历史记录管理（前端 localStorage 持久化）。
 * 业界惯例：时间倒序、上限 FIFO、本地存储不上报后端（隐私友好）。
 */
export function useTryonHistory() {
  const [items, setItems] = useState<TryonHistoryItem[]>(() => readStorage());

  // 多 tab 同步：监听 storage 事件（同源不同 tab 修改 localStorage 时触发）
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key === STORAGE_KEY) setItems(readStorage());
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  const persist = useCallback((next: TryonHistoryItem[]) => {
    setItems(next);
    writeStorage(next);
  }, []);

  /** 新增一条；同 id 已存在则覆盖（避免同一次任务重复插入） */
  const add = useCallback((item: TryonHistoryItem) => {
    setItems((prev) => {
      const without = prev.filter((x) => x.id !== item.id);
      const next = [item, ...without].slice(0, MAX_HISTORY);
      writeStorage(next);
      return next;
    });
  }, []);

  const remove = useCallback((id: string) => {
    setItems((prev) => {
      const next = prev.filter((x) => x.id !== id);
      writeStorage(next);
      return next;
    });
  }, []);

  const removeMany = useCallback((ids: string[]) => {
    const set = new Set(ids);
    setItems((prev) => {
      const next = prev.filter((x) => !set.has(x.id));
      writeStorage(next);
      return next;
    });
  }, []);

  const clear = useCallback(() => persist([]), [persist]);

  const toggleLike = useCallback((id: string) => {
    setItems((prev) => {
      const next = prev.map((x) => (x.id === id ? { ...x, liked: !x.liked } : x));
      writeStorage(next);
      return next;
    });
  }, []);

  return { items, add, remove, removeMany, clear, toggleLike, MAX_HISTORY };
}
